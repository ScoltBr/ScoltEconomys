package me.scoltbr.scoltEconomys.core;

public final class ShutdownHook {

    private final Bootstrap bootstrap;

    public ShutdownHook(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    public void run() {
        try {
            if (bootstrap.accountFlushService() != null) {
                bootstrap.accountFlushService().flushAllBlocking();
            }
        } catch (Exception ignored) {}

        try {
            if (bootstrap.tasks() != null) {
                bootstrap.tasks().cancelAll();
            }
        } catch (Exception ignored) {}

        try {
            if (bootstrap.asyncExecutor() != null) {
                bootstrap.asyncExecutor().shutdown();
            }
        } catch (Exception ignored) {}

        try {
            if (bootstrap.databaseManager() != null) {
                bootstrap.databaseManager().stop();
            }
        } catch (Exception ignored) {}
    }
}
