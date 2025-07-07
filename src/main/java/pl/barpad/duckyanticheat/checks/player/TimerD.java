package pl.barpad.duckyanticheat.checks.player;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import pl.barpad.duckyanticheat.Main;
import pl.barpad.duckyanticheat.utils.DiscordHook;
import pl.barpad.duckyanticheat.utils.PermissionBypass;
import pl.barpad.duckyanticheat.utils.ViolationAlerts;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TimerD implements Listener {

    private final ViolationAlerts violationAlerts;
    private final ConfigManager config;
    private final DiscordHook discordHook;

    // Stores packet counts per player UUID
    private final ConcurrentHashMap<UUID, Integer> packetCounts = new ConcurrentHashMap<>();
    // Stores last reset timestamps per player UUID
    private final ConcurrentHashMap<UUID, Long> lastReset = new ConcurrentHashMap<>();

    public TimerD(Main plugin, ViolationAlerts violationAlerts, DiscordHook discordHook, ConfigManager config) {
        this.violationAlerts = violationAlerts;
        this.config = config;
        this.discordHook = discordHook;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Handles packet tracking and violation logic.
     * Called by each relevant event involving player interaction with entities.
     *
     * @param player Player to handle
     * @return true if the event should be cancelled due to violation, false otherwise
     */
    private boolean handlePacket(Player player) {
        if (!config.isTimerDEnabled()) return false;
        if (PermissionBypass.hasBypass(player)) return false;
        if (player.hasPermission("duckyac.bypass.timer-d")) return false;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long last = lastReset.getOrDefault(uuid, 0L);

        // Reset counter every second
        if (now - last >= 1000) {
            packetCounts.put(uuid, 1);
            lastReset.put(uuid, now);
        } else {
            int count = packetCounts.getOrDefault(uuid, 0) + 1;
            packetCounts.put(uuid, count);

            // Debug information for developers/admins
            if (config.isTimerDDebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (TimerD Debug) " + player.getName() + " packet count: " + count);
            }

            // Check if packet count exceeds allowed maximum
            if (count >= config.getMaxPacketsPerSecondD()) {
                violationAlerts.reportViolation(player.getName(), "TimerD");
                int vl = violationAlerts.getViolationCount(player.getName(), "TimerD");

                if (config.isTimerDDebugMode()) {
                    Bukkit.getLogger().info("[DuckyAntiCheat] (TimerD Debug) " + player.getName() + " VL: " + vl);
                }

                if (vl >= config.getMaxTimerDAlerts()) {
                    String command = config.getTimerDCommand();
                    violationAlerts.executePunishment(player.getName(), "TimerD", command);
                    discordHook.sendPunishmentCommand(player.getName(), command);
                    if (config.isTimerDDebugMode()) {
                        Bukkit.getLogger().info("[DuckyAntiCheat] (TimerD Debug) Executed punishment: " + command);
                    }
                }

                // Cancel event if configured to do so
                return config.isTimerDCancelEvents();
            }
        }

        // Nothing to cancel
        return false;
    }

    /**
     * Event handler for entity interaction by the player (right-clicking mobs, players, item frames, etc.)
     *
     * @param event PlayerInteractEntityEvent
     */
    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (handlePacket(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}