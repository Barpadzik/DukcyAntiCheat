package pl.barpad.duckyanticheat.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pl.barpad.duckyanticheat.Main;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ViolationAlerts {

    private final Main plugin;
    private final HashMap<String, Integer> violations = new HashMap<>();
    private final HashMap<String, Long> lastViolationTime = new HashMap<>();

    private final ConfigManager configManager;
    private final DiscordHook discordHook;

    public ViolationAlerts(Main plugin, ConfigManager configManager, DiscordHook discordHook) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.discordHook = discordHook;

        startCleanupTask();
    }

    public int reportViolation(String playerName, String checkType) {
        String key = playerName + ":" + checkType;
        int count = violations.getOrDefault(key, 0) + 1;
        violations.put(key, count);
        lastViolationTime.put(key, System.currentTimeMillis());

        String message = configManager.getAlertMessage()
                .replace("%player%", playerName)
                .replace("%check%", checkType)
                .replace("%vl%", String.valueOf(count));

        Bukkit.getConsoleSender().sendMessage(message);
        discordHook.sendViolationAlert(playerName, checkType, count);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("duckyac.alerts")) {
                player.sendMessage(color(message));
            }
        }
        return count;
    }

    public void executePunishment(String playerName, String check, String command) {
        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", playerName));
            violations.entrySet().removeIf(entry -> entry.getKey().startsWith(playerName + ":"));
            lastViolationTime.entrySet().removeIf(entry -> entry.getKey().startsWith(playerName + ":"));
            removeOldViolations();
        });
    }

    public int getViolationCount(String playerName, String checkType) {
        String key = playerName + ":" + checkType;
        return violations.getOrDefault(key, 0);
    }

    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::removeOldViolations, 1200L, 1200L);
    }

    public void clearAllViolations() {
        violations.clear();
        lastViolationTime.clear();
    }

    private String color(String message) {
        return message == null ? "" : message.replace("&", "§");
    }

    private void removeOldViolations() {
        long timeout = configManager.getAlertTimeoutSeconds() * 1000L;
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> iterator = lastViolationTime.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (currentTime - entry.getValue() > timeout) {
                violations.remove(entry.getKey());
                iterator.remove();
            }
        }
    }
}