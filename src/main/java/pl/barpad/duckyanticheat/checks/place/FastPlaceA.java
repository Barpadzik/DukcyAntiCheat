package pl.barpad.duckyanticheat.checks.place;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import pl.barpad.duckyanticheat.Main;
import pl.barpad.duckyanticheat.utils.DiscordHook;
import pl.barpad.duckyanticheat.utils.ViolationAlerts;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.util.HashMap;
import java.util.UUID;

public class FastPlaceA implements Listener {

    private final ConfigManager config;
    private final ViolationAlerts alerts;
    private final DiscordHook discordHook;
    private final HashMap<UUID, Integer> placeCounts = new HashMap<>();

    public FastPlaceA(Main plugin, ViolationAlerts alerts, DiscordHook discordHook, ConfigManager config) {
        this.config = config;
        this.alerts = alerts;
        this.discordHook = discordHook;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, placeCounts::clear, 1L, 1L);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!config.isFastPlaceAEnabled()) return;

        if (player.hasPermission("duckyac.bypass") && player.hasPermission("duckyac.*") && player.hasPermission("duckyac.bypass.fastplace-a")) {
            return;
        }

        UUID uuid = player.getUniqueId();
        placeCounts.put(uuid, placeCounts.getOrDefault(uuid, 0) + 1);

        int placed = placeCounts.get(uuid);
        int maxAllowed = config.getFastPlaceAMaxPerSecond();

        if (placed > maxAllowed) {
            alerts.reportViolation(player.getName(), "FastPlaceA");
            int vl = alerts.getViolationCount(player.getName(), "FastPlaceA");

            if (config.isFastPlaceACancelEvent()) {
                event.setCancelled(true);
            }

            if (config.isFastPlaceADebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (FastPlaceA Debug) " + player.getName() + " placed " + placed + " blocks/sec (VL: " + vl + ")");
            }

            if (vl >= config.getMaxFastPlaceAAlerts()) {
                String cmd = config.getFastPlaceACommand();
                alerts.executePunishment(player.getName(), "FastPlaceA", cmd);
                discordHook.sendPunishmentCommand(player.getName(), cmd);
            }
        }
    }
}