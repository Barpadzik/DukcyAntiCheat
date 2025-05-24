package pl.barpad.duckyanticheat.utils.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    // === IMPORTANT ===

    public String getAlertMessage() {
        return config.getString("alert-message", "kick %player% Too fast totem swap (AutoTotemA)");
    }

    public String getString(String path, String def) {
        return config.contains(path) ? config.getString(path) : def;
    }
    
    // === DISCORD WEBHOOK ===

    public boolean isDiscordEnabled() {
        return config.getBoolean("discord.enabled", false);
    }

    public String getDiscordWebhookUrl() {
        return config.getString("discord.discord-webhook-url", "");
    }

    public String getDiscordUsername() {
        return config.getString("discord.username", "DuckyAntiCheat");
    }

    public String getDiscordAvatarUrl() {
        return config.getString("discord.avatar-url", "https://i.imgur.com/wPfoYdI.png");
    }

    public String getDiscordViolationMessageTemplate() {
        return config.getString("discord.violation-message-template",
                "**AntiCheatSystem**\nPlayer: **%player%**\nCheck: **%check%**\nViolation: **%vl%**");
    }

    public String getDiscordPunishmentMessageTemplate() {
        return config.getString("discord.punishment-message-template",
                "**Punishment Executed**\nPlayer: **%player%**\nCommand: `%command%`");
    }

    // === ALERT SYSTEM ===

    public int getAlertTimeoutSeconds() {
        return config.getInt("alert-timeout", 300);
    }

    // === ELYTRA AIM A ===

    public boolean isElytraAimAEnabled() {
        return config.getBoolean("elytra-aim-a.enabled", true);
    }

    public boolean isElytraAimACancelEvent() {
        return plugin.getConfig().getBoolean("elytra-aim-a.cancel-event", true);
    }

    public int getElytraAimAMaxFireworkDelay() {
        return config.getInt("elytra-aim-a.max-firework-delay", 200);
    }

    public int getElytraAimAMaxAlerts() {
        return config.getInt("elytra-aim-a.max-alerts", 5);
    }

    public String getElytraAimACommand() {
        return config.getString("elytra-aim-a.command", "kick %player% Cheating with Elytra (ElytraAimA)");
    }

    public boolean isElytraAimADebugMode() {
        return config.getBoolean("elytra-aim-a.debug-mode", false);
    }

    // === THRU BLOCKS A ===

    public boolean isThruBlocksEnabled() {
        return plugin.getConfig().getBoolean("thru-blocks-a.enabled", true);
    }

    public boolean isThruBlocksCancelEvent() {
        return plugin.getConfig().getBoolean("thru-blocks-a.cancel-event", true);
    }

    public int getMaxThruBlocksAlerts() {
        return plugin.getConfig().getInt("thru-blocks-a.max-alerts", 5);
    }

    public String getThruBlocksCommand() {
        return plugin.getConfig().getString("thru-blocks-a.command", "kick %player% Hitting through blocks (ThruBlocksA)");
    }

    public boolean isThruBlocksDebugMode() {
        return plugin.getConfig().getBoolean("thru-blocks-a.debug-mode", false);
    }

    // === ELYTRA CRITICALS A ===

    public boolean isElytraCriticalsAEnabled() {
        return config.getBoolean("elytra-criticals-a.enabled", true);
    }

    public boolean isElytraCriticalsACancelEvent() {
        return plugin.getConfig().getBoolean("elytra-criticals-a.cancel-event", true);
    }

    public int getElytraCriticalsACriticalHitsRequired() {
        return config.getInt("elytra-criticals-a.critical-hits-required", 2);
    }

    public int getElytraCriticalsATimeframe() {
        return config.getInt("elytra-criticals-a.timeframe", 500);
    }

    public int getMaxElytraCriticalsAAlerts() {
        return config.getInt("elytra-criticals-a.max-alerts", 5);
    }

    public String getElytraCriticalsACommand() {
        return config.getString("elytra-criticals-a.command", "kick %player% Cheating with Elytra (ElytraCriticalsA)");
    }

    public boolean isElytraCriticalsADebugMode() {
        return config.getBoolean("elytra-criticals-a.debug-mode", false);
    }

    // === NO WEB A ===

    public boolean isNoWebAEnabled() {
        return plugin.getConfig().getBoolean("no-web-a.enabled", true);
    }

    public boolean isNoWebACancelEvent() {
        return plugin.getConfig().getBoolean("no-web-a.cancel-event", true);
    }

    public int getMaxNoWebAAlerts() {
        return plugin.getConfig().getInt("no-web-a.max-alerts", 5);
    }

    public String getNoWebACommand() {
        return plugin.getConfig().getString("no-web-a.command", "kick %player% Suspicious movement in cobwebs (NoWebA)");
    }

    public boolean isNoWebADebugMode() {
        return config.getBoolean("no-web-a.debug-mode", false);
    }

    // === NO SLOW A ===

    public boolean isNoSlowDownAEnabled() {
        return plugin.getConfig().getBoolean("no-slowdown-a.enabled", true);
    }

    public boolean shouldNoSlowDownACancelEvent() {
        return plugin.getConfig().getBoolean("no-slowdown-a.cancel-event", true);
    }

    public double getNoSlowDownAMaxEatingSpeed() {
        return plugin.getConfig().getDouble("no-slowdown-a.max-eating-speed", 0.20);
    }

    public double getNoSlowDownAMaxIgnoreSpeed() {
        return plugin.getConfig().getDouble("no-slowdown-max-distance", 1.0);
    }

    public List<Double> getNoSlowDownAIgnoredSpeedValues() {
        return config.getDoubleList("no-slowdown-a.ignored-speeds");
    }

    public int getMaxNoSlowDownAAlerts() {
        return plugin.getConfig().getInt("no-slowdown-a.max-alerts", 5);
    }

    public String getNoSlowDownACommand() {
        return plugin.getConfig().getString("no-slowdown-a.command", "kick %player% Player was walking too fast while eating (NoSlowDownA)");
    }

    public boolean isNoSlowDownADebugMode() {
        return plugin.getConfig().getBoolean("no-slowdown-a.debug", false);
    }

    // === NO SLOW B ===

    public boolean isNoSlowDownBEnabled() {
        return plugin.getConfig().getBoolean("no-slowdown-b.enabled", true);
    }

    public boolean shouldNoSlowDownBCancelEvent() {
        return plugin.getConfig().getBoolean("no-slowdown-b.cancel-event", true);
    }

    public double getNoSlowDownBMaxBowSpeed() {
        return plugin.getConfig().getDouble("no-slowdown-b.max-bow-speed", 0.20);
    }

    public double getNoSlowDownBMaxIgnoreSpeed() {
        return plugin.getConfig().getDouble("no-slowdown-b.no-slowdown-max-distance", 1.0);
    }

    public List<Double> getNoSlowDownBIgnoredSpeedValues() {
        return config.getDoubleList("no-slowdown-b.ignored-speeds");
    }

    public int getMaxNoSlowDownBAlerts() {
        return plugin.getConfig().getInt("no-slowdown-b.max-alerts", 5);
    }

    public String getNoSlowDownBCommand() {
        return plugin.getConfig().getString("no-slowdown-b.command", "kick %player% Player was walking too fast with a drawn bow (NoSlowDownB)");
    }

    public boolean isNoSlowDownBDebugMode() {
        return plugin.getConfig().getBoolean("no-slowdown-b.debug", false);
    }

    // === NO SLOW C ===

    public boolean isNoSlowDownCEnabled() {
        return plugin.getConfig().getBoolean("no-slowdown-c.enabled", true);
    }

    public boolean shouldNoSlowDownCCancelEvent() {
        return plugin.getConfig().getBoolean("no-slowdown-c.cancel-event", true);
    }

    public double getNoSlowDownCMaxSpeed() {
        return plugin.getConfig().getDouble("no-slowdown-c.max-speed", 0.20);
    }

    public double getNoSlowDownCMaxIgnoreSpeed() {
        return plugin.getConfig().getDouble("no-slowdown-max-distance", 1.0);
    }

    public List<Double> getNoSlowDownCIgnoredSpeedValues() {
        return config.getDoubleList("no-slowdown-c.ignored-speeds");
    }

    public int getMaxNoSlowDownCAlerts() {
        return plugin.getConfig().getInt("no-slowdown-c.max-alerts", 5);
    }

    public String getNoSlowDownCCommand() {
        return plugin.getConfig().getString("no-slowdown-c.command", "kick %player% Player walked too fast while drawing the crossbow (NoSlowDownC)");
    }

    public boolean isNoSlowDownCDebugMode() {
        return plugin.getConfig().getBoolean("no-slowdown-c.debug", false);
    }

    // === NO SLOW D ===

    public boolean isNoSlowDownDEnabled() {
        return plugin.getConfig().getBoolean("no-slowdown-d.enabled", true);
    }

    public boolean shouldNoSlowDownDCancelEvent() {
        return plugin.getConfig().getBoolean("no-slowdown-d.cancel-event", true);
    }

    public double getNoSlowDownDMaxSpeed() {
        return plugin.getConfig().getDouble("no-slowdown-d.max-speed", 0.20);
    }

    public double getNoSlowDownDMaxIgnoreSpeed() {
        return plugin.getConfig().getDouble("no-slowdown-max-distance", 1.0);
    }

    public List<Double> getNoSlowDownDIgnoredSpeedValues() {
        return config.getDoubleList("no-slowdown-d.ignored-speeds");
    }

    public int getMaxNoSlowDownDAlerts() {
        return plugin.getConfig().getInt("no-slowdown-d.max-alerts", 5);
    }

    public String getNoSlowDownDCommand() {
        return plugin.getConfig().getString("no-slowdown-d.command", "kick %player% Player was walking too fast while holding a shield (NoSlowDownD)");
    }

    public boolean isNoSlowDownDDebugMode() {
        return plugin.getConfig().getBoolean("no-slowdown-d.debug", false);
    }

    // === NO SLOW E ===

    public boolean isNoSlowDownEEnabled() {
        return plugin.getConfig().getBoolean("no-slowdown-e.enabled", true);
    }

    public double getNoSlowDownEMaxSpeed() {
        return plugin.getConfig().getDouble("no-slowdown-e.max-speed", 0.170);
    }

    public int getMaxNoSlowDownEAlerts() {
        return plugin.getConfig().getInt("no-slowdown-e.max-alerts", 5);
    }

    public String getNoSlowDownECommand() {
        return plugin.getConfig().getString("no-slowdown-e.command", "kick %player% Player was walking too fast on honey block (NoSlowDownE)");
    }

    public boolean isNoSlowDownEDebugMode() {
        return plugin.getConfig().getBoolean("no-slowdown-e.debug", false);
    }

    // === NO SLOW F ===

    public boolean isNoSlowDownFEnabled() {
        return plugin.getConfig().getBoolean("no-slowdown-f.enabled", true);
    }

    public double getNoSlowDownFMaxSpeed() {
        return plugin.getConfig().getDouble("no-slowdown-f.max-speed", 0.170);
    }

    public int getMaxNoSlowDownFAlerts() {
        return plugin.getConfig().getInt("no-slowdown-f.max-alerts", 5);
    }

    public String getNoSlowDownFCommand() {
        return plugin.getConfig().getString("no-slowdown-f.command", "kick %player% Player was walking too fast on soul sand (NoSlowDownF)");
    }

    public boolean isNoSlowDownFDebugMode() {
        return plugin.getConfig().getBoolean("no-slowdown-f.debug", false);
    }

    // === NO SLOW G ===

    public boolean isNoSlowDownGEnabled() {
        return plugin.getConfig().getBoolean("no-slowdown-g.enabled", true);
    }

    public double getNoSlowDownGMaxSpeed() {
        return plugin.getConfig().getDouble("no-slowdown-g.max-speed", 0.135);
    }

    public int getMaxNoSlowDownGAlerts() {
        return plugin.getConfig().getInt("no-slowdown-g.max-alerts", 5);
    }

    public String getNoSlowDownGCommand() {
        return plugin.getConfig().getString("no-slowdown-g.command", "kick %player% Player was walking too fast while sneaking (NoSlowDownG)");
    }

    public boolean isNoSlowDownGDebugMode() {
        return plugin.getConfig().getBoolean("no-slowdown-g.debug", false);
    }

    // === INVALID PLACE A ===

    public boolean isInvalidPlaceAEnabled() {
        return plugin.getConfig().getBoolean("invalid-place-a.enabled", true);
    }

    public boolean isInvalidPlaceACancelEvent() {
        return plugin.getConfig().getBoolean("invalid-place-a.cancel-event", false);
    }

    public double getInvalidPlaceAThreshold() {
        return plugin.getConfig().getDouble("invalid-place-a.max-angle", 50.0);
    }

    public int getMaxInvalidPlaceAAlerts() {
        return plugin.getConfig().getInt("invalid-place-a.max-alerts", 5);
    }

    public String getInvalidPlaceACommand() {
        return plugin.getConfig().getString("invalid-place-a.command", "kick %player% Invalid block placement (InvalidPlaceA)");
    }

    // === FAST PLACE A ===

    public boolean isFastPlaceAEnabled() {
        return plugin.getConfig().getBoolean("fast-place.enabled", true);
    }

    public boolean isFastPlaceACancelEvent() {
        return plugin.getConfig().getBoolean("fast-place.cancel-event", true);
    }

    public int getFastPlaceAMaxPerSecond() {
        return plugin.getConfig().getInt("fast-place.max-per-tick", 4);
    }

    public int getMaxFastPlaceAAlerts() {
        return plugin.getConfig().getInt("fast-place.max-alerts", 3);
    }

    public String getFastPlaceACommand() {
        return plugin.getConfig().getString("fast-place.command", "kick %player% Too fast block placement (FastPlaceA)");
    }

    public boolean isFastPlaceADebugMode() {
        return plugin.getConfig().getBoolean("fast-place.debug", false);
    }

    // === AUTO TOTEM A ===

    public boolean isAutoTotemAEnabled() {
        return config.getBoolean("auto-totem-a.enabled", true);
    }

    public long getAutoTotemAMinDelay() {
        return config.getLong("auto-totem-a.min-delay", 150);
    }

    public int getAutoTotemATickInterval() {
        return config.getInt("auto-totem-a.tick-interval", 3);
    }

    public double getMaxPing() {
        return config.getDouble("auto-totem-a.max-ping", -1.0);
    }

    public int getMaxAutoTotemAAlerts() {
        return config.getInt("auto-totem-a.max-alerts", 5);
    }

    public String getAutoTotemACommand() {
        return config.getString("auto-totem-a.command", "kick %player% Too fast totem swap (AutoTotemA)");
    }

    public boolean isAutoTotemADebugMode() {
        return config.getBoolean("auto-totem-a.debug", false);
    }
}