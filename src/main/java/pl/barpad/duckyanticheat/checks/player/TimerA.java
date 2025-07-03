package pl.barpad.duckyanticheat.checks.player;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import pl.barpad.duckyanticheat.Main;
import pl.barpad.duckyanticheat.utils.DiscordHook;
import pl.barpad.duckyanticheat.utils.PermissionBypass;
import pl.barpad.duckyanticheat.utils.ViolationAlerts;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TimerA implements Listener {

    private final ViolationAlerts violationAlerts;
    private final ConfigManager config;
    private final DiscordHook discordHook;

    // Stores how many packets each player has sent during the current second
    private final ConcurrentHashMap<UUID, Integer> packetCounts = new ConcurrentHashMap<>();

    // Stores the timestamp of the last reset for each player's packet counter
    private final ConcurrentHashMap<UUID, Long> lastReset = new ConcurrentHashMap<>();

    /**
     * Constructor. Registers event listeners and initializes dependencies.
     */
    public TimerA(Main plugin, ViolationAlerts violationAlerts, DiscordHook discordHook, ConfigManager config) {
        this.violationAlerts = violationAlerts;
        this.config = config;
        this.discordHook = discordHook;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Handles logic for counting packets sent by a player.
     * If the number exceeds the configured limit, it reports a violation and executes a punishment if necessary.
     *
     * @param player the player to check
     * @return true if the event should be cancelled, false otherwise
     */
    private boolean handlePacket(Player player) {
        // Feature is disabled in config
        if (!config.isTimerAEnabled()) return false;

        // Check if player has bypass permission
        if (PermissionBypass.hasBypass(player)) return false;
        if (player.hasPermission("duckyac.bypass.timer-a")) return false;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long last = lastReset.getOrDefault(uuid, 0L);

        // If 1 second has passed, reset packet counter and timestamp
        if (now - last >= 1000) {
            packetCounts.put(uuid, 1); // Start new count from 1 for current packet
            lastReset.put(uuid, now);
        } else {
            // Otherwise, increment current packet count
            int count = packetCounts.getOrDefault(uuid, 0) + 1;
            packetCounts.put(uuid, count);

            // Debug output if enabled
            if (config.isTimerADebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (TimerA Debug) " + player.getName() + " packet count: " + count);
            }

            // If packet count exceeds allowed limit, trigger violation
            if (count >= config.getMaxPacketsPerSecondA()) {
                violationAlerts.reportViolation(player.getName(), "TimerA");
                int vl = violationAlerts.getViolationCount(player.getName(), "TimerA");

                // Debug output for violation level
                if (config.isTimerADebugMode()) {
                    Bukkit.getLogger().info("[DuckyAntiCheat] (TimerA Debug) " + player.getName() + " VL: " + vl);
                }

                // If violation level exceeds allowed threshold, apply punishment
                if (vl >= config.getMaxTimerAAlerts()) {
                    String command = config.getTimerACommand();
                    violationAlerts.executePunishment(player.getName(), "TimerA", command);
                    discordHook.sendPunishmentCommand(player.getName(), command);

                    if (config.isTimerADebugMode()) {
                        Bukkit.getLogger().info("[DuckyAntiCheat] (TimerA Debug) Executed punishment: " + command);
                    }
                }

                // Cancel event if configured to do so
                return config.isTimerACancelEvents();
            }
        }

        // Nothing to cancel
        return false;
    }

    /**
     * Called when a player moves.
     * This event is triggered very frequently and is suitable for measuring "packet rate".
     */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (handlePacket(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}