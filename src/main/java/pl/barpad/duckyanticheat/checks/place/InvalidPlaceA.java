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
import pl.barpad.duckyanticheat.utils.ViolationAlerts;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.util.HashMap;
import java.util.Map;

public class InvalidPlaceA implements Listener {

    private final ViolationAlerts violationAlerts;
    private final ConfigManager config;
    private final DiscordHook discordHook;

    private final Map<String, Integer> placeViolations = new HashMap<>();

    public InvalidPlaceA(Main plugin, ViolationAlerts violationAlerts, DiscordHook discordHook, ConfigManager config) {
        this.violationAlerts = violationAlerts;
        this.discordHook = discordHook;
        this.config = config;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!config.isInvalidPlaceAEnabled()) return;

        Player player = event.getPlayer();

        if (player.hasPermission("duckyac.bypass") || player.hasPermission("duckyac.*") || player.hasPermission("duckyac.bypass.invalidplace-a")) {
            return;
        }

        Block block = event.getBlockPlaced();
        Location eyeLoc = player.getEyeLocation();
        Vector directionToBlock = block.getLocation().add(0.5, 0.5, 0.5).toVector().subtract(eyeLoc.toVector()).normalize();
        Vector lookDirection = eyeLoc.getDirection().normalize();

        double angle = Math.toDegrees(directionToBlock.angle(lookDirection));

        if (angle > config.getInvalidPlaceAThreshold()) {
            String name = player.getName();
            placeViolations.put(name, placeViolations.getOrDefault(name, 0) + 1);

            if (config.isInvalidPlaceACancelEvent()) {
                event.setCancelled(true);
            }

            if (placeViolations.get(name) >= 3) {
                violationAlerts.reportViolation(name, "InvalidPlaceA");
                int vl = violationAlerts.getViolationCount(name, "InvalidPlaceA");

                if (vl >= config.getMaxInvalidPlaceAAlerts()) {
                    String command = config.getInvalidPlaceACommand();
                    violationAlerts.executePunishment(name, "InvalidPlaceA", command);
                    discordHook.sendPunishmentCommand(name, command);
                }

                placeViolations.put(name, 0);
            }
        } else {
            placeViolations.put(player.getName(), 0);
        }
    }
}