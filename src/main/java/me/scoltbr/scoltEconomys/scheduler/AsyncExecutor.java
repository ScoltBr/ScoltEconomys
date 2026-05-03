package me.scoltbr.scoltEconomys.scheduler;

import org.bukkit.plugin.Plugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class AsyncExecutor {

    private final ExecutorService pool;

    public AsyncExecutor(Plugin plugin) {
        // Utilizando Virtual Threads (Java 21) para máxima performance em I/O assíncrono (Database, etc)
        this.pool = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name(plugin.getName() + "-async-", 0).factory()
        );
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