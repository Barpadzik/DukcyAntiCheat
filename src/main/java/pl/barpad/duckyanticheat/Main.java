package pl.barpad.duckyanticheat;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import pl.barpad.duckyanticheat.checks.combat.ThruBlocksA;
import pl.barpad.duckyanticheat.checks.elytra.ElytraAimA;
import pl.barpad.duckyanticheat.checks.movement.*;
import pl.barpad.duckyanticheat.checks.place.FastPlaceA;
import pl.barpad.duckyanticheat.checks.place.InvalidPlaceA;
import pl.barpad.duckyanticheat.checks.elytra.ElytraCriticalsA;
import pl.barpad.duckyanticheat.checks.player.AutoTotemA;
import pl.barpad.duckyanticheat.checks.player.TimerA;
import pl.barpad.duckyanticheat.checks.player.TimerB;
import pl.barpad.duckyanticheat.checks.player.TimerC;
import pl.barpad.duckyanticheat.command.Reload;
import pl.barpad.duckyanticheat.utils.MetricsLite;
import pl.barpad.duckyanticheat.utils.UpdateChecker;
import pl.barpad.duckyanticheat.utils.DiscordHook;
import pl.barpad.duckyanticheat.utils.ViolationAlerts;
import pl.barpad.duckyanticheat.utils.managers.ConfigManager;

import java.util.ArrayList;
import java.util.List;

public final class Main extends JavaPlugin {

    private ConfigManager configManager;
    private ViolationAlerts violationAlerts;
    private DiscordHook discordHook;
    private final List<Listener> registeredChecks = new ArrayList<>();

    @Override
    public void onLoad() {
        getLogger().info("DuckyAntiCheat Loading...");
    }

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            initializeComponents();
            registerChecks();
            initializeCommands();
            initializeExtras();

            getLogger().info("DuckyAntiCheat Enabled - Welcome :] | Author: Barpad");
        } catch (Exception e) {
            getLogger().severe("Failed to enable DuckyAntiCheat: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);

        if (violationAlerts != null) {
            violationAlerts.clearAllViolations();
        }

        getLogger().info("DuckyAntiCheat Disabled - Thank You | Author: Barpad");
    }

    private void initializeComponents() {
        this.configManager = new ConfigManager(this);
        this.discordHook = new DiscordHook(this, configManager);
        this.violationAlerts = new ViolationAlerts(this, configManager, discordHook);
    }

    private void registerChecks() {
        registerCheck(new ThruBlocksA(this, violationAlerts, discordHook, configManager));
        registerCheck(new ElytraAimA(this, violationAlerts, discordHook, configManager));
        registerCheck(new ElytraCriticalsA(this, violationAlerts, discordHook, configManager));
        registerCheck(new NoWebA(this, violationAlerts, discordHook, configManager));
        registerCheck(new NoSlowDownA(this, violationAlerts, discordHook, configManager));
        registerCheck(new NoSlowDownB(this, violationAlerts, discordHook, configManager));
        registerCheck(new NoSlowDownC(this, violationAlerts, discordHook, configManager));
        registerCheck(new NoSlowDownD(this, violationAlerts, discordHook, configManager));
        registerCheck(new NoSlowDownE(this, violationAlerts, discordHook, configManager));
        registerCheck(new NoSlowDownF(this, violationAlerts, discordHook, configManager));
        registerCheck(new NoSlowDownG(this, violationAlerts, discordHook, configManager));
        registerCheck(new InvalidPlaceA(this, violationAlerts, discordHook, configManager));
        registerCheck(new FastPlaceA(this, violationAlerts, discordHook, configManager));
        registerCheck(new AutoTotemA(this, violationAlerts, discordHook, configManager));
        registerCheck(new TimerA(this, violationAlerts, discordHook, configManager));
        registerCheck(new TimerB(this, violationAlerts, discordHook, configManager));
        registerCheck(new TimerC(this, violationAlerts, discordHook, configManager));

        getLogger().info("Registered " + registeredChecks.size() + " anti-cheat checks");
    }

    private void registerCheck(Listener check) {
        registeredChecks.add(check);
    }

    private void initializeCommands() {
        new Reload(this, configManager, violationAlerts);
    }

    private void initializeExtras() {
        new MetricsLite(this, 25802);

        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                new UpdateChecker(this, configManager).checkForUpdates();
            } catch (Exception e) {
                getLogger().warning("Failed to check for updates: " + e.getMessage());
            }
        });

        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                registeredChecks.stream()
                        .filter(check -> check instanceof AutoTotemA)
                        .forEach(check -> ((AutoTotemA) check).cleanupOfflinePlayers());
            } catch (Exception e) {
                getLogger().warning("Error during cleanup: " + e.getMessage());
            }
        }, 6000L, 6000L);
    }

    public ViolationAlerts getViolationAlerts() {
        return violationAlerts;
    }

    public DiscordHook getDiscordHook() {
        return discordHook;
    }
}