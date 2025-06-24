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

public class NoSlowDownE implements Listener {

    private final ViolationAlerts alerts;
    private final DiscordHook discord;
    private final ConfigManager config;

    // Stores the last timestamp when a player was checked for violations
    private final ConcurrentHashMap<UUID, Long> lastCheck = new ConcurrentHashMap<>();

    // Stores the last known location of a player to calculate movement speed
    private final ConcurrentHashMap<UUID, Location> lastLocations = new ConcurrentHashMap<>();

    // Stores the timestamp when a player last stopped gliding with an elytra
    private final ConcurrentHashMap<UUID, Long> lastElytraFlight = new ConcurrentHashMap<>();

    // Stores the timestamp when a player last stopped flying (creative mode or similar)
    private final ConcurrentHashMap<UUID, Long> lastPlayerFlight = new ConcurrentHashMap<>();

    // Tracks whether the player was gliding in the previous check
    private final ConcurrentHashMap<UUID, Boolean> wasGliding = new ConcurrentHashMap<>();

    // Tracks whether the player was flying in the previous check
    private final ConcurrentHashMap<UUID, Boolean> wasFlying = new ConcurrentHashMap<>();

    public NoSlowDownE(Plugin plugin, ViolationAlerts alerts, DiscordHook discord, ConfigManager config) {
        this.alerts = alerts;
        this.discord = discord;
        this.config = config;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!config.isNoSlowDownEEnabled()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Ignore players with bypass permissions
        if (PermissionBypass.hasBypass(player)) return;
        if (player.hasPermission("duckyac.bypass.noslowdown-e") || player.hasPermission("duckyac.bypass.noslowdown.*")) return;

        // Ignore flying players
        if (player.isFlying()) return;

        // Ignore players who recently stopped flying or gliding
        if (recentElytraFlight(player, uuid) || recentPlayerFlight(player, uuid)) return;

        // Check if player is on a honey block
        Block blockBelow = player.getLocation().subtract(0, 0.1, 0).getBlock();
        if (blockBelow.getType() != Material.HONEY_BLOCK) return;

        // Check if player is actually on ground and not in creative/spectator
        if (!player.isOnGround() || player.getGameMode().name().contains("CREATIVE") || player.getGameMode().name().contains("SPECTATOR"))
            return;

        // Ignore players wearing Depth Strider boots
        if (hasDepthStrider(player)) return;

        // Track movement and calculate speed
        Location current = player.getLocation();
        Location previous = lastLocations.getOrDefault(uuid, current);
        lastLocations.put(uuid, current.clone());

        // Ignore vertical movement
        if (Math.abs(current.getY() - previous.getY()) > 0.001) return;

        double speed = getHorizontalSpeed(event);
        double maxSpeed = config.getNoSlowDownEMaxSpeed();

        // If player moved too fast
        if (speed > maxSpeed) {
            long now = System.currentTimeMillis();
            long last = lastCheck.getOrDefault(uuid, 0L);

            // Prevent reporting too frequently
            if (now - last < 300L) return;

            // Report violation
            int vl = alerts.reportViolation(player.getName(), "NoSlowDownE");

            // Log debug information
            if (config.isNoSlowDownEDebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (NoSlowDownE Debug) " + player.getName()
                        + " moved too fast on HONEY_BLOCK (" + String.format("%.3f", speed) + " > " + String.format("%.3f", maxSpeed) + ")");
            }

            // Apply punishment if violation level is too high
            if (vl >= config.getMaxNoSlowDownEAlerts()) {
                String cmd = config.getNoSlowDownECommand();
                alerts.executePunishment(player.getName(), "NoSlowDownE", cmd);
                discord.sendPunishmentCommand(player.getName(), cmd);
            }

            lastCheck.put(uuid, now);
        }
    }

    // Track if player recently stopped gliding with elytra
    private boolean recentElytraFlight(Player player, UUID uuid) {
        boolean current = player.isGliding();
        boolean previous = wasGliding.getOrDefault(uuid, false);

        if (!current && previous) {
            lastElytraFlight.put(uuid, System.currentTimeMillis());
        }

        wasGliding.put(uuid, current);

        return !current && lastElytraFlight.containsKey(uuid)
                && System.currentTimeMillis() - lastElytraFlight.get(uuid) < 1000;
    }

    // Track if player recently stopped flying
    private boolean recentPlayerFlight(Player player, UUID uuid) {
        boolean current = player.isFlying();
        boolean previous = wasFlying.getOrDefault(uuid, false);

        if (!current && previous) {
            lastPlayerFlight.put(uuid, System.currentTimeMillis());
        }

        wasFlying.put(uuid, current);

        return !current && lastPlayerFlight.containsKey(uuid)
                && System.currentTimeMillis() - lastPlayerFlight.get(uuid) < 1000;
    }

    // Check if player has Depth Strider enchantment on boots
    private boolean hasDepthStrider(Player player) {
        ItemStack boots = player.getInventory().getBoots();
        return boots != null && boots.getEnchantments().containsKey(Enchantment.DEPTH_STRIDER);
    }

    // Calculate horizontal speed (ignoring vertical Y axis)
    private double getHorizontalSpeed(PlayerMoveEvent event) {
        return Math.sqrt(Math.pow(Objects.requireNonNull(event.getTo()).getX() - event.getFrom().getX(), 2)
                + Math.pow(event.getTo().getZ() - event.getFrom().getZ(), 2));
    }
}