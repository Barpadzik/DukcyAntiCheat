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

public class NoSlowDownC implements Listener {

    private final ViolationAlerts alerts;
    private final DiscordHook discord;
    private final ConfigManager config;

    private static final double EPSILON = 0.0001;

    private final List<Double> ignoredSpeeds;
    private final ConcurrentHashMap<UUID, Location> lastLocations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> ignoreUntil = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastElytra = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> wasGliding = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> wasFlying = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> handRaiseStart = new ConcurrentHashMap<>();

    public NoSlowDownC(Main plugin, ViolationAlerts alerts, DiscordHook discord, ConfigManager config) {
        this.alerts = alerts;
        this.discord = discord;
        this.config = config;
        this.ignoredSpeeds = config.getNoSlowDownCIgnoredSpeedValues();
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

        if (PermissionBypass.hasBypass(player)) return;
        if (!config.isNoSlowDownCEnabled()) return;
        if (!player.isOnline() || player.isFlying()) return;
        if (player.hasPermission("duckyac.bypass.noslowdown-c") || player.hasPermission("duckyac.bypass.noslowdown.*")) return;

        // Elytra cooldown
        boolean glidingNow = player.isGliding();
        if (!glidingNow && wasGliding.getOrDefault(uuid, false)) {
            lastElytra.put(uuid, System.currentTimeMillis());
        }
        wasGliding.put(uuid, glidingNow);
        if (!glidingNow && lastElytra.containsKey(uuid) && System.currentTimeMillis() - lastElytra.get(uuid) < 1000) {
            return;
        }

        // Flying cooldown
        boolean flyingNow = player.isFlying();
        if (!flyingNow && wasFlying.getOrDefault(uuid, false)) {
            lastFlight.put(uuid, System.currentTimeMillis());
        }
        wasFlying.put(uuid, flyingNow);
        if (!flyingNow && lastFlight.containsKey(uuid) && System.currentTimeMillis() - lastFlight.get(uuid) < 1000) {
            return;
        }

        // Check if charging crossbow
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (hand.getType() != Material.CROSSBOW || !player.isHandRaised()) {
            handRaiseStart.remove(uuid); // reset if no longer holding the crossbow
            return;
        }

        // If the player has just started loading the crossbow, note the time
        handRaiseStart.putIfAbsent(uuid, System.currentTimeMillis());

        // Please wait at least 500ms after starting to charge
        if (System.currentTimeMillis() - handRaiseStart.get(uuid) < 500) {
            return;
        }

        // Movement calculations
        Location curr = player.getLocation();
        Location prev = lastLocations.getOrDefault(uuid, curr);
        double dist = curr.toVector().distance(prev.toVector());
        lastLocations.put(uuid, curr.clone());

        if (Math.abs(curr.getY() - prev.getY()) > 0.001) return;

        for (double ignored : ignoredSpeeds) {
            if (Math.abs(dist - ignored) < EPSILON) return;
        }

        double maxSpeed = config.getNoSlowDownCMaxSpeed();
        PotionEffect speed = player.getPotionEffect(PotionEffectType.SPEED);
        if (speed != null) {
            maxSpeed *= 1.0 + (speed.getAmplifier() + 1) * 0.2;
        }

        Material ground = curr.subtract(0, 1, 0).getBlock().getType();
        if (ground == Material.ICE || ground == Material.PACKED_ICE || ground == Material.BLUE_ICE) return;

        if (dist > config.getNoSlowDownCMaxIgnoreSpeed()) return;
        if (dist <= maxSpeed) return;

        alerts.reportViolation(player.getName(), "NoSlowDownC");
        int vl = alerts.getViolationCount(player.getName(), "NoSlowDownC");

        if (config.isNoSlowDownCDebugMode()) {
            Bukkit.getLogger().info("[DuckyAntiCheat] (NoSlowDownC Debug) " + player.getName()
                    + " moved too fast while charging crossbow: " + String.format("%.4f", dist)
                    + " (allowed: " + String.format("%.4f", maxSpeed) + ") (VL: " + vl + ")");
        }

        if (config.shouldNoSlowDownCCancelEvent()) {
            player.teleport(prev);
        }

        if (vl >= config.getMaxNoSlowDownCAlerts()) {
            String cmd = config.getNoSlowDownCCommand();
            alerts.executePunishment(player.getName(), "NoSlowDownC", cmd);
            discord.sendPunishmentCommand(player.getName(), cmd);
        }
    }
}