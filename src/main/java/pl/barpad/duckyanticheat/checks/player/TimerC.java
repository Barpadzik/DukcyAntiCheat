package pl.barpad.duckyanticheat.checks.player;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import pl.barpad.duckyanticheat.Main;
import pl.barpad.duckyanticheat.utils.DiscordHook;
import pl.barpad.duckyanticheat.utils.PermissionBypass;
import pl.barpad.duckyanticheat.utils.ViolationAlerts;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TimerC implements Listener {

    private final ViolationAlerts violationAlerts;
    private final ConfigManager config;
    private final DiscordHook discordHook;

    // Stores number of animation packets (swings) per player
    private final ConcurrentHashMap<UUID, Integer> packetCounts = new ConcurrentHashMap<>();

    // Stores the last time packet count was reset per player
    private final ConcurrentHashMap<UUID, Long> lastReset = new ConcurrentHashMap<>();

    /**
     * Constructor for TimerC.
     * Registers the event listener and stores dependencies.
     */
    public TimerC(Main plugin, ViolationAlerts violationAlerts, DiscordHook discordHook, ConfigManager config) {
        this.violationAlerts = violationAlerts;
        this.config = config;
        this.discordHook = discordHook;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Handles animation packet (swing) tracking and violation logic.
     * Called from onAnimate() on each PlayerAnimationEvent.
     *
     * @param player The player who triggered the animation
     * @return true if the event should be cancelled due to violation threshold
     */
    private boolean handlePacket(Player player) {
        // Check if TimerC is enabled in config
        if (!config.isTimerCEnabled()) return false;

        // Check if player has any bypass permissions
        if (PermissionBypass.hasBypass(player)) return false;
        if (player.hasPermission("duckyac.bypass.timer-c")) return false;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long last = lastReset.getOrDefault(uuid, 0L);

        // If a second has passed, reset the packet count
        if (now - last >= 1000) {
            packetCounts.put(uuid, 1);
            lastReset.put(uuid, now);
        } else {
            // Otherwise, increment current packet count
            int count = packetCounts.getOrDefault(uuid, 0) + 1;
            packetCounts.put(uuid, count);

            // Debug output for developers/admins
            if (config.isTimerCDebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (TimerC Debug) " + player.getName() + " animation count: " + count);
            }

            // If animation count exceeds allowed packets per second
            if (count >= config.getMaxPacketsPerSecondC()) {
                // Report violation to internal system
                violationAlerts.reportViolation(player.getName(), "TimerC");

                // Get current violation level
                int vl = violationAlerts.getViolationCount(player.getName(), "TimerC");

                if (config.isTimerCDebugMode()) {
                    Bukkit.getLogger().info("[DuckyAntiCheat] (TimerC Debug) " + player.getName() + " VL: " + vl);
                }

                // If violation level exceeds punishment threshold
                if (vl >= config.getMaxTimerCAlerts()) {
                    String command = config.getTimerCCommand();

                    // Execute punishment (e.g., kick, ban)
                    violationAlerts.executePunishment(player.getName(), "TimerC", command);

                    // Send punishment notification to Discord
                    discordHook.sendPunishmentCommand(player.getName(), command);

                    if (config.isTimerCDebugMode()) {
                        Bukkit.getLogger().info("[DuckyAntiCheat] (TimerC Debug) Executed punishment: " + command);
                    }
                }

                // Return whether this should cancel the animation event
                return config.isTimerCCancelEvents();
            }
        }

        return false;
    }

    /**
     * Event triggered every time the player swings their hand.
     * Used to track the frequency of swing animations (clicks, attacks, block breaking).
     */
    @EventHandler
    public void onAnimate(PlayerAnimationEvent event) {
        if (handlePacket(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}