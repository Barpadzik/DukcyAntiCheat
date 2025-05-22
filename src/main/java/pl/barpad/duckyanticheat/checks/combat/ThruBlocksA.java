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
import pl.barpad.duckyanticheat.utils.ViolationAlerts;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

public class ThruBlocksA implements Listener {

    private final ViolationAlerts violationAlerts;
    private final DiscordHook discordHook;
    private final ConfigManager config;

    public ThruBlocksA(Main plugin, ViolationAlerts violationAlerts, DiscordHook discordHook, ConfigManager config) {
        this.violationAlerts = violationAlerts;
        this.discordHook = discordHook;
        this.config = config;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!config.isThruBlocksEnabled()) return;
        if (attacker.isGliding()) return;
        if (attacker.hasPermission("duckyac.bypass") && attacker.hasPermission("duckyac.*") && attacker.hasPermission("duckyac.bypass.thrublocks-a")) {
            return;
        }

        double distance = attacker.getEyeLocation().distance(victim.getLocation());
        if (distance < 2.2) return;

        Location[] targetPoints = {
                victim.getLocation().add(0, 0.1, 0),
                victim.getLocation().add(0, 0.9, 0),
                victim.getEyeLocation()
        };

        boolean hasClearPath = false;

        for (Location target : targetPoints) {
            BlockIterator iterator = new BlockIterator(
                    attacker.getWorld(),
                    attacker.getEyeLocation().toVector(),
                    target.toVector().subtract(attacker.getEyeLocation().toVector()).normalize(),
                    0.0,
                    (int) attacker.getEyeLocation().distance(target));

            boolean pathBlocked = false;

            while (iterator.hasNext()) {
                Block block = iterator.next();
                if (block.getType().isSolid() || block.getType() == Material.COBWEB) {
                    pathBlocked = true;
                    break;
                }
            }

            if (!pathBlocked) {
                hasClearPath = true;
                break;
            }
        }

        if (!hasClearPath) {
            if (config.isThruBlocksCancelEvent()) {
                event.setCancelled(true);
            }

            violationAlerts.reportViolation(attacker.getName(), "ThruBlocksA");
            int vl = violationAlerts.getViolationCount(attacker.getName(), "ThruBlocksA");

            if (config.isThruBlocksDebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (ThruBlocksA Debug) " + attacker.getName() + " hit through a block (VL: " + vl + ")");
            }

            if (vl >= config.getMaxThruBlocksAlerts()) {
                String cmd = config.getThruBlocksCommand();
                violationAlerts.executePunishment(attacker.getName(), "ThruBlocksA", cmd);
                discordHook.sendPunishmentCommand(attacker.getName(), cmd);
            }
        }
    }
}