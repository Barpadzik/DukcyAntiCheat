package pl.barpad.duckyanticheat.checks.player;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import pl.barpad.duckyanticheat.Main;
import pl.barpad.duckyanticheat.utils.DiscordHook;
import pl.barpad.duckyanticheat.utils.PermissionBypass;
import pl.barpad.duckyanticheat.utils.ViolationAlerts;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TimerB implements Listener {

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
    public TimerB(Main plugin, ViolationAlerts violationAlerts, DiscordHook discordHook, ConfigManager config) {
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
        if (!config.isTimerBEnabled()) return false;
        if (PermissionBypass.hasBypass(player)) return false;
        if (player.hasPermission("duckyac.bypass.timer-b")) return false;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long last = lastReset.getOrDefault(uuid, 0L);

        if (now - last >= 1000) {
            packetCounts.put(uuid, 1);
            lastReset.put(uuid, now);
        } else {
            int count = packetCounts.getOrDefault(uuid, 0) + 1;
            packetCounts.put(uuid, count);

            if (config.isTimerBDebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (TimerB Debug) " + player.getName() + " packet count: " + count);
            }

            if (count >= config.getMaxPacketsPerSecondB()) {
                violationAlerts.reportViolation(player.getName(), "TimerB");
                int vl = violationAlerts.getViolationCount(player.getName(), "TimerB");

                if (config.isTimerBDebugMode()) {
                    Bukkit.getLogger().info("[DuckyAntiCheat] (TimerB Debug) " + player.getName() + " VL: " + vl);
                }

                if (vl >= config.getMaxTimerBAlerts()) {
                    String command = config.getTimerBCommand();
                    violationAlerts.executePunishment(player.getName(), "TimerB", command);
                    discordHook.sendPunishmentCommand(player.getName(), command);
                    if (config.isTimerBDebugMode()) {
                        Bukkit.getLogger().info("[DuckyAntiCheat] (TimerB Debug) Executed punishment: " + command);
                    }
                }

                // Cancel event if configured to do so
                return config.isTimerBCancelEvents();
            }
        }

        // Nothing to cancel
        return false;
    }

    /**
     * Called when a player interacts (clicks with item, opens doors, presses buttons, etc.)
     * This event is commonly triggered by players during regular gameplay.
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (handlePacket(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}