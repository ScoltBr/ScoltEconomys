package me.scoltbr.scoltEconomys.scheduler;

import org.bukkit.plugin.Plugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class AsyncExecutor {

    private final ExecutorService pool;

    public AsyncExecutor(Plugin plugin) {
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        this.pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, plugin.getName() + "-async");
            t.setDaemon(true);
            return t;
        });
    }

    public void runAsync(Runnable task) {
        pool.execute(task);
    }

    public void shutdown() {
        pool.shutdown();
        try {
            pool.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}