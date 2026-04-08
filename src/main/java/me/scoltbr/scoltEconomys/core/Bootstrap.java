package me.scoltbr.scoltEconomys.core;

import me.scoltbr.scoltEconomys.Main;
import me.scoltbr.scoltEconomys.account.*;
import me.scoltbr.scoltEconomys.admin.AdminMenuListener;
import me.scoltbr.scoltEconomys.admin.AdminMenuService;
import me.scoltbr.scoltEconomys.admin.EcoAdminCommand;
import me.scoltbr.scoltEconomys.alerts.AlertService;
import me.scoltbr.scoltEconomys.audit.TransactionAuditService;
import me.scoltbr.scoltEconomys.bank.BankInterestService;
import me.scoltbr.scoltEconomys.command.EcoAdminTabCompleter;
import me.scoltbr.scoltEconomys.command.MoneyCommand;
import me.scoltbr.scoltEconomys.command.MoneyCommandTabCompleter;
import me.scoltbr.scoltEconomys.command.PayAliasCommand;
import me.scoltbr.scoltEconomys.database.DatabaseManager;
import me.scoltbr.scoltEconomys.database.Migrations;
import me.scoltbr.scoltEconomys.scheduler.AsyncExecutor;
import me.scoltbr.scoltEconomys.scheduler.Tasks;
import me.scoltbr.scoltEconomys.stats.AdminStatsService;
import me.scoltbr.scoltEconomys.stats.EconomyDailyRepositorySql;
import me.scoltbr.scoltEconomys.stats.MoneyTopService;
import me.scoltbr.scoltEconomys.stats.StatsTickService;
import me.scoltbr.scoltEconomys.api.ScoltEconomyAPI;
import me.scoltbr.scoltEconomys.api.ScoltEconomyAPIImpl;
import me.scoltbr.scoltEconomys.stock.StockMarketCommand;
import me.scoltbr.scoltEconomys.stock.StockMarketService;
import me.scoltbr.scoltEconomys.stock.StockMarketTabCompleter;
import me.scoltbr.scoltEconomys.stock.StockPriceTicker;
import me.scoltbr.scoltEconomys.stock.StockRepositorySql;
import me.scoltbr.scoltEconomys.stock.gui.StockMenuListener;
import me.scoltbr.scoltEconomys.stock.gui.StockMenuService;
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
    private me.scoltbr.scoltEconomys.event.EventManager eventManager;

    // stats + alerts
    private AdminStatsService adminStatsService;
    private AlertService alertService;
    private StatsTickService statsTickService;
    private AdminMenuService adminMenuService;
    private MoneyTopService moneyTopService;

    // stock market
    private StockRepositorySql stockRepository;
    private StockMarketService stockMarketService;
    private StockPriceTicker   stockPriceTicker;
    private StockMenuService   stockMenuService;

    // API pública
    private ScoltEconomyAPIImpl publicApi;

    public Bootstrap(Main plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        // 1) Infra
        this.asyncExecutor = new AsyncExecutor(plugin);
        this.tasks = new Tasks(plugin);
        // 2) Database
        this.databaseManager = new DatabaseManager(plugin);
        this.databaseManager.start();
        Migrations.run(plugin, databaseManager.dataSource());

        // 3) Cache + repo
        this.accountCache = new AccountCache();
        this.accountRepository = new AccountRepositorySql(databaseManager.dataSource());
        this.moneyTopService = new MoneyTopService(plugin, asyncExecutor, accountRepository);


        // 4) Services core
        this.treasuryService = new TreasuryService(plugin, databaseManager.dataSource());
        this.treasuryService.start();
        this.eventManager = new me.scoltbr.scoltEconomys.event.EventManager(plugin);
        this.taxManager = new TaxManager(plugin.getConfig().getConfigurationSection("tax"));
        this.taxManager.setEventManager(eventManager);
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
        this.bankInterestService = new BankInterestService(plugin, accountCache, accountService, eventManager);

        // 6) Stats + alerts
        var economyCalculator = new me.scoltbr.scoltEconomys.stats.EconomyCalculator(accountRepository);
        var dailyRepo = new EconomyDailyRepositorySql(databaseManager.dataSource());
        this.adminStatsService = new AdminStatsService(economyCalculator, dailyRepo);
        this.alertService = new AlertService(plugin, adminStatsService);
        this.statsTickService = new StatsTickService(plugin, adminStatsService, alertService);

        // 6.5) Admin GUI (AGORA sim, stats/alerts já existem)
        this.adminMenuService = new AdminMenuService(plugin, asyncExecutor, adminStatsService, alertService, taxManager);

        // 7) Stock Market
        if (plugin.getConfig().getBoolean("stock-market.enabled", false)) {
            this.stockRepository     = new StockRepositorySql(databaseManager.dataSource());
            this.stockMarketService  = new StockMarketService(plugin, asyncExecutor, stockRepository, accountService, treasuryService, eventManager);
            this.stockMarketService.loadInitialState();
            this.stockPriceTicker    = new StockPriceTicker(stockMarketService);
            this.stockMenuService    = new StockMenuService(plugin, stockMarketService);
        }

        // 8) API Pública — registra no ServicesManager para outros plugins
        this.publicApi = new ScoltEconomyAPIImpl(
                plugin,
                asyncExecutor,
                accountService,
                treasuryService,
                eventManager,
                stockMarketService  // null-safe: pode ser null se stock-market.enabled=false
        );
        plugin.getServer().getServicesManager().register(
                ScoltEconomyAPI.class,
                publicApi,
                plugin,
                org.bukkit.plugin.ServicePriority.Normal
        );
        plugin.getLogger().info("[API] ScoltEconomyAPI v" + publicApi.apiVersion() + " registrada.");

        // 9) App layer
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
        int autoEventInterval = plugin.getConfig().getInt("events.auto.interval-seconds", 14400);

        long flushTicks = 20L * flushIntervalSeconds;
        long interestTicks = 20L * interestInterval;
        long statsTicks = 20L * statsInterval;
        long autoEventTicks = 20L * autoEventInterval;

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

        // eventos automáticos
        if (plugin.getConfig().getBoolean("events.enabled", true) && 
            plugin.getConfig().getBoolean("events.auto.enabled", true)) {
            tasks.runRepeatingAsync(
                    () -> eventManager.checkAutoEvent(),
                    autoEventTicks,
                    autoEventTicks
            );
        }

        // feedback visual (ActionBar)
        tasks.runRepeatingSync(
                () -> eventManager.tickActionBar(),
                20L,
                20L
        );

        // stock market ticker
        if (stockPriceTicker != null) {
            int stockTickInterval = plugin.getConfig().getInt("stock-market.tick-interval-seconds", 300);
            long stockTicks = 20L * stockTickInterval;
            tasks.runRepeatingAsync(
                    stockPriceTicker::tick,
                    stockTicks,
                    stockTicks
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
        register("money", new MoneyCommand(accountService, moneyTopService, adminMenuService, eventManager));
        var moneyCmd = plugin.getCommand("money");
        if (moneyCmd != null) moneyCmd.setTabCompleter(new MoneyCommandTabCompleter());

        // EcoCommand deve tratar /eco admin etc.
        register("eco", new EcoAdminCommand(
                plugin,
                accountService,
                auditService,
                treasuryService,
                adminStatsService,
                alertService,
                adminMenuService
        ));
        var ecoCmd = plugin.getCommand("eco");
        if (ecoCmd != null) ecoCmd.setTabCompleter(new EcoAdminTabCompleter());

        // Stock market command — só registra se o módulo estiver ativo
        if (stockMarketService != null) {
            register("bolsa", new StockMarketCommand(stockMarketService, stockMenuService));
            var bolsaCmd = plugin.getCommand("bolsa");
            if (bolsaCmd != null) bolsaCmd.setTabCompleter(new StockMarketTabCompleter(stockMarketService));
        }
    }

    private void registerListeners() {
        plugin.getServer().getPluginManager().registerEvents(
                new PlayerLifecycleListener(accountService, accountFlushService, accountRepository, asyncExecutor),
                plugin
        );

        plugin.getServer().getPluginManager().registerEvents(
                new AdminMenuListener(adminMenuService),
                plugin
        );

        if (stockMarketService != null) {
            plugin.getServer().getPluginManager().registerEvents(
                    new StockMenuListener(plugin, stockMarketService, stockMenuService),
                    plugin
            );
        }
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
    public TransactionAuditService auditService() { return auditService; }
    public TreasuryService treasuryService() { return treasuryService; }
}