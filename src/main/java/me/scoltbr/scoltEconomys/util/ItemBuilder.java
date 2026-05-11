package me.scoltbr.scoltEconomys.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Utilitário fluente para criação de Itens.
 * Suporta texturas base64 para cabeças e MiniMessage nativo.
 */
public class ItemBuilder {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final ItemStack item;
    private final ItemMeta meta;

    private ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    private ItemBuilder(ItemStack item) {
        this.item = item.clone();
        this.meta = this.item.getItemMeta();
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    public static ItemBuilder of(ItemStack item) {
        return new ItemBuilder(item);
    }

    /**
     * Define o nome do item usando MiniMessage.
     */
    public ItemBuilder name(String miniMessageFormat) {
        if (meta != null && miniMessageFormat != null) {
            meta.displayName(MM.deserialize("<!italic>" + miniMessageFormat));
        }
        return this;
    }

    /**
     * Define a quantidade do item.
     */
    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    /**
     * Define a lore do item usando MiniMessage.
     */
    public ItemBuilder lore(String... lines) {
        if (meta != null) {
            List<Component> lore = new ArrayList<>();
            for (String line : lines) {
                if (line != null) {
                    lore.add(MM.deserialize("<!italic>" + line));
                }
            }
            meta.lore(lore);
        }
        return this;
    }

    /**
     * Define a lore do item a partir de uma lista usando MiniMessage.
     */
    public ItemBuilder lore(List<String> lines) {
        if (meta != null && lines != null) {
            List<Component> lore = new ArrayList<>();
            for (String line : lines) {
                if (line != null) {
                    lore.add(MM.deserialize("<!italic>" + line));
                }
            }
            meta.lore(lore);
        }
        return this;
    }

    /**
     * Adiciona um encantamento brilhante e esconde a tag de encantamento.
     */
    public ItemBuilder glow(boolean glow) {
        if (meta != null && glow) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    /**
     * Adiciona flags no item (esconder atributos, etc).
     */
    public ItemBuilder flags(ItemFlag... flags) {
        if (meta != null) {
            meta.addItemFlags(flags);
        }
        return this;
    }

    /**
     * Aplica uma textura Base64 se o material for PLAYER_HEAD.
     */
    public ItemBuilder texture(String base64) {
        if (item.getType() == Material.PLAYER_HEAD && meta instanceof SkullMeta skullMeta) {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", base64));
            skullMeta.setPlayerProfile(profile);
        }
        return this;
    }

    /**
     * Constrói e retorna o ItemStack final.
     */
    public ItemStack build() {
        if (meta != null) {
            item.setItemMeta(meta);
        }
        return item;
    }
}
