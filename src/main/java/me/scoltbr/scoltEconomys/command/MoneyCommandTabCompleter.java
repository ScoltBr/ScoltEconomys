package me.scoltbr.scoltEconomys.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class MoneyCommandTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("enviar", "depositar", "sacar", "top", "admin", "event");
    private static final List<String> EVENT_ACTIONS = Arrays.asList("info", "list", "start", "stop");

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(sub))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("enviar")) {
                String partial = args[1].toLowerCase(Locale.ROOT);
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(partial))
                        .collect(Collectors.toList());
            }
            if (sub.equals("event")) {
                String partial = args[1].toLowerCase(Locale.ROOT);
                return EVENT_ACTIONS.stream()
                        .filter(s -> s.startsWith(partial))
                        .collect(Collectors.toList());
            }
        }
        
        if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String action = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("event") && action.equals("start")) {
                // Infelizmente o TabCompleter não tem acesso ao EventManager fácil aqui sem injeção
                // Mas podemos sugerir os IDs fixos se quisermos, ou deixar vazio.
                return new ArrayList<>(); 
            }
        }

        return new ArrayList<>();
    }
}
