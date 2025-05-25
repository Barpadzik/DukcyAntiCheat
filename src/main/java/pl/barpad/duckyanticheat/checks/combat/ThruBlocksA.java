package pl.barpad.duckyanticheat.checks.combat;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.BlockIterator;
import pl.barpad.duckyanticheat.Main;
import pl.barpad.duckyanticheat.utils.DiscordHook;
import pl.barpad.duckyanticheat.utils.PermissionBypass;
import pl.barpad.duckyanticheat.utils.ViolationAlerts;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.util.Objects;

public class ThruBlocksA implements Listener {

    private final ViolationAlerts violationAlerts;
    private final DiscordHook discordHook;
    private final ConfigManager config;

    /**
     * Constructor - initializes references and registers event listener.
     */
    public ThruBlocksA(Main plugin, ViolationAlerts violationAlerts, DiscordHook discordHook, ConfigManager config) {
        this.violationAlerts = violationAlerts;
        this.discordHook = discordHook;
        this.config = config;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Listens for player-vs.-player damage events.
     * Detects if the attacker hits through blocks illegally.
     */
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Check if damager and victim are players
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        // Check if feature is enabled and attacker is not gliding (elytra)
        if (!config.isThruBlocksEnabled()) return;
        if (attacker.isGliding()) return;

        // Permission bypass check
        if (PermissionBypass.hasBypass(Objects.requireNonNull(attacker.getPlayer()))) return;
        if (attacker.hasPermission("duckyac.bypass.thrublocks-a")) return;

        // Ignore very close hits to reduce false positives
        double distance = attacker.getEyeLocation().distance(victim.getLocation());
        if (distance < 2.2) return;

        // Define multiple target points on the victim for ray tracing
        Location[] targetPoints = {
                victim.getLocation().add(0, 0.1, 0),    // Near feet
                victim.getLocation().add(0, 0.9, 0),    // Near the chest
                victim.getEyeLocation()                  // Head height
        };

        boolean hasClearPath = false;

        // Check line of sight for each target point
        for (Location target : targetPoints) {
            BlockIterator iterator = new BlockIterator(
                    attacker.getWorld(),
                    attacker.getEyeLocation().toVector(),
                    target.toVector().subtract(attacker.getEyeLocation().toVector()).normalize(),
                    0.0,
                    (int) attacker.getEyeLocation().distance(target)
            );

            boolean pathBlocked = false;

            // Iterate through blocks on path and check if any are solid or cobweb
            while (iterator.hasNext()) {
                Block block = iterator.next();
                if (block.getType().isSolid() || block.getType() == Material.COBWEB) {
                    pathBlocked = true;
                    break;
                }
            }

            if (!pathBlocked) {
                hasClearPath = true;
                break;  // No need to check other points if one clear path found
            }
        }

        // If no clear path was found, this is potentially hitting through blocks
        if (!hasClearPath) {
            // Cancel event if configured
            if (config.isThruBlocksCancelEvent()) {
                event.setCancelled(true);
            }

            // Report a violation and retrieve the updated violation level (VL)
            int vl = violationAlerts.reportViolation(attacker.getName(), "ThruBlocksA"); // <- Returns the new VL after incrementing

            // If debug mode is enabled, log detailed info to the console
            if (config.isThruBlocksDebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (ThruBlocksA Debug) " + attacker.getName() +
                        " hit through a block (VL: " + vl + ")");
            }

            // If the violation level has reached or exceeded the configured maximum, execute punishment
            if (vl >= config.getMaxThruBlocksAlerts()) {
                String cmd = config.getThruBlocksCommand(); // Get the punishment command from config

                // Execute the punishment command on the player
                violationAlerts.executePunishment(attacker.getName(), "ThruBlocksA", cmd);

                // Send the punishment info to Discord (if applicable)
                discordHook.sendPunishmentCommand(attacker.getName(), cmd);

                // Log punishment execution if debug mode is enabled
                if (config.isThruBlocksDebugMode()) {
                    Bukkit.getLogger().info("[DuckyAntiCheat] (ThruBlocksA Debug) Punishment executed for " + attacker.getName());
                }
            }
        }
    }
}