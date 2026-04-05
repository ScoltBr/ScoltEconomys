package me.scoltbr.scoltEconomys.admin;

import me.scoltbr.scoltEconomys.alerts.Alert;
import me.scoltbr.scoltEconomys.alerts.AlertService;
import me.scoltbr.scoltEconomys.stats.AdminStatsService;
import me.scoltbr.scoltEconomys.stats.EconomySnapshot;
import me.scoltbr.scoltEconomys.tax.TaxManager;
import me.scoltbr.scoltEconomys.tax.TaxType;
import me.scoltbr.scoltEconomys.util.MoneyFormat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class AdminMenuService {

    private final AdminStatsService stats;
    private final AlertService alerts;
    private final Plugin plugin;
    private final TaxManager taxManager;
    private final me.scoltbr.scoltEconomys.scheduler.AsyncExecutor async;

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

    public void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(new AdminMenuHolder(AdminMenuPage.MAIN), 54, "§6§lScoltEconomy §7(Admin)");
        renderMain(inv);
        player.openInventory(inv);
    }

    public void openAlerts(Player player) {
        Inventory inv = Bukkit.createInventory(new AdminMenuHolder(AdminMenuPage.ALERTS), 54, "§6§lScoltEconomy §7(Alertas)");
        renderAlerts(inv);
        player.openInventory(inv);
    }

    public void openTax(Player player) {
        Inventory inv = Bukkit.createInventory(new AdminMenuHolder(AdminMenuPage.TAX), 54, "§6§lScoltEconomy §7(Impostos)");
        renderTax(inv);
        player.openInventory(inv);
    }

    public void refresh(Inventory inv) {
        if (!(inv.getHolder() instanceof AdminMenuHolder holder)) return;

        inv.clear();
        if (holder.page() == AdminMenuPage.MAIN) renderMain(inv);
        if (holder.page() == AdminMenuPage.ALERTS) renderAlerts(inv);
        if (holder.page() == AdminMenuPage.TAX) renderTax(inv);
    }

    private void renderMain(Inventory inv) {
        EconomySnapshot snap = stats.calculateNow();
        Optional<Double> growthOpt = stats.growth24h();
        List<Alert> activeAlerts = alerts.activeAlerts();

        // Moldura
        MenuItems.fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE, " ");

        // Stats card
        inv.setItem(13, MenuItems.item(Material.EMERALD, "§a§lVisão Geral",
                "§7Total Coins: §f" + MoneyFormat.format(snap.totalCoins()),
                "§7Wallet: §f" + MoneyFormat.format(snap.totalWallet()),
                "§7Banco: §f" + MoneyFormat.format(snap.totalBank()),
                "§7Ativos: §f" + snap.activePlayers(),
                "§7Média/ativo: §f" + MoneyFormat.format(snap.averagePerActivePlayer()),
                "§7Top 10%: §f" + pct(snap.top10Concentration())
        ));

        // Growth 24h
        String growthLine = growthOpt
                .map(g -> "§7Crescimento 24h: " + colorGrowth(g) + pct(g))
                .orElse("§7Crescimento 24h: §8Sem dados (aguarde 1 dia)");

        inv.setItem(22, MenuItems.item(Material.CLOCK, "§e§lCrescimento",
                growthLine,
                "§8Base: hoje vs ontem (tabela diária)"
        ));

        // Alerts summary
        inv.setItem(31, MenuItems.item(
                activeAlerts.isEmpty() ? Material.LIME_DYE : Material.RED_DYE,
                "§c§lAlertas",
                activeAlerts.isEmpty()
                        ? "§aNenhum alerta ativo"
                        : ("§c" + activeAlerts.size() + " alerta(s) ativo(s)"),
                "§7Clique para abrir"
        ));

        // Buttons
        inv.setItem(49, MenuItems.item(Material.ENDER_EYE, "§b§lAtualizar", "§7Clique para atualizar esta página"));

        inv.setItem(46, MenuItems.item(Material.PAPER, "§f§lImpostos", "§7Clique para gerenciar as taxas"));
        inv.setItem(47, MenuItems.item(Material.GOLD_INGOT, "§6§lBanco", "§7(Em breve)"));
        inv.setItem(48, MenuItems.item(Material.BELL, "§c§lAlertas", "§7Clique para ver detalhes"));

        inv.setItem(53, MenuItems.item(Material.BARRIER, "§c§lFechar", "§7Clique para fechar"));
    }

    private void renderAlerts(Inventory inv) {
        MenuItems.fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE, " ");

        List<Alert> active = alerts.activeAlerts();

        inv.setItem(49, MenuItems.item(Material.ENDER_EYE, "§b§lAtualizar", "§7Clique para atualizar"));
        inv.setItem(45, MenuItems.item(Material.ARROW, "§f§lVoltar", "§7Voltar ao painel"));
        inv.setItem(53, MenuItems.item(Material.BARRIER, "§c§lFechar", "§7Clique para fechar"));

        if (active.isEmpty()) {
            inv.setItem(22, MenuItems.item(Material.LIME_DYE, "§a§lSem alertas",
                    "§7A economia está estável no momento."));
            return;
        }

        int slot = 10;
        for (Alert a : active) {
            if (slot >= 44) break; // área interna
            inv.setItem(slot++, MenuItems.item(Material.RED_DYE, "§c§l" + a.type().name(),
                    "§7" + a.message(),
                    "§8Desde: §7" + a.since().toString()
            ));
            if (slot % 9 == 8) slot += 2; // pula bordas
        }
    }

    private void renderTax(Inventory inv) {
        MenuItems.fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE, " ");

        inv.setItem(45, MenuItems.item(Material.ARROW, "§f§lVoltar", "§7Voltar ao painel"));
        inv.setItem(49, MenuItems.item(Material.ENDER_EYE, "§b§lAtualizar", "§7Clique para atualizar"));
        inv.setItem(53, MenuItems.item(Material.BARRIER, "§c§lFechar", "§7Clique para fechar"));

        // TRANSFER
        var t = taxManager.policy(TaxType.TRANSFER);
        inv.setItem(20, taxCard(
                "§e§lTaxa de Transferência",
                "tax.transfer",
                t.enabled(),
                t.rate()
        ));

        // WITHDRAW
        var w = taxManager.policy(TaxType.WITHDRAW);
        inv.setItem(24, taxCard(
                "§6§lTaxa de Saque",
                "tax.withdraw",
                w.enabled(),
                w.rate()
        ));

        // Controles (legenda)
        inv.setItem(31, MenuItems.item(Material.BOOK, "§f§lControles",
                "§7Clique esquerdo: §a+0.1%",
                "§7Clique direito: §c-0.1%",
                "§7Shift+Esq: §a+1.0%",
                "§7Shift+Dir: §c-1.0%",
                "§7Tecla Q: §eAtivar/Desativar"
        ));
    }

    private ItemStack taxCard(String title, String keyPrefix, boolean enabled, double rate) {
        Material mat = enabled ? Material.LIME_TERRACOTTA : Material.RED_TERRACOTTA;

        return MenuItems.item(mat, title,
                "§7Status: " + (enabled ? "§aATIVO" : "§cDESATIVADO"),
                "§7Taxa: §f" + pct(rate),
                "§8",
                "§7Ajuste com cliques",
                "§8Key: " + keyPrefix
        );
    }

    public void adjustTaxRate(Player p, TaxType type, double delta) {
        var policy = taxManager.policy(type);
        double newRate = clamp(policy.rate() + delta, 0.0, 1.0);
        taxManager.setRate(type, newRate);

        String key = type == TaxType.TRANSFER
                ? "tax.transfer.rate"
                : "tax.withdraw.rate";

        plugin.getConfig().set(key, newRate);
        async.runAsync(plugin::saveConfig);

        p.sendMessage("§aTaxa atualizada: §f" + pct(newRate));
    }

    public void toggleTax(Player p, TaxType type) {
        var policy = taxManager.policy(type);
        boolean newEnabled = !policy.enabled();
        taxManager.setEnabled(type, newEnabled);

        String key = type == TaxType.TRANSFER
                ? "tax.transfer.enabled"
                : "tax.withdraw.enabled";

        plugin.getConfig().set(key, newEnabled);
        async.runAsync(plugin::saveConfig);

        p.sendMessage("§eTaxa " + (newEnabled ? "§aATIVADA" : "§cDESATIVADA") + "§e.");
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private String pct(double v) {
        return String.format(Locale.US, "%.2f%%", v * 100.0);
    }

    private String colorGrowth(double g) {
        if (g >= 0.25) return "§c";
        if (g >= 0.10) return "§e";
        if (g >= 0.00) return "§a";
        return "§b";
    }
}
