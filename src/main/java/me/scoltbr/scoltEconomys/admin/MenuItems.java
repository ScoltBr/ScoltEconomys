package me.scoltbr.scoltEconomys.admin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class MenuItems {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private MenuItems() {}

    public static ItemStack item(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(LEGACY.deserialize(name));
            if (lore != null && lore.length > 0) {
                List<Component> loreComponents = Arrays.stream(lore)
                        .map(LEGACY::deserialize)
                        .collect(Collectors.toList());
                meta.lore(loreComponents);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    public static void fillBorder(Inventory inv, Material mat, String name) {
        ItemStack glass = item(mat, name);

        int size = inv.getSize();
        int rows = size / 9;

        // top
        for (int i = 0; i < 9; i++) inv.setItem(i, glass);
        // bottom
        for (int i = (rows - 1) * 9; i < rows * 9; i++) inv.setItem(i, glass);
        // sides
        for (int r = 1; r < rows - 1; r++) {
            inv.setItem(r * 9, glass);
            inv.setItem(r * 9 + 8, glass);
        }
    }
}
