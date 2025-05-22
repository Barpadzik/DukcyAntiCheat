package pl.barpad.duckyanticheat.utils;

import org.bukkit.Bukkit;
import pl.barpad.duckyanticheat.Main;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DiscordHook {

    private final Main plugin;
    private final ConfigManager configManager;

    public DiscordHook(Main plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void sendViolationAlert(String playerName, String checkType, int violationCount) {
        if (!configManager.isDiscordEnabled()) return;

        String template = configManager.getDiscordViolationMessageTemplate();
        String message = template
                .replace("%player%", playerName)
                .replace("%check%", checkType)
                .replace("%vl%", String.valueOf(violationCount));

        sendDiscordEmbed("🚨 DuckyAntiCheat Violation", message, 0xFF0000);
    }

    public void sendPunishmentCommand(String playerName, String command) {
        if (!configManager.isDiscordEnabled()) return;

        String template = configManager.getDiscordPunishmentMessageTemplate();
        String message = template
                .replace("%player%", playerName)
                .replace("%command%", command);

        sendDiscordEmbed("⚠️ Punishment Executed", message, 0xFFA500);
    }

    private void sendDiscordEmbed(String title, String content, int color) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String webhookUrl = configManager.getDiscordWebhookUrl();
                String username = configManager.getDiscordUsername();
                String avatarUrl = configManager.getDiscordAvatarUrl();

                if (webhookUrl == null || webhookUrl.isEmpty()) return;

                URL url = new URL(webhookUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");

                String jsonPayload = "{\n" +
                        "  \"username\": \"" + escapeJson(username) + "\",\n" +
                        "  \"avatar_url\": \"" + escapeJson(avatarUrl) + "\",\n" +
                        "  \"embeds\": [\n" +
                        "    {\n" +
                        "      \"title\": \"" + escapeJson(title) + "\",\n" +
                        "      \"description\": \"" + escapeJson(content) + "\",\n" +
                        "      \"color\": " + color + "\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}";

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                connection.getInputStream().close();
            } catch (Exception e) {
                plugin.getLogger().warning("[DuckyAC] Failed to send Discord webhook: " + e.getMessage());
            }
        });
    }

    private String escapeJson(String text) {
        return text.replace("\"", "\\\"").replace("\n", "\\n");
    }
}