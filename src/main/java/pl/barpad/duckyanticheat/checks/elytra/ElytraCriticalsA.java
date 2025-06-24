package pl.barpad.duckyanticheat.checks.elytra;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import pl.barpad.duckyanticheat.utils.PermissionBypass;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;
import pl.barpad.duckyanticheat.utils.DiscordHook;
import pl.barpad.duckyanticheat.Main;
import pl.barpad.duckyanticheat.utils.ViolationAlerts;

import java.util.concurrent.ConcurrentHashMap;

public class ElytraCriticalsA implements Listener {

    // Utilities for violation reporting, Discord notifications and config management
    private final ViolationAlerts violationAlerts;
    private final DiscordHook discordHook;
    private final ConfigManager config;

    // Tracks consecutive critical hits per player
    private final ConcurrentHashMap<String, Integer> playerCriticalHits = new ConcurrentHashMap<>();
    // Tracks last critical hit timestamp per player for timeframe checks
    private final ConcurrentHashMap<String, Long> lastHitTime = new ConcurrentHashMap<>();
    // Tracks how many times the player has triggered violation reports (pending)
    private final ConcurrentHashMap<String, Integer> pendingViolations = new ConcurrentHashMap<>();

    /**
     * Constructor initializes references and registers event listener.
     * @param plugin instance of main plugin class
     * @param violationAlerts handles violation tracking and notifications
     * @param discordHook sends messages to Discord channel
     * @param config plugin configuration manager
     */
    public ElytraCriticalsA(Main plugin, ViolationAlerts violationAlerts, DiscordHook discordHook, ConfigManager config) {
        this.violationAlerts = violationAlerts;
        this.discordHook = discordHook;
        this.config = config;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Event handler for when an entity is damaged by another entity.
     * Checks if the attacker is a player gliding with elytra performing critical hits.
     */
    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        // Exit if the check is disabled in config
        if (!config.isElytraCriticalsAEnabled()) return;

        // Only proceed if damager is a Player and entity damaged is also a Player
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player)) return;

        // Bypass check if player has all bypass permissions
        if (PermissionBypass.hasBypass(attacker)) return;
        if (attacker.hasPermission("duckyac.bypass.elytracriticals-a")) {
            return;
        }

        // Check if attacker is gliding and falling (required for critical hits)
        if (attacker.isGliding() && attacker.getFallDistance() > 0.0) {
            String playerName = attacker.getName();
            long currentTime = System.currentTimeMillis();

            // Determine if the attack qualifies as a critical hit while gliding
            boolean isCritical = attacker.getFallDistance() > 0 &&
                    !attacker.isOnGround() &&
                    !attacker.isInsideVehicle() &&
                    !attacker.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS) &&
                    attacker.getVelocity().getY() < 0;

            if (isCritical) {
                // Reset or increment consecutive critical hits count based on timeframe
                long lastTime = lastHitTime.getOrDefault(playerName, 0L);
                if (currentTime - lastTime > config.getElytraCriticalsATimeframe()) {
                    playerCriticalHits.put(playerName, 1);
                } else {
                    playerCriticalHits.put(playerName, playerCriticalHits.getOrDefault(playerName, 0) + 1);
                }
                lastHitTime.put(playerName, currentTime);

                // Debug info logging if enabled
                if (config.isElytraCriticalsADebugMode()) {
                    Bukkit.getLogger().info("[DuckyAntiKomar] (ElytraCriticalsA Debug) " + playerName +
                            " landed a critical hit while gliding. (" + playerCriticalHits.get(playerName) + "/" +
                            config.getElytraCriticalsACriticalHitsRequired() + ")");
                }
            } else {
                // Reset count if the current hit is not critical
                playerCriticalHits.put(playerName, 0);
            }

            // Check if player reached the required consecutive critical hits threshold
            if (playerCriticalHits.getOrDefault(playerName, 0) >= config.getElytraCriticalsACriticalHitsRequired()) {
                // Increment pending violation reports count
                pendingViolations.put(playerName, pendingViolations.getOrDefault(playerName, 0) + 1);

                // If a player has 2 or more pending violations, report violation
                if (pendingViolations.get(playerName) >= 2) {
                    violationAlerts.reportViolation(playerName, "ElytraCriticalsA");
                    int vl = violationAlerts.getViolationCount(playerName, "ElytraCriticalsA");

                    // Cancel the event if configured to do so (stop damage)
                    if (config.isElytraCriticalsACancelEvent()) {
                        event.setCancelled(true);
                    }

                    // Debug info for violation trigger
                    if (config.isElytraCriticalsADebugMode()) {
                        Bukkit.getLogger().info("[DuckyAntiCheat] (ElytraCriticalsA Debug) " + playerName +
                                " triggered violation after 2 reports (VL: " + vl + ")");
                    }

                    // Execute punishment if violation count reached max alerts configured
                    if (vl == config.getMaxElytraCriticalsAAlerts()) {
                        String cmd = config.getElytraCriticalsACommand();
                        violationAlerts.executePunishment(playerName, "ElytraCriticalsA", cmd);
                        discordHook.sendPunishmentCommand(playerName, cmd);

                        if (config.isElytraCriticalsADebugMode()) {
                            Bukkit.getLogger().info("[DuckyAntiCheat] (ElytraCriticalsA Debug) Penalty executed for " + playerName);
                        }
                    }

                    // Reset pending violations after reporting
                    pendingViolations.put(playerName, 0);
                }

                // Reset critical hits counter after violation check
                playerCriticalHits.put(playerName, 0);
            }
        }
    }
}