package pl.barpad.duckyanticheat.checks.movement;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import pl.barpad.duckyanticheat.Main;
import pl.barpad.duckyanticheat.utils.DiscordHook;
import pl.barpad.duckyanticheat.utils.PermissionBypass;
import pl.barpad.duckyanticheat.utils.ViolationAlerts;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class NoWebA implements Listener {

    private final ViolationAlerts violationAlerts;
    private final ConfigManager config;
    private final DiscordHook discordHook;

    // Stores the number of violations per player concurrently for thread safety
    private final ConcurrentHashMap<String, Integer> webViolations = new ConcurrentHashMap<>();

    /**
     * Constructor - initializes the class with necessary managers and registers event listener.
     * @param plugin Main plugin instance to register events.
     * @param violationAlerts Handler for violation reporting.
     * @param discordHook Handler for Discord webhook integration.
     * @param config Configuration manager for plugin settings.
     */
    public NoWebA(Main plugin, ViolationAlerts violationAlerts, DiscordHook discordHook, ConfigManager config) {
        this.violationAlerts = violationAlerts;
        this.discordHook = discordHook;
        this.config = config;
        // Register this class as an event listener for Bukkit events
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Event handler for player movement events.
     * Checks if player is moving inside cobweb block in an invalid way (moving too fast).
     * @param event PlayerMoveEvent triggered when a player moves.
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // If the NoWebA check is disabled in config, do nothing
        if (!config.isNoWebAEnabled()) return;

        Player player = event.getPlayer();

        // Skip checks if player has bypass permission or custom bypass conditions
        if (PermissionBypass.hasBypass(player)) return;
        if (player.hasPermission("duckyac.bypass.noweb-a")) return;

        // Ignore players who can fly or are inside vehicles
        if (player.getAllowFlight() || player.isInsideVehicle()) {
            if (config.isNoWebADebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (NoWebA Debug) " + player.getName() + " can fly or is in vehicle - skipping");
            }
            return;
        }

        // Get the block at the player's current location
        Block block = player.getLocation().getBlock();

        // If player is not in a cobweb block, reset their violation count
        if (block.getType() != Material.COBWEB) {
            if (config.isNoWebADebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (NoWebA Debug) " + player.getName() + " is not in cobweb - resetting violations");
            }
            webViolations.put(player.getName(), 0);
            return;
        }

        // Calculate vertical and horizontal movement distance since the last event
        double deltaY = Objects.requireNonNull(event.getTo()).getY() - event.getFrom().getY();
        double deltaXZ = Math.hypot(
                event.getTo().getX() - event.getFrom().getX(),
                event.getTo().getZ() - event.getFrom().getZ()
        );

        if (config.isNoWebADebugMode()) {
            Bukkit.getLogger().info("[DuckyAntiCheat] (NoWebA Debug) " + player.getName() + " deltaY=" + deltaY + ", deltaXZ=" + deltaXZ);
        }

        // Check if player moved too far vertically or horizontally inside cobweb (exceeding thresholds)
        if (deltaY > 0.15 || deltaXZ > 0.15) {
            String name = player.getName();
            // Increase violation count by 1 for this player
            int current = webViolations.getOrDefault(name, 0) + 1;
            webViolations.put(name, current);

            if (config.isNoWebADebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (NoWebA Debug) " + name + " violation count increased to " + current);
            }

            // If the violation count exceeds a threshold (3), report and punish
            if (current >= 3) {
                if (config.isNoWebADebugMode()) {
                    Bukkit.getLogger().info("[DuckyAntiCheat] (NoWebA Debug) " + name + " exceeded violation threshold - reporting violation");
                }

                // Report the violation to the alert system
                violationAlerts.reportViolation(name, "NoWebA");
                int vl = violationAlerts.getViolationCount(name, "NoWebA");

                // Optionally, cancel the movement event (prevent movement) if enabled in config
                if (config.isNoWebACancelEvent()) {
                    event.setCancelled(true);
                    if (config.isNoWebADebugMode()) {
                        Bukkit.getLogger().info("[DuckyAntiCheat] (NoWebA Debug) " + name + " event cancelled due to violation");
                    }
                }

                // If the violation level exceeds max alerts, execute punishment command
                if (vl >= config.getMaxNoWebAAlerts()) {
                    String command = config.getNoWebACommand();

                    if (config.isNoWebADebugMode()) {
                        Bukkit.getLogger().info("[DuckyAntiCheat] (NoWebA Debug) " + name + " exceeded alert threshold - executing punishment: " + command);
                    }

                    // Execute punishment command and notify Discord
                    violationAlerts.executePunishment(name, "NoWebA", command);
                    discordHook.sendPunishmentCommand(name, command);
                }

                // Reset violation count after punishment/reporting
                webViolations.put(name, 0);
            }
        } else {
            // If movement is within allowed thresholds, reset violation count for this player
            if (config.isNoWebADebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (NoWebA Debug) " + player.getName() + " moved within threshold - resetting violations");
            }
            webViolations.put(player.getName(), 0);
        }
    }
}