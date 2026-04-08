package me.scoltbr.scoltEconomys.stock;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

public final class StockRepositorySql implements StockRepository {

    private final DataSource ds;

    public StockRepositorySql(DataSource ds) {
        this.ds = ds;
    }

    // -------------------------------------------------------
    // Preços
    // -------------------------------------------------------

    @Override
    public void savePrice(StockPrice p) {
        exec("INSERT INTO se_stock_prices (stock_id, price, recorded_at) VALUES (?,?,?)",
                ps -> {
                    ps.setString(1, p.stockId());
                    ps.setDouble(2, p.price());
                    ps.setLong(3, p.recordedAt());
                });
    }

    @Override
    public List<StockPrice> getHistory(String stockId, int limit) {
        String sql = "SELECT stock_id, price, recorded_at FROM se_stock_prices " +
                "WHERE stock_id = ? ORDER BY recorded_at DESC LIMIT ?";
        List<StockPrice> result = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, stockId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new StockPrice(
                            rs.getString("stock_id"),
                            rs.getDouble("price"),
                            rs.getLong("recorded_at")));
                }
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        Collections.reverse(result); // oldest-first
        return result;
    }

    // -------------------------------------------------------
    // Holdings
    // -------------------------------------------------------

    @Override
    public Optional<StockHolding> getHolding(UUID uuid, String stockId) {
        String sql = "SELECT uuid, stock_id, quantity, avg_price FROM se_stock_holdings " +
                "WHERE uuid = ? AND stock_id = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, stockId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rowToHolding(rs));
                }
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return Optional.empty();
    }

    @Override
    public Map<String, StockHolding> getAllHoldings(UUID uuid) {
        String sql = "SELECT uuid, stock_id, quantity, avg_price FROM se_stock_holdings " +
                "WHERE uuid = ? AND quantity > 0";
        Map<String, StockHolding> result = new LinkedHashMap<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StockHolding h = rowToHolding(rs);
                    result.put(h.stockId(), h);
                }
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return result;
    }

    @Override
    public void upsertHolding(StockHolding h) {
        exec("INSERT INTO se_stock_holdings (uuid, stock_id, quantity, avg_price) VALUES (?,?,?,?) " +
                "ON DUPLICATE KEY UPDATE quantity = VALUES(quantity), avg_price = VALUES(avg_price)",
                ps -> {
                    ps.setString(1, h.uuid().toString());
                    ps.setString(2, h.stockId());
                    ps.setLong(3, h.quantity());
                    ps.setDouble(4, h.avgPrice());
                });
    }

    @Override
    public void deleteHolding(UUID uuid, String stockId) {
        exec("DELETE FROM se_stock_holdings WHERE uuid = ? AND stock_id = ?",
                ps -> {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, stockId);
                });
    }

    // -------------------------------------------------------
    // Transações
    // -------------------------------------------------------

    @Override
    public void recordTransaction(UUID uuid, String stockId, String type, long qty, double price, double total) {
        exec("INSERT INTO se_stock_transactions (uuid, stock_id, type, quantity, price, total, executed_at) " +
                "VALUES (?,?,?,?,?,?,?)",
                ps -> {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, stockId);
                    ps.setString(3, type);
                    ps.setLong(4, qty);
                    ps.setDouble(5, price);
                    ps.setDouble(6, total);
                    ps.setLong(7, System.currentTimeMillis());
                });
    }

    // -------------------------------------------------------
    // Rankings / Supply
    // -------------------------------------------------------

    @Override
    public List<StockHolding> getTopHolders(String stockId, int limit) {
        String sql = "SELECT uuid, stock_id, quantity, avg_price FROM se_stock_holdings " +
                "WHERE stock_id = ? AND quantity > 0 ORDER BY quantity DESC LIMIT ?";
        List<StockHolding> result = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, stockId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(rowToHolding(rs));
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return result;
    }

    @Override
    public long sumHeldShares(String stockId) {
        String sql = "SELECT COALESCE(SUM(quantity), 0) FROM se_stock_holdings WHERE stock_id = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, stockId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return 0;
    }

    @Override
    public void purgeOldPrices(String stockId, int keep) {
        // Usa tabela derivada para contornar limitação do MySQL (não permite subquery da mesma tabela no DELETE)
        String sql = """
            DELETE FROM se_stock_prices
            WHERE stock_id = ?
            AND recorded_at NOT IN (
                SELECT recorded_at FROM (
                    SELECT recorded_at FROM se_stock_prices
                    WHERE stock_id = ?
                    ORDER BY recorded_at DESC
                    LIMIT ?
                ) AS tmp
            )
        """;
        exec(sql, ps -> {
            ps.setString(1, stockId);
            ps.setString(2, stockId);
            ps.setInt(3, keep);
        });
    }



    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private StockHolding rowToHolding(ResultSet rs) throws SQLException {
        return new StockHolding(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("stock_id"),
                rs.getLong("quantity"),
                rs.getDouble("avg_price"));
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T t) throws SQLException;
    }

    private void exec(String sql, ThrowingConsumer<PreparedStatement> binder) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            binder.accept(ps);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }
}
