package me.scoltbr.scoltEconomys.audit;

import me.scoltbr.scoltEconomys.scheduler.AsyncExecutor;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.UUID;

public final class TransactionAuditService {

    private final Plugin plugin;
    private final AsyncExecutor async;

    public TransactionAuditService(Plugin plugin, AsyncExecutor async) {
        this.plugin = plugin;
        this.async = async;
    }

    // Método genérico
    public void record(TransactionType type,
                       UUID from,
                       UUID to,
                       double gross,
                       double net,
                       double fee,
                       String source,
                       String context) {

        async.runAsync(() -> {
            plugin.getLogger().info(
                    "[AUDIT] " + type +
                            " | from=" + from +
                            " | to=" + to +
                            " | gross=" + gross +
                            " | net=" + net +
                            " | fee=" + fee +
                            " | source=" + source +
                            " | ctx=" + context +
                            " | at=" + Instant.now()
            );
        });
    }

    // ----------------------------
    // Admin helpers
    // ----------------------------

    public void recordAdminGive(String adminName, UUID target, double amount) {
        record(
                TransactionType.ADMIN_GIVE,
                null,
                target,
                amount,
                amount,
                0.0,
                "admin:" + adminName,
                "eco give"
        );
    }

    public void recordAdminTake(String adminName, UUID target, double amount) {
        record(
                TransactionType.ADMIN_TAKE,
                target,
                null,
                amount,
                amount,
                0.0,
                "admin:" + adminName,
                "eco take"
        );
    }

    public void recordAdminSet(String adminName, UUID target, double newValue) {
        record(
                TransactionType.ADMIN_SET,
                null,
                target,
                newValue,
                newValue,
                0.0,
                "admin:" + adminName,
                "eco set"
        );
    }
}
