// src/main/java/me/scoltbr/scoltEconomys/command/MoneyCommand.java
package me.scoltbr.scoltEconomys.command;

import me.scoltbr.scoltEconomys.account.AccountService;
import me.scoltbr.scoltEconomys.stats.MoneyTopService;
import me.scoltbr.scoltEconomys.util.MoneyFormat;
import me.scoltbr.scoltEconomys.util.MoneyParser;
import me.scoltbr.scoltEconomys.util.Preconditions;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class MoneyCommand implements CommandExecutor {

    private final AccountService accounts;
    private final MoneyTopService topService;


    public MoneyCommand(AccountService accountService,
                        MoneyTopService topService) {
        this.accounts = accountService;
        this.topService = topService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cApenas jogadores.");
            return true;
        }

        // /money
        if (args.length == 0) {
            showBalance(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        return switch (sub) {
            case "enviar" -> handlePay(player, args);
            case "depositar" -> handleDeposit(player, args);
            case "sacar" -> handleWithdraw(player, args);
            case "top" -> moneyTop(sender);
            default -> {
                player.sendMessage("§cSubcomando inválido.");
                sendHelp(player);
                yield true;
            }
        };
    }

    private boolean moneyTop(CommandSender sender) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cApenas jogadores podem usar isso.");
            return true;
        }


        topService.fetchTop10(lines -> {

            player.sendMessage("§6§lTop 10 - Jogadores Mais Ricos");
            player.sendMessage(" ");

            if (lines.isEmpty()) {
                player.sendMessage("§cNenhum dado encontrado.");
                return;
            }

            int rank = 1;

            for (var line : lines) {

                player.sendMessage(
                        "§e#" + rank +
                                " §f" + line.name() +
                                " §7- §a$" +
                                me.scoltbr.scoltEconomys.util.MoneyFormat.format(line.total())
                );

                rank++;
            }
        });

        return true;
    }

    private void showBalance(Player player) {
        accounts.getOrLoad(player.getUniqueId(), acc -> {
            player.sendMessage("§6§lScoltEconomy");
            player.sendMessage("§aCarteira: §f" + MoneyFormat.format(acc.wallet()));
            player.sendMessage("§aBanco: §f" + MoneyFormat.format(acc.bank()));
        });
    }

    private boolean handlePay(Player from, String[] args) {
        // /money pay <player> <amount>
        if (args.length < 3) {
            from.sendMessage("§cUso: /money enviar <player> <quantidade>");
            return true;
        }

        Player to = Bukkit.getPlayerExact(args[1]);
        if (to == null) {
            from.sendMessage("§cJogador não encontrado (por enquanto só online).");
            return true;
        }

        if (to.getUniqueId().equals(from.getUniqueId())) {
            from.sendMessage("§cVocê não pode pagar a si mesmo.");
            return true;
        }

        double amount;
        try {
            amount = MoneyParser.parse(args[2]);
        } catch (Exception e) {
            from.sendMessage("§cValor inválido.");
            return true;
        }

        accounts.getOrLoad(from.getUniqueId(), fromAcc -> {
            accounts.getOrLoad(to.getUniqueId(), toAcc -> {

                var result = accounts.transferWallet(from.getUniqueId(), to.getUniqueId(), amount);

                if (!result.success()) {
                    if ("insufficient-funds".equals(result.reason())) {
                        from.sendMessage("§cSaldo insuficiente.");
                    } else {
                        from.sendMessage("§cNão foi possível concluir a transferência.");
                    }
                    return;
                }

                from.sendMessage("§aVocê enviou §f" + MoneyFormat.format(result.net())
                        + "§a para §f" + to.getName() + "§a.");

                if (result.fee() > 0) {
                    from.sendMessage("§7Imposto: §f" + MoneyFormat.format(result.fee()));
                }

                to.sendMessage("§aVocê recebeu §f" + MoneyFormat.format(result.net())
                        + "§a de §f" + from.getName() + "§a.");
            });
        });

        return true;
    }

    private boolean handleDeposit(Player player, String[] args) {
        // /money deposit <amount>
        if (args.length < 2) {
            player.sendMessage("§cUso: /money depositar <quantidade>");
            return true;
        }

        double amount;
        try {
            amount = MoneyParser.parse(args[1]);
        } catch (Exception e) {
            player.sendMessage("§cValor inválido.");
            return true;
        }

        accounts.getOrLoad(player.getUniqueId(), acc -> {
            var res = accounts.depositToBank(player.getUniqueId(), amount);

            if (!res.success()) {
                switch (res.reason()) {
                    case "insufficient-wallet" -> player.sendMessage("§cVocê não tem saldo suficiente na wallet.");
                    case "bank-limit" -> player.sendMessage("§cSeu banco atingiu o limite máximo.");
                    default -> player.sendMessage("§cNão foi possível depositar.");
                }
                return;
            }

            player.sendMessage("§aVocê depositou §f" + MoneyFormat.format(amount) + "§a no banco.");
        });

        return true;
    }

    private boolean handleWithdraw(Player player, String[] args) {
        // /money withdraw <amount>
        if (args.length < 2) {
            player.sendMessage("§cUso: /money sacar <quantidade>");
            return true;
        }

        double amount;
        try {
            amount = MoneyParser.parse(args[1]);
        } catch (Exception e) {
            player.sendMessage("§cValor inválido.");
            return true;
        }

        accounts.getOrLoad(player.getUniqueId(), acc -> {
            var res = accounts.withdrawFromBank(player.getUniqueId(), amount);

            if (!res.success()) {
                switch (res.reason()) {
                    case "insufficient-bank" -> player.sendMessage("§cVocê não tem saldo suficiente no banco.");
                    default -> player.sendMessage("§cNão foi possível sacar.");
                }
                return;
            }

            player.sendMessage("§aVocê sacou §f" + MoneyFormat.format(res.net()) + "§a do banco.");
            if (res.fee() > 0) {
                player.sendMessage("§7Imposto de saque: §f" + MoneyFormat.format(res.fee()));
            }
        });

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§7Use:");
        player.sendMessage("§f/money §7(ver saldo)");
        player.sendMessage("§f/money enviar <player> <quantidade>");
        player.sendMessage("§f/money depositar <quantidade>");
        player.sendMessage("§f/money sacar <quantidade>");
        player.sendMessage("§f/money top");

    }
}
