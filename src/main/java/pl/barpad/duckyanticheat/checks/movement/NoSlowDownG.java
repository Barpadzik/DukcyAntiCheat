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
import pl.barpad.duckyanticheat.utils.ViolationAlerts;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.util.*;

public class NoSlowDownG implements Listener {

    private final ViolationAlerts alerts;
    private final DiscordHook discord;
    private final ConfigManager config;
    private final Map<UUID, Long> lastCheck = new HashMap<>();
    private final Map<UUID, Location> lastLocations = new HashMap<>();
    private final Map<UUID, Long> lastElytraFlight = new HashMap<>();
    private final Map<UUID, Long> lastPlayerFlight = new HashMap<>();
    private final Map<UUID, Boolean> wasGliding = new HashMap<>();
    private final Map<UUID, Boolean> wasFlying = new HashMap<>();

    private final Set<Material> ignoredBlocks = EnumSet.of(
            Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE, Material.FROSTED_ICE
    );

    public NoSlowDownG(Plugin plugin, ViolationAlerts alerts, DiscordHook discord, ConfigManager config) {
        this.alerts = alerts;
        this.discord = discord;
        this.config = config;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!config.isNoSlowDownGEnabled()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (player.hasPermission("duckyac.bypass") && player.hasPermission("duckyac.*")
                && player.hasPermission("duckyac.bypass.noslowdown-f") && player.hasPermission("duckyac.bypass.noslowdown.*")) {
            return;
        }

        boolean isCurrentlyGliding = player.isGliding();
        boolean wasPreviouslyGliding = wasGliding.getOrDefault(uuid, false);
        if (!isCurrentlyGliding && wasPreviouslyGliding) {
            lastElytraFlight.put(uuid, System.currentTimeMillis());
        }
        wasGliding.put(uuid, isCurrentlyGliding);
        if (!player.isGliding() && lastElytraFlight.containsKey(uuid)) {
            if (System.currentTimeMillis() - lastElytraFlight.get(uuid) < 1000) return;
        }

        boolean isCurrentlyFlying = player.isFlying();
        boolean wasPreviouslyFlying = wasFlying.getOrDefault(uuid, false);
        if (!isCurrentlyFlying && wasPreviouslyFlying) {
            lastPlayerFlight.put(uuid, System.currentTimeMillis());
        }
        wasFlying.put(uuid, isCurrentlyFlying);
        if (!player.isFlying() && lastPlayerFlight.containsKey(uuid)) {
            if (System.currentTimeMillis() - lastPlayerFlight.get(uuid) < 1000) return;
        }

        if (!player.isSneaking()) return;
        if (!player.isOnGround()) return;
        if (player.getGameMode().name().contains("CREATIVE") || player.getGameMode().name().contains("SPECTATOR")) return;

        Block blockBelow = player.getLocation().subtract(0, 0.1, 0).getBlock();
        if (ignoredBlocks.contains(blockBelow.getType())) return;

        Location current = player.getLocation();
        Location previous = lastLocations.getOrDefault(uuid, current);
        lastLocations.put(uuid, current.clone());

        if (Math.abs(current.getY() - previous.getY()) > 0.001) return;

        double speed = getHorizontalSpeed(event);
        double maxSpeed = config.getNoSlowDownGMaxSpeed();

        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            int amplifier = Objects.requireNonNull(player.getPotionEffect(PotionEffectType.SPEED)).getAmplifier();
            maxSpeed *= 1.0 + 0.2 * (amplifier + 1);
        }

        if (player.getInventory().getBoots() != null) {
            int depthStriderLevel = player.getInventory().getBoots().getEnchantmentLevel(org.bukkit.enchantments.Enchantment.DEPTH_STRIDER);
            if (depthStriderLevel > 0) {
                maxSpeed *= 1.0 + (0.15 * depthStriderLevel);
            }

            int soulSpeedLevel = player.getInventory().getBoots().getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SOUL_SPEED);
            Material blockType = blockBelow.getType();
            if (soulSpeedLevel > 0 && (blockType == Material.SOUL_SAND || blockType == Material.SOUL_SOIL)) {
                maxSpeed *= 1.2 + (0.15 * soulSpeedLevel);
            }
        }

        if (speed > maxSpeed) {
            long now = System.currentTimeMillis();
            long last = lastCheck.getOrDefault(uuid, 0L);
            if (now - last < 300L) return;

            int vl = alerts.reportViolation(player.getName(), "NoSlowDownG");

            if (config.isNoSlowDownGDebugMode()) {
                Bukkit.getLogger().info("[DuckyAntiCheat] (NoSlowDownG Debug) " + player.getName()
                        + " moved too fast while sneaking (" + String.format("%.3f", speed) + " > " + String.format("%.3f", maxSpeed) + ")");
            }

            if (vl >= config.getMaxNoSlowDownGAlerts()) {
                String cmd = config.getNoSlowDownGCommand();
                alerts.executePunishment(player.getName(), "NoSlowDownG", cmd);
                discord.sendPunishmentCommand(player.getName(), cmd);
            }

            lastCheck.put(uuid, now);
        }
    }

    private double getHorizontalSpeed(PlayerMoveEvent event) {
        return Math.sqrt(Math.pow(Objects.requireNonNull(event.getTo()).getX() - event.getFrom().getX(), 2)
                + Math.pow(event.getTo().getZ() - event.getFrom().getZ(), 2));
    }
}