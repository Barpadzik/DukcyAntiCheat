package pl.barpad.duckyanticheat.checks.player;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import pl.barpad.duckyanticheat.Main;
import pl.barpad.duckyanticheat.utils.DiscordHook;
import pl.barpad.duckyanticheat.utils.ViolationAlerts;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AutoTotemA implements Listener {

    private final Main plugin;
    private final ViolationAlerts violationAlerts;
    private final DiscordHook discordHook;
    private final ConfigManager config;
    private final ConcurrentHashMap<UUID, Long> lastTotemSwap = new ConcurrentHashMap<>();
    private final Set<BukkitTask> activeTasks = ConcurrentHashMap.newKeySet();

    public AutoTotemA(Main plugin, ViolationAlerts violationAlerts, DiscordHook discordHook, ConfigManager config) {
        this.plugin = plugin;
        this.violationAlerts = violationAlerts;
        this.discordHook = discordHook;
        this.config = config;

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!config.isAutoTotemAEnabled()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (hasAnyBypassPermission(player)) return;

        if (config.getMaxPing() > 0 && player.getPing() > config.getMaxPing()) return;

        int rawSlot = event.getRawSlot();
        ItemStack clicked = event.getCursor();

        if (!isOffhandTotemClick(rawSlot, clicked)) return;

        ItemStack before = player.getInventory().getItem(EquipmentSlot.OFF_HAND);
        int delayTicks = Math.max(1, config.getAutoTotemATickInterval());

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    checkTotemSwap(player, before);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error in AutoTotemA check for " + player.getName() + ": " + e.getMessage());
                } finally {
                    activeTasks.remove(this);
                }
            }
        }.runTaskLater(plugin, delayTicks);

        activeTasks.add(task);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastTotemSwap.remove(uuid);
    }

    private boolean isOffhandTotemClick(int rawSlot, ItemStack clicked) {
        return rawSlot == 45 &&
                clicked != null &&
                clicked.getType() == Material.TOTEM_OF_UNDYING;
    }

    private void checkTotemSwap(Player player, ItemStack before) {
        if (!player.isOnline()) return;

        ItemStack after = player.getInventory().getItem(EquipmentSlot.OFF_HAND);

        boolean hadTotem = (before != null && before.getType() == Material.TOTEM_OF_UNDYING);
        boolean hasTotem = (after.getType() == Material.TOTEM_OF_UNDYING);

        if (hasTotem && !hadTotem) {
            checkTotemSwapSpeed(player);
        }
    }

    private void checkTotemSwapSpeed(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long last = lastTotemSwap.getOrDefault(uuid, 0L);
        long diff = now - last;
        long minDelay = Math.max(50, config.getAutoTotemAMinDelay());

        if (diff < minDelay) {
            handleViolation(player, diff, minDelay);
        }

        lastTotemSwap.put(uuid, now);
    }

    private void handleViolation(Player player, long actualDelay, long minDelay) {
        int vl = violationAlerts.reportViolation(player.getName(), "AutoTotemA");

        if (config.isAutoTotemADebugMode()) {
            plugin.getLogger().info(String.format(
                    "[DuckyAntiCheat] AutoTotemA: %s swapped totem in %dms (threshold: %dms, VL: %d)",
                    player.getName(), actualDelay, minDelay, vl
            ));
        }

        if (vl >= config.getMaxAutoTotemAAlerts()) {
            executePunishment(player);
        }
    }

    private void executePunishment(Player player) {
        try {
            String cmd = config.getAutoTotemACommand();
            if (cmd != null && !cmd.trim().isEmpty()) {
                violationAlerts.executePunishment(player.getName(), "AutoTotemA", cmd);
                discordHook.sendPunishmentCommand(player.getName(), cmd);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to execute punishment for " + player.getName() + ": " + e.getMessage());
        }
    }

    private boolean hasAnyBypassPermission(Player player) {
        return player.hasPermission("duckyac.bypass") ||
                player.hasPermission("duckyac.*") ||
                player.hasPermission("duckyac.bypass.autototem-a");
    }

    public void cleanupOfflinePlayers() {
        try {
            lastTotemSwap.entrySet().removeIf(entry ->
                    Bukkit.getPlayer(entry.getKey()) == null);
        } catch (Exception e) {
            plugin.getLogger().warning("Error during AutoTotemA cleanup: " + e.getMessage());
        }
    }

    public void cleanup() {
        activeTasks.forEach(task -> {
            if (!task.isCancelled()) {
                task.cancel();
            }
        });

        activeTasks.clear();
        lastTotemSwap.clear();
    }
}