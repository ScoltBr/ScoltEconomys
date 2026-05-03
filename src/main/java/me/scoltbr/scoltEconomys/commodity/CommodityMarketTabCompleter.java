package me.scoltbr.scoltEconomys.commodity;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;
import java.util.stream.Collectors;

/** Tab-completer para /commodities. */
public final class CommodityMarketTabCompleter implements TabCompleter {

    private final CommodityMarketService market;

    private static final List<String> ROOT_SUBS = List.of("listar", "preco", "vender");

    public CommodityMarketTabCompleter(CommodityMarketService market) {
        this.market = market;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return filter(ROOT_SUBS, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2 && (sub.equals("preco") || sub.equals("price")
                || sub.equals("vender") || sub.equals("sell"))) {
            return filter(new ArrayList<>(market.getCommodities().keySet()), args[1]);
        }

        if (args.length == 3 && (sub.equals("vender") || sub.equals("sell"))) {
            return List.of("1", "10", "64", "all");
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> candidates, String partial) {
        String low = partial.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(s -> s.startsWith(low))
                .collect(Collectors.toList());
    }
}
