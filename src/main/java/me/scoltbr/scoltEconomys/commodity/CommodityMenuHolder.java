package me.scoltbr.scoltEconomys.commodity;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public record CommodityMenuHolder(int page) implements InventoryHolder {
    @Override
    public Inventory getInventory() {
        return null;
    }
}
