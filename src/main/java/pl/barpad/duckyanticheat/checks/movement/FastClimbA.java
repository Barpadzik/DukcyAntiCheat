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

import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

public class FastClimbA implements Listener {

    private final ViolationAlerts violationAlerts;
    private final ConfigManager config;
    private final DiscordHook discordHook;

    // Stores violation levels for each player
    private final HashMap<UUID, Integer> violationLevels = new HashMap<>();

    public FastClimbA(Main plugin, ViolationAlerts violationAlerts, DiscordHook discordHook, ConfigManager config) {
        this.violationAlerts = violationAlerts;
        this.config = config;
        this.discordHook = discordHook;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!config.isFastClimbAEnabled()) return;

        Player player = event.getPlayer();

        // Skip checks for players with bypass permission
        if (PermissionBypass.hasBypass(player)) return;
        if (player.hasPermission("duckyac.bypass.fastclimb-a")) return;

        Block block = player.getLocation().getBlock();
        Material type = block.getType();

        // Check if a player is currently climbing a climbable block
        if (type != Material.LADDER &&
                type != Material.VINE &&
                type != Material.TWISTING_VINES &&
                type != Material.SCAFFOLDING) {
            return;
        }

        // Calculate vertical movement (Y-axis)
        double deltaY = Objects.requireNonNull(event.getTo()).getY() - event.getFrom().getY();

        // Get the configured max allowed climbing speed (default ~0.1176 is vanilla)
        double maxClimbSpeed = config.getFastClimbAMaxSpeed();

        if (deltaY > maxClimbSpeed) {
            String playerName = player.getName();

            // Optional debug logging
            if (config.isFastClimbADebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (FastClimbA Debug) " + playerName + " is climbing too fast: " + deltaY);
            }

            // Report the violation to the alert system
            violationAlerts.reportViolation(playerName, "FastClimbA");

            // Increase and store violation level
            int vl = violationLevels.getOrDefault(player.getUniqueId(), 0) + 1;
            violationLevels.put(player.getUniqueId(), vl);

            if (config.isFastClimbADebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (FastClimbA Debug) " + playerName + " VL: " + vl);
            }

            // If the player reached the threshold, punish
            if (vl >= config.getMaxFastClimbAAlerts()) {
                String command = config.getFastClimbACommand();
                violationAlerts.executePunishment(playerName, "FastClimbA", command);
                discordHook.sendPunishmentCommand(playerName, command);

                if (config.isFastClimbADebugMode()) {
                    Bukkit.getLogger().info("[DuckyAntiCheat] (FastClimbA Debug) Executed punishment: " + command);
                }

                // Reset VL after punishment
                violationLevels.put(player.getUniqueId(), 0);
            }

            // Cancel the climbing movement if configured
            if (config.isFastClimbACancelEvents()) {
                event.setCancelled(true);
            }
        }
    }
}