package me.scoltbr.scoltEconomys.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MessageUtils {

    // Prefixo Moderno em Degradê de Verde-Esmeralda para Azul Ciano
    private static final String PREFIX = "<gradient:#00ffa1:#0099ff><b>[Economia]</b></gradient> <gray>»</gray> ";
    private static final String ERROR_PREFIX = "<gradient:#ff3333:#990000><b>[Erro]</b></gradient> <gray>»</gray> <red>";

    private MessageUtils() {}

    public static Component format(String input) {
        return MiniMessage.miniMessage().deserialize(PREFIX + translate(input));
    }
    
    public static Component formatError(String input) {
        return MiniMessage.miniMessage().deserialize(ERROR_PREFIX + translate(input) + "</red>");
    }

    // Apenas parsa string bruta
    public static Component parseRaw(String input) {
        return MiniMessage.miniMessage().deserialize(translate(input));
    }

    private static String translate(String input) {
        if (input == null) return "";
        return input.replace("&0", "<black>")
                .replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>")
                .replace("&5", "<dark_purple>")
                .replace("&6", "<gold>")
                .replace("&7", "<gray>")
                .replace("&8", "<dark_gray>")
                .replace("&9", "<blue>")
                .replace("&a", "<green>")
                .replace("&b", "<aqua>")
                .replace("&c", "<red>")
                .replace("&d", "<light_purple>")
                .replace("&e", "<yellow>")
                .replace("&f", "<white>")
                .replace("&k", "<obfuscated>")
                .replace("&l", "<bold>")
                .replace("&m", "<strikethrough>")
                .replace("&n", "<underlined>")
                .replace("&o", "<italic>")
                .replace("&r", "<reset>");
    }

    // Enviar mensagem padronizada no plugin
    public static void send(CommandSender sender, String message) {
        sender.sendMessage(format(message));
    }

    // Enviar mensagem de erro focada
    public static void sendError(CommandSender sender, String message) {
        sender.sendMessage(formatError(message));
        if (sender instanceof Player p) {
            playError(p);
        }
    }

    // Sons Padrões
    public static void playSuccess(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
    }

    public static void playError(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }
    
    // Action bar genérica
    public static void actionBar(Player player, String message) {
        player.sendActionBar(parseRaw(message));
    }

    // Broadcast global formatado
    public static void broadcast(String message) {
        org.bukkit.Bukkit.broadcast(format(message));
    }
}
