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
import pl.barpad.duckyanticheat.utils.PermissionBypass;
import pl.barpad.duckyanticheat.utils.ViolationAlerts;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ElytraAimA implements Listener {

    private final ConfigManager config;
    private final ViolationAlerts violationAlerts;
    private final DiscordHook discordHook;

    // Stores the timestamp of the last firework usage per player UUID
    private final ConcurrentHashMap<UUID, Long> fireworkUsageTimes = new ConcurrentHashMap<>();

    /**
     * Constructor registers this listener and initializes dependencies.
     */
    public ElytraAimA(Main plugin, ViolationAlerts violationAlerts, DiscordHook discordHook, ConfigManager config) {
        this.config = config;
        this.violationAlerts = violationAlerts;
        this.discordHook = discordHook;

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Event handler for when a player uses a firework rocket.
     * Records the usage time if the player is gliding (elytra flying).
     */
    @EventHandler
    public void onFireworkUse(PlayerInteractEvent event) {
        // Exit if the check is disabled in config
        if (config.isElytraAimAEnabled()) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Check if the player used a firework rocket while gliding
        if (item != null && item.getType() == Material.FIREWORK_ROCKET && player.isGliding()) {
            fireworkUsageTimes.put(player.getUniqueId(), System.currentTimeMillis());

            if (config.isElytraAimADebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (ElytraAimA Debug) " + player.getName() + " used a firework while gliding.");
            }
        }
    }

    /**
     * Event handler for when a player hits another entity.
     * Checks if the hit was done shortly after a firework used while gliding,
     * which might indicate suspicious behavior.
     */
    @EventHandler
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        // Exit if the check is disabled in config
        if (!config.isElytraAimAEnabled()) return;

        // Verify both damager and victim are players
        if (!(event.getDamager() instanceof Player damager) || !(event.getEntity() instanceof Player victim)) return;

        UUID damagerUUID = damager.getUniqueId();
        String playerName = damager.getName();

        // Check for permission bypass
        if (PermissionBypass.hasBypass(damager)) return;
        if (damager.hasPermission("duckyac.bypass.elytraaim-a")) return;

        // Retrieve and remove the last firework usage time for this player
        Long fireworkTime = fireworkUsageTimes.remove(damagerUUID);
        if (fireworkTime == null) return; // No recent firework use recorded

        long delay = System.currentTimeMillis() - fireworkTime;

        if (config.isElytraAimADebugMode()) {
            Bukkit.getLogger().info("[DuckyAntiCheat] (ElytraAimA Debug) " + playerName + " hit " + victim.getName() +
                    " | Delay: " + delay + "ms | Max: " + config.getElytraAimAMaxFireworkDelay() + "ms");
        }

        // If hit occurred within the max allowed delay after firework use, register violation
        if (delay <= config.getElytraAimAMaxFireworkDelay()) {
            violationAlerts.reportViolation(playerName, "ElytraAimA");

            if (config.isElytraAimACancelEvent()) {
                event.setCancelled(true); // Cancel the hit event if configured
            }

            if (config.isElytraAimADebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (ElytraAimA Debug) Violation reported for " + playerName + " (ElytraAimA).");
            }

            // Check if player reached max violations and apply punishment if so
            int violationLevel = violationAlerts.getViolationCount(playerName, "ElytraAimA");
            if (violationLevel >= config.getElytraAimAMaxAlerts()) {
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