package pl.barpad.duckyanticheat.checks.movement;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
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

public class NoSlowDownD implements Listener {

    private static final double EPSILON = 0.0001;

    private final ViolationAlerts violationAlerts;
    private final DiscordHook discordHook;
    private final ConfigManager config;
    private final List<Double> ignoredSpeedValues;

    private final ConcurrentHashMap<UUID, Location> lastLocations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> ignoreUntil = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastElytraFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastPlayerFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> wasGliding = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> wasFlying = new ConcurrentHashMap<>();

    public NoSlowDownD(Main plugin, ViolationAlerts violationAlerts, DiscordHook discordHook, ConfigManager config) {
        this.violationAlerts = violationAlerts;
        this.discordHook = discordHook;
        this.config = config;
        this.ignoredSpeedValues = config.getNoSlowDownDIgnoredSpeedValues();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onItemUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Ignore movement checks for 1 second after eating
        if (item.getType().isEdible()) {
            ignoreUntil.put(player.getUniqueId(), System.currentTimeMillis() + 1000);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (ignoreUntil.containsKey(uuid) && System.currentTimeMillis() < ignoreUntil.get(uuid)) {
            return;
        }

        // Skip if player has bypass permission or check is disabled
        if (PermissionBypass.hasBypass(player)) return;
        if (!config.isNoSlowDownDEnabled()) return;
        if (!player.isOnline()) return;
        if (player.isFlying()) return;
        if (!player.isBlocking()) return;
        if (player.hasPermission("duckyac.bypass.noslowdown-d") || player.hasPermission("duckyac.bypass.noslowdown.*")) return;

        // Elytra gliding cooldown
        boolean isGliding = player.isGliding();
        boolean wasGlidingBefore = wasGliding.getOrDefault(uuid, false);
        if (!isGliding && wasGlidingBefore) {
            lastElytraFlight.put(uuid, System.currentTimeMillis());
        }
        wasGliding.put(uuid, isGliding);
        if (!isGliding && lastElytraFlight.containsKey(uuid) &&
                System.currentTimeMillis() - lastElytraFlight.get(uuid) < 1000) {
            return;
        }

        // Flying cooldown
        boolean isFlying = player.isFlying();
        boolean wasFlyingBefore = wasFlying.getOrDefault(uuid, false);
        if (!isFlying && wasFlyingBefore) {
            lastPlayerFlight.put(uuid, System.currentTimeMillis());
        }
        wasFlying.put(uuid, isFlying);
        if (!isFlying && lastPlayerFlight.containsKey(uuid) &&
                System.currentTimeMillis() - lastPlayerFlight.get(uuid) < 1000) {
            return;
        }

        // Only check players holding a shield
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (mainHand.getType() != Material.SHIELD && offHand.getType() != Material.SHIELD) return;

        // Measure movement distance
        Location current = player.getLocation();
        Location previous = lastLocations.getOrDefault(uuid, current);
        double distance = current.toVector().distance(previous.toVector());
        lastLocations.put(uuid, current.clone());

        // Ignore vertical movement
        if (Math.abs(current.getY() - previous.getY()) > 0.001) return;

        // Skip values configured to be ignored
        for (double ignored : ignoredSpeedValues) {
            if (Math.abs(distance - ignored) < EPSILON) return;
        }

        // Apply speed effect multiplier
        double adjustedMaxSpeed = config.getNoSlowDownDMaxSpeed();
        PotionEffect speed = player.getPotionEffect(PotionEffectType.SPEED);
        if (speed != null) {
            adjustedMaxSpeed *= 1.0 + (speed.getAmplifier() + 1) * 0.2;
        }

        // Ignore players walking on ice
        Material below = player.getLocation().subtract(0, 1, 0).getBlock().getType();
        if (below == Material.ICE || below == Material.PACKED_ICE || below == Material.BLUE_ICE) return;

        // Ignore abnormal movement jumps (likely caused by lag or teleport)
        if (distance > config.getNoSlowDownDMaxIgnoreSpeed()) return;

        // Trigger violation if speed is too high
        if (distance > adjustedMaxSpeed) {
            violationAlerts.reportViolation(player.getName(), "NoSlowDownD");
            int vl = violationAlerts.getViolationCount(player.getName(), "NoSlowDownD");

            // Debug output to console
            if (config.isNoSlowDownDDebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (NoSlowDownD Debug) " + player.getName()
                        + " moved too fast while blocking with shield: "
                        + String.format("%.4f", distance) + " (allowed: "
                        + String.format("%.4f", adjustedMaxSpeed) + ") (VL: " + vl + ")");
            }

            // Cancel movement by teleporting back
            if (config.shouldNoSlowDownDCancelEvent()) {
                player.teleport(previous);
            }

            // Execute punishment if violation level is too high
            if (vl >= config.getMaxNoSlowDownDAlerts()) {
                String cmd = config.getNoSlowDownDCommand();
                violationAlerts.executePunishment(player.getName(), "NoSlowDownD", cmd);
                discordHook.sendPunishmentCommand(player.getName(), cmd);
            }
        }
    }
}