package pl.barpad.duckyanticheat.checks.place;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import pl.barpad.duckyanticheat.Main;
import pl.barpad.duckyanticheat.utils.DiscordHook;
import pl.barpad.duckyanticheat.utils.PermissionBypass;
import pl.barpad.duckyanticheat.utils.ViolationAlerts;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FastPlaceA implements Listener {

    // Reference to plugin configuration for various check settings
    private final ConfigManager config;

    // Utility for reporting violations and managing violation levels
    private final ViolationAlerts alerts;

    // Discord webhook handler for sending punishment commands to a Discord channel
    private final DiscordHook discordHook;

    // Map storing number of blocks placed per player within the current second
    private final ConcurrentHashMap<UUID, Integer> placeCounts = new ConcurrentHashMap<>();

    /**
     * Constructor registers the event listener and schedules the task
     * to reset block placement counters every second.
     *
     * @param plugin      the main plugin instance
     * @param alerts      violation alerts manager
     * @param discordHook Discord integration for punishment commands
     * @param config      configuration manager for check settings
     */
    public FastPlaceA(Main plugin, ViolationAlerts alerts, DiscordHook discordHook, ConfigManager config) {
        this.config = config;
        this.alerts = alerts;
        this.discordHook = discordHook;

        // Register the block place event listener
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Schedule task to clear place counts every 20 ticks (1 second)
        Bukkit.getScheduler().runTaskTimer(plugin, placeCounts::clear, 1L, 1L);
    }

    /**
     * Event handler for block placement events.
     * Tracks the number of blocks placed by each player per second,
     * checks if it exceeds the allowed maximum, and triggers violations.
     *
     * @param event the block placement event
     */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        // Check if the FastPlaceA check is enabled in config
        if (!config.isFastPlaceAEnabled()) return;

        // Ignore players with permission bypass
        if (PermissionBypass.hasBypass(player)) return;

        // Also ignore players with specific bypass permission for this check
        if (player.hasPermission("duckyac.bypass.fastplace-a")) return;

        UUID uuid = player.getUniqueId();

        // Increment the block place count for this player
        placeCounts.put(uuid, placeCounts.getOrDefault(uuid, 0) + 1);

        int placed = placeCounts.get(uuid);
        int maxAllowed = config.getFastPlaceAMaxPerSecond();

        // If the player placed more blocks than allowed per second, trigger violation
        if (placed > maxAllowed) {
            // Report the violation and get current violation level (VL)
            int vl = alerts.reportViolation(player.getName(), "FastPlaceA");

            // Cancel the event if configured to prevent block placement
            if (config.isFastPlaceACancelEvent()) {
                event.setCancelled(true);
            }

            // If debug mode is enabled, log info about the violation
            if (config.isFastPlaceADebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (FastPlaceA Debug) " + player.getName()
                        + " placed " + placed + " blocks/tick (VL: " + vl + ")");
            }

            // If violation level reaches the configured max, execute punishment command
            if (vl >= config.getMaxFastPlaceAAlerts()) {
                String cmd = config.getFastPlaceACommand();
                alerts.executePunishment(player.getName(), "FastPlaceA", cmd);
                discordHook.sendPunishmentCommand(player.getName(), cmd);
            }
        }
    }
}