package me.scoltbr.scoltEconomys;

import me.scoltbr.scoltEconomys.core.Bootstrap;
import me.scoltbr.scoltEconomys.core.ShutdownHook;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

    private Bootstrap bootstrap;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.bootstrap = new Bootstrap(this);
        this.bootstrap.enable();
    }

    @Override
    public void onDisable() {
        if (this.bootstrap != null) {
            new ShutdownHook(this.bootstrap).run();
        }
    }
}
