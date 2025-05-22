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

public class NoSlowDownE implements Listener {

    private final ViolationAlerts alerts;
    private final DiscordHook discord;
    private final ConfigManager config;
    private final Map<UUID, Long> lastCheck = new HashMap<>();
    private final Map<UUID, Location> lastLocations = new HashMap<>();
    private final Map<UUID, Long> lastElytraFlight = new HashMap<>();
    private final Map<UUID, Long> lastPlayerFlight = new HashMap<>();
    private final Map<UUID, Boolean> wasGliding = new HashMap<>();
    private final Map<UUID, Boolean> wasFlying = new HashMap<>();

    public NoSlowDownE(Plugin plugin, ViolationAlerts alerts, DiscordHook discord, ConfigManager config) {
        this.alerts = alerts;
        this.discord = discord;
        this.config = config;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!config.isNoSlowDownEEnabled()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (player.hasPermission("duckyac.bypass") && player.hasPermission("duckyac.*")
                && player.hasPermission("duckyac.bypass.noslowdown-e") && player.hasPermission("duckyac.bypass.noslowdown.*")) {
            return;
        }

        if (player.isFlying()) return;

        if (recentElytraFlight(player, uuid) || recentPlayerFlight(player, uuid)) return;

        Block blockBelow = player.getLocation().subtract(0, 0.1, 0).getBlock();
        if (blockBelow.getType() != Material.HONEY_BLOCK) return;

        if (!player.isOnGround() || player.getGameMode().name().contains("CREATIVE") || player.getGameMode().name().contains("SPECTATOR"))
            return;

        if (hasDepthStrider(player)) return;

        Location current = player.getLocation();
        Location previous = lastLocations.getOrDefault(uuid, current);
        lastLocations.put(uuid, current.clone());

        if (Math.abs(current.getY() - previous.getY()) > 0.001) return;

        double speed = getHorizontalSpeed(event);
        double maxSpeed = config.getNoSlowDownEMaxSpeed();

        if (speed > maxSpeed) {
            long now = System.currentTimeMillis();
            long last = lastCheck.getOrDefault(uuid, 0L);
            if (now - last < 300L) return;

            int vl = alerts.reportViolation(player.getName(), "NoSlowDownE");

            if (config.isNoSlowDownEDebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (NoSlowDownE Debug) " + player.getName()
                        + " moved too fast on HONEY_BLOCK (" + String.format("%.3f", speed) + " > " + String.format("%.3f", maxSpeed) + ")");
            }

            if (vl >= config.getMaxNoSlowDownEAlerts()) {
                String cmd = config.getNoSlowDownECommand();
                alerts.executePunishment(player.getName(), "NoSlowDownE", cmd);
                discord.sendPunishmentCommand(player.getName(), cmd);
            }

            lastCheck.put(uuid, now);
        }
    }

    private boolean recentElytraFlight(Player player, UUID uuid) {
        boolean current = player.isGliding();
        boolean previous = wasGliding.getOrDefault(uuid, false);

        if (!current && previous) {
            lastElytraFlight.put(uuid, System.currentTimeMillis());
        }

        wasGliding.put(uuid, current);

        return !current && lastElytraFlight.containsKey(uuid)
                && System.currentTimeMillis() - lastElytraFlight.get(uuid) < 1000;
    }

    private boolean recentPlayerFlight(Player player, UUID uuid) {
        boolean current = player.isFlying();
        boolean previous = wasFlying.getOrDefault(uuid, false);

        if (!current && previous) {
            lastPlayerFlight.put(uuid, System.currentTimeMillis());
        }

        wasFlying.put(uuid, current);

        return !current && lastPlayerFlight.containsKey(uuid)
                && System.currentTimeMillis() - lastPlayerFlight.get(uuid) < 1000;
    }

    private boolean hasDepthStrider(Player player) {
        ItemStack boots = player.getInventory().getBoots();
        return boots != null && boots.getEnchantments().containsKey(Enchantment.DEPTH_STRIDER);
    }

    private double getHorizontalSpeed(PlayerMoveEvent event) {
        return Math.sqrt(Math.pow(Objects.requireNonNull(event.getTo()).getX() - event.getFrom().getX(), 2)
                + Math.pow(event.getTo().getZ() - event.getFrom().getZ(), 2));
    }
}