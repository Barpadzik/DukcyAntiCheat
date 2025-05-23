package pl.barpad.duckyanticheat.checks.place;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.Vector;
import pl.barpad.duckyanticheat.Main;
import pl.barpad.duckyanticheat.utils.DiscordHook;
import pl.barpad.duckyanticheat.utils.PermissionBypass;
import pl.barpad.duckyanticheat.utils.ViolationAlerts;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.util.HashMap;
import java.util.Map;

public class InvalidPlaceA implements Listener {

    // Manager for violation alerts and punishments
    private final ViolationAlerts violationAlerts;

    // Configuration manager for this specific check
    private final ConfigManager config;

    // Discord webhook to send punishment commands externally
    private final DiscordHook discordHook;

    // Map to count consecutive invalid place attempts per player
    private final Map<String, Integer> placeViolations = new HashMap<>();

    /**
     * Constructor registers the event listener and initializes dependencies.
     *
     * @param plugin         main plugin instance
     * @param violationAlerts violation alerts handler
     * @param discordHook    Discord integration for punishment logging
     * @param config         configuration manager for check settings
     */
    public InvalidPlaceA(Main plugin, ViolationAlerts violationAlerts, DiscordHook discordHook, ConfigManager config) {
        this.violationAlerts = violationAlerts;
        this.discordHook = discordHook;
        this.config = config;

        // Register this class as an event listener
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Event handler for block placement events.
     * Checks if the block is placed at an angle exceeding the threshold
     * compared to where the player is looking.
     *
     * @param event the block place event
     */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // If check is disabled in config, do nothing
        if (!config.isInvalidPlaceAEnabled()) return;

        Player player = event.getPlayer();

        // Bypass players with global or specific permission
        if (PermissionBypass.hasBypass(player)) return;
        if (player.hasPermission("duckyac.bypass.invalidplace-a")) return;

        Block block = event.getBlockPlaced();

        // Calculate vector from player's eye location to center of placed block
        Location eyeLoc = player.getEyeLocation();
        Vector directionToBlock = block.getLocation().add(0.5, 0.5, 0.5).toVector()
                .subtract(eyeLoc.toVector()).normalize();

        // Get player's looking direction vector normalized
        Vector lookDirection = eyeLoc.getDirection().normalize();

        // Calculate the angle in degrees between looking direction and block direction
        double angle = Math.toDegrees(directionToBlock.angle(lookDirection));

        // If angle exceeds configured threshold, treat as suspicious placement
        if (angle > config.getInvalidPlaceAThreshold()) {
            String name = player.getName();

            // Increase count of invalid placements for this player
            placeViolations.put(name, placeViolations.getOrDefault(name, 0) + 1);

            // Cancel the block place event if configured
            if (config.isInvalidPlaceACancelEvent()) {
                event.setCancelled(true);
            }

            // If player reached 3 invalid placements, report violation
            if (placeViolations.get(name) >= 3) {
                violationAlerts.reportViolation(name, "InvalidPlaceA");
                int vl = violationAlerts.getViolationCount(name, "InvalidPlaceA");

                // If violation level exceeds max allowed, execute punishment
                if (vl >= config.getMaxInvalidPlaceAAlerts()) {
                    String command = config.getInvalidPlaceACommand();
                    violationAlerts.executePunishment(name, "InvalidPlaceA", command);
                    discordHook.sendPunishmentCommand(name, command);
                }

                // Reset invalid placement counter after reporting
                placeViolations.put(name, 0);
            }
        } else {
            // Reset invalid placement count if placement is valid
            placeViolations.put(player.getName(), 0);
        }
    }
}