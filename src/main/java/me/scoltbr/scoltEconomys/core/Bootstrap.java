// src/main/java/me/scoltbr/scoltEconomys/core/Bootstrap.java
package me.scoltbr.scoltEconomys.core;

import me.scoltbr.scoltEconomys.Main;
import me.scoltbr.scoltEconomys.account.*;
import me.scoltbr.scoltEconomys.admin.AdminMenuListener;
import me.scoltbr.scoltEconomys.admin.AdminMenuService;
import me.scoltbr.scoltEconomys.admin.EcoAdminCommand;
import me.scoltbr.scoltEconomys.alerts.AlertService;
import me.scoltbr.scoltEconomys.audit.TransactionAuditService;
import me.scoltbr.scoltEconomys.bank.BankInterestService;
import me.scoltbr.scoltEconomys.command.MoneyCommand;
import me.scoltbr.scoltEconomys.command.PayAliasCommand;
import me.scoltbr.scoltEconomys.database.DatabaseManager;
import me.scoltbr.scoltEconomys.database.Migrations;
import me.scoltbr.scoltEconomys.scheduler.AsyncExecutor;
import me.scoltbr.scoltEconomys.scheduler.Tasks;
import me.scoltbr.scoltEconomys.stats.AdminStatsService;
import me.scoltbr.scoltEconomys.stats.EconomyDailyRepositorySql;
import me.scoltbr.scoltEconomys.stats.MoneyTopService;
import me.scoltbr.scoltEconomys.stats.StatsTickService;
import me.scoltbr.scoltEconomys.tax.TaxManager;
import org.bukkit.command.CommandExecutor;

public final class Bootstrap {

    private final Main plugin;

    private AsyncExecutor asyncExecutor;
    private Tasks tasks;

    private DatabaseManager databaseManager;

    private AccountCache accountCache;
    private AccountRepository accountRepository;
    private AccountService accountService;
    private AccountFlushService accountFlushService;

    private TreasuryService treasuryService;
    private TaxManager taxManager;
    private TransactionAuditService auditService;

    private BankInterestService bankInterestService;

    // stats + alerts
    private AdminStatsService adminStatsService;
    private AlertService alertService;
    private StatsTickService statsTickService;
    private AdminMenuService adminMenuService;
    private MoneyTopService moneyTopService;

    public Bootstrap(Main plugin) {
        this.plugin = plugin;
    }

    public void enable() {

        // 0) Config
        plugin.saveDefaultConfig();

        // 1) Infra
        this.asyncExecutor = new AsyncExecutor(plugin);
        this.tasks = new Tasks(plugin);
        this.moneyTopService = new me.scoltbr.scoltEconomys.stats.MoneyTopService(plugin, asyncExecutor, accountRepository);
        // 2) Database
        this.databaseManager = new DatabaseManager(plugin);
        this.databaseManager.start();
        Migrations.run(plugin, databaseManager.dataSource());

        // 3) Cache + repo
        this.accountCache = new AccountCache();
        this.accountRepository = new AccountRepositorySql(databaseManager.dataSource());

        // 4) Services core
        this.treasuryService = new TreasuryService(plugin);
        this.taxManager = new TaxManager(plugin.getConfig().getConfigurationSection("tax"));
        this.auditService = new TransactionAuditService(plugin, asyncExecutor);

        this.accountService = new AccountService(
                plugin,
                asyncExecutor,
                accountCache,
                accountRepository,
                taxManager,
                treasuryService,
                auditService
        );

        this.accountFlushService = new AccountFlushService(plugin, asyncExecutor, accountCache, accountRepository);

        // 5) Bank interest
        this.bankInterestService = new BankInterestService(plugin, accountCache, accountService);

        // 6) Stats + alerts
        var economyCalculator = new me.scoltbr.scoltEconomys.stats.EconomyCalculator(accountCache);
        var dailyRepo = new EconomyDailyRepositorySql(databaseManager.dataSource());
        var moneyTopService = new me.scoltbr.scoltEconomys.stats.MoneyTopService(plugin, asyncExecutor, accountRepository);

        this.adminStatsService = new AdminStatsService(economyCalculator, dailyRepo);
        this.alertService = new AlertService(plugin, adminStatsService);
        this.statsTickService = new StatsTickService(plugin, adminStatsService, alertService);

        // 6.5) Admin GUI (AGORA sim, stats/alerts já existem)
        this.adminMenuService = new AdminMenuService(plugin, adminStatsService, alertService, taxManager);
        // 7) App layer
        registerCommands();
        registerListeners();
        scheduleTasks();
        hookVaultEconomy();
    }

    private void scheduleTasks() {
        int flushIntervalSeconds = plugin.getConfig().getInt("flush.interval-seconds", 120);
        int maxPerFlush = plugin.getConfig().getInt("flush.max-accounts-per-flush", 250);

        int interestInterval = plugin.getConfig().getInt("bank.interest.interval-seconds", 600);
        int statsInterval = plugin.getConfig().getInt("stats.interval-seconds", 600);

        long flushTicks = 20L * flushIntervalSeconds;
        long interestTicks = 20L * interestInterval;
        long statsTicks = 20L * statsInterval;

        // juros
        if (plugin.getConfig().getBoolean("bank.interest.enabled", false)) {
            tasks.runRepeatingAsync(
                    () -> bankInterestService.tick(),
                    interestTicks,
                    interestTicks
            );
        }

        // stats + alertas
        if (plugin.getConfig().getBoolean("stats.enabled", true)) {
            tasks.runRepeatingAsync(
                    () -> statsTickService.tick(),
                    statsTicks,
                    statsTicks
            );
        }

        // flush
        tasks.runRepeatingAsync(
                accountFlushService::flushDirtyBatch,
                flushTicks,
                flushTicks,
                maxPerFlush
        );
    }

    private void registerCommands() {
        register("pay", new PayAliasCommand());
        register("money", new MoneyCommand(accountService, moneyTopService));

        // EcoCommand deve tratar /eco admin etc.
        register("eco", new EcoAdminCommand(
                plugin,
                accountService,
                auditService,
                treasuryService,
                adminStatsService,
                alertService,
                adminMenuService
        ));    }

    private void registerListeners() {
        plugin.getServer().getPluginManager().registerEvents(
                new PlayerLifecycleListener(accountService, accountFlushService),
                plugin
        );

        plugin.getServer().getPluginManager().registerEvents(
                new AdminMenuListener(adminMenuService),
                plugin
        );

    }

    private void register(String name, CommandExecutor executor) {
        var cmd = plugin.getCommand(name);
        if (cmd == null) {
            plugin.getLogger().severe("Comando '" + name + "' não está no plugin.yml!");
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }
        cmd.setExecutor(executor);
    }

    private void hookVaultEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("[Vault] Vault não encontrado. Rodando sem integração.");
            return;
        }

        var provider = new me.scoltbr.scoltEconomys.vault.ScoltVaultEconomy(plugin, accountService);
        plugin.getServer().getServicesManager().register(
                net.milkbowl.vault.economy.Economy.class,
                provider,
                plugin,
                org.bukkit.plugin.ServicePriority.Highest
        );

        plugin.getLogger().info("[Vault] Economy provider registrado com sucesso.");
    }

    // Getters pro shutdown
    public AccountFlushService accountFlushService() { return accountFlushService; }
    public AsyncExecutor asyncExecutor() { return asyncExecutor; }
    public Tasks tasks() { return tasks; }
    public DatabaseManager databaseManager() { return databaseManager; }
}