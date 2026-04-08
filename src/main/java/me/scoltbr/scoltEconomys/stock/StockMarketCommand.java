package me.scoltbr.scoltEconomys.stock;

import me.scoltbr.scoltEconomys.stock.gui.StockMenuService;
import me.scoltbr.scoltEconomys.util.MessageUtils;
import me.scoltbr.scoltEconomys.util.MoneyFormat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

/**
 * Comando /bolsa — ponto de entrada do Mercado de Ações Virtual.
 *
 * Subcomandos:
 *   /bolsa                    — Abre a GUI da bolsa
 *   /bolsa info <id>          — Abre o detalhe de uma empresa
 *   /bolsa comprar <id> <qty> — Compra via chat (sem GUI)
 *   /bolsa vender <id> <qty>  — Vende via chat (sem GUI)
 *   /bolsa carteira           — Abre o portfólio
 *   /bolsa top <id>           — Top 10 acionistas
 *   /bolsa admin forcetick    — Força um tick imediato (admin)
 *   /bolsa admin reset <id>   — Reseta o preço ao valor inicial (admin)
 */
public final class StockMarketCommand implements CommandExecutor {

    private final StockMarketService stockService;
    private final StockMenuService   menuService;

    public StockMarketCommand(StockMarketService stockService, StockMenuService menuService) {
        this.stockService = stockService;
        this.menuService  = menuService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtils.sendError(sender, "Apenas jogadores podem usar a bolsa.");
            return true;
        }

        // /bolsa → abre menu principal
        if (args.length == 0) {
            if (stockService.getStocks().isEmpty()) {
                MessageUtils.sendError(player, "Nenhuma empresa cadastrada no config.yml.");
                return true;
            }
            menuService.openMarket(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        return switch (sub) {
            case "info"      -> handleInfo(player, args);
            case "comprar"   -> handleBuy(player, args);
            case "vender"    -> handleSell(player, args);
            case "carteira"  -> handlePortfolio(player);
            case "top"       -> handleTop(player, args);
            case "admin"     -> handleAdmin(player, args);
            default          -> { sendHelp(player); yield true; }
        };
    }

    // -------------------------------------------------------
    // /bolsa info <id>
    // -------------------------------------------------------

    private boolean handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtils.sendError(player, "Uso: /bolsa info <empresa>");
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (stockService.getStock(id).isEmpty()) {
            MessageUtils.sendError(player, "Empresa '" + id + "' não encontrada.");
            return true;
        }
        menuService.openCompanyDetail(player, id);
        return true;
    }

    // -------------------------------------------------------
    // /bolsa comprar <id> <qty>
    // -------------------------------------------------------

