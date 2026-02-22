package me.scoltbr.scoltEconomys.account;

import me.scoltbr.scoltEconomys.scheduler.AsyncExecutor;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AccountFlushService {

    private final Plugin plugin;
    private final AsyncExecutor async;
    private final AccountCache cache;
    private final AccountRepository repo;

    public AccountFlushService(Plugin plugin, AsyncExecutor async, AccountCache cache, AccountRepository repo) {
        this.plugin = plugin;
        this.async = async;
        this.cache = cache;
        this.repo = repo;
    }

    /**
     * Drena até "max" contas da fila dirty e salva em batch (async).
     */
    public void flushDirtyBatch(int max) {
        List<PlayerAccount> batch = cache.pollDirtyBatch(max);
        if (batch.isEmpty()) return;

        async.runAsync(() -> saveBatch(batch));
    }

    /**
     * Flush final no shutdown.
     * Importante: não recalcular tudo, apenas drenar a fila dirty em batches.
     */
    public void flushAllBlocking() {
        // Evita travar com um batch gigante
        final int batchSize = 500;

        List<PlayerAccount> batch;
        do {
            batch = cache.pollDirtyBatch(batchSize);
            if (batch.isEmpty()) break;

            try {
                repo.upsertBatch(batch);
                for (PlayerAccount acc : batch) acc.clearDirty();
            } catch (Exception e) {
                plugin.getLogger().severe("Final flush failed: " + e.getMessage());
                // Se falhou, requeue e aborta (pra não loopar infinito)
                for (PlayerAccount acc : batch) cache.requeueDirty(acc.uuid());
                break;
            }
        } while (true);
    }

    /**
     * Salva uma conta específica (async). Útil em quit.
     * Não depende do acc.isDirty() porque a fonte de verdade é a fila do cache.
     */
    public void flushNow(UUID uuid) {
        cache.get(uuid).ifPresent(acc -> {
            // garante que o UUID está na fila (caso alguém tenha mexido e não enfileirou por bug)
            cache.markDirty(uuid);

            async.runAsync(() -> {
                try {
                    repo.upsertBatch(List.of(acc));
                    acc.clearDirty();
                } catch (Exception e) {
                    cache.requeueDirty(uuid);
                }
            });
        });
    }

    private void saveBatch(List<PlayerAccount> batch) {
        try {
            repo.upsertBatch(batch);
            for (PlayerAccount acc : batch) acc.clearDirty();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed saving batch: " + e.getMessage());
            for (PlayerAccount acc : batch) cache.requeueDirty(acc.uuid());
        }
    }

    public void flushNowAndRemove(UUID uuid) {
        cache.get(uuid).ifPresent(acc -> {
            cache.markDirty(uuid);

            async.runAsync(() -> {
                try {
                    repo.upsertBatch(List.of(acc));
                    acc.clearDirty();
                } catch (Exception e) {
                    cache.requeueDirty(uuid);
                } finally {
                    // remove do cache depois da tentativa de salvar
                    cache.remove(uuid);
                }
            });
        });
    }


}
