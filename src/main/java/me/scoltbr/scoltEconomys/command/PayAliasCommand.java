package me.scoltbr.scoltEconomys.command;

import me.scoltbr.scoltEconomys.util.MoneyParser;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class PayAliasCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length < 2) {
            sender.sendMessage("§cUso: /pay <player> <quantidade>");
            return true;
        }

        String target = args[0];
        double amount;
        try {
            amount = MoneyParser.parse(args[1]);
        } catch (Exception e) {
            sender.sendMessage("§cValor inválido.");
            return true;
        }

        Bukkit.dispatchCommand(sender, "money enviar " + target + " " + amount);
        return true;
    }
}