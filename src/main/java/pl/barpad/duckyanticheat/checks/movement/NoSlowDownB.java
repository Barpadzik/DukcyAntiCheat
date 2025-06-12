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

    private final ConcurrentHashMap<UUID, Location> lastLocations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> ignoreUntil = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<UUID, Long> lastElytra = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> wasGliding = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> wasFlying = new ConcurrentHashMap<>();

    private final List<Double> ignoredSpeeds;
    private static final double EPSILON = 0.0001;

    public NoSlowDownB(Main plugin, ViolationAlerts alerts, DiscordHook discord, ConfigManager config) {
        this.alerts = alerts;
        this.discord = discord;
        this.config = config;
        this.ignoredSpeeds = config.getNoSlowDownBIgnoredSpeedValues();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // Track when the player uses an edible item to grant temporary immunity
    @EventHandler
    public void onItemUse(PlayerInteractEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (item.getType().isEdible()) {
            ignoreUntil.put(event.getPlayer().getUniqueId(), System.currentTimeMillis() + 1000);
        }
    }

    // Main movement check triggered on every player movement
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (ignoreUntil.containsKey(uuid) && System.currentTimeMillis() < ignoreUntil.get(uuid)) {
            return;
        }

        // Skip check if player has bypass permission or certain conditions are not met
        if (PermissionBypass.hasBypass(player)) return;
        if (!config.isNoSlowDownBEnabled() || !player.isOnline() || player.isFlying() || !player.isHandRaised()) return;
        if (player.hasPermission("duckyac.bypass.noslowdown-b") || player.hasPermission("duckyac.bypass.noslowdown.*")) return;

        // Elytra gliding tracking with delay immunity
        boolean glidingNow = player.isGliding();
        if (!glidingNow && wasGliding.getOrDefault(uuid, false)) {
            lastElytra.put(uuid, System.currentTimeMillis());
        }
        wasGliding.put(uuid, glidingNow);
        if (!glidingNow && lastElytra.containsKey(uuid) && System.currentTimeMillis() - lastElytra.get(uuid) < 1000) return;

        // Creative flight tracking with delay immunity
        boolean flyingNow = player.isFlying();
        if (!flyingNow && wasFlying.getOrDefault(uuid, false)) {
            lastFlight.put(uuid, System.currentTimeMillis());
        }
        wasFlying.put(uuid, flyingNow);
        if (!flyingNow && lastFlight.containsKey(uuid) && System.currentTimeMillis() - lastFlight.get(uuid) < 1000) return;

        // Only trigger when holding a bow
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.BOW) return;

        // Calculate horizontal movement distance
        Location curr = player.getLocation();
        Location prev = lastLocations.getOrDefault(uuid, curr);
        double dist = curr.toVector().distance(prev.toVector());
        lastLocations.put(uuid, curr.clone());

        // Skip if player is moving vertically
        if (Math.abs(curr.getY() - prev.getY()) > 0.001) return;

        // Ignore movement if distance matches any configured exempt values
        for (double ignored : ignoredSpeeds) {
            if (Math.abs(dist - ignored) < EPSILON) return;
        }

        // Adjust max allowed speed based on potion effect
        double maxSpeed = config.getNoSlowDownBMaxBowSpeed();
        PotionEffect speed = player.getPotionEffect(PotionEffectType.SPEED);
        if (speed != null) {
            maxSpeed *= 1.0 + (speed.getAmplifier() + 1) * 0.2;
        }

        // Ignore if player is on ice
        Material ground = player.getLocation().subtract(0, 1, 0).getBlock().getType();
        if (ground == Material.ICE || ground == Material.PACKED_ICE || ground == Material.BLUE_ICE) return;

        // Skip if distance exceeds max ignore threshold or is under allowed speed
        if (dist > config.getNoSlowDownBMaxIgnoreSpeed()) return;
        if (dist <= maxSpeed) return;

        // Player exceeded speed while pulling bow → flag violation
        alerts.reportViolation(player.getName(), "NoSlowDownB");
        int vl = alerts.getViolationCount(player.getName(), "NoSlowDownB");

        // Debug message for admins
        if (config.isNoSlowDownBDebugMode()) {
            Bukkit.getLogger().info("[DuckyAntiCheat] (NoSlowDownB Debug) " + player.getName()
                    + " moved too fast with bow drawn: " + String.format("%.4f", dist)
                    + " (allowed: " + String.format("%.4f", maxSpeed) + ") (VL: " + vl + ")");
        }

        // Optionally cancel movement by teleporting the player back
        if (config.shouldNoSlowDownBCancelEvent()) {
            player.teleport(prev);
        }

        // Execute punishment if violation level exceeds threshold
        if (vl >= config.getMaxNoSlowDownBAlerts()) {
            String cmd = config.getNoSlowDownBCommand();
            alerts.executePunishment(player.getName(), "NoSlowDownB", cmd);
            discord.sendPunishmentCommand(player.getName(), cmd);
        }
    }
}