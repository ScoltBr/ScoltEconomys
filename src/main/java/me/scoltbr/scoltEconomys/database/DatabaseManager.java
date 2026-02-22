package me.scoltbr.scoltEconomys.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;

public final class DatabaseManager {

    private final Plugin plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        HikariConfig cfg = new HikariConfig();

        String host = plugin.getConfig().getString("database.host", "127.0.0.1");
        int port = plugin.getConfig().getInt("database.port", 3306);
        String db = plugin.getConfig().getString("database.name", "scolteconomy");
        String user = plugin.getConfig().getString("database.user", "root");
        String pass = plugin.getConfig().getString("database.password", "");
        boolean ssl = plugin.getConfig().getBoolean("database.use-ssl", false);

        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + db
                + "?useSSL=" + ssl
                + "&allowPublicKeyRetrieval=true"
                + "&useUnicode=true&characterEncoding=utf8";

        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(user);
        cfg.setPassword(pass);

        int max = plugin.getConfig().getInt("database.pool.max-size", 10);
        int minIdle = plugin.getConfig().getInt("database.pool.min-idle", 2);

        cfg.setMaximumPoolSize(max);
        cfg.setMinimumIdle(minIdle);
        cfg.setMaxLifetime(plugin.getConfig().getLong("database.pool.max-lifetime-ms", 1800000));
        cfg.setConnectionTimeout(plugin.getConfig().getLong("database.pool.connection-timeout-ms", 5000));

        cfg.setPoolName(plugin.getName() + "-hikari");

        // recomendado
        cfg.addDataSourceProperty("cachePrepStmts", "true");
        cfg.addDataSourceProperty("prepStmtCacheSize", "250");
        cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new HikariDataSource(cfg);
    }

    public DataSource dataSource() {
        if (dataSource == null) throw new IllegalStateException("Database not started");
        return dataSource;
    }

    public void stop() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }
}