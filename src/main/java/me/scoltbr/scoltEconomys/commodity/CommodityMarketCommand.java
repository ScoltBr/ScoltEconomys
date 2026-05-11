package me.scoltbr.scoltEconomys.commodity;

import me.scoltbr.scoltEconomys.util.MessageUtils;
import me.scoltbr.scoltEconomys.util.MoneyFormat;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.*;

/**
 * Comando principal do Mercado de Commodities.
 *
 * <pre>
 *   /commodities                         → abre a GUI (novo)
 *   /commodities ajuda                   → mostra ajuda
 *   /commodities listar                  → lista todas as commodities com preço atual
 *   /commodities preco <commodity>       → mostra preço atual e variação
 *   /commodities vender <commodity> <qty|all>  → vende itens ao mercado
 * </pre>
 */
public final class CommodityMarketCommand implements CommandExecutor {

    private final CommodityMarketService market;
    private final CommodityMenuService menus;

    public CommodityMarketCommand(CommodityMarketService market, CommodityMenuService menus) {
        this.market = market;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            MessageUtils.sendError(sender, "Apenas jogadores podem usar este comando.");
            return true;
        }

        if (args.length == 0) {
            menus.openMenu(player, 1);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "gui", "menu"        -> { menus.openMenu(player, 1); yield true; }
            case "listar", "list"     -> handleList(player);
            case "preco", "price"     -> handlePrice(player, args);
            case "vender", "sell"     -> handleSell(player, args);
            case "comprar", "buy"     -> handleBuy(player, args);
            case "ajuda", "help"      -> { sendHelp(sender); yield true; }
            default -> { menus.openMenu(player, 1); yield true; }
        };
    }

    // -------------------------------------------------------
    // /commodity listar
    // -------------------------------------------------------

    private boolean handleList(Player player) {
        Map<String, Commodity> all = market.getCommodities();
        if (all.isEmpty()) {
            MessageUtils.send(player, "<gray>Nenhuma commodity disponível no momento.</gray>");
            return true;
        }

        MessageUtils.send(player, "<gold><bold>🌍 Mercado Global de Commodities</bold></gold>");
        MessageUtils.send(player, "<dark_gray>─────────────────────────────────────────</dark_gray>");

        for (Commodity c : all.values()) {
            BigDecimal price   = market.currentPrice(c.id());
            BigDecimal initial = c.initialPrice();
            double changePct   = 0;
            if (initial.compareTo(BigDecimal.ZERO) > 0) {
                changePct = (price.doubleValue() - initial.doubleValue()) / initial.doubleValue() * 100;
            }

            String trend   = changePct >= 0 ? "<green>▲" : "<red>▼";
            String pctStr  = String.format("%.1f%%", Math.abs(changePct));
            String sector  = sectorEmoji(c.sector());

            MessageUtils.send(player,
                    sector + " " + c.displayName() +
                    " <gray>→</gray> <white><bold>$ " + MoneyFormat.format(price) + "</bold></white>" +
                    "  " + trend + " " + pctStr + (changePct >= 0 ? "</green>" : "</red>") +
                    "  <dark_gray>[" + c.id() + "]</dark_gray>"
            );
        }

        MessageUtils.send(player, "<dark_gray>─────────────────────────────────────────</dark_gray>");
        MessageUtils.send(player, "<gray>Use <yellow>/commodities vender \\<id\\> \\<qty\\></yellow> ou <yellow>comprar</yellow> para operar.</gray>");
        return true;
    }

    // -------------------------------------------------------
    // /commodity preco <commodity>
    // -------------------------------------------------------

    private boolean handlePrice(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtils.sendError(player, "Uso: /commodities preco \\<id\\>");
            return true;
        }

        String id = args[1].toLowerCase(Locale.ROOT);
        Optional<Commodity> opt = market.getCommodity(id);
        if (opt.isEmpty()) {
            MessageUtils.sendError(player, "Commodity '<yellow>" + id + "</yellow>' não encontrada.");
            return true;
        }

        Commodity c    = opt.get();
        BigDecimal now = market.currentPrice(id);
        BigDecimal ini = c.initialPrice();
        double diff    = now.doubleValue() - ini.doubleValue();
        double pct     = ini.compareTo(BigDecimal.ZERO) > 0 ? diff / ini.doubleValue() * 100 : 0;
        String trend   = pct >= 0 ? "<green>▲" : "<red>▼";
        String pctStr  = String.format("%.2f%%", Math.abs(pct));

        MessageUtils.send(player, "<gold><bold>" + sectorEmoji(c.sector()) + " " + c.displayName() + "</bold></gold>");
        MessageUtils.send(player, " <gray>•</gray> Preço atual:  <white><bold>$ " + MoneyFormat.format(now) + "</bold></white>");
        MessageUtils.send(player, " <gray>•</gray> Preço base:   <gray>$ " + MoneyFormat.format(ini) + "</gray>");
        MessageUtils.send(player, " <gray>•</gray> Variação:     " + trend + " " + pctStr + (pct >= 0 ? "</green>" : "</red>"));
        MessageUtils.send(player, " <gray>•</gray> Taxa corret.: <yellow>" + String.format("%.1f%%", c.brokerageFee().doubleValue() * 100) + "</yellow>");
        MessageUtils.send(player, " <gray>•</gray> Max/transação: <white>" + c.maxPerTransaction() + " un.</white>");
        MessageUtils.send(player, " <gray>•</gray> Material:     <dark_green>" + c.material().name() + "</dark_green>");

        return true;
    }

    // -------------------------------------------------------
    // /commodity vender <commodity> <qty|all>
    // -------------------------------------------------------

    private boolean handleSell(Player player, String[] args) {
        if (args.length < 3) {
            MessageUtils.sendError(player, "Uso: /commodities vender \\<id\\> \\<quantidade|all\\>");
            return true;
        }

        String id = args[1].toLowerCase(Locale.ROOT);
        if (market.getCommodity(id).isEmpty()) {
            MessageUtils.sendError(player, "Commodity '<yellow>" + id + "</yellow>' não encontrada. Use <yellow>/commodities listar</yellow>.");
            return true;
        }

        int qty;
        if (args[2].equalsIgnoreCase("all") || args[2].equalsIgnoreCase("tudo")) {
            qty = -1; // flag: vender tudo (respeitando o limite)
        } else {
            try {
                qty = Integer.parseInt(args[2]);
                if (qty <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                MessageUtils.sendError(player, "Quantidade inválida. Use um número positivo ou <yellow>all</yellow>.");
                return true;
            }
        }

        CommodityTransactionResult result = market.sell(player, id, qty);
        Commodity c = market.getCommodity(id).get();

        if (!result.success()) {
            String msg = switch (result.reason()) {
                case "no-items"           -> "Você não possui <bold>" + c.material().name() + "</bold> no inventário.";
                case "invalid-quantity"   -> "Quantidade inválida.";
                case "account-not-cached" -> "Erro ao acessar sua conta. Reconecte-se.";
                default -> result.reason().startsWith("exceeds-limit-")
                        ? "Limite por transação: <bold>" + c.maxPerTransaction() + " unidades</bold>."
                        : result.reason().startsWith("insufficient-items")
                        ? "Você não possui itens suficientes."
                        : "Erro: " + result.reason();
            };
            MessageUtils.sendError(player, msg);
            return true;
        }

        // Feedback de sucesso
        MessageUtils.send(player,
                "<green>✔ Vendido!</green> <white>" + result.quantity() + "x " + c.displayName() + "</white>");
        MessageUtils.send(player,
                "  <gray>•</gray> Preço unit.: <yellow>$ " + MoneyFormat.format(result.pricePerUnit()) + "</yellow>");
        MessageUtils.send(player,
                "  <gray>•</gray> Taxa cobrada: <red>- $ " + MoneyFormat.format(result.fee()) + "</red>");
        MessageUtils.send(player,
                "  <gray>•</gray> Recebido líquido: <green><bold>+ $ " + MoneyFormat.format(result.total()) + "</bold></green>");

        // Action bar de confirmação rápida
        MessageUtils.actionBar(player,
                "<gradient:#00ffa1:#0099ff>+$ " + MoneyFormat.format(result.total()) + " — " + c.displayName() + "</gradient>");

        MessageUtils.playSuccess(player);

        return true;
    }

    // -------------------------------------------------------
    // /commodity comprar <commodity> <qty>
    // -------------------------------------------------------

    private boolean handleBuy(Player player, String[] args) {
        if (args.length < 3) {
            MessageUtils.sendError(player, "Uso: /commodities comprar \\<id\\> \\<quantidade\\>");
            return true;
        }

        String id = args[1].toLowerCase(Locale.ROOT);
        if (market.getCommodity(id).isEmpty()) {
            MessageUtils.sendError(player, "Commodity '<yellow>" + id + "</yellow>' não encontrada. Use <yellow>/commodities listar</yellow>.");
            return true;
        }

        int qty;
        try {
            qty = Integer.parseInt(args[2]);
            if (qty <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            MessageUtils.sendError(player, "Quantidade inválida. Use um número positivo.");
            return true;
        }

        CommodityTransactionResult result = market.buyPhysical(player, id, qty);
        Commodity c = market.getCommodity(id).get();

        if (!result.success()) {
            String msg = switch (result.reason()) {
                case "invalid-quantity"   -> "Quantidade inválida.";
                case "account-not-cached" -> "Erro ao acessar sua conta. Reconecte-se.";
                case "insufficient-funds" -> "Você não possui dinheiro suficiente.";
                case "no-space"           -> "Você não tem espaço no inventário.";
                default -> result.reason().startsWith("exceeds-limit-")
                        ? "Limite por transação: <bold>" + c.maxPerTransaction() + " unidades</bold>."
                        : "Erro: " + result.reason();
            };
            MessageUtils.sendError(player, msg);
            return true;
        }

        // Feedback de sucesso
        MessageUtils.send(player,
                "<gold>✔ Comprado!</gold> <white>" + result.quantity() + "x " + c.displayName() + "</white>");
        MessageUtils.send(player,
                "  <gray>•</gray> Preço unit.: <yellow>$ " + MoneyFormat.format(result.pricePerUnit()) + "</yellow>");
        MessageUtils.send(player,
                "  <gray>•</gray> Taxa cobrada: <red>- $ " + MoneyFormat.format(result.fee()) + "</red>");
        MessageUtils.send(player,
                "  <gray>•</gray> Total pago: <red><bold>- $ " + MoneyFormat.format(result.total()) + "</bold></red>");

        MessageUtils.actionBar(player,
                "<gradient:#ff3300:#ff9900>-$ " + MoneyFormat.format(result.total()) + " — Comprado " + c.displayName() + "</gradient>");

        MessageUtils.playSuccess(player);

        return true;
    }

    // -------------------------------------------------------
    // Ajuda
    // -------------------------------------------------------

    private void sendHelp(CommandSender sender) {
        MessageUtils.send(sender, "<gold><bold>🌍 Commodities Globais — Ajuda</bold></gold>");
        MessageUtils.send(sender, " <gray>•</gray> <yellow>/commodities menu</yellow>                    <gray>- Abre a interface gráfica</gray>");
        MessageUtils.send(sender, " <gray>•</gray> <yellow>/commodities listar</yellow>                  <gray>- Preços do mercado no chat</gray>");
        MessageUtils.send(sender, " <gray>•</gray> <yellow>/commodities preco \\<id\\></yellow>              <gray>- Detalhes de uma commodity</gray>");
        MessageUtils.send(sender, " <gray>•</gray> <yellow>/commodities vender \\<id\\> \\<qty|all\\></yellow>  <gray>- Vender seus itens por comando</gray>");
        MessageUtils.send(sender, " <gray>•</gray> <yellow>/commodities comprar \\<id\\> \\<qty\\></yellow>      <gray>- Comprar itens do mercado</gray>");
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private static String sectorEmoji(String sector) {
        return switch (sector.toLowerCase(Locale.ROOT)) {
            case "energia" -> "⚡";
            case "metais"  -> "⚙";
            case "raros"   -> "💎";
            case "agricola"-> "🌾";
            default        -> "📦";
        };
    }
}
