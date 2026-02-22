package me.scoltbr.scoltEconomys.account;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerLifecycleListener implements Listener {

    private final AccountService accountService;
    private final AccountFlushService flushService;

    public PlayerLifecycleListener(AccountService accountService, AccountFlushService flushService) {
        this.accountService = accountService;
        this.flushService = flushService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        accountService.getOrLoad(e.getPlayer().getUniqueId(), acc -> {
            // cache já está preenchido; nada pesado aqui
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        flushService.flushNowAndRemove(e.getPlayer().getUniqueId());
        // opcional: remover do cache depois de um tempo/instantâneo
    }
}