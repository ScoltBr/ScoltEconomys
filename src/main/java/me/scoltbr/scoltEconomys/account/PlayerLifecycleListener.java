package me.scoltbr.scoltEconomys.account;

import me.scoltbr.scoltEconomys.scheduler.AsyncExecutor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerLifecycleListener implements Listener {

    private final AccountService accountService;
    private final AccountFlushService flushService;
    private final AccountRepository accountRepository;
    private final AsyncExecutor async;

    public PlayerLifecycleListener(AccountService accountService,
                                   AccountFlushService flushService,
                                   AccountRepository accountRepository,
                                   AsyncExecutor async) {
        this.accountService = accountService;
        this.flushService = flushService;
        this.accountRepository = accountRepository;
        this.async = async;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        accountService.getOrLoad(e.getUniqueId(), acc -> {
            latch.countDown();
        });
        try {
            if (!latch.await(3, java.util.concurrent.TimeUnit.SECONDS)) {
                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "§cErro ao carregar dados financeiros. Tente novamente.");
            }
        } catch (InterruptedException ex) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "§cErro interno. Tente novamente.");
            Thread.currentThread().interrupt();
        }

        // Salva o nome do jogador para o ranking funcionar corretamente
        String name = e.getName();
        if (name != null && !name.isBlank()) {
            async.runAsync(() -> accountRepository.updatePlayerName(e.getUniqueId(), name));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        flushService.flushNowAndRemove(e.getPlayer().getUniqueId());
        // opcional: remover do cache depois de um tempo/instantâneo
    }
}