package me.scoltbr.scoltEconomys.admin;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class AdminMenuHolder implements InventoryHolder {

    private final AdminMenuPage page;

    public AdminMenuHolder(AdminMenuPage page) {
        this.page = page;
    }

    public AdminMenuPage page() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return null; // não usado
    }
}
