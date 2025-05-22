package pl.barpad.duckyanticheat.checks.player;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import pl.barpad.duckyanticheat.Main;
import pl.barpad.duckyanticheat.utils.DiscordHook;
import pl.barpad.duckyanticheat.utils.ViolationAlerts;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AutoTotemA implements Listener {

    private final Main plugin;
    private final ViolationAlerts violationAlerts;
    private final DiscordHook discordHook;
    private final ConfigManager config;
    private final Map<UUID, Long> lastTotemSwap = new HashMap<>();

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

        if (player.hasPermission("duckyac.bypass") && player.hasPermission("duckyac.*") && player.hasPermission("duckyac.bypass.autototem-a")) {
            return;
        }

        UUID uuid = player.getUniqueId();

        if (config.getMaxPing() > 0 && player.getPing() > config.getMaxPing()) return;

        int rawSlot = event.getRawSlot();
        ItemStack clicked = event.getCursor();
        if (rawSlot == 45 && clicked != null && clicked.getType() == Material.TOTEM_OF_UNDYING) {

            ItemStack before = player.getInventory().getItem(EquipmentSlot.OFF_HAND);
            int delayTicks = config.getAutoTotemATickInterval();

            new BukkitRunnable() {
                @Override
                public void run() {
                    ItemStack after = player.getInventory().getItem(EquipmentSlot.OFF_HAND);

                    if (after.getType() == Material.TOTEM_OF_UNDYING && before.getType() != Material.TOTEM_OF_UNDYING) {

                        long now = System.currentTimeMillis();
                        long last = lastTotemSwap.getOrDefault(uuid, 0L);
                        long diff = now - last;

                        if (diff < config.getAutoTotemAMinDelay()) {
                            int vl = violationAlerts.reportViolation(player.getName(), "AutoTotemA");

                            if (config.isAutoTotemADebugMode()) {
                                Bukkit.getLogger().info("[DuckyAntiCheat] (AutoTotemA Debug) " + player.getName()
                                        + " swapped totem too fast! Delay: " + diff + "ms (VL: " + vl + ")");
                            }

                            if (vl >= config.getMaxAutoTotemAAlerts()) {
                                String cmd = config.getAutoTotemACommand();
                                violationAlerts.executePunishment(player.getName(), "AutoTotemA", cmd);
                                discordHook.sendPunishmentCommand(player.getName(), cmd);
                            }
                        }

                        lastTotemSwap.put(uuid, now);
                    }
                }
            }.runTaskLater(plugin, delayTicks);
        }
    }
}