package me.scoltbr.scoltEconomys.stats;

import me.scoltbr.scoltEconomys.account.AccountRepository;
import me.scoltbr.scoltEconomys.account.TopBalanceRow;
import me.scoltbr.scoltEconomys.scheduler.AsyncExecutor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.function.Consumer;

public final class MoneyTopService {

    private final AsyncExecutor async;
    private final AccountRepository repo;
    private final Plugin plugin;

    public MoneyTopService(Plugin plugin, AsyncExecutor async, AccountRepository repo) {
        this.async = async;
        this.repo = repo;
        this.plugin = plugin;
    }

    public void fetchTop10(Consumer<List<TopLine>> callbackSync) {
        async.runAsync(() -> {
            List<TopBalanceRow> rows = repo.topTotal(10);

            // resolve nomes (pode ser null se nunca entrou)
            List<TopLine> lines = rows.stream().map(r -> {
                OfflinePlayer op = Bukkit.getOfflinePlayer(r.uuid());
                String name = op.getName();
                if (name == null || name.isBlank()) {
                    name = r.uuid().toString().substring(0, 8);
                }
                return new TopLine(name, r.total());
            }).toList();

            // volta pro sync para mandar mensagem
            Bukkit.getScheduler().runTask(plugin, () -> callbackSync.accept(lines));
        });
    }

    public record TopLine(String name, double total) {}
}