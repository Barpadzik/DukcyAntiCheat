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
import pl.barpad.duckyanticheat.utils.PermissionBypass;
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

    // Map storing player's UUID and the timestamp of their last totem swap (in milliseconds)
    private final ConcurrentHashMap<UUID, Long> lastTotemSwap = new ConcurrentHashMap<>();

    // Set of currently active asynchronous Bukkit tasks
    private final Set<BukkitTask> activeTasks = ConcurrentHashMap.newKeySet();

    public AutoTotemA(Main plugin, ViolationAlerts violationAlerts, DiscordHook discordHook, ConfigManager config) {
        this.plugin = plugin;
        this.violationAlerts = violationAlerts;
        this.discordHook = discordHook;
        this.config = config;

        // Register this class as an event listener
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Event triggered when a player clicks inside an inventory.
     * Checks if the player is trying to quickly swap a totem into their offhand,
     * and schedules a delayed check for swap speed.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!config.isAutoTotemAEnabled()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Skip players with bypass permissions
        if (PermissionBypass.hasBypass(player)) return;
        if (player.hasPermission("duckyac.bypass.autototem-a")) return;

        // Skip players with ping above configured limit, if set
        if (config.getMaxPing() > 0 && player.getPing() > config.getMaxPing()) return;

        int rawSlot = event.getRawSlot();
        ItemStack clicked = event.getCursor();

        // Check if the click is attempting to place a totem into the offhand slot (slot 45)
        if (!isOffhandTotemClick(rawSlot, clicked)) return;

        // Get the item previously in the offhand slot before the click
        ItemStack before = player.getInventory().getItem(EquipmentSlot.OFF_HAND);
        // Delay (in ticks) before checking swap speed, at least 1 tick
        int delayTicks = Math.max(1, config.getAutoTotemATickInterval());

        // Schedule a task to run after delayTicks that checks the totem swap
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    checkTotemSwap(player, before);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error in AutoTotemA check for " + player.getName() + ": " + e.getMessage());
                } finally {
                    // Remove the task from active tasks after running
                    activeTasks.remove(this);
                }
            }
        }.runTaskLater(plugin, delayTicks);

        // Add the scheduled task to the set of active tasks
        activeTasks.add(task);
    }

    /**
     * Event triggered when a player quits the server.
     * Cleans up stored last totem swap data for that player.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastTotemSwap.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Checks if the click involved placing a totem into the offhand slot.
     * @param rawSlot the raw slot number clicked (45 = offhand)
     * @param clicked the item on the cursor during the click
     * @return true if the click is a totem placement into offhand
     */
    private boolean isOffhandTotemClick(int rawSlot, ItemStack clicked) {
        return rawSlot == 45 &&
                clicked != null &&
                clicked.getType() == Material.TOTEM_OF_UNDYING;
    }

    /**
     * After the delay, checks if the player swapped a totem into their offhand.
     * Compares the offhand item before and after the click.
     * @param player the player to check
     * @param before the item in offhand before the click
     */
    private void checkTotemSwap(Player player, ItemStack before) {
        if (!player.isOnline()) return;

        ItemStack after = player.getInventory().getItem(EquipmentSlot.OFF_HAND);

        boolean hadTotem = before != null && before.getType() == Material.TOTEM_OF_UNDYING;
        boolean hasTotem = after != null && after.getType() == Material.TOTEM_OF_UNDYING;

        // If player now has a totem but didn't before, a swap occurred
        if (hasTotem && !hadTotem) {
            checkTotemSwapSpeed(player);
        }
    }

    /**
     * Checks the time elapsed since the player's last totem swap.
     * If the swap happened too quickly, it triggers a violation report.
     * @param player the player to check
     */
    private void checkTotemSwapSpeed(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long last = lastTotemSwap.getOrDefault(uuid, 0L);
        long diff = now - last;

        // Minimum allowed time between swaps (in ms), defaulting to 50ms minimum
        long minDelay = Math.max(50, config.getAutoTotemAMinDelay());

        if (diff < minDelay) {
            handleViolation(player, diff, minDelay);
        }

        // Update the last swap time to now
        lastTotemSwap.put(uuid, now);
    }

    /**
     * Handles a detected violation by reporting it,
     * logging debug info if enabled, and executing punishment if violation threshold reached.
     * @param player the player who violated the rule
     * @param actualDelay the actual time between swaps in milliseconds
     * @param minDelay the minimum allowed time in milliseconds
     */
    private void handleViolation(Player player, long actualDelay, long minDelay) {
        int vl = violationAlerts.reportViolation(player.getName(), "AutoTotemA");

        if (config.isAutoTotemADebugMode()) {
            plugin.getLogger().info(String.format(
                    "[DuckyAntiCheat] AutoTotemA: %s swapped totem in %dms (threshold: %dms, VL: %d)",
                    player.getName(), actualDelay, minDelay, vl
            ));
        }

        // If violation level reached or exceeded the limit, execute configured punishment
        if (vl >= config.getMaxAutoTotemAAlerts()) {
            executePunishment(player);
        }
    }

    /**
     * Executes the punishment command configured in the plugin,
     * and sends notification to Discord.
     * @param player the punished player
     */
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

    /**
     * Cleans up entries for players who are no longer online
     * to prevent memory leaks in the lastTotemSwap map.
     */
    public void cleanupOfflinePlayers() {
        try {
            lastTotemSwap.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) == null);
        } catch (Exception e) {
            plugin.getLogger().warning("Error during AutoTotemA cleanup: " + e.getMessage());
        }
    }

    /**
     * Cancels all active Bukkit tasks and clears internal data.
     * Should be called when disabling the plugin or the check.
     */
    public void disable() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
        lastTotemSwap.clear();
    }
}
