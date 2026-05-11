package me.scoltbr.scoltEconomys.admin;

import me.scoltbr.scoltEconomys.alerts.Alert;
import me.scoltbr.scoltEconomys.alerts.AlertService;
import me.scoltbr.scoltEconomys.stats.AdminStatsService;
import me.scoltbr.scoltEconomys.stats.EconomySnapshot;
import me.scoltbr.scoltEconomys.tax.TaxManager;
import me.scoltbr.scoltEconomys.tax.TaxType;
import me.scoltbr.scoltEconomys.util.ItemBuilder;
import me.scoltbr.scoltEconomys.util.MessageUtils;
import me.scoltbr.scoltEconomys.util.MoneyFormat;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class AdminMenuService {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // Texturas Base64 para navegação padrão
    private static final String TEX_CLOSE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2VkMWFiYTczZjYzNGY0ZjQ0NjRiNDdhZjJhNWQ0NGMyNGM2MGFjYmQ4ZWIyOGQzMjdjNWMxMWRmYWViYTIzMSJ9fX0=";
    private static final String TEX_BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODY1MmUyYjkzNmNhODAyNmJkMjg2NTFkN2M5ZjI4MTlkMmU5MjM2OTc3MzRkMThkZmRiMTM1NTBmOGZkYWQ1ZiJ9fX0=";

    private final AdminStatsService stats;
    private final AlertService alerts;
    private final Plugin plugin;
    private final TaxManager taxManager;
    private final me.scoltbr.scoltEconomys.scheduler.AsyncExecutor async;
    
    private final Map<UUID, BukkitRunnable> liveTasks = new HashMap<>();

    public AdminMenuService(Plugin plugin,
            me.scoltbr.scoltEconomys.scheduler.AsyncExecutor async,
            AdminStatsService stats,
            AlertService alerts,
            TaxManager taxManager) {
        this.plugin = plugin;
        this.async = async;
        this.stats = stats;
        this.alerts = alerts;
        this.taxManager = taxManager;
    }

    public void startLiveRefresh(Player player, AdminMenuPage page) {
        cancelLiveRefresh(player);
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof AdminMenuHolder holder) {
                    if (holder.page() == page) {
                        refreshSilently(player.getOpenInventory().getTopInventory());
                    } else {
                        cancel();
                    }
                } else {
                    cancel();
                }
            }
        };
        task.runTaskTimer(plugin, 40L, 40L); // atualiza a cada 2 segundos
        liveTasks.put(player.getUniqueId(), task);
    }

    public void cancelLiveRefresh(Player player) {
        BukkitRunnable task = liveTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    public void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(new AdminMenuHolder(AdminMenuPage.MAIN), 54,
                MM.deserialize("<gradient:#ff5555:#ffaa00><b>ScoltEconomy - Admin</b></gradient>"));
        inv.setItem(22, ItemBuilder.of(Material.CLOCK).name("<yellow><b>Carregando...</b></yellow>").lore("<gray>Buscando estatísticas do banco de dados...").build());
        player.openInventory(inv);
        renderMain(inv);
        startLiveRefresh(player, AdminMenuPage.MAIN);
    }

    public void openAlerts(Player player) {
        Inventory inv = Bukkit.createInventory(new AdminMenuHolder(AdminMenuPage.ALERTS), 54,
                MM.deserialize("<gradient:#ff5555:#ffaa00><b>Admin - Alertas</b></gradient>"));
        renderAlerts(inv);
        player.openInventory(inv);
        startLiveRefresh(player, AdminMenuPage.ALERTS);
    }

    public void openTax(Player player) {
        Inventory inv = Bukkit.createInventory(new AdminMenuHolder(AdminMenuPage.TAX), 54,
                MM.deserialize("<gradient:#ff5555:#ffaa00><b>Admin - Impostos</b></gradient>"));
        renderTax(inv);
        player.openInventory(inv);
        startLiveRefresh(player, AdminMenuPage.TAX);
    }

    public void refreshSilently(Inventory inv) {
        if (!(inv.getHolder() instanceof AdminMenuHolder holder)) return;

        if (holder.page() == AdminMenuPage.MAIN) {
            renderMain(inv);
        } else if (holder.page() == AdminMenuPage.ALERTS) {
            renderAlerts(inv);
        } else if (holder.page() == AdminMenuPage.TAX) {
            renderTax(inv);
        }
    }

    // Compatibilidade reversa caso ainda exista uso
    public void refresh(Inventory inv) {
        refreshSilently(inv);
    }

    private void renderMain(Inventory inv) {
        async.runAsync(() -> {
            EconomySnapshot snap = stats.calculateNow();
            Optional<Double> growthOpt = stats.growth24h();
            List<Alert> activeAlerts = alerts.activeAlerts();

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!(inv.getHolder() instanceof AdminMenuHolder holder) || holder.page() != AdminMenuPage.MAIN) return;
                
                fillBorders(inv, 6);

                // Stats card
                inv.setItem(13, ItemBuilder.of(Material.EMERALD).name("<green><b>Visão Geral</b></green>")
                        .lore(
                            "<gray>Total Coins: <white>$" + MoneyFormat.format(snap.totalCoins()),
                            "<gray>Wallet: <white>$" + MoneyFormat.format(snap.totalWallet()),
                            "<gray>Banco: <white>$" + MoneyFormat.format(snap.totalBank()),
                            "<gray>Ativos: <white>" + snap.activePlayers(),
                            "<gray>Média/ativo: <white>$" + MoneyFormat.format(snap.averagePerActivePlayer()),
                            "<gray>Top 10%: <white>" + pct(snap.top10Concentration())
                        ).build());

                // Growth 24h
                String growthLine = growthOpt
                        .map(g -> "<gray>Crescimento 24h: " + colorGrowth(g) + pct(g))
                        .orElse("<gray>Crescimento 24h: <dark_gray>Sem dados (aguarde 1 dia)");

                inv.setItem(22, ItemBuilder.of(Material.CLOCK).name("<yellow><b>Crescimento</b></yellow>")
                        .lore(growthLine, "<dark_gray>Base: hoje vs ontem").build());

                // Alerts summary
                inv.setItem(31, ItemBuilder.of(activeAlerts.isEmpty() ? Material.LIME_DYE : Material.RED_DYE)
                        .name("<red><b>Alertas</b></red>")
                        .lore(
                            activeAlerts.isEmpty() ? "<green>Nenhum alerta ativo" : ("<red>" + activeAlerts.size() + " alerta(s) ativo(s)"),
                            "", "<aqua>Clique para abrir"
                        ).build());

                // Buttons Base
                inv.setItem(46, ItemBuilder.of(Material.PAPER).name("<white><b>Impostos</b></white>").lore("<gray>Clique para gerenciar as taxas").build());
                inv.setItem(47, ItemBuilder.of(Material.GOLD_INGOT).name("<gold><b>Banco</b></gold>").lore("<dark_gray>(Em breve)").build());
                inv.setItem(48, ItemBuilder.of(Material.BELL).name("<red><b>Alertas Detalhados</b></red>").lore("<gray>Clique para ver detalhes").build());

                inv.setItem(53, ItemBuilder.of(Material.PLAYER_HEAD).texture(TEX_CLOSE).name("<red><b>Fechar</b></red>").lore("<gray>Sair do painel").build());
            });
        });
    }

    private void renderAlerts(Inventory inv) {
        fillBorders(inv, 6);

        List<Alert> active = alerts.activeAlerts();

        inv.setItem(45, ItemBuilder.of(Material.PLAYER_HEAD).texture(TEX_BACK).name("<yellow><b>Voltar</b></yellow>").lore("<gray>Voltar ao painel principal").build());
        inv.setItem(53, ItemBuilder.of(Material.PLAYER_HEAD).texture(TEX_CLOSE).name("<red><b>Fechar</b></red>").lore("<gray>Sair do painel").build());

        if (active.isEmpty()) {
            inv.setItem(22, ItemBuilder.of(Material.LIME_DYE).name("<green><b>Sem alertas</b></green>")
                    .lore("<gray>A economia está estável no momento.").build());
        } else {
            int slot = 10;
            for (Alert a : active) {
                if (slot >= 44) break; 
                inv.setItem(slot++, ItemBuilder.of(Material.RED_DYE).name("<red><b>" + a.type().name() + "</b></red>")
                        .lore(
                            "<gray>" + a.message(),
                            "<dark_gray>Desde: <gray>" + a.since().toString()
                        ).build());
                if (slot % 9 == 8) slot += 2; // pula bordas
            }
        }
    }

    private void renderTax(Inventory inv) {
        fillBorders(inv, 6);

        inv.setItem(45, ItemBuilder.of(Material.PLAYER_HEAD).texture(TEX_BACK).name("<yellow><b>Voltar</b></yellow>").lore("<gray>Voltar ao painel principal").build());
        inv.setItem(53, ItemBuilder.of(Material.PLAYER_HEAD).texture(TEX_CLOSE).name("<red><b>Fechar</b></red>").lore("<gray>Sair do painel").build());

        // TRANSFER
        var t = taxManager.policy(TaxType.TRANSFER);
        inv.setItem(20, taxCard(
                "<yellow><b>Taxa de Transferência</b></yellow>",
                "tax.transfer",
                t.enabled(),
                t.rate()));

        // WITHDRAW
        var w = taxManager.policy(TaxType.WITHDRAW);
        inv.setItem(24, taxCard(
                "<gold><b>Taxa de Saque</b></gold>",
                "tax.withdraw",
                w.enabled(),
                w.rate()));

        // Controles (legenda)
        inv.setItem(31, ItemBuilder.of(Material.BOOK).name("<white><b>Controles de Ajuste</b></white>")
                .lore(
                    "<gray>Clique esquerdo: <green>+0.1%",
                    "<gray>Clique direito: <red>-0.1%",
                    "<gray>Shift+Esq: <green>+1.0%",
                    "<gray>Shift+Dir: <red>-1.0%",
                    "<gray>Tecla Q: <yellow>Ativar/Desativar"
                ).build());
    }

    private ItemStack taxCard(String title, String keyPrefix, boolean enabled, double rate) {
        Material mat = enabled ? Material.LIME_TERRACOTTA : Material.RED_TERRACOTTA;

        return ItemBuilder.of(mat).name(title)
                .lore(
                    "<gray>Status: " + (enabled ? "<green>ATIVO</green>" : "<red>DESATIVADO</red>"),
                    "<gray>Taxa: <white>" + pct(rate),
                    "",
                    "<gray>Ajuste com os cliques (veja a legenda)",
                    "<dark_gray>Key: " + keyPrefix
                ).build();
    }

    private void fillBorders(Inventory inv, int rows) {
        ItemStack borderOuter = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        ItemStack borderInner = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();

        for (int i = 0; i < rows * 9; i++) {
            int r = i / 9;
            int c = i % 9;
            if (r == 0 || r == rows - 1 || c == 0 || c == 8) {
                inv.setItem(i, borderOuter);
            } else if (r == 1 || r == rows - 2 || c == 1 || c == 7) {
                if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                    inv.setItem(i, borderInner);
                }
            }
        }
    }

    public void adjustTaxRate(Player p, TaxType type, double delta) {
        var policy = taxManager.policy(type);
        double newRate = clamp(policy.rate() + delta, 0.0, 1.0);
        taxManager.setRate(type, newRate);

        String key = type == TaxType.TRANSFER
                ? "tax.transfer.rate"
                : "tax.withdraw.rate";

        plugin.getConfig().set(key, newRate);
        plugin.saveConfig();

        MessageUtils.send(p, "<green>✔ Taxa atualizada: <white>" + pct(newRate));
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }

    public void toggleTax(Player p, TaxType type) {
        var policy = taxManager.policy(type);
        boolean newEnabled = !policy.enabled();
        taxManager.setEnabled(type, newEnabled);

        String key = type == TaxType.TRANSFER
                ? "tax.transfer.enabled"
                : "tax.withdraw.enabled";

        plugin.getConfig().set(key, newEnabled);
        plugin.saveConfig();

        MessageUtils.send(p, "<yellow>Taxa " + (newEnabled ? "<green>ATIVADA" : "<red>DESATIVADA") + "<yellow>.");
        p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private String pct(double v) {
        return String.format(Locale.US, "%.2f%%", v * 100.0);
    }

    private String pct(java.math.BigDecimal v) {
        return pct(v.doubleValue());
    }

    private String colorGrowth(double g) {
        if (g >= 0.25) return "<red>";
        if (g >= 0.10) return "<yellow>";
        if (g >= 0.00) return "<green>";
        return "<aqua>";
    }
}