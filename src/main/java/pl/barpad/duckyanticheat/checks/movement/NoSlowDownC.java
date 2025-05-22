package pl.barpad.duckyanticheat.checks.movement;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
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
import pl.barpad.duckyanticheat.utils.ViolationAlerts;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NoSlowDownC implements Listener {

    private final ViolationAlerts violationAlerts;
    private final DiscordHook discordHook;
    private final ConfigManager config;
    private final Map<UUID, Location> lastLocations = new HashMap<>();
    private final Map<UUID, Long> ignoreUntil = new HashMap<>();
    private final List<Double> ignoredSpeedValues;
    private static final double EPSILON = 0.0001;
    private final Map<UUID, Long> lastElytraFlight = new HashMap<>();
    private final Map<UUID, Long> lastPlayerFlight = new HashMap<>();
    private final Map<UUID, Boolean> wasGliding = new HashMap<>();
    private final Map<UUID, Boolean> wasFlying = new HashMap<>();

    public NoSlowDownC(Main plugin, ViolationAlerts violationAlerts, DiscordHook discordHook, ConfigManager config) {
        this.violationAlerts = violationAlerts;
        this.discordHook = discordHook;
        this.config = config;
        this.ignoredSpeedValues = config.getNoSlowDownCIgnoredSpeedValues();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onItemUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType().isEdible()) {
            ignoreUntil.put(player.getUniqueId(), System.currentTimeMillis() + 1000);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!config.isNoSlowDownCEnabled()) return;
        if (!player.isOnline()) return;
        if (player.isFlying()) return;

        if (player.hasPermission("duckyac.bypass") || player.hasPermission("duckyac.*") ||
                player.hasPermission("duckyac.bypass.noslowdown-c") || player.hasPermission("duckyac.bypass.noslowdown.*")) {
            return;
        }

        boolean isCurrentlyGliding = player.isGliding();
        boolean wasPreviouslyGliding = wasGliding.getOrDefault(uuid, false);
        if (!isCurrentlyGliding && wasPreviouslyGliding) {
            lastElytraFlight.put(uuid, System.currentTimeMillis());
        }
        wasGliding.put(uuid, isCurrentlyGliding);
        if (!isCurrentlyGliding && lastElytraFlight.containsKey(uuid) &&
                System.currentTimeMillis() - lastElytraFlight.get(uuid) < 1000) {
            return;
        }

        boolean isCurrentlyFlying = player.isFlying();
        boolean wasPreviouslyFlying = wasFlying.getOrDefault(uuid, false);
        if (!isCurrentlyFlying && wasPreviouslyFlying) {
            lastPlayerFlight.put(uuid, System.currentTimeMillis());
        }
        wasFlying.put(uuid, isCurrentlyFlying);
        if (!isCurrentlyFlying && lastPlayerFlight.containsKey(uuid) &&
                System.currentTimeMillis() - lastPlayerFlight.get(uuid) < 1000) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.CROSSBOW || !player.isHandRaised()) return;

        Location current = player.getLocation();
        Location previous = lastLocations.getOrDefault(uuid, current);
        double distance = current.toVector().distance(previous.toVector());
        lastLocations.put(uuid, current.clone());

        if (Math.abs(current.getY() - previous.getY()) > 0.001) return;

        double adjustedMaxSpeed = config.getNoSlowDownCMaxSpeed();
        double maxIgnoreSpeed = config.getNoSlowDownCMaxIgnoreSpeed();
        boolean debugMode = config.isNoSlowDownCDebugMode();
        boolean cancelEvent = config.shouldNoSlowDownCCancelEvent();
        int maxAlerts = config.getMaxNoSlowDownCAlerts();
        String punishCommand = config.getNoSlowDownCCommand();

        PotionEffect speed = player.getPotionEffect(PotionEffectType.SPEED);
        if (speed != null) {
            int level = speed.getAmplifier() + 1;
            adjustedMaxSpeed *= (1.0 + level * 0.2);
        }

        Material belowType = player.getLocation().subtract(0, 1, 0).getBlock().getType();
        if (belowType == Material.ICE || belowType == Material.PACKED_ICE || belowType == Material.BLUE_ICE) return;

        for (double ignored : ignoredSpeedValues) {
            if (Math.abs(distance - ignored) < EPSILON) {
                return;
            }
        }

        if (distance > maxIgnoreSpeed) return;

        if (distance > adjustedMaxSpeed) {
            violationAlerts.reportViolation(player.getName(), "NoSlowDownC");
            int vl = violationAlerts.getViolationCount(player.getName(), "NoSlowDownC");

            if (debugMode) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (NoSlowDownC Debug) " + player.getName()
                        + " moved too fast while charging a crossbow: "
                        + String.format("%.4f", distance) + " (allowed: "
                        + String.format("%.4f", adjustedMaxSpeed) + ") (VL: " + vl + ")");
            }

            if (cancelEvent) {
                player.teleport(previous);
            }

            if (vl >= maxAlerts) {
                violationAlerts.executePunishment(player.getName(), "NoSlowDownC", punishCommand);
                discordHook.sendPunishmentCommand(player.getName(), punishCommand);
            }
        }
    }
}