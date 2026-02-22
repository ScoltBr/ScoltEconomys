// src/main/java/me/scoltbr/scoltEconomys/command/EcoCommand.java
package me.scoltbr.scoltEconomys.admin;

import me.scoltbr.scoltEconomys.account.AccountService;
import me.scoltbr.scoltEconomys.account.TreasuryService;
import me.scoltbr.scoltEconomys.audit.TransactionAuditService;
import me.scoltbr.scoltEconomys.stats.AdminStatsService;
import me.scoltbr.scoltEconomys.util.MoneyFormat;
import me.scoltbr.scoltEconomys.util.MoneyParser;
import me.scoltbr.scoltEconomys.util.Preconditions;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

public final class EcoAdminCommand implements CommandExecutor {

    private final Plugin plugin;
    private final AccountService accounts;
    private final TransactionAuditService audit;
    private final TreasuryService treasury;

    // admin modules
    private final AdminStatsService stats;
    private final me.scoltbr.scoltEconomys.alerts.AlertService alerts;
    private final AdminMenuService adminMenus;

    public EcoAdminCommand(Plugin plugin,
                      AccountService accounts,
                      TransactionAuditService audit,
                      TreasuryService treasury,
                      AdminStatsService stats,
                      me.scoltbr.scoltEconomys.alerts.AlertService alerts,
                      AdminMenuService adminMenus) {
        this.plugin = plugin;
        this.accounts = accounts;
        this.audit = audit;
        this.treasury = treasury;
        this.stats = stats;
        this.alerts = alerts;
        this.adminMenus = adminMenus;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        return switch (sub) {
            case "give" -> handleGive(sender, args);
            case "take" -> handleTake(sender, args);
            case "set" -> handleSet(sender, args);

            case "admin" -> handleAdmin(sender);
            case "alerts" -> handleAlerts(sender);
            case "treasury" -> handleTreasury(sender);

            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    // ----------------------------
    // Admin: open GUI
    // ----------------------------
    private boolean handleAdmin(CommandSender sender) {
        if (!sender.hasPermission("scolteconomy.admin")) {
            sender.sendMessage("§cSem permissão.");
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cApenas jogadores podem abrir o painel.");
            return true;
        }

        adminMenus.openMain(p);
        return true;
    }

    private boolean handleAlerts(CommandSender sender) {
        if (!sender.hasPermission("scolteconomy.admin")) {
            sender.sendMessage("§cSem permissão.");
            return true;
        }
        sender.sendMessage("§6§lAlertas Econômicos");
        var list = alerts.activeAlerts();
        if (list.isEmpty()) {
            sender.sendMessage("§aNenhum alerta ativo.");
            return true;
        }
        for (var a : list) {
            sender.sendMessage("§c- §f" + a.message());
        }
        return true;
    }

    private boolean handleTreasury(CommandSender sender) {
        if (!sender.hasPermission("scolteconomy.admin")) {
            sender.sendMessage("§cSem permissão.");
            return true;
        }
        sender.sendMessage("§6Tesouro: §f" + MoneyFormat.format(treasury.balance()));
        return true;
    }

    // ----------------------------
    // Economy admin: give/take/set
    // ----------------------------
    private boolean handleGive(CommandSender sender, String[] args) {
        // /eco give <player> <amount>
        if (!sender.hasPermission("scolteconomy.admin")) {
            sender.sendMessage("§cSem permissão.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§cUso: /eco give <player> <amount>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cJogador não encontrado (por enquanto só online).");
            return true;
        }

        double amount;
        try {
            amount = MoneyParser.parse(args[2]);
        } catch (Exception e) {
            sender.sendMessage("§cValor inválido.");
            return true;
        }

        accounts.getOrLoad(target.getUniqueId(), acc -> {
            accounts.depositWallet(target.getUniqueId(), amount);

            sender.sendMessage("§aAdicionado §f" + MoneyFormat.format(amount) + "§a para §f" + target.getName() + "§a.");
            target.sendMessage("§aVocê recebeu §f" + MoneyFormat.format(amount) + "§a (admin).");

            audit.recordAdminGive(sender.getName(), target.getUniqueId(), amount);
        });

        return true;
    }

    private boolean handleTake(CommandSender sender, String[] args) {
        // /eco take <player> <amount>
        if (!sender.hasPermission("scolteconomy.admin")) {
            sender.sendMessage("§cSem permissão.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§cUso: /eco take <player> <amount>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cJogador não encontrado (por enquanto só online).");
            return true;
        }

        double amount;
        try {
            amount = MoneyParser.parse(args[2]);
        } catch (Exception e) {
            sender.sendMessage("§cValor inválido.");
            return true;
        }

        accounts.getOrLoad(target.getUniqueId(), acc -> {
            boolean ok = accounts.withdrawWallet(target.getUniqueId(), amount);

            if (!ok) {
                sender.sendMessage("§cO jogador não tem saldo suficiente.");
                return;
            }

            sender.sendMessage("§aRemovido §f" + MoneyFormat.format(amount) + "§a de §f" + target.getName() + "§a.");
            target.sendMessage("§cForam removidos §f" + MoneyFormat.format(amount) + "§c (admin).");

            audit.recordAdminTake(sender.getName(), target.getUniqueId(), amount);
        });

        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        // /eco set <player> <amount>
        if (!sender.hasPermission("scolteconomy.admin")) {
            sender.sendMessage("§cSem permissão.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§cUso: /eco set <player> <amount>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cJogador não encontrado (por enquanto só online).");
            return true;
        }

        double amount;
        try {
            amount = MoneyParser.parse(args[2]);
        } catch (Exception e) {
            sender.sendMessage("§cValor inválido.");
            return true;
        }

        accounts.getOrLoad(target.getUniqueId(), acc -> {
            accounts.setWallet(target.getUniqueId(), amount);

            sender.sendMessage("§aWallet de §f" + target.getName() + "§a agora é §f" + MoneyFormat.format(amount) + "§a.");
            target.sendMessage("§eSeu saldo foi ajustado para §f" + MoneyFormat.format(amount) + "§e (admin).");

            audit.recordAdminSet(sender.getName(), target.getUniqueId(), amount);
        });

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§lScoltEconomy");
        sender.sendMessage("§7/eco admin §f- abre o painel");
        sender.sendMessage("§7/eco alerts §f- lista alertas");
        sender.sendMessage("§7/eco treasury §f- ver tesouro");
        sender.sendMessage("§7/eco give <player> <amount>");
        sender.sendMessage("§7/eco take <player> <amount>");
        sender.sendMessage("§7/eco set <player> <amount>");
    }
}
