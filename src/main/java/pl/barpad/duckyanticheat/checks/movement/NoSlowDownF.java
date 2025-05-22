package pl.barpad.duckyanticheat.checks.movement;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import pl.barpad.duckyanticheat.utils.DiscordHook;
import pl.barpad.duckyanticheat.utils.ViolationAlerts;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.util.*;

public class NoSlowDownF implements Listener {

    private final ViolationAlerts alerts;
    private final DiscordHook discord;
    private final ConfigManager config;
    private final Map<UUID, Long> lastCheck = new HashMap<>();
    private final Map<UUID, Location> lastLocations = new HashMap<>();
    private final Map<UUID, Long> lastElytraFlight = new HashMap<>();
    private final Map<UUID, Long> lastPlayerFlight = new HashMap<>();
    private final Map<UUID, Boolean> wasGliding = new HashMap<>();
    private final Map<UUID, Boolean> wasFlying = new HashMap<>();

    public NoSlowDownF(Plugin plugin, ViolationAlerts alerts, DiscordHook discord, ConfigManager config) {
        this.alerts = alerts;
        this.discord = discord;
        this.config = config;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!config.isNoSlowDownFEnabled()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (player.hasPermission("duckyac.bypass") && player.hasPermission("duckyac.*")
                && player.hasPermission("duckyac.bypass.noslowdown-f") && player.hasPermission("duckyac.bypass.noslowdown.*")) {
            return;
        }

        if (player.isFlying()) return;

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

        if (!player.isOnGround()
                || player.getGameMode().name().contains("CREATIVE")
                || player.getGameMode().name().contains("SPECTATOR")) {
            return;
        }

        Block blockBelow = player.getLocation().subtract(0, 0.1, 0).getBlock();
        if (blockBelow.getType() != Material.SOUL_SAND) return;

        Location current = player.getLocation();
        Location previous = lastLocations.getOrDefault(uuid, current);
        lastLocations.put(uuid, current.clone());

        if (Math.abs(current.getY() - previous.getY()) > 0.001) return;

        double speed = getHorizontalSpeed(event);
        double maxSpeed = config.getNoSlowDownFMaxSpeed();

        int soulSpeedLevel = getEnchantmentLevel(player.getInventory().getBoots(), Enchantment.SOUL_SPEED);
        if (soulSpeedLevel > 0) {
            maxSpeed *= 1.2 + (0.15 * soulSpeedLevel);
        }

        int depthStriderLevel = getEnchantmentLevel(player.getInventory().getBoots(), Enchantment.DEPTH_STRIDER);
        if (depthStriderLevel > 0) {
            maxSpeed *= 1.0 + (0.15 * depthStriderLevel);
        }

        if (speed > maxSpeed) {
            long now = System.currentTimeMillis();
            long last = lastCheck.getOrDefault(uuid, 0L);
            if (now - last < 300L) return;

            int vl = alerts.reportViolation(player.getName(), "NoSlowDownF");

            if (config.isNoSlowDownFDebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (NoSlowDownF Debug) " + player.getName()
                        + " moved too fast on SOUL_SAND (" + String.format("%.3f", speed)
                        + " > " + String.format("%.3f", maxSpeed) + ")");
            }

            if (vl >= config.getMaxNoSlowDownFAlerts()) {
                String cmd = config.getNoSlowDownFCommand();
                alerts.executePunishment(player.getName(), "NoSlowDownF", cmd);
                discord.sendPunishmentCommand(player.getName(), cmd);
            }

            lastCheck.put(uuid, now);
        }
    }

    private int getEnchantmentLevel(ItemStack item, Enchantment enchantment) {
        return (item != null && item.containsEnchantment(enchantment)) ? item.getEnchantmentLevel(enchantment) : 0;
    }

    private double getHorizontalSpeed(PlayerMoveEvent event) {
        return Math.sqrt(Math.pow(Objects.requireNonNull(event.getTo()).getX() - event.getFrom().getX(), 2)
                + Math.pow(event.getTo().getZ() - event.getFrom().getZ(), 2));
    }
}