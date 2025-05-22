package pl.barpad.duckyanticheat.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import pl.barpad.duckyanticheat.Main;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {
    private final Main plugin;
    private final ConfigManager configManager;
    private static final String GITHUB_API_URL = "https://api.github.com/repos/Barpadzik/DuckyAntiCheat/releases/latest";

    public UpdateChecker(Main plugin, ConfigManager configManager) {
        this.configManager = configManager;
        this.plugin = plugin;
    }

    public void checkForUpdates() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(GITHUB_API_URL).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder responseBuilder = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line);
                }

                reader.close();
                String json = responseBuilder.toString();

                String latestVersion = extractValue(json, "tag_name");
                String downloadUrl = extractValue(json, "html_url");

                if (latestVersion == null || downloadUrl == null) {
                    throw new Exception("Could not parse GitHub response.");
                }

                if (!plugin.getDescription().getVersion().equalsIgnoreCase(latestVersion)) {
                    String updateMessage = ChatColor.translateAlternateColorCodes('&',
                                    configManager.getString("update-available", "§d§lDuckyAC §8» &eA new version is available: &c%version%"))
                            .replace("%version%", latestVersion);

                    String downloadMessage = ChatColor.translateAlternateColorCodes('&',
                                    configManager.getString("update-download", "§d§lDuckyAC §8» &eDownload: &a%url%"))
                            .replace("%url%", downloadUrl);

                    Bukkit.getConsoleSender().sendMessage(updateMessage);
                    Bukkit.getConsoleSender().sendMessage(downloadMessage);

                    Bukkit.getOnlinePlayers().stream()
                            .filter(player -> player.hasPermission("duckyac.update"))
                            .forEach(player -> {
                                player.sendMessage(updateMessage);
                                player.sendMessage(downloadMessage);
                            });
                }

            } catch (Exception e) {
                String errorMessage = ChatColor.translateAlternateColorCodes('&',
                        configManager.getString("update-check-failed", "&d&lDuckyAC &8» &cCould not check for updates."));
                Bukkit.getConsoleSender().sendMessage(errorMessage);
            }
        });
    }

    private String extractValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) return null;

        int colonIndex = json.indexOf(":", keyIndex);
        int quoteStart = json.indexOf("\"", colonIndex);
        int quoteEnd = json.indexOf("\"", quoteStart + 1);

        if (quoteStart == -1 || quoteEnd == -1) return null;

        return json.substring(quoteStart + 1, quoteEnd);
    }
}