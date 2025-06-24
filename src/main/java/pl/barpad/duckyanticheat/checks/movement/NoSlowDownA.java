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

    // Maps player's UUID to their last known location
    private final ConcurrentHashMap<UUID, Location> lastLocations = new ConcurrentHashMap<>();

    // Maps UUIDs to timestamps indicating until when movement checks should be ignored (e.g. just started eating)
    private final ConcurrentHashMap<UUID, Long> ignoreUntil = new ConcurrentHashMap<>();

    // Maps UUIDs to timestamps indicating immunity from checks (e.g. after gliding or flying)
    private final ConcurrentHashMap<UUID, Long> immunityUntil = new ConcurrentHashMap<>();

    // Tracks when the player last stopped gliding with elytra
    private final ConcurrentHashMap<UUID, Long> lastElytraFlight = new ConcurrentHashMap<>();

    // Tracks when the player last stopped creative/survival flying
    private final ConcurrentHashMap<UUID, Long> lastPlayerFlight = new ConcurrentHashMap<>();

    // Stores whether a player was gliding in the previous tick
    private final ConcurrentHashMap<UUID, Boolean> wasGliding = new ConcurrentHashMap<>();

    // Stores whether a player was flying in the previous tick
    private final ConcurrentHashMap<UUID, Boolean> wasFlying = new ConcurrentHashMap<>();

    // Tracks when a player started eating
    private final ConcurrentHashMap<UUID, Long> eatingStartTime = new ConcurrentHashMap<>();

    // Speed values that are ignored by the check (configured in config.yml)
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
     * Called when player interacts (e.g., uses an edible item like food).
     * Temporarily disables speed checks to avoid false positives when beginning to eat.
     */
    @EventHandler
    public void onItemUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType().isEdible()) {
            UUID uuid = player.getUniqueId();
            ignoreUntil.put(uuid, System.currentTimeMillis() + 1000); // ignore checks for 1 second
            eatingStartTime.put(uuid, System.currentTimeMillis());    // track when eating started
        }
    }

    /**
     * Called on every movement to check if a player is moving unusually fast while eating.
     */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Ignore if player started eating less than 0.5s ago
        Long start = eatingStartTime.get(uuid);
        if (start == null || System.currentTimeMillis() - start < 500) return;

        // Skip if feature is disabled, player is offline, or has bypass permission
        if (!config.isNoSlowDownAEnabled() || !player.isOnline() || PermissionBypass.hasBypass(player)) return;
        if (player.hasPermission("duckyac.bypass.noslowdown-a") || player.hasPermission("duckyac.bypass.noslowdown.*")) return;

        // Skip if not actively eating
        if (!player.isHandRaised() || !isEating(player)) return;

        // Temporary immunity after gliding or flying
        if (immunityUntil.containsKey(uuid) && System.currentTimeMillis() < immunityUntil.get(uuid)) return;
        else immunityUntil.remove(uuid);

        // Track transitions from gliding (Elytra)
        boolean isCurrentlyGliding = player.isGliding();
        boolean wasPreviouslyGliding = wasGliding.getOrDefault(uuid, false);
        if (!isCurrentlyGliding && wasPreviouslyGliding) {
            lastElytraFlight.put(uuid, System.currentTimeMillis());
        }
        wasGliding.put(uuid, isCurrentlyGliding);

        // Skip if recently finished gliding
        if (!player.isGliding() && lastElytraFlight.containsKey(uuid)) {
            if (System.currentTimeMillis() - lastElytraFlight.get(uuid) < 1000) return;
        }

        // Track transitions from flying (creative/survival)
        boolean isCurrentlyFlying = player.isFlying();
        boolean wasPreviouslyFlying = wasFlying.getOrDefault(uuid, false);
        if (!isCurrentlyFlying && wasPreviouslyFlying) {
            lastPlayerFlight.put(uuid, System.currentTimeMillis());
        }
        wasFlying.put(uuid, isCurrentlyFlying);

        // Skip if recently finished flying
        if (!player.isFlying() && lastPlayerFlight.containsKey(uuid)) {
            if (System.currentTimeMillis() - lastPlayerFlight.get(uuid) < 1000) return;
        }

        // Calculate horizontal distance moved since last tick
        Location currentLoc = player.getLocation();
        Location lastLoc = lastLocations.getOrDefault(uuid, currentLoc);
        double distance = currentLoc.toVector().distance(lastLoc.toVector());

        lastLocations.put(uuid, currentLoc.clone()); // Save current location for next tick

        // Skip if there was notable vertical movement (e.g., falling or jumping)
        if (Math.abs(currentLoc.getY() - lastLoc.getY()) > 0.001) return;

        double adjustedMaxSpeed = config.getNoSlowDownAMaxEatingSpeed();

        // Skip if speed is above a max threshold to avoid false positives
        if (distance > config.getNoSlowDownAMaxIgnoreSpeed()) return;

        // Skip if the current speed is among ignored values
        for (double ignored : ignoredSpeedValues) {
            if (Math.abs(distance - ignored) < EPSILON) return;
        }

        // Account for Speed potion effects
        PotionEffect speedEffect = player.getPotionEffect(PotionEffectType.SPEED);
        if (speedEffect != null) {
            int amplifier = speedEffect.getAmplifier() + 1;
            adjustedMaxSpeed *= (1.0 + amplifier * 0.2);
        }

        // Ignore if standing on ice-type block
        Material belowType = player.getLocation().subtract(0, 1, 0).getBlock().getType();
        if (belowType == Material.ICE || belowType == Material.PACKED_ICE || belowType == Material.BLUE_ICE) return;

        // Player is moving too fast while eating — possible NoSlowDown cheat
        if (distance > adjustedMaxSpeed) {
            violationAlerts.reportViolation(player.getName(), "NoSlowDownA");
            int vl = violationAlerts.getViolationCount(player.getName(), "NoSlowDownA");

            if (config.isNoSlowDownADebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (NoSlowDownA Debug) " + player.getName()
                        + " moved too fast while eating: " + String.format("%.4f", distance)
                        + " (allowed: " + String.format("%.4f", adjustedMaxSpeed) + ") (VL: " + vl + ")");
            }

            // Optionally cancel movement by teleporting back
            if (config.shouldNoSlowDownACancelEvent()) {
                player.teleport(lastLoc);
            }

            // Execute punishment if a violation threshold exceeded
            if (vl >= config.getMaxNoSlowDownAAlerts()) {
                String cmd = config.getNoSlowDownACommand();
                violationAlerts.executePunishment(player.getName(), "NoSlowDownA", cmd);
                discordHook.sendPunishmentCommand(player.getName(), cmd);
            }
        }
    }

    /**
     * Utility method to determine if the player is eating.
     */
    private boolean isEating(Player player) {
        if (!player.isHandRaised()) return false;
        ItemStack item = player.getInventory().getItemInMainHand();
        return item.getType().isEdible();
    }
}