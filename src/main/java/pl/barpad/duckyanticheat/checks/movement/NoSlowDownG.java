package pl.barpad.duckyanticheat.checks.movement;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import pl.barpad.duckyanticheat.utils.DiscordHook;
import pl.barpad.duckyanticheat.utils.PermissionBypass;
import pl.barpad.duckyanticheat.utils.ViolationAlerts;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NoSlowDownG implements Listener {

    private final ViolationAlerts alerts;
    private final DiscordHook discord;
    private final ConfigManager config;

    // Timestamp of last check per player to throttle violation reports
    private final ConcurrentHashMap<UUID, Long> lastCheck = new ConcurrentHashMap<>();

    // Last known location per player to calculate speed
    private final ConcurrentHashMap<UUID, Location> lastLocations = new ConcurrentHashMap<>();

    // Track last time player stopped elytra gliding to avoid false positives
    private final ConcurrentHashMap<UUID, Long> lastElytraFlight = new ConcurrentHashMap<>();

    // Track last time player stopped flying for similar reasons
    private final ConcurrentHashMap<UUID, Long> lastPlayerFlight = new ConcurrentHashMap<>();

    // Keep previous gliding state to detect when player stops gliding
    private final ConcurrentHashMap<UUID, Boolean> wasGliding = new ConcurrentHashMap<>();

    // Keep previous flying state to detect when player stops flying
    private final ConcurrentHashMap<UUID, Boolean> wasFlying = new ConcurrentHashMap<>();

    // Blocks to ignore speed checks on (ice variants)
    private final Set<Material> ignoredBlocks = EnumSet.of(
            Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE, Material.FROSTED_ICE
    );

    // Constructor - registers event listener and initializes dependencies
    public NoSlowDownG(Plugin plugin, ViolationAlerts alerts, DiscordHook discord, ConfigManager config) {
        this.alerts = alerts;
        this.discord = discord;
        this.config = config;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // If this check is disabled in config, ignore
        if (!config.isNoSlowDownGEnabled()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Ignore players with bypass permission
        if (PermissionBypass.hasBypass(player)) return;
        if (player.hasPermission("duckyac.bypass.noslowdown-g") || player.hasPermission("duckyac.bypass.noslowdown.*")) return;

        // Detect when player stops gliding, store timestamp
        boolean isCurrentlyGliding = player.isGliding();
        boolean wasPreviouslyGliding = wasGliding.getOrDefault(uuid, false);
        if (!isCurrentlyGliding && wasPreviouslyGliding) {
            lastElytraFlight.put(uuid, System.currentTimeMillis());
        }
        wasGliding.put(uuid, isCurrentlyGliding);

        // Ignore movement shortly after elytra gliding ends to avoid false positives
        if (!isCurrentlyGliding && lastElytraFlight.containsKey(uuid)) {
            if (System.currentTimeMillis() - lastElytraFlight.get(uuid) < 1000) return;
        }

        // Detect when player stops flying, store timestamp
        boolean isCurrentlyFlying = player.isFlying();
        boolean wasPreviouslyFlying = wasFlying.getOrDefault(uuid, false);
        if (!isCurrentlyFlying && wasPreviouslyFlying) {
            lastPlayerFlight.put(uuid, System.currentTimeMillis());
        }
        wasFlying.put(uuid, isCurrentlyFlying);

        // Ignore movement shortly after flying ends
        if (!isCurrentlyFlying && lastPlayerFlight.containsKey(uuid)) {
            if (System.currentTimeMillis() - lastPlayerFlight.get(uuid) < 1000) return;
        }

        // Only check when player is sneaking and on the ground
        if (!player.isSneaking()) return;
        if (!player.isOnGround()) return;

        // Ignore creative and spectator mode players
        if (player.getGameMode().name().contains("CREATIVE") || player.getGameMode().name().contains("SPECTATOR")) return;

        // Get the block directly below the player (slightly offset down)
        Block blockBelow = player.getLocation().subtract(0, 0.1, 0).getBlock();

        // Ignore checks on ice blocks to prevent false alerts on slippery surfaces
        if (ignoredBlocks.contains(blockBelow.getType())) return;

        // Get current and previous locations to calculate horizontal movement
        Location current = player.getLocation();
        Location previous = lastLocations.getOrDefault(uuid, current);
        lastLocations.put(uuid, current.clone());

        // Ignore movement with significant vertical change (e.g. stairs, slabs)
        if (Math.abs(current.getY() - previous.getY()) > 0.001) return;

        // Calculate horizontal speed between the two locations
        double speed = getHorizontalSpeed(event);

        // Retrieve configured max allowed speed for this check
        double maxSpeed = config.getNoSlowDownGMaxSpeed();

        // If player has Speed potion effect, increase max speed proportionally
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            int amplifier = Objects.requireNonNull(player.getPotionEffect(PotionEffectType.SPEED)).getAmplifier();
            maxSpeed *= 1.0 + 0.2 * (amplifier + 1);
        }

        // Check for boots enchantments that increase movement speed on ground
        if (player.getInventory().getBoots() != null) {
            int depthStriderLevel = player.getInventory().getBoots().getEnchantmentLevel(org.bukkit.enchantments.Enchantment.DEPTH_STRIDER);
            if (depthStriderLevel > 0) {
                maxSpeed *= 1.0 + (0.15 * depthStriderLevel);
            }

            int soulSpeedLevel = player.getInventory().getBoots().getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SOUL_SPEED);
            Material blockType = blockBelow.getType();
            // Soul Speed enchantment applies only on soul sand or soul soil blocks
            if (soulSpeedLevel > 0 && (blockType == Material.SOUL_SAND || blockType == Material.SOUL_SOIL)) {
                maxSpeed *= 1.2 + (0.15 * soulSpeedLevel);
            }
        }

        // If player speed exceeds allowed max speed, proceed to violation handling
        if (speed > maxSpeed) {
            long now = System.currentTimeMillis();
            long last = lastCheck.getOrDefault(uuid, 0L);

            // Throttle violation reports to once every 300ms per player
            if (now - last < 300L) return;

            // Report violation and get current violation level
            int vl = alerts.reportViolation(player.getName(), "NoSlowDownG");

            // Debug log for server console if enabled in config
            if (config.isNoSlowDownGDebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (NoSlowDownG Debug) " + player.getName()
                        + " moved too fast while sneaking (" + String.format("%.3f", speed) + " > " + String.format("%.3f", maxSpeed) + ")");
            }

            // If violation level exceeds configured threshold, execute punishment command and send Discord alert
            if (vl >= config.getMaxNoSlowDownGAlerts()) {
                String cmd = config.getNoSlowDownGCommand();
                alerts.executePunishment(player.getName(), "NoSlowDownG", cmd);
                discord.sendPunishmentCommand(player.getName(), cmd);
            }

            // Update last check timestamp
            lastCheck.put(uuid, now);
        }
    }

    /**
     * Calculates horizontal speed based on the difference in X and Z coordinates between PlayerMoveEvent locations.
     *
     * @param event PlayerMoveEvent to extract movement data
     * @return horizontal movement distance (speed)
     */
    private double getHorizontalSpeed(PlayerMoveEvent event) {
        return Math.sqrt(Math.pow(Objects.requireNonNull(event.getTo()).getX() - event.getFrom().getX(), 2)
                + Math.pow(event.getTo().getZ() - event.getFrom().getZ(), 2));
    }
}