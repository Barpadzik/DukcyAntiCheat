package pl.barpad.duckyanticheat.checks.elytra;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import pl.barpad.duckyanticheat.Main;
import pl.barpad.duckyanticheat.utils.DiscordHook;
import pl.barpad.duckyanticheat.utils.ViolationAlerts;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.util.HashMap;
import java.util.UUID;

public class ElytraAimA implements Listener {

    private final ConfigManager config;
    private final ViolationAlerts violationAlerts;
    private final DiscordHook discordHook;

    private final HashMap<UUID, Long> fireworkUsageTimes = new HashMap<>();

    public ElytraAimA(Main plugin, ViolationAlerts violationAlerts, DiscordHook discordHook, ConfigManager config) {
        this.config = config;
        this.violationAlerts = violationAlerts;
        this.discordHook = discordHook;

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onFireworkUse(PlayerInteractEvent event) {
        if (config.isElytraAimAEnabled()) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item != null && item.getType() == Material.FIREWORK_ROCKET && player.isGliding()) {
            fireworkUsageTimes.put(player.getUniqueId(), System.currentTimeMillis());

            if (config.isElytraAimADebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (ElytraAimA Debug) " + player.getName() + " used a firework while gliding.");
            }
        }
    }

    @EventHandler
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (config.isElytraAimAEnabled()) return;

        if (!(event.getDamager() instanceof Player damager) || !(event.getEntity() instanceof Player victim)) return;

        UUID damagerUUID = damager.getUniqueId();
        String playerName = damager.getName();

        if (damager.hasPermission("duckyac.bypass") && damager.hasPermission("duckyac.*") && damager.hasPermission("duckyac.bypass.elytraaim-a")) {
            return;
        }

        Long fireworkTime = fireworkUsageTimes.remove(damagerUUID);

        if (fireworkTime == null) return;

        long delay = System.currentTimeMillis() - fireworkTime;

        if (config.isElytraAimADebugMode()) {
            Bukkit.getLogger().info("[DuckyAntiCheat] (ElytraAimA Debug) " + playerName + " hit " + victim.getName() +
                    " | Delay: " + delay + "ms | Max: " + config.getElytraAimAMaxFireworkDelay() + "ms");
        }

        if (delay <= config.getElytraAimAMaxFireworkDelay()) {
            violationAlerts.reportViolation(playerName, "ElytraAimA");

            if (config.isElytraAimACancelEvent()) {
                event.setCancelled(true);
            }

            if (config.isElytraAimADebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (ElytraAimA Debug) Violation reported for " + playerName + " (ElytraAimA).");
            }

            int vl = violationAlerts.getViolationCount(playerName, "ElytraAimA");
            if (vl == config.getElytraAimAMaxAlerts()) {
                String punishmentCommand = config.getElytraAimACommand();

                violationAlerts.executePunishment(playerName, "ElytraAimA", punishmentCommand);
                discordHook.sendPunishmentCommand(playerName, punishmentCommand);

                if (config.isElytraAimADebugMode()) {
                    Bukkit.getLogger().info("[DuckyAntiCheat] (ElytraAimA Debug) Punishment executed for " + playerName);
                }
            }
        }
    }
}