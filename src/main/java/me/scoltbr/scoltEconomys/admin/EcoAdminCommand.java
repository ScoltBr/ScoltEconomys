package me.scoltbr.scoltEconomys.admin;

import me.scoltbr.scoltEconomys.account.AccountService;
import me.scoltbr.scoltEconomys.account.TreasuryService;
import me.scoltbr.scoltEconomys.audit.TransactionAuditService;
import me.scoltbr.scoltEconomys.stats.AdminStatsService;
import me.scoltbr.scoltEconomys.util.MessageUtils;
import me.scoltbr.scoltEconomys.util.MoneyFormat;
import me.scoltbr.scoltEconomys.util.MoneyParser;
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
            case "balance" -> handleBalance(sender, args);

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
            MessageUtils.sendError(sender, "Sem permissão.");
            return true;
        }
        if (!(sender instanceof Player p)) {
            MessageUtils.sendError(sender, "Apenas jogadores podem abrir o painel.");
            return true;
        }

        adminMenus.openMain(p);
        return true;
    }

    private boolean handleAlerts(CommandSender sender) {
        if (!sender.hasPermission("scolteconomy.admin")) {
            MessageUtils.sendError(sender, "Sem permissão.");
            return true;
        }
        MessageUtils.send(sender, "<gold><bold>Alertas Econômicos</bold></gold>");
        var list = alerts.activeAlerts();
        if (list.isEmpty()) {
            MessageUtils.send(sender, "<green>Nenhum alerta ativo.</green>");
            return true;
        }
        for (var a : list) {
            MessageUtils.send(sender, "<red>⚠ </red><white>" + a.message() + "</white>");
        }
        return true;
    }

    private boolean handleTreasury(CommandSender sender) {
        if (!sender.hasPermission("scolteconomy.admin")) {
            MessageUtils.sendError(sender, "Sem permissão.");
            return true;
        }
        MessageUtils.send(sender, "<gold>Tesouro do Servidor:</gold> <white>$ <bold>" + MoneyFormat.format(treasury.balance()) + "</bold></white>");
        return true;
    }

    private boolean handleBalance(CommandSender sender, String[] args) {
        if (!sender.hasPermission("scolteconomy.admin")) {
            MessageUtils.sendError(sender, "Sem permissão.");
            return true;
        }
        if (args.length < 2) {
            MessageUtils.sendError(sender, "Uso: /eco balance \\<player\\>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            MessageUtils.sendError(sender, "Jogador não encontrado ou offline.");
            return true;
        }

        accounts.getOrLoad(target.getUniqueId(), acc -> {
            MessageUtils.send(sender, "<gold><bold>Saldo de</bold></gold> <white>" + target.getName() + "</white>");
            MessageUtils.send(sender, " <gray>•</gray> Carteira: <green>$ <bold>" + MoneyFormat.format(acc.wallet()) + "</bold></green>");
            MessageUtils.send(sender, " <gray>•</gray> Banco: <aqua>$ <bold>" + MoneyFormat.format(acc.bank()) + "</bold></aqua>");
            MessageUtils.send(sender, " <gray>•</gray> Total: <yellow>$ <bold>" + MoneyFormat.format(acc.wallet().add(acc.bank())) + "</bold></yellow>");
        });

        return true;
    }

    // ----------------------------
    // Economy admin: give/take/set
    // ----------------------------
    private boolean handleGive(CommandSender sender, String[] args) {
        // /eco give <player> <amount>
        if (!sender.hasPermission("scolteconomy.admin")) {
            MessageUtils.sendError(sender, "Sem permissão.");
            return true;
        }
        if (args.length < 3) {
            MessageUtils.sendError(sender, "Uso: /eco give \\<player\\> \\<amount\\>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            MessageUtils.sendError(sender, "Jogador não encontrado (apenas online).");
            return true;
        }

        java.math.BigDecimal amount;
        try {
            amount = MoneyParser.parse(args[2]);
        } catch (Exception e) {
            MessageUtils.sendError(sender, "Valor inválido.");
            return true;
        }

        accounts.getOrLoad(target.getUniqueId(), acc -> {
            accounts.depositWallet(target.getUniqueId(), amount);

            MessageUtils.send(sender, "<green>✓ Adicionado <white>$ " + MoneyFormat.format(amount) + "</white> para <aqua>" + target.getName() + "</aqua>.</green>");
            MessageUtils.send(target, "<green>✓ Você recebeu <white>$ " + MoneyFormat.format(amount) + "</white> <gray>(admin)</gray>.</green>");
            MessageUtils.playSuccess(target);

            audit.recordAdminGive(sender.getName(), target.getUniqueId(), amount);
        });

        return true;
    }

    private boolean handleTake(CommandSender sender, String[] args) {
        // /eco take <player> <amount>
        if (!sender.hasPermission("scolteconomy.admin")) {
            MessageUtils.sendError(sender, "Sem permissão.");
            return true;
        }
        if (args.length < 3) {
            MessageUtils.sendError(sender, "Uso: /eco take \\<player\\> \\<amount\\>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            MessageUtils.sendError(sender, "Jogador não encontrado (apenas online).");
            return true;
        }

        java.math.BigDecimal amount;
        try {
            amount = MoneyParser.parse(args[2]);
        } catch (Exception e) {
            MessageUtils.sendError(sender, "Valor inválido.");
            return true;
        }

        accounts.getOrLoad(target.getUniqueId(), acc -> {
            boolean ok = accounts.withdrawWallet(target.getUniqueId(), amount);

            if (!ok) {
                MessageUtils.sendError(sender, "O jogador não tem saldo suficiente.");
                return;
            }

            MessageUtils.send(sender, "<green>✓ Removido <white>$ " + MoneyFormat.format(amount) + "</white> de <aqua>" + target.getName() + "</aqua>.</green>");
            MessageUtils.send(target, "<red>✗ Foram removidos <white>$ " + MoneyFormat.format(amount) + "</white> <gray>(admin)</gray>.</red>");

            audit.recordAdminTake(sender.getName(), target.getUniqueId(), amount);
        });

        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        // /eco set <player> <amount>
        if (!sender.hasPermission("scolteconomy.admin")) {
            MessageUtils.sendError(sender, "Sem permissão.");
            return true;
        }
        if (args.length < 3) {
            MessageUtils.sendError(sender, "Uso: /eco set \\<player\\> \\<amount\\>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            MessageUtils.sendError(sender, "Jogador não encontrado (apenas online).");
            return true;
        }

        java.math.BigDecimal amount;
        try {
            amount = MoneyParser.parse(args[2]);
        } catch (Exception e) {
            MessageUtils.sendError(sender, "Valor inválido.");
            return true;
        }

        accounts.getOrLoad(target.getUniqueId(), acc -> {
            accounts.setWallet(target.getUniqueId(), amount);

            MessageUtils.send(sender, "<green>✓ Carteira de <aqua>" + target.getName() + "</aqua> definida para <white>$ " + MoneyFormat.format(amount) + "</white>.</green>");
            MessageUtils.send(target, "<yellow>Seu saldo foi ajustado para <white>$ " + MoneyFormat.format(amount) + "</white> <gray>(admin)</gray>.</yellow>");

            audit.recordAdminSet(sender.getName(), target.getUniqueId(), amount);
        });

        return true;
    }

    private void sendHelp(CommandSender sender) {
        MessageUtils.send(sender, "<gold><bold>ScoltEconomy — Admin</bold></gold>");
        MessageUtils.send(sender, " <gray>•</gray> <yellow>/eco admin</yellow>            <gray>- Abre o painel administrativo</gray>");
        MessageUtils.send(sender, " <gray>•</gray> <yellow>/eco alerts</yellow>           <gray>- Lista alertas econômicos ativos</gray>");
        MessageUtils.send(sender, " <gray>•</gray> <yellow>/eco treasury</yellow>         <gray>- Ver saldo do Tesouro</gray>");
        MessageUtils.send(sender, " <gray>•</gray> <yellow>/eco balance \\<player\\></yellow> <gray>- Ver saldo de um jogador</gray>");
        MessageUtils.send(sender, " <gray>•</gray> <yellow>/eco give \\<player\\> \\<val\\></yellow> <gray>- Dar dinheiro</gray>");
        MessageUtils.send(sender, " <gray>•</gray> <yellow>/eco take \\<player\\> \\<val\\></yellow> <gray>- Remover dinheiro</gray>");
        MessageUtils.send(sender, " <gray>•</gray> <yellow>/eco set \\<player\\> \\<val\\></yellow>  <gray>- Definir carteira</gray>");
    }
}
