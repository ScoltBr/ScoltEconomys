package me.scoltbr.scoltEconomys.account;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerLifecycleListener implements Listener {

    private final AccountService accountService;
    private final AccountFlushService flushService;

    public PlayerLifecycleListener(AccountService accountService, AccountFlushService flushService) {
        this.accountService = accountService;
        this.flushService = flushService;
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
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        flushService.flushNowAndRemove(e.getPlayer().getUniqueId());
        // opcional: remover do cache depois de um tempo/instantâneo
    }
}