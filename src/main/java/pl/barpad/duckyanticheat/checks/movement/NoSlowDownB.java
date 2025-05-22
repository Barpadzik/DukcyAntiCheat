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
import pl.barpad.duckyanticheat.utils.ViolationAlerts;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.util.*;

public class NoSlowDownB implements Listener {

    private final ViolationAlerts alerts;
    private final DiscordHook discord;
    private final ConfigManager config;

    private final Map<UUID, Location> lastLocations = new HashMap<>();
    private final Map<UUID, Long> ignoreUntil = new HashMap<>();
    private final List<Double> ignoredSpeeds;
    private static final double EPSILON = 0.0001;

    private final Map<UUID, Long> lastElytra = new HashMap<>();
    private final Map<UUID, Long> lastFlight = new HashMap<>();
    private final Map<UUID, Boolean> wasGliding = new HashMap<>();
    private final Map<UUID, Boolean> wasFlying = new HashMap<>();

    public NoSlowDownB(Main plugin, ViolationAlerts alerts, DiscordHook discord, ConfigManager config) {
        this.alerts = alerts;
        this.discord = discord;
        this.config = config;
        this.ignoredSpeeds = config.getNoSlowDownBIgnoredSpeedValues();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onItemUse(PlayerInteractEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (item.getType().isEdible()) {
            ignoreUntil.put(event.getPlayer().getUniqueId(), System.currentTimeMillis() + 1000);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        UUID uuid = p.getUniqueId();

        if (!config.isNoSlowDownBEnabled() || !p.isOnline() || p.isFlying() || !p.isHandRaised()) return;
        if (p.hasPermission("duckyac.bypass") || p.hasPermission("duckyac.*") || p.hasPermission("duckyac.bypass.noslowdown-b") || p.hasPermission("duckyac.bypass.noslowdown.*")) return;

        boolean glidingNow = p.isGliding();
        if (!glidingNow && wasGliding.getOrDefault(uuid, false)) lastElytra.put(uuid, System.currentTimeMillis());
        wasGliding.put(uuid, glidingNow);
        if (!glidingNow && lastElytra.containsKey(uuid) && System.currentTimeMillis() - lastElytra.get(uuid) < 1000) return;

        boolean flyingNow = p.isFlying();
        if (!flyingNow && wasFlying.getOrDefault(uuid, false)) lastFlight.put(uuid, System.currentTimeMillis());
        wasFlying.put(uuid, flyingNow);
        if (!flyingNow && lastFlight.containsKey(uuid) && System.currentTimeMillis() - lastFlight.get(uuid) < 1000) return;

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() != Material.BOW) return;

        Location curr = p.getLocation();
        Location prev = lastLocations.getOrDefault(uuid, curr);
        double dist = curr.toVector().distance(prev.toVector());
        lastLocations.put(uuid, curr.clone());

        if (Math.abs(curr.getY() - prev.getY()) > 0.001) return;

        for (double ignored : ignoredSpeeds) {
            if (Math.abs(dist - ignored) < EPSILON) return;
        }

        double maxSpeed = config.getNoSlowDownBMaxBowSpeed();
        PotionEffect speed = p.getPotionEffect(PotionEffectType.SPEED);
        if (speed != null) {
            maxSpeed *= 1.0 + (speed.getAmplifier() + 1) * 0.2;
        }

        Material ground = p.getLocation().subtract(0, 1, 0).getBlock().getType();
        if (ground == Material.ICE || ground == Material.PACKED_ICE || ground == Material.BLUE_ICE) return;

        if (dist > config.getNoSlowDownBMaxIgnoreSpeed()) return;
        if (dist <= maxSpeed) return;

        alerts.reportViolation(p.getName(), "NoSlowDownB");
        int vl = alerts.getViolationCount(p.getName(), "NoSlowDownB");

        if (config.isNoSlowDownBDebugMode()) {
            Bukkit.getLogger().info("[DuckyAntiCheat] (NoSlowDownB Debug) " + p.getName()
                    + " moved too fast with bow drawn: " + String.format("%.4f", dist)
                    + " (allowed: " + String.format("%.4f", maxSpeed) + ") (VL: " + vl + ")");
        }

        if (config.shouldNoSlowDownBCancelEvent()) {
            p.teleport(prev);
        }

        if (vl >= config.getMaxNoSlowDownBAlerts()) {
            String cmd = config.getNoSlowDownBCommand();
            alerts.executePunishment(p.getName(), "NoSlowDownB", cmd);
            discord.sendPunishmentCommand(p.getName(), cmd);
        }
    }
}