package me.scoltbr.scoltEconomys.command;

import me.scoltbr.scoltEconomys.account.AccountService;
import me.scoltbr.scoltEconomys.util.MoneyFormat;
import me.scoltbr.scoltEconomys.util.MoneyParser;
import me.scoltbr.scoltEconomys.util.Preconditions;
import me.scoltbr.scoltEconomys.util.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class PayCommand implements CommandExecutor {

    private final AccountService accounts;

    public PayCommand(AccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player from)) {
            MessageUtils.sendError(sender, "Apenas jogadores.");
            return true;
        }

        if (args.length < 2) {
            MessageUtils.sendError(from, "Uso: /pay <player> <amount>");
            return true;
        }

        Player to = Bukkit.getPlayerExact(args[0]);
        if (to == null) {
            MessageUtils.sendError(from, "Jogador não encontrado (por enquanto só online).");
            return true;
        }

        if (to.getUniqueId().equals(from.getUniqueId())) {
            MessageUtils.sendError(from, "Você não pode pagar a si mesmo.");
            return true;
        }

        double amount;
        try {
            amount = MoneyParser.parse(args[1]); // fix: era args[2]
        } catch (Exception e) {
            MessageUtils.sendError(from, "Valor inválido.");
            return true;
        }

        try {
            Preconditions.positive(amount, "amount");
        } catch (IllegalArgumentException e) {
            MessageUtils.sendError(from, "O valor precisa ser maior que 0.");
            return true;
        }

        // Garante que as duas contas estão carregadas antes de transferir
        accounts.getOrLoad(from.getUniqueId(), fromAcc -> {
            accounts.getOrLoad(to.getUniqueId(), toAcc -> {

                // Usa transferWallet: respeita impostos, audit e StripedLocks (anti-deadlock)
                var result = accounts.transferWallet(from.getUniqueId(), to.getUniqueId(), amount);

                if (!result.success()) {
                    if ("insufficient-funds".equals(result.reason())) {
                        MessageUtils.sendError(from, "Saldo insuficiente.");
                    } else {
                        MessageUtils.sendError(from, "Não foi possível concluir a transferência.");
                    }
                    return;
                }

                String formattedNet = MoneyFormat.format(result.net());
                MessageUtils.send(from, "<green>Você enviou <white>$ " + formattedNet + "</white> para <aqua>" + to.getName() + "</aqua>.</green>");
                MessageUtils.playSuccess(from);
                MessageUtils.actionBar(from, "<red>-$ " + formattedNet + "</red>");

                if (result.fee() > 0) {
                    MessageUtils.send(from, "<gray>Imposto retido: <white>$ " + MoneyFormat.format(result.fee()) + "</white></gray>");
                }

                MessageUtils.send(to, "<green>Você recebeu <white>$ " + formattedNet + "</white> de <aqua>" + from.getName() + "</aqua>.</green>");
                MessageUtils.playSuccess(to);
                MessageUtils.actionBar(to, "<green>+$ " + formattedNet + "</green>");
            });
        });

        return true;
    }
}