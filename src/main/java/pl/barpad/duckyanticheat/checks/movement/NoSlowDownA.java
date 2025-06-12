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

public class NoSlowDownA implements Listener {

    private final ViolationAlerts violationAlerts;
    private final DiscordHook discordHook;
    private final ConfigManager config;

    private final ConcurrentHashMap<UUID, Location> lastLocations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> ignoreUntil = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> immunityUntil = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastElytraFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastPlayerFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> wasGliding = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> wasFlying = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> eatingStartTime = new ConcurrentHashMap<>();

    private final List<Double> ignoredSpeedValues;
    private static final double EPSILON = 0.0001;

    public NoSlowDownA(Main plugin, ViolationAlerts violationAlerts, DiscordHook discordHook, ConfigManager config) {
        this.violationAlerts = violationAlerts;
        this.discordHook = discordHook;
        this.config = config;
        this.ignoredSpeedValues = config.getNoSlowDownAIgnoredSpeedValues();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Called when player interacts (e.g., eats food).
     * Temporarily ignores movement checks for 1 second.
     */
    @EventHandler
    public void onItemUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType().isEdible()) {
            UUID uuid = player.getUniqueId();
            ignoreUntil.put(uuid, System.currentTimeMillis() + 1000);
            eatingStartTime.put(uuid, System.currentTimeMillis());
        }
    }

    /**
     * Called on player movement to check if the player is moving too fast while eating.
     */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Long start = eatingStartTime.get(uuid);
        if (start == null || System.currentTimeMillis() - start < 500) {
            return;
        }

        // Skip checks for bypass players or disabled config
        if (!config.isNoSlowDownAEnabled() || !player.isOnline() || PermissionBypass.hasBypass(player)) return;
        if (player.hasPermission("duckyac.bypass.noslowdown-a") || player.hasPermission("duckyac.bypass.noslowdown.*")) return;

        // Skip if not eating
        if (!player.isHandRaised() || !isEating(player)) return;

        // Immunity time (e.g. after Elytra/flying)
        if (immunityUntil.containsKey(uuid) && System.currentTimeMillis() < immunityUntil.get(uuid)) {
            return;
        } else {
            immunityUntil.remove(uuid);
        }

        // Handle Elytra and gliding immunity
        boolean isCurrentlyGliding = player.isGliding();
        boolean wasPreviouslyGliding = wasGliding.getOrDefault(uuid, false);

        if (!isCurrentlyGliding && wasPreviouslyGliding) {
            lastElytraFlight.put(uuid, System.currentTimeMillis());
        }
        wasGliding.put(uuid, isCurrentlyGliding);

        if (!player.isGliding() && lastElytraFlight.containsKey(uuid)) {
            if (System.currentTimeMillis() - lastElytraFlight.get(uuid) < 1000) return;
        }

        // Handle flying immunity
        boolean isCurrentlyFlying = player.isFlying();
        boolean wasPreviouslyFlying = wasFlying.getOrDefault(uuid, false);

        if (!isCurrentlyFlying && wasPreviouslyFlying) {
            lastPlayerFlight.put(uuid, System.currentTimeMillis());
        }
        wasFlying.put(uuid, isCurrentlyFlying);

        if (!player.isFlying() && lastPlayerFlight.containsKey(uuid)) {
            if (System.currentTimeMillis() - lastPlayerFlight.get(uuid) < 1000) return;
        }

        // Movement distance calculation
        Location currentLoc = player.getLocation();
        Location lastLoc = lastLocations.getOrDefault(uuid, currentLoc);
        double distance = currentLoc.toVector().distance(lastLoc.toVector());

        lastLocations.put(uuid, currentLoc.clone());

        // Skip if Y-axis movement is too large
        if (Math.abs(currentLoc.getY() - lastLoc.getY()) > 0.001) return;

        // Check against max speed
        double adjustedMaxSpeed = config.getNoSlowDownAMaxEatingSpeed();
        if (distance > config.getNoSlowDownAMaxIgnoreSpeed()) return;

        // Skip if speed is in ignored values
        for (double ignored : ignoredSpeedValues) {
            if (Math.abs(distance - ignored) < EPSILON) return;
        }

        // Adjust for speed potion effects
        PotionEffect speedEffect = player.getPotionEffect(PotionEffectType.SPEED);
        if (speedEffect != null) {
            int amplifier = speedEffect.getAmplifier() + 1;
            adjustedMaxSpeed *= (1.0 + amplifier * 0.2);
        }

        // Ignore if standing on ice
        Material belowType = player.getLocation().subtract(0, 1, 0).getBlock().getType();
        if (belowType == Material.ICE || belowType == Material.PACKED_ICE || belowType == Material.BLUE_ICE) return;

        // Player is too fast while eating
        if (distance > adjustedMaxSpeed) {
            violationAlerts.reportViolation(player.getName(), "NoSlowDownA");
            int vl = violationAlerts.getViolationCount(player.getName(), "NoSlowDownA");

            if (config.isNoSlowDownADebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (NoSlowDownA Debug) " + player.getName()
                        + " moved too fast while eating: " + String.format("%.4f", distance)
                        + " (allowed: " + String.format("%.4f", adjustedMaxSpeed) + ") (VL: " + vl + ")");
            }

            if (config.shouldNoSlowDownACancelEvent()) {
                player.teleport(lastLoc); // Cancel movement
            }

            if (vl >= config.getMaxNoSlowDownAAlerts()) {
                String cmd = config.getNoSlowDownACommand();
                violationAlerts.executePunishment(player.getName(), "NoSlowDownA", cmd);
                discordHook.sendPunishmentCommand(player.getName(), cmd);
            }
        }
    }

    /**
     * Checks if the player is currently eating.
     */
    private boolean isEating(Player player) {
        if (!player.isHandRaised()) return false;
        ItemStack item = player.getInventory().getItemInMainHand();
        return item.getType().isEdible();
    }
}