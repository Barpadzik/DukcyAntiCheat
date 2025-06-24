package pl.barpad.duckyanticheat.checks.movement;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pl.barpad.duckyanticheat.Main;
import pl.barpad.duckyanticheat.utils.DiscordHook;
import pl.barpad.duckyanticheat.utils.PermissionBypass;
import pl.barpad.duckyanticheat.utils.ViolationAlerts;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NoSlowDownB implements Listener {

    private final ViolationAlerts alerts;
    private final DiscordHook discord;
    private final ConfigManager config;

    // Map storing last known location for each player
    private final ConcurrentHashMap<UUID, Location> lastLocations = new ConcurrentHashMap<>();

    // Map for storing temporary immunity after using food items
    private final ConcurrentHashMap<UUID, Long> ignoreUntil = new ConcurrentHashMap<>();

    // Track last elytra and flight activity to ignore false positives
    private final ConcurrentHashMap<UUID, Long> lastElytra = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> wasGliding = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> wasFlying = new ConcurrentHashMap<>();

    // List of configured speed values to ignore in checks
    private final List<Double> ignoredSpeeds;

    // Small delta used to compare floating-point values
    private static final double EPSILON = 0.0001;

    public NoSlowDownB(Main plugin, ViolationAlerts alerts, DiscordHook discord, ConfigManager config) {
        this.alerts = alerts;
        this.discord = discord;
        this.config = config;
        this.ignoredSpeeds = config.getNoSlowDownBIgnoredSpeedValues();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // Handle player interaction with edible items → grant 1s immunity from check
    @EventHandler
    public void onItemUse(PlayerInteractEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (item.getType().isEdible()) {
            ignoreUntil.put(event.getPlayer().getUniqueId(), System.currentTimeMillis() + 1000);
        }
    }

    // Main check triggered every time the player moves
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Skip check if player is temporarily immune due to recent item use
        if (ignoreUntil.containsKey(uuid) && System.currentTimeMillis() < ignoreUntil.get(uuid)) return;

        // Skip if player has bypass permissions or irrelevant conditions
        if (PermissionBypass.hasBypass(player)) return;
        if (!config.isNoSlowDownBEnabled() || !player.isOnline() || player.isFlying() || !player.isHandRaised()) return;
        if (player.hasPermission("duckyac.bypass.noslowdown-b") || player.hasPermission("duckyac.bypass.noslowdown.*")) return;

        // Handle elytra gliding state changes and add temporary delay immunity
        boolean glidingNow = player.isGliding();
        if (!glidingNow && wasGliding.getOrDefault(uuid, false)) {
            lastElytra.put(uuid, System.currentTimeMillis());
        }
        wasGliding.put(uuid, glidingNow);
        if (!glidingNow && lastElytra.containsKey(uuid) && System.currentTimeMillis() - lastElytra.get(uuid) < 1000) return;

        // Handle creative flight state changes and add temporary delay immunity
        boolean flyingNow = player.isFlying();
        if (!flyingNow && wasFlying.getOrDefault(uuid, false)) {
            lastFlight.put(uuid, System.currentTimeMillis());
        }
        wasFlying.put(uuid, flyingNow);
        if (!flyingNow && lastFlight.containsKey(uuid) && System.currentTimeMillis() - lastFlight.get(uuid) < 1000) return;

        // Only check if the player is holding a bow
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.BOW) return;

        // Get current and previous location to calculate movement distance
        Location curr = player.getLocation();
        Location prev = lastLocations.getOrDefault(uuid, curr);
        double dist = curr.toVector().distance(prev.toVector());
        lastLocations.put(uuid, curr.clone());

        // Ignore if player moved vertically (e.g. jumping)
        if (Math.abs(curr.getY() - prev.getY()) > 0.001) return;

        // Skip if the movement distance matches any exempt values (e.g. due to lag)
        for (double ignored : ignoredSpeeds) {
            if (Math.abs(dist - ignored) < EPSILON) return;
        }

        // Calculate allowed max speed, modified by SPEED potion effects
        double maxSpeed = config.getNoSlowDownBMaxBowSpeed();
        PotionEffect speed = player.getPotionEffect(PotionEffectType.SPEED);
        if (speed != null) {
            maxSpeed *= 1.0 + (speed.getAmplifier() + 1) * 0.2;
        }

        // Skip check if player is on ice blocks (movement naturally increased)
        Material ground = player.getLocation().subtract(0, 1, 0).getBlock().getType();
        if (ground == Material.ICE || ground == Material.PACKED_ICE || ground == Material.BLUE_ICE) return;

        // Ignore if distance is outside max allowed check range
        if (dist > config.getNoSlowDownBMaxIgnoreSpeed()) return;

        // Ignore if speed is within an allowed threshold
        if (dist <= maxSpeed) return;

        // Player moved faster than allowed while drawing bow → violation
        alerts.reportViolation(player.getName(), "NoSlowDownB");
        int vl = alerts.getViolationCount(player.getName(), "NoSlowDownB");

        // Debug message for staff/admins
        if (config.isNoSlowDownBDebugMode()) {
            Bukkit.getLogger().info("[DuckyAntiCheat] (NoSlowDownB Debug) " + player.getName()
                    + " moved too fast with bow drawn: " + String.format("%.4f", dist)
                    + " (allowed: " + String.format("%.4f", maxSpeed) + ") (VL: " + vl + ")");
        }

        // Optionally revert player movement (teleport back)
        if (config.shouldNoSlowDownBCancelEvent()) {
            player.teleport(prev);
        }

        // Execute punishment if VL exceeds a configured threshold
        if (vl >= config.getMaxNoSlowDownBAlerts()) {
            String cmd = config.getNoSlowDownBCommand();
            alerts.executePunishment(player.getName(), "NoSlowDownB", cmd);
            discord.sendPunishmentCommand(player.getName(), cmd);
        }
    }
}