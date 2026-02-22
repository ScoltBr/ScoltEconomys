package me.scoltbr.scoltEconomys.admin;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class AdminMenuListener implements Listener {

    private final AdminMenuService menus;

    public AdminMenuListener(AdminMenuService menus) {
        this.menus = menus;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getInventory().getHolder() instanceof AdminMenuHolder holder)) return;

        e.setCancelled(true);

        if (e.getCurrentItem() == null) return;
        Material type = e.getCurrentItem().getType();

        switch (holder.page()) {
            case MAIN -> handleMain(p, e.getInventory(), type);
            case ALERTS -> handleAlerts(p, e.getInventory(), type);
            case TAX -> handleTax(p, e, e.getInventory(), type);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof AdminMenuHolder) {
            e.setCancelled(true);
        }
    }

    private void handleMain(Player p, org.bukkit.inventory.Inventory inv, Material type) {
        if (type == Material.BARRIER) {
            p.closeInventory();
            return;
        }
        if (type == Material.ENDER_EYE) { // atualizar
            menus.refresh(inv);
            p.sendMessage("§bPainel atualizado.");
            return;
        }

        if (type == Material.PAPER) {
            menus.openTax(p);
            return;
        }

        if (type == Material.BELL || type == Material.RED_DYE || type == Material.LIME_DYE) {
            menus.openAlerts(p);
        }
    }

    private void handleAlerts(Player p, org.bukkit.inventory.Inventory inv, Material type) {
        if (type == Material.BARRIER) {
            p.closeInventory();
            return;
        }
        if (type == Material.ENDER_EYE) {
            menus.refresh(inv);
            p.sendMessage("§bAlertas atualizados.");
            return;
        }
        if (type == Material.ARROW) {
            menus.openMain(p);
        }
    }

    private void handleTax(Player p, InventoryClickEvent e, org.bukkit.inventory.Inventory inv, Material type) {
        if (type == Material.BARRIER) {
            p.closeInventory();
            return;
        }
        if (type == Material.ARROW) {
            menus.openMain(p);
            return;
        }
        if (type == Material.ENDER_EYE) {
            menus.refresh(inv);
            p.sendMessage("§bImpostos atualizados.");
            return;
        }

        // Só responde aos cards de imposto
        if (type != Material.LIME_TERRACOTTA && type != Material.RED_TERRACOTTA) return;
        if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;

        String name = e.getCurrentItem().getItemMeta().getDisplayName();
        boolean isTransfer = name.contains("Transfer");
        boolean isWithdraw = name.contains("Saque");

        if (!isTransfer && !isWithdraw) return;

        var taxType = isTransfer
                ? me.scoltbr.scoltEconomys.tax.TaxType.TRANSFER
                : me.scoltbr.scoltEconomys.tax.TaxType.WITHDRAW;

        // Q = toggle
        if (e.getClick() == org.bukkit.event.inventory.ClickType.DROP) {
            menus.toggleTax(p, taxType);
            menus.refresh(inv);
            return;
        }

        double delta = 0.0;
        boolean shift = e.isShiftClick();

        if (e.isLeftClick()) delta = shift ? 0.01 : 0.001;     // +1% ou +0.1%
        else if (e.isRightClick()) delta = shift ? -0.01 : -0.001;

        if (delta != 0.0) {
            menus.adjustTaxRate(p, taxType, delta);
            menus.refresh(inv);
        }
    }

}
