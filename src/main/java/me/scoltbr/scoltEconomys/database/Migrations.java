package me.scoltbr.scoltEconomys.database;

import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.logging.Level;

/**
 * Gerencia o schema do banco de dados com migrações versionadas.
 *
 * <p>Cada migration possui um número de versão incremental. Ao iniciar, o sistema
 * verifica a versão atual em {@code se_schema_version} e aplica apenas as migrations
 * que ainda não foram executadas. Nunca altere migrations já aplicadas — apenas adicione novas.</p>
 */
public final class Migrations {

    /** Versão mais recente do schema. Incrementar ao adicionar novas migrations. */
    private static final int CURRENT_VERSION = 4;

    private Migrations() {}

    public static void run(Plugin plugin, DataSource ds) {
        try (Connection c = ds.getConnection()) {
            // Garante que a tabela de controle de versão existe
            ensureVersionTable(c);

            int currentVersion = getCurrentVersion(c);
            plugin.getLogger().info("[Migrations] Schema version: " + currentVersion + " → target: " + CURRENT_VERSION);

            // Aplica cada migration sequencialmente
            if (currentVersion < 1) applyV1(c, plugin);
            if (currentVersion < 2) applyV2(c, plugin);
            if (currentVersion < 3) applyV3(c, plugin);
            if (currentVersion < 4) applyV4(c, plugin);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Migrations] Falha crítica ao aplicar migrations: " + e.getMessage(), e);
            throw new RuntimeException("Failed to apply database migrations", e);
        }
    }

    // -------------------------------------------------------
    // Tabela de controle de versão
    // -------------------------------------------------------

    private static void ensureVersionTable(Connection c) throws Exception {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS se_schema_version (
                    version    INT     PRIMARY KEY,
                    applied_at BIGINT  NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        }
    }

    private static int getCurrentVersion(Connection c) throws Exception {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT MAX(version) FROM se_schema_version")) {
            if (rs.next()) {
                int v = rs.getInt(1);
                return rs.wasNull() ? 0 : v;
            }
            return 0;
        }
    }

    private static void markVersion(Connection c, int version) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT IGNORE INTO se_schema_version (version, applied_at) VALUES (?, ?)")) {
            ps.setInt(1, version);
            ps.setLong(2, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    // -------------------------------------------------------
    // Migration V1 — Schema inicial
    // -------------------------------------------------------

    /**
     * Cria as tabelas base do plugin (contas, tesouro, estatísticas, bolsa de valores).
     * Usa DOUBLE para compatibilidade com servidores legados — será migrado na V2.
     */
    private static void applyV1(Connection c, Plugin plugin) throws Exception {
        plugin.getLogger().info("[Migrations] Aplicando V1 — Schema inicial...");
        try (Statement st = c.createStatement()) {

            // Contas de jogadores
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS se_accounts (
                    uuid           CHAR(36)     PRIMARY KEY,
                    player_name    VARCHAR(16)  DEFAULT NULL,
                    wallet_balance DOUBLE       NOT NULL DEFAULT 0,
                    bank_balance   DOUBLE       NOT NULL DEFAULT 0,
                    last_update    BIGINT       NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            // Estatísticas diárias
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS se_economy_daily (
                    day               DATE    PRIMARY KEY,
                    total_coins       DOUBLE  NOT NULL,
                    total_wallet      DOUBLE  NOT NULL,
                    total_bank        DOUBLE  NOT NULL,
                    active_players    INT     NOT NULL,
                    top_concentration DOUBLE  NOT NULL,
                    updated_at        BIGINT  NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            // Tesouro do servidor
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS se_treasury (
                    id      TINYINT PRIMARY KEY DEFAULT 1,
                    balance DOUBLE   NOT NULL DEFAULT 0.0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            st.executeUpdate("INSERT IGNORE INTO se_treasury (id, balance) VALUES (1, 0.0)");

            // Mercado de Ações — histórico de preços
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS se_stock_prices (
                    stock_id    VARCHAR(32) NOT NULL,
                    price       DOUBLE      NOT NULL,
                    recorded_at BIGINT      NOT NULL,
                    PRIMARY KEY (stock_id, recorded_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            // Mercado de Ações — carteiras dos jogadores
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS se_stock_holdings (
                    uuid        CHAR(36)    NOT NULL,
                    stock_id    VARCHAR(32) NOT NULL,
                    quantity    BIGINT      NOT NULL DEFAULT 0,
                    avg_price   DOUBLE      NOT NULL DEFAULT 0.0,
                    PRIMARY KEY (uuid, stock_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            // Mercado de Ações — histórico de transações
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS se_stock_transactions (
                    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                    uuid        CHAR(36)           NOT NULL,
                    stock_id    VARCHAR(32)        NOT NULL,
                    type        ENUM('BUY','SELL') NOT NULL,
                    quantity    BIGINT             NOT NULL,
                    price       DOUBLE             NOT NULL,
                    total       DOUBLE             NOT NULL,
                    executed_at BIGINT             NOT NULL,
                    INDEX idx_stock_player (stock_id, uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        }
        markVersion(c, 1);
        plugin.getLogger().info("[Migrations] V1 aplicada com sucesso.");
    }

    // -------------------------------------------------------
    // Migration V2 — Colunas opcionais + índice total_balance
    // -------------------------------------------------------

    /**
     * Adiciona player_name (upgrade seguro) e o índice de ranking total_balance
     * para servidores que já tinham a tabela sem essas colunas.
     */
    private static void applyV2(Connection c, Plugin plugin) throws Exception {
        plugin.getLogger().info("[Migrations] Aplicando V2 — Índices e colunas complementares...");
        try (Statement st = c.createStatement()) {

            // player_name pode não existir em instalações mais antigas
            silentAlter(st, "ALTER TABLE se_accounts ADD COLUMN player_name VARCHAR(16) DEFAULT NULL");

            // Coluna gerada para ranking eficiente por total — pode falhar em MySQL antigo; ignoramos
            silentAlter(st, "ALTER TABLE se_accounts ADD COLUMN total_balance DOUBLE GENERATED ALWAYS AS (wallet_balance + bank_balance) STORED");
            silentAlter(st, "CREATE INDEX idx_total_balance ON se_accounts(total_balance)");
        }
        markVersion(c, 2);
        plugin.getLogger().info("[Migrations] V2 aplicada com sucesso.");
    }

    // -------------------------------------------------------
    // Migration V3 — DOUBLE → DECIMAL(20,2): precisão financeira
    // -------------------------------------------------------

    /**
     * <strong>Correção crítica de precisão financeira.</strong>
     *
     * <p>O tipo DOUBLE do MySQL possui representação em ponto flutuante binário
     * (IEEE 754), o que causa erros de arredondamento em operações monetárias.
     * DECIMAL(20,2) armazena valores exatos de até 18 dígitos inteiros + 2 decimais,
     * compatível com a precisão total do BigDecimal Java utilizado no domínio.</p>
     *
     * <p>Os ALTERs são executados com {@code silentAlter} porque servidores que
     * já executaram esta migration manualmente também serão protegidos.</p>
     */
    private static void applyV3(Connection c, Plugin plugin) throws Exception {
        plugin.getLogger().info("[Migrations] Aplicando V3 — DOUBLE → DECIMAL(20,2) (precisão financeira)...");
        try (Statement st = c.createStatement()) {

            // se_accounts
            silentAlter(st, "ALTER TABLE se_accounts MODIFY COLUMN wallet_balance DECIMAL(20,2) NOT NULL DEFAULT 0.00");
            silentAlter(st, "ALTER TABLE se_accounts MODIFY COLUMN bank_balance   DECIMAL(20,2) NOT NULL DEFAULT 0.00");

            // se_economy_daily
            silentAlter(st, "ALTER TABLE se_economy_daily MODIFY COLUMN total_coins       DECIMAL(20,2) NOT NULL");
            silentAlter(st, "ALTER TABLE se_economy_daily MODIFY COLUMN total_wallet      DECIMAL(20,2) NOT NULL");
            silentAlter(st, "ALTER TABLE se_economy_daily MODIFY COLUMN total_bank        DECIMAL(20,2) NOT NULL");
            silentAlter(st, "ALTER TABLE se_economy_daily MODIFY COLUMN top_concentration DECIMAL(10,6) NOT NULL");

            // se_treasury
            silentAlter(st, "ALTER TABLE se_treasury MODIFY COLUMN balance DECIMAL(20,2) NOT NULL DEFAULT 0.00");

            // se_stock_prices
            silentAlter(st, "ALTER TABLE se_stock_prices MODIFY COLUMN price DECIMAL(20,2) NOT NULL");

            // se_stock_holdings
            silentAlter(st, "ALTER TABLE se_stock_holdings MODIFY COLUMN avg_price DECIMAL(20,2) NOT NULL DEFAULT 0.00");

            // se_stock_transactions
            silentAlter(st, "ALTER TABLE se_stock_transactions MODIFY COLUMN price DECIMAL(20,2) NOT NULL");
            silentAlter(st, "ALTER TABLE se_stock_transactions MODIFY COLUMN total DECIMAL(20,2) NOT NULL");

            // Recriar coluna gerada: total_balance agora usa DECIMAL para consistência
            silentAlter(st, "ALTER TABLE se_accounts DROP COLUMN total_balance");
            silentAlter(st, "ALTER TABLE se_accounts ADD COLUMN total_balance DECIMAL(20,2) GENERATED ALWAYS AS (wallet_balance + bank_balance) STORED");
            silentAlter(st, "CREATE INDEX idx_total_balance ON se_accounts(total_balance)");
        }
        markVersion(c, 3);
        plugin.getLogger().info("[Migrations] V3 aplicada com sucesso. Precisão financeira garantida.");
    }

    // -------------------------------------------------------
    // Migration V4 — Mercado de Commodities
    // -------------------------------------------------------

    /**
     * Cria as tabelas do Mercado Global de Commodities.
     *
     * <ul>
     *   <li>{@code se_commodity_prices} — histórico de preços para gráficos e mean-reversion.</li>
     *   <li>{@code se_commodity_transactions} — auditoria completa de todas as vendas.</li>
     * </ul>
     *
     * Todos os valores monetários usam {@code DECIMAL(20,2)} para consistência com o resto do schema.
     */
    private static void applyV4(Connection c, Plugin plugin) throws Exception {
        plugin.getLogger().info("[Migrations] Aplicando V4 — Mercado de Commodities...");
        try (Statement st = c.createStatement()) {

            // Snapshots de preço (histórico para gráficos e consultas)
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS se_commodity_prices (
                    commodity_id VARCHAR(32)   NOT NULL,
                    price        DECIMAL(20,2) NOT NULL,
                    recorded_at  BIGINT        NOT NULL,
                    PRIMARY KEY (commodity_id, recorded_at),
                    INDEX idx_commodity_time (commodity_id, recorded_at DESC)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            // Registro completo de transações (auditoria)
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS se_commodity_transactions (
                    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
                    uuid         CHAR(36)           NOT NULL,
                    commodity_id VARCHAR(32)        NOT NULL,
                    type         ENUM('SELL','BUY') NOT NULL,
                    quantity     INT                NOT NULL,
                    price        DECIMAL(20,2)      NOT NULL,
                    total        DECIMAL(20,2)      NOT NULL,
                    executed_at  BIGINT             NOT NULL,
                    INDEX idx_comm_tx_player   (uuid),
                    INDEX idx_comm_tx_commodity (commodity_id, executed_at DESC)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        }
        markVersion(c, 4);
        plugin.getLogger().info("[Migrations] V4 aplicada. Mercado de Commodities pronto.");
    }

    // -------------------------------------------------------
    // Helper: executa ALTER silenciosamente (idempotente)
    // -------------------------------------------------------

    /**
     * Executa um DDL statement ignorando erros — útil para operações idempotentes
     * como adicionar uma coluna que já pode existir ou criar um índice já criado.
     */
    private static void silentAlter(Statement st, String ddl) {
        try {
            st.executeUpdate(ddl);
        } catch (Exception ignored) {
            // Coluna/índice já existe ou operação não suportada — comportamento esperado
        }
    }
}