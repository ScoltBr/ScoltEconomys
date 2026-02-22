package me.scoltbr.scoltEconomys.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

public final class Tasks {

    private final Plugin plugin;

    // thread-safe porque podemos agendar/cancelar em contextos diferentes
    private final List<Integer> taskIds = Collections.synchronizedList(new ArrayList<>());

    public Tasks(Plugin plugin) {
        this.plugin = plugin;
    }

    // --- API atual (mantida) ---
    public void runRepeatingAsync(IntConsumer task, long delayTicks, long periodTicks, int arg) {
        int id = Bukkit.getScheduler()
                .runTaskTimerAsynchronously(plugin, () -> task.accept(arg), delayTicks, periodTicks)
                .getTaskId();
        taskIds.add(id);
    }

    // --- Overload clean para Runnable (juros, etc.) ---
    public void runRepeatingAsync(Runnable task, long delayTicks, long periodTicks) {
        int id = Bukkit.getScheduler()
                .runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks)
                .getTaskId();
        taskIds.add(id);
    }

    // --- Um-off async (útil pra jobs curtos) ---
    public void runAsync(Runnable task) {
        int id = Bukkit.getScheduler()
                .runTaskAsynchronously(plugin, task)
                .getTaskId();
        taskIds.add(id);
    }

    // --- Opcional: sync helpers (se quiser futuramente) ---
    public void runRepeatingSync(Runnable task, long delayTicks, long periodTicks) {
        int id = Bukkit.getScheduler()
                .runTaskTimer(plugin, task, delayTicks, periodTicks)
                .getTaskId();
        taskIds.add(id);
    }

    public void runSync(Runnable task) {
        int id = Bukkit.getScheduler()
                .runTask(plugin, task)
                .getTaskId();
        taskIds.add(id);
    }

    public void cancelAll() {
        synchronized (taskIds) {
            for (int id : taskIds) {
                Bukkit.getScheduler().cancelTask(id);
            }
            taskIds.clear();
        }
    }
}