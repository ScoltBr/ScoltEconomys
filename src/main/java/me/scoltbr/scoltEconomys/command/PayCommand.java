package me.scoltbr.scoltEconomys.command;

import me.scoltbr.scoltEconomys.account.AccountService;
import me.scoltbr.scoltEconomys.util.MoneyFormat;
import me.scoltbr.scoltEconomys.util.MoneyParser;
import me.scoltbr.scoltEconomys.util.Preconditions;
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
            sender.sendMessage("Apenas jogadores.");
            return true;
        }

        if (args.length < 2) {
            from.sendMessage("§cUso: /pay <player> <amount>");
            return true;
        }

        Player to = Bukkit.getPlayerExact(args[0]);
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
            sender.sendMessage("§cValor inválido.");
            return true;
        }

        try {
            Preconditions.positive(amount, "amount");
        } catch (IllegalArgumentException e) {
            from.sendMessage("§cO valor precisa ser maior que 0.");
            return true;
        }

        // Carrega as duas contas (se já estiverem em cache, é imediato).
        accounts.getOrLoad(from.getUniqueId(), fromAcc -> {
            accounts.getOrLoad(to.getUniqueId(), toAcc -> {

                // ✅ Aqui fica pronto para plugar imposto depois:
                // double fee = taxManager.calculateTransferFee(amount);
                // double finalAmount = amount - fee;
                // Por enquanto: sem taxa.
                double finalAmount = amount;

                boolean ok = accounts.withdrawWallet(from.getUniqueId(), amount);
                if (!ok) {
                    from.sendMessage("§cSaldo insuficiente.");
                    return;
                }

                accounts.depositWallet(to.getUniqueId(), finalAmount);

                from.sendMessage("§aVocê enviou §f" + MoneyFormat.format(finalAmount) + "§a para §f" + to.getName() + "§a.");
                to.sendMessage("§aVocê recebeu §f" + MoneyFormat.format(finalAmount) + "§a de §f" + from.getName() + "§a.");
            });
        });

        return true;
    }
}