package pl.barpad.duckyanticheat.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import pl.barpad.duckyanticheat.Main;
import pl.barpad.duckyanticheat.utils.ViolationAlerts;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.util.Collections;
import java.util.List;

public class Reload extends AbstractCommand {

    private final Main plugin;
    private final ConfigManager configManager;
    private final ViolationAlerts violationAlerts;

    public Reload(Main plugin, ConfigManager configManager, ViolationAlerts violationAlerts) {
        super("duckyac", "Plugin Reload Command", "/duckyac reload", "§f§l≫ §cUnknown Command");
        this.plugin = plugin;
        this.configManager = configManager;
        this.violationAlerts = violationAlerts;
        this.register();
    }

    private String getMessage(String key) {
        return configManager.getString(key, "§c[DuckyAntiCheat] Missing message: " + key).replace("&", "§");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String [] args) {
        if (!sender.hasPermission("duckyac.reload")) {
            sender.sendMessage(color(getMessage("no-permission")));
            return false;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            configManager.reload();
            violationAlerts.clearAllViolations();

            sender.sendMessage(color(getMessage("config-reloaded")));
            sender.sendMessage(color(getMessage("plugin-reloaded")));
            return true;
        }

        sender.sendMessage(color(getMessage("incorrect-usage")));
        return false;
    }

    private String color(String message) {
        return message == null ? "" : message.replace("&", "§");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String [] args) {
        if (sender.hasPermission("duckyac.reload")) {
            if (args.length == 1) {
                return Collections.singletonList("reload");
            }
        }
        return Collections.emptyList();
    }
}