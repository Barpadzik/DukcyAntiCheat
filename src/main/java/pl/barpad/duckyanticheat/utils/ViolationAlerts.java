package pl.barpad.duckyanticheat.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import pl.barpad.duckyanticheat.Main;
import pl.barpad.duckyanticheat.model.ViolationKey;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.util.concurrent.ConcurrentHashMap;

public class ViolationAlerts {

    private final Main plugin;
    private final ConcurrentHashMap<ViolationKey, Integer> violations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ViolationKey, Long> lastViolationTime = new ConcurrentHashMap<>();
    private final ConfigManager configManager;
    private final DiscordHook discordHook;
    private BukkitTask cleanupTask;

    public ViolationAlerts(Main plugin, ConfigManager configManager, DiscordHook discordHook) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.discordHook = discordHook;
        startCleanupTask();
    }

    public int reportViolation(String playerName, String checkType) {
        if (isValidInput(playerName, checkType)) {
            return 0;
        }

        ViolationKey key = new ViolationKey(playerName, checkType);
        int count = violations.getOrDefault(key, 0) + 1;
        violations.put(key, count);
        lastViolationTime.put(key, System.currentTimeMillis());

        sendAlerts(playerName, checkType, count);
        return count;
    }

    public void executePunishment(String playerName, String check, String command) {
        if (isValidInput(playerName, check) || command == null || command.trim().isEmpty()) {
            plugin.getLogger().warning("Invalid punishment parameters");
            return;
        }

        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            plugin.getLogger().warning("Cannot execute punishment: player " + playerName + " is not online");
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                String processedCommand = command.replace("%player%", playerName);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
                clearPlayerViolations(playerName);
                plugin.getLogger().info("Executed punishment for " + playerName + ": " + processedCommand);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to execute punishment for " + playerName + ": " + e.getMessage());
            }
        });
    }

    public int getViolationCount(String playerName, String checkType) {
        if (isValidInput(playerName, checkType)) {
            return 0;
        }

        ViolationKey key = new ViolationKey(playerName, checkType);
        return violations.getOrDefault(key, 0);
    }

    public void clearPlayerViolations(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return;
        }

        violations.entrySet().removeIf(entry ->
                entry.getKey().getPlayerName().equals(playerName));
        lastViolationTime.entrySet().removeIf(entry ->
                entry.getKey().getPlayerName().equals(playerName));
    }

    public void clearAllViolations() {
        violations.clear();
        lastViolationTime.clear();
        plugin.getLogger().info("Cleared all violation data");
    }

    public void cleanupOldViolations() {
        long timeout = Math.max(1000L, configManager.getAlertTimeoutSeconds() * 1000L);
        long currentTime = System.currentTimeMillis();

        int removedCount = 0;
        var iterator = lastViolationTime.entrySet().iterator();

        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (currentTime - entry.getValue() > timeout) {
                violations.remove(entry.getKey());
                iterator.remove();
                removedCount++;
            }
        }

        if (removedCount > 0) {
            plugin.getLogger().info("Cleaned up " + removedCount + " old violations");
        }
    }

    public void cleanup() {
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }
        clearAllViolations();
    }

    private boolean isValidInput(String playerName, String checkType) {
        if (playerName == null || playerName.trim().isEmpty()) {
            plugin.getLogger().warning("Attempted operation with null/empty player name");
            return true;
        }

        if (checkType == null || checkType.trim().isEmpty()) {
            plugin.getLogger().warning("Attempted operation with null/empty check type");
            return true;
        }

        return false;
    }

    private void sendAlerts(String playerName, String checkType, int count) {
        String message = formatAlertMessage(playerName, checkType, count);
        Bukkit.getConsoleSender().sendMessage(message);

        try {
            discordHook.sendViolationAlert(playerName, checkType, count);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send Discord alert: " + e.getMessage());
        }

        sendPlayerAlerts(message);
    }

    private String formatAlertMessage(String playerName, String checkType, int count) {
        try {
            return configManager.getAlertMessage()
                    .replace("%player%", playerName)
                    .replace("%check%", checkType)
                    .replace("%vl%", String.valueOf(count));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to format alert message: " + e.getMessage());
            return "[DuckyAntiCheat] " + playerName + " failed " + checkType + " (VL: " + count + ")";
        }
    }

    private void sendPlayerAlerts(String message) {
        String coloredMessage = color(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (player.hasPermission("duckyac.alerts")) {
                    player.sendMessage(coloredMessage);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send alert to " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    private void startCleanupTask() {
        long interval = Math.max(1200L, configManager.getAlertTimeoutSeconds() * 20L / 10);
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupOldViolations, interval, interval);
    }

    private String color(String message) {
        return message == null ? "" : message.replace("&", "§");
    }
}