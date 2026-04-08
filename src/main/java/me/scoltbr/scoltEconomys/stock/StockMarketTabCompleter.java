package me.scoltbr.scoltEconomys.stock;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/** Tab-completer para o comando /bolsa. */
public final class StockMarketTabCompleter implements TabCompleter {

    private final StockMarketService stockService;

    private static final List<String> ROOT_SUBS = List.of(
            "info", "comprar", "vender", "carteira", "top", "admin"
    );
    private static final List<String> ADMIN_SUBS = List.of("list", "forcetick", "reset");

    public StockMarketTabCompleter(StockMarketService stockService) {
        this.stockService = stockService;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(ROOT_SUBS);

        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "info", "comprar", "vender", "top" ->
                        completions.addAll(stockService.getStocks().keySet());
                case "admin" ->
                        completions.addAll(ADMIN_SUBS);
            }

        } else if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("admin") && args[1].equalsIgnoreCase("reset")) {
                completions.addAll(stockService.getStocks().keySet());
            } else if (sub.equals("comprar") || sub.equals("vender")) {
                completions.addAll(List.of("1", "10", "100"));
                if (sub.equals("vender")) completions.add("all");
            }
        }

        String partial = args[args.length - 1].toLowerCase(Locale.ROOT);
        return completions.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(partial))
                .sorted()
                .collect(Collectors.toList());
    }
}
