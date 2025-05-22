package pl.barpad.duckyanticheat;

import org.bukkit.plugin.java.JavaPlugin;
import pl.barpad.duckyanticheat.checks.combat.ThruBlocksA;
import pl.barpad.duckyanticheat.checks.elytra.ElytraAimA;
import pl.barpad.duckyanticheat.checks.movement.*;
import pl.barpad.duckyanticheat.checks.place.FastPlaceA;
import pl.barpad.duckyanticheat.checks.place.InvalidPlaceA;
import pl.barpad.duckyanticheat.checks.elytra.ElytraCriticalsA;
import pl.barpad.duckyanticheat.checks.player.AutoTotemA;
import pl.barpad.duckyanticheat.command.Reload;
import pl.barpad.duckyanticheat.utils.MetricsLite;
import pl.barpad.duckyanticheat.utils.UpdateChecker;
import pl.barpad.duckyanticheat.utils.DiscordHook;
import pl.barpad.duckyanticheat.utils.ViolationAlerts;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

public final class Main extends JavaPlugin {

    private ConfigManager configManager;
    private ViolationAlerts violationAlerts;
    private DiscordHook discordHook;

    @Override
    public void onLoad() {
        getLogger().info("DuckyAntiCheat Enabling...");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.configManager = new ConfigManager(this);
        this.discordHook = new DiscordHook(this, configManager);
        this.violationAlerts = new ViolationAlerts(this, configManager, discordHook);

        new ElytraAimA(this, violationAlerts, discordHook, configManager);
        new ElytraCriticalsA(this, violationAlerts, discordHook, configManager);
        new ThruBlocksA(this, violationAlerts, discordHook, configManager);
        new NoWebA(this, violationAlerts, discordHook, configManager);
        new NoSlowDownA(this, violationAlerts, discordHook, configManager);
        new NoSlowDownB(this, violationAlerts, discordHook, configManager);
        new NoSlowDownC(this, violationAlerts, discordHook, configManager);
        new NoSlowDownD(this, violationAlerts, discordHook, configManager);
        new NoSlowDownE(this, violationAlerts, discordHook, configManager);
        new NoSlowDownF(this, violationAlerts, discordHook, configManager);
        new NoSlowDownG(this, violationAlerts, discordHook, configManager);
        new InvalidPlaceA(this, violationAlerts, discordHook, configManager);
        new FastPlaceA(this, violationAlerts, discordHook, configManager);
        new AutoTotemA(this, violationAlerts, discordHook, configManager);

        new Reload(this, configManager, violationAlerts);

        new MetricsLite(this, 25802);
        new UpdateChecker(this, configManager).checkForUpdates();

        getLogger().info("DuckyAntiCheat Enabled - Welcome :] | Author: Barpad");
    }

    @Override
    public void onDisable() {
        getLogger().info("DuckyAntiCheat Disabled - Thank You | Author: Barpad");
    }
}