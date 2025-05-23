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

    private final ConcurrentHashMap<String, Integer> webViolations = new ConcurrentHashMap<>();

    public NoWebA(Main plugin, ViolationAlerts violationAlerts, DiscordHook discordHook, ConfigManager config) {
        this.violationAlerts = violationAlerts;
        this.discordHook = discordHook;
        this.config = config;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!config.isNoWebAEnabled()) return;

        Player player = event.getPlayer();

        if (PermissionBypass.hasBypass(player)) return;

        if (player.hasPermission("duckyac.bypass.noweb-a")) {
            return;
        }

        if (player.getAllowFlight() || player.isInsideVehicle()) {
            if (config.isNoWebADebugMode()) {
                Bukkit.getLogger().info("[NoWebA] " + player.getName() + " can fly or is in vehicle - skipping");
            }
            return;
        }

        Block block = player.getLocation().getBlock();
        if (block.getType() != Material.COBWEB) {
            if (config.isNoWebADebugMode()) {
                Bukkit.getLogger().info("[NoWebA] " + player.getName() + " is not in cobweb - resetting violations");
            }
            webViolations.put(player.getName(), 0);
            return;
        }

        double deltaY = Objects.requireNonNull(event.getTo()).getY() - event.getFrom().getY();
        double deltaXZ = Math.hypot(
                event.getTo().getX() - event.getFrom().getX(),
                event.getTo().getZ() - event.getFrom().getZ()
        );

        if (config.isNoWebADebugMode()) {
            Bukkit.getLogger().info("[NoWebA] " + player.getName() + " deltaY=" + deltaY + ", deltaXZ=" + deltaXZ);
        }

        if (deltaY > 0.15 || deltaXZ > 0.15) {
            String name = player.getName();
            int current = webViolations.getOrDefault(name, 0) + 1;
            webViolations.put(name, current);

            if (config.isNoWebADebugMode()) {
                Bukkit.getLogger().info("[NoWebA] " + name + " violation count increased to " + current);
            }

            if (current >= 3) {
                if (config.isNoWebADebugMode()) {
                    Bukkit.getLogger().info("[NoWebA] " + name + " exceeded violation threshold - reporting violation");
                }

                violationAlerts.reportViolation(name, "NoWebA");
                int vl = violationAlerts.getViolationCount(name, "NoWebA");

                if (config.isNoWebACancelEvent()) {
                    event.setCancelled(true);
                    if (config.isNoWebADebugMode()) {
                        Bukkit.getLogger().info("[NoWebA] " + name + " event cancelled due to violation");
                    }
                }

                if (vl >= config.getMaxNoWebAAlerts()) {
                    String command = config.getNoWebACommand();

                    if (config.isNoWebADebugMode()) {
                        Bukkit.getLogger().info("[NoWebA] " + name + " exceeded alert threshold - executing punishment: " + command);
                    }

                    violationAlerts.executePunishment(name, "NoWebA", command);
                    discordHook.sendPunishmentCommand(name, command);
                }

                webViolations.put(name, 0);
            }
        } else {
            if (config.isNoWebADebugMode()) {
                Bukkit.getLogger().info("[NoWebA] " + player.getName() + " moved within threshold - resetting violations");
            }
            webViolations.put(player.getName(), 0);
        }
    }
}