package me.scoltbr.scoltEconomys.command;

import me.scoltbr.scoltEconomys.account.AccountService;
import me.scoltbr.scoltEconomys.admin.AdminMenuService;
import me.scoltbr.scoltEconomys.stats.MoneyTopService;
import me.scoltbr.scoltEconomys.util.MoneyFormat;
import me.scoltbr.scoltEconomys.util.MoneyParser;
import me.scoltbr.scoltEconomys.util.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class MoneyCommand implements CommandExecutor {

    private final AccountService accounts;
    private final MoneyTopService topService;
    private final AdminMenuService adminMenuService;
    private final me.scoltbr.scoltEconomys.event.EventManager eventManager;

    public MoneyCommand(AccountService accountService,
            MoneyTopService topService,
            AdminMenuService adminMenuService,
            me.scoltbr.scoltEconomys.event.EventManager eventManager) {
        this.accounts = accountService;
        this.topService = topService;
        this.adminMenuService = adminMenuService;
        this.eventManager = eventManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            MessageUtils.sendError(sender, "Apenas jogadores.");
            return true;
        }

        // /money
        if (args.length == 0) {
            showBalance(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        return switch (sub) {
            case "enviar"    -> handlePay(player, args);
            case "depositar" -> handleDeposit(player, args);
            case "sacar"     -> handleWithdraw(player, args);
            case "top"       -> moneyTop(sender);
            case "admin"     -> handleAdmin(player);
            case "event"     -> handleEvent(player, args);
            default -> {
                MessageUtils.sendError(player, "Subcomando inválido.");
                sendHelp(player);
                yield true;
            }
        };
    }

    // ----------------------------
    // Admin GUI via /money admin
    // ----------------------------
    private boolean handleAdmin(Player player) {
        if (!player.hasPermission("scolteconomy.admin")) {
            MessageUtils.sendError(player, "Você não tem permissão para isso.");
            return true;
        }
        adminMenuService.openMain(player);
        return true;
    }

    private boolean moneyTop(CommandSender sender) {

        if (!(sender instanceof Player)) {
            MessageUtils.sendError(sender, "Apenas jogadores podem usar isso.");
            return true;
        }

        topService.fetchTop10(lines -> {
            MessageUtils.send(sender, "<gold><b>TOP 10 - JOGADORES MAIS RICOS</b></gold>");
            sender.sendMessage(" "); // break line sem prefixo

            if (lines.isEmpty()) {
                MessageUtils.sendError(sender, "Nenhum dado encontrado no momento.");
                return;
            }

            int rank = 1;
            for (var line : lines) {
                MessageUtils.send(sender,
                        "<yellow>#" + rank +
                                " <white>" + line.name() +
                                " <gray>| <green>$ <bold>" +
                                MoneyFormat.format(line.total()) + "</bold>");
                rank++;
            }
        });

        return true;
    }

    private void showBalance(Player player) {
        accounts.getOrLoad(player.getUniqueId(), acc -> {
            MessageUtils.send(player,
                    "<white>Carteira: <green>$ <bold>" + MoneyFormat.format(acc.wallet()) + "</bold></green>");
            MessageUtils.send(player,
                    "<white>Banco: <aqua>$ <bold>" + MoneyFormat.format(acc.bank()) + "</bold></aqua>");
        });
    }

    private boolean handlePay(Player from, String[] args) {
        if (args.length < 3) {
            MessageUtils.sendError(from, "Uso incorreto. Utilize o menu ou /money enviar <jogador> <valor>");
            return true;
        }

        Player to = Bukkit.getPlayerExact(args[1]);
        if (to == null) {
            MessageUtils.sendError(from, "Jogador não encontrado.");
            return true;
        }

        if (to.getUniqueId().equals(from.getUniqueId())) {
            MessageUtils.sendError(from, "Você não pode enviar dinheiro para si mesmo.");
            return true;
        }

        double amount;
        try {
            amount = MoneyParser.parse(args[2]);
        } catch (Exception e) {
            MessageUtils.sendError(from, "Valor inválido inserido.");
            return true;
        }

        accounts.getOrLoad(from.getUniqueId(), fromAcc -> {
            accounts.getOrLoad(to.getUniqueId(), toAcc -> {

                var result = accounts.transferWallet(from.getUniqueId(), to.getUniqueId(), amount);

                if (!result.success()) {
                    if ("insufficient-funds".equals(result.reason())) {
                        MessageUtils.sendError(from, "Saldo insuficiente.");
                    } else {
                        MessageUtils.sendError(from, "A transação falhou internamente.");
                    }
                    return;
                }

                String formattedNet = MoneyFormat.format(result.net());
                MessageUtils.send(from, "<green>Você enviou <white>$ " + formattedNet + "</white> para <aqua>"
                        + to.getName() + "</aqua>.</green>");
                MessageUtils.playSuccess(from);
                MessageUtils.actionBar(from, "<red>-$ " + formattedNet + "</red>");

                if (result.fee() > 0) {
                    MessageUtils.send(from,
                            "<gray>Imposto retido: <white>$ " + MoneyFormat.format(result.fee()) + "</white></gray>");
                }

                MessageUtils.send(to, "<green>Você recebeu <white>$ " + formattedNet + "</white> de <aqua>"
                        + from.getName() + "</aqua>.</green>");
                MessageUtils.playSuccess(to);
                MessageUtils.actionBar(to, "<green>+$ " + formattedNet + "</green>");
            });
        });

        return true;
    }

    private boolean handleDeposit(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtils.sendError(player, "Uso: /money depositar <quantidade>");
            return true;
        }

        double amount;
        try {
            amount = MoneyParser.parse(args[1]);
        } catch (Exception e) {
            MessageUtils.sendError(player, "Valor inválido.");
            return true;
        }

        accounts.getOrLoad(player.getUniqueId(), acc -> {
            var res = accounts.depositToBank(player.getUniqueId(), amount);

            if (!res.success()) {
                switch (res.reason()) {
                    case "insufficient-wallet" ->
                        MessageUtils.sendError(player, "Você não tem dinheiro na carteira suficiente.");
                    case "bank-limit" -> MessageUtils.sendError(player, "Sua conta atingiu o saldo máximo.");
                    default -> MessageUtils.sendError(player, "Não foi possível depositar no banco.");
                }
                return;
            }

            MessageUtils.send(player,
                    "<green>Você depositou <white>$ " + MoneyFormat.format(amount) + "</white> no banco.</green>");
            MessageUtils.playSuccess(player);
        });

        return true;
    }

    private boolean handleWithdraw(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtils.sendError(player, "Uso: /money sacar <valor>");
            return true;
        }

        double amount;
        try {
            amount = MoneyParser.parse(args[1]);
        } catch (Exception e) {
            MessageUtils.sendError(player, "Valor inválido inserido.");
            return true;
        }

        accounts.getOrLoad(player.getUniqueId(), acc -> {
            var res = accounts.withdrawFromBank(player.getUniqueId(), amount);

            if (!res.success()) {
                switch (res.reason()) {
                    case "insufficient-bank" -> MessageUtils.sendError(player, "Você não possui fundos no banco.");
                    default -> MessageUtils.sendError(player, "Falha ao sacar. Entre em contato com a equipe.");
                }
                return;
            }

            MessageUtils.send(player,
                    "<green>Você sacou <white>$ " + MoneyFormat.format(res.net()) + "</white> do seu banco.</green>");
            MessageUtils.playSuccess(player);

            if (res.fee() > 0) {
                MessageUtils.send(player,
                        "<gray>Imposto de Renda: <white>$ " + MoneyFormat.format(res.fee()) + "</white></gray>");
            }
        });

        return true;
    }

    private boolean handleEvent(Player player, String[] args) {
        if (!player.hasPermission("scolteconomy.admin")) {
            MessageUtils.sendError(player, "Você não tem permissão para gerenciar eventos.");
            return true;
        }

        if (args.length < 2) {
            MessageUtils.send(player, "<gold>Gerenciamento de Eventos:</gold>");
            MessageUtils.send(player, " <yellow>/money event info</yellow> - Status atual");
            MessageUtils.send(player, " <yellow>/money event list</yellow> - Lista eventos disponíveis");
            MessageUtils.send(player, " <yellow>/money event start <id></yellow> - Inicia um evento");
            MessageUtils.send(player, " <yellow>/money event stop</yellow> - Para o evento atual");
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "info" -> {
                var active = eventManager.getActiveEvent();
                if (active.isEmpty()) {
                    MessageUtils.send(player, "<gray>Nenhum evento econômico ativo no momento.</gray>");
                } else {
                    var e = active.get();
                    MessageUtils.send(player, "<gold><bold>EVENTO ATIVO:</bold></gold> " + e.displayName());
                    MessageUtils.send(player, " <gray>•</gray> Multiplicador Juros: <yellow>" + e.interestMultiplier() + "x</yellow>");
                    MessageUtils.send(player, " <gray>•</gray> Multiplicador Taxas: <yellow>" + e.taxMultiplier() + "x</yellow>");
                    MessageUtils.send(player, " <gray>•</gray> Tempo restante: <aqua>" + (eventManager.getRemainingSeconds() / 60) + " min</aqua>");
                }
            }
            case "list" -> {
                MessageUtils.send(player, "<gold>Eventos configurados:</gold>");
                eventManager.getDefinitions().forEach((id, e) -> {
                    MessageUtils.send(player, " <yellow>• " + id + "</yellow> <gray>(" + e.durationSeconds() / 60 + " min)</gray>");
                });
            }
            case "start" -> {
                if (args.length < 3) {
                    MessageUtils.sendError(player, "Uso: /money event start <id>");
                    return true;
                }
                String id = args[2];
                if (eventManager.startEvent(id)) {
                    MessageUtils.playSuccess(player);
                } else {
                    MessageUtils.sendError(player, "Evento '" + id + "' não encontrado no config.yml.");
                }
            }
            case "stop" -> {
                if (eventManager.getActiveEvent().isEmpty()) {
                    MessageUtils.sendError(player, "Não há nenhum evento ativo.");
                } else {
                    eventManager.stopEvent();
                    MessageUtils.playSuccess(player);
                    MessageUtils.send(player, "<green>Evento encerrado com sucesso.</green>");
                }
            }
            default -> MessageUtils.sendError(player, "Ação desconhecida.");
        }

        return true;
    }

    private void sendHelp(Player player) {
        MessageUtils.send(player, "<gray>Comandos Básicos de Economia:</gray>");
        player.sendMessage(MessageUtils.parseRaw(
                " <gray>•</gray> <hover:show_text:'<gray>Ver meu próprio saldo'><click:run_command:'/money'><aqua>/money</aqua></click></hover>"));
        player.sendMessage(MessageUtils.parseRaw(
                " <gray>•</gray> <hover:show_text:'<gray>Clique para enviar a um amigo'><click:suggest_command:'/money enviar '><aqua>/money enviar</aqua> <white><jog><val></white></click></hover>"));
        player.sendMessage(MessageUtils.parseRaw(
                " <gray>•</gray> <hover:show_text:'<gray>Clique para guardar no banco'><click:suggest_command:'/money depositar '><aqua>/money depositar</aqua> <white><val></white></click></hover>"));
        player.sendMessage(MessageUtils.parseRaw(
                " <gray>•</gray> <hover:show_text:'<gray>Clique para retirar do banco'><click:suggest_command:'/money sacar '><aqua>/money sacar</aqua> <white><val></white></click></hover>"));
        player.sendMessage(MessageUtils.parseRaw(
                " <gray>•</gray> <hover:show_text:'<gray>Ver o ranking bilionário'><click:run_command:'/money top'><aqua>/money top</aqua></click></hover>"));
        
        if (player.hasPermission("scolteconomy.admin")) {
            player.sendMessage(MessageUtils.parseRaw(
                " <gray>•</gray> <hover:show_text:'<gray>Gerenciar eventos'><click:suggest_command:'/money event '><gold>/money event</gold></click></hover>"));
        }
    }
}
