package me.scoltbr.scoltEconomys.audit;

import me.scoltbr.scoltEconomys.scheduler.AsyncExecutor;
import org.bukkit.plugin.Plugin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;

public final class TransactionFileSink {

    private final Plugin plugin;
    private final AsyncExecutor async;
    private final Path path;

    public TransactionFileSink(Plugin plugin, AsyncExecutor async, String relativePath) {
        this.plugin = plugin;
        this.async = async;
        this.path = plugin.getDataFolder().toPath().resolve(relativePath);
    }

    public void append(TransactionRecord r) {
        async.runAsync(() -> {
            try {
                Files.createDirectories(path.getParent());
                try (BufferedWriter w = Files.newBufferedWriter(
                        path,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND
                )) {
                    w.write(format(r));
                    w.newLine();
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Audit file write failed: " + e.getMessage());
            }
        });
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