package pl.barpad.duckyanticheat.checks.movement;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import pl.barpad.duckyanticheat.utils.DiscordHook;
import pl.barpad.duckyanticheat.utils.PermissionBypass;
import pl.barpad.duckyanticheat.utils.ViolationAlerts;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;


import java.util.UUID;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class NoSlowDownF implements Listener {

    private final ViolationAlerts alerts;
    private final DiscordHook discord;
    private final ConfigManager config;

    // Maps for tracking player states
    private final ConcurrentHashMap<UUID, Long> lastCheck = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Location> lastLocations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastElytraFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastPlayerFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> wasGliding = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> wasFlying = new ConcurrentHashMap<>();

    public NoSlowDownF(Plugin plugin, ViolationAlerts alerts, DiscordHook discord, ConfigManager config) {
        this.alerts = alerts;
        this.discord = discord;
        this.config = config;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!config.isNoSlowDownFEnabled()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Check if player has a bypass
        if (PermissionBypass.hasBypass(player)) return;
        if (player.hasPermission("duckyac.bypass.noslowdown-f") || player.hasPermission("duckyac.bypass.noslowdown.*")) return;

        // Ignore if player is flying
        if (player.isFlying()) return;

        // Handle recent elytra gliding
        boolean isCurrentlyGliding = player.isGliding();
        boolean wasPreviouslyGliding = wasGliding.getOrDefault(uuid, false);
        if (!isCurrentlyGliding && wasPreviouslyGliding) {
            lastElytraFlight.put(uuid, System.currentTimeMillis());
        }
        wasGliding.put(uuid, isCurrentlyGliding);
        if (!isCurrentlyGliding && lastElytraFlight.containsKey(uuid)
                && System.currentTimeMillis() - lastElytraFlight.get(uuid) < 1000) {
            return;
        }

        // Handle recent player flying
        boolean isCurrentlyFlying = player.isFlying();
        boolean wasPreviouslyFlying = wasFlying.getOrDefault(uuid, false);
        if (!isCurrentlyFlying && wasPreviouslyFlying) {
            lastPlayerFlight.put(uuid, System.currentTimeMillis());
        }
        wasFlying.put(uuid, isCurrentlyFlying);
        if (!isCurrentlyFlying && lastPlayerFlight.containsKey(uuid)
                && System.currentTimeMillis() - lastPlayerFlight.get(uuid) < 1000) {
            return;
        }

        // Ignore if not on ground or in creative/spectator mode
        if (!player.isOnGround()
                || player.getGameMode().name().contains("CREATIVE")
                || player.getGameMode().name().contains("SPECTATOR")) {
            return;
        }

        // Check if player is on SOUL_SAND
        Block blockBelow = player.getLocation().subtract(0, 0.1, 0).getBlock();
        if (blockBelow.getType() != Material.SOUL_SAND) return;

        // Track current and last location
        Location current = player.getLocation();
        Location previous = lastLocations.getOrDefault(uuid, current);
        lastLocations.put(uuid, current.clone());

        // Ignore movement if Y position changed significantly
        if (Math.abs(current.getY() - previous.getY()) > 0.001) return;

        // Calculate horizontal speed
        double speed = getHorizontalSpeed(event);
        double maxSpeed = config.getNoSlowDownFMaxSpeed();

        // Adjust max speed based on enchantments
        int soulSpeedLevel = getEnchantmentLevel(player.getInventory().getBoots(), Enchantment.SOUL_SPEED);
        if (soulSpeedLevel > 0) {
            maxSpeed *= 1.2 + (0.15 * soulSpeedLevel);
        }

        int depthStriderLevel = getEnchantmentLevel(player.getInventory().getBoots(), Enchantment.DEPTH_STRIDER);
        if (depthStriderLevel > 0) {
            maxSpeed *= 1.0 + (0.15 * depthStriderLevel);
        }

        // Check if player exceeded speed threshold
        if (speed > maxSpeed) {
            long now = System.currentTimeMillis();
            long last = lastCheck.getOrDefault(uuid, 0L);
            if (now - last < 300L) return;

            int vl = alerts.reportViolation(player.getName(), "NoSlowDownF");

            // Debug message
            if (config.isNoSlowDownFDebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (NoSlowDownF Debug) " + player.getName()
                        + " moved too fast on SOUL_SAND (" + String.format("%.3f", speed)
                        + " > " + String.format("%.3f", maxSpeed) + ")");
            }

            // Apply punishment if violation level is high enough
            if (vl >= config.getMaxNoSlowDownFAlerts()) {
                String cmd = config.getNoSlowDownFCommand();
                alerts.executePunishment(player.getName(), "NoSlowDownF", cmd);
                discord.sendPunishmentCommand(player.getName(), cmd);
            }

            lastCheck.put(uuid, now);
        }
    }

    // Returns the enchantment level on a given item, or 0 if not present
    private int getEnchantmentLevel(ItemStack item, Enchantment enchantment) {
        return (item != null && item.containsEnchantment(enchantment)) ? item.getEnchantmentLevel(enchantment) : 0;
    }

    // Calculates the horizontal speed of the player
    private double getHorizontalSpeed(PlayerMoveEvent event) {
        return Math.sqrt(Math.pow(Objects.requireNonNull(event.getTo()).getX() - event.getFrom().getX(), 2)
                + Math.pow(event.getTo().getZ() - event.getFrom().getZ(), 2));
    }
}