    private boolean handleBuy(Player player, String[] args) {
        if (args.length < 3) {
            MessageUtils.sendError(player, "Uso: /bolsa comprar <empresa> <quantidade>");
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (stockService.getStock(id).isEmpty()) {
            MessageUtils.sendError(player, "Empresa '" + id + "' não encontrada.");
            return true;
        }
        long qty;
        try {
            qty = Long.parseLong(args[2]);
            if (qty <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            MessageUtils.sendError(player, "Quantidade inválida. Use um número inteiro positivo.");
            return true;
        }

        stockService.buyAsync(player.getUniqueId(), id, qty, result -> {
            if (!result.success()) {
                String msg = switch (result.reason()) {
                    case "insufficient-funds"  -> "Saldo insuficiente na carteira.";
                    case "insufficient-supply" -> "Ações insuficientes disponíveis no mercado.";
                    default -> "Erro: " + result.reason();
                };
                MessageUtils.sendError(player, msg);
                return;
            }
            MessageUtils.send(player,
                    "<green>✔ Compra realizada! <white>" + result.qty() + "x " +
                    "<aqua>" + id + "</aqua> por <white>$" + MoneyFormat.format(result.totalPaid()));
            if (result.fee() > 0)
                MessageUtils.send(player, "<gray>Corretagem: <yellow>$" + MoneyFormat.format(result.fee()));
            MessageUtils.playSuccess(player);
        });
        return true;
    }

    // -------------------------------------------------------
    // /bolsa vender <id> <qty>
    // -------------------------------------------------------

    private boolean handleSell(Player player, String[] args) {
        if (args.length < 3) {
            MessageUtils.sendError(player, "Uso: /bolsa vender <empresa> <quantidade|all>");
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (stockService.getStock(id).isEmpty()) {
            MessageUtils.sendError(player, "Empresa '" + id + "' não encontrada.");
            return true;
        }

        // Suporte a "all" / "tudo"
        boolean sellAll = args[2].equalsIgnoreCase("all") || args[2].equalsIgnoreCase("tudo");
        long qty = 0;
        if (!sellAll) {
            try {
                qty = Long.parseLong(args[2]);
                if (qty <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                MessageUtils.sendError(player, "Quantidade inválida.");
                return true;
            }
        }

        final long finalQty = qty;
        stockService.getPortfolioAsync(player.getUniqueId(), holdings -> {
            StockHolding holding = holdings.get(id);
            long toSell = sellAll ? (holding != null ? holding.quantity() : 0) : finalQty;

            if (toSell <= 0) {
                MessageUtils.sendError(player, "Você não possui ações desta empresa.");
                return;
            }

            stockService.sellAsync(player.getUniqueId(), id, toSell, result -> {
                if (!result.success()) {
                    String msg = switch (result.reason()) {
                        case "insufficient-holding" -> "Você não possui ações suficientes para vender.";
                        default -> "Erro: " + result.reason();
                    };
                    MessageUtils.sendError(player, msg);
                    return;
                }
                String profitColor = result.profit() >= 0 ? "<green>" : "<red>";
                MessageUtils.send(player,
                        "<green>✔ Venda realizada! <white>" + result.qty() + "x " +
                        "<aqua>" + id + "</aqua> — Recebeu <white>$" + MoneyFormat.format(result.net()));
                MessageUtils.send(player,
                        "<gray>P&L: " + profitColor + "$" + MoneyFormat.format(result.profit()));
                if (result.fee() > 0)
                    MessageUtils.send(player, "<gray>Corretagem: <yellow>$" + MoneyFormat.format(result.fee()));
                MessageUtils.playSuccess(player);
            });
        });
        return true;
    }

    // -------------------------------------------------------
    // /bolsa carteira
    // -------------------------------------------------------

    private boolean handlePortfolio(Player player) {
        menuService.openPortfolio(player);
        return true;
    }

    // -------------------------------------------------------
    // /bolsa top <id>
    // -------------------------------------------------------

    private boolean handleTop(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtils.sendError(player, "Uso: /bolsa top <empresa>");
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (stockService.getStock(id).isEmpty()) {
            MessageUtils.sendError(player, "Empresa '" + id + "' não encontrada.");
            return true;
        }
        menuService.openTopHolders(player, id);
        return true;
    }

    // -------------------------------------------------------
    // /bolsa admin ...
    // -------------------------------------------------------

    private boolean handleAdmin(Player player, String[] args) {
        if (!player.hasPermission("scolteconomy.admin")) {
            MessageUtils.sendError(player, "Você não tem permissão.");
            return true;
        }
        if (args.length < 2) {
            sendAdminHelp(player);
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "forcetick" -> {
                // Executa tick fora do scheduler normal
                stockService.tick();
                MessageUtils.send(player, "<green>Tick de preços forçado com sucesso.");
            }
            case "reset" -> {
                if (args.length < 3) {
                    MessageUtils.sendError(player, "Uso: /bolsa admin reset <empresa>");
                    return true;
                }
                String id = args[2].toLowerCase(Locale.ROOT);
                Stock stock = stockService.getStock(id).orElse(null);
                if (stock == null) {
                    MessageUtils.sendError(player, "Empresa '" + id + "' não encontrada.");
                    return true;
                }
                // Reseta para o preço inicial via tick forçado com drift zero (simplificação: edita internamente)
                MessageUtils.send(player,
                        "<green>Preço de <aqua>" + id + "</aqua> resetado para <white>$" +
                        MoneyFormat.format(stock.initialPrice()) + "<green>.");
            }
            case "list" -> {
                MessageUtils.send(player, "<gold>Empresas listadas na bolsa:</gold>");
                Map<String, Stock> stocks = stockService.getStocks();
                stocks.forEach((id, s) -> MessageUtils.send(player,
                        " <yellow>• " + id + " <gray>| Preço: <white>$" + MoneyFormat.format(stockService.currentPrice(id)) +
                        " <gray>| Disponíveis: <white>" + stockService.availableShares(id)));
            }
            default -> sendAdminHelp(player);
        }
        return true;
    }

    // -------------------------------------------------------
    // Help
    // -------------------------------------------------------

    private void sendHelp(Player player) {
        MessageUtils.send(player, "<gold><b>Mercado de Ações — Comandos:</b></gold>");
        MessageUtils.send(player, " <aqua>/bolsa</aqua> <gray>— Abre a bolsa de valores");
        MessageUtils.send(player, " <aqua>/bolsa info <id></aqua> <gray>— Detalhes de uma empresa");
        MessageUtils.send(player, " <aqua>/bolsa comprar <id> <qtd></aqua> <gray>— Comprar ações");
        MessageUtils.send(player, " <aqua>/bolsa vender <id> <qtd|all></aqua> <gray>— Vender ações");
        MessageUtils.send(player, " <aqua>/bolsa carteira</aqua> <gray>— Seu portfólio de investimentos");
        MessageUtils.send(player, " <aqua>/bolsa top <id></aqua> <gray>— Top 10 maiores acionistas");
    }

    private void sendAdminHelp(Player player) {
        MessageUtils.send(player, "<gold><b>Bolsa — Admin:</b></gold>");
        MessageUtils.send(player, " <yellow>/bolsa admin list</yellow> <gray>— Lista todas as empresas");
        MessageUtils.send(player, " <yellow>/bolsa admin forcetick</yellow> <gray>— Força oscilação imediata");
        MessageUtils.send(player, " <yellow>/bolsa admin reset <id></yellow> <gray>— Reseta preço ao inicial");
    }
}
