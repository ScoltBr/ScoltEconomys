package me.scoltbr.scoltEconomys.audit;

import me.scoltbr.scoltEconomys.scheduler.AsyncExecutor;
import org.bukkit.plugin.Plugin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;

public final class TransactionFileSink {

    private final Plugin plugin;
    private final Path path;
    private final java.util.concurrent.ConcurrentLinkedQueue<TransactionRecord> queue;
    private final int taskId;

    public TransactionFileSink(Plugin plugin, AsyncExecutor async, String relativePath) {
        this.plugin = plugin;
        this.path = plugin.getDataFolder().toPath().resolve(relativePath);
        this.queue = new java.util.concurrent.ConcurrentLinkedQueue<>();
        
        // Timer de flush periódico (a cada 5 segundos)
        this.taskId = org.bukkit.Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flush, 100L, 100L).getTaskId();
    }

    public void append(TransactionRecord r) {
        queue.add(r);
    }
    
    public void flush() {
        if (queue.isEmpty()) return;
        
        java.util.List<TransactionRecord> batch = new java.util.ArrayList<>();
        TransactionRecord req;
        while ((req = queue.poll()) != null) {
            batch.add(req);
        }
        
        if (batch.isEmpty()) return;
        
        try {
            Files.createDirectories(path.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(
                    path,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            )) {
                for (TransactionRecord r : batch) {
                    w.write(format(r));
                    w.newLine();
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Audit file write failed: " + e.getMessage());
        }
    }
    
    public void close() {
        org.bukkit.Bukkit.getScheduler().cancelTask(taskId);
        flush();
    }



    private String format(TransactionRecord r) {
        // linha simples e parseável
        // ISO_TIME | TYPE | from | to | gross | net | fee | source | note
        return DateTimeFormatter.ISO_INSTANT.format(r.at()) + " | " +
                r.type() + " | " +
                (r.from() == null ? "-" : r.from()) + " | " +
                (r.to() == null ? "-" : r.to()) + " | " +
                r.gross() + " | " + r.net() + " | " + r.fee() + " | " +
                safe(r.source()) + " | " + safe(r.note());
    }

    private String safe(String s) {
        if (s == null) return "-";
        return s.replace("\n", "\\n").replace("\r", "\\r");
    }
}