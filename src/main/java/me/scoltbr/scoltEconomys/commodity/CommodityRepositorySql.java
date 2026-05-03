package me.scoltbr.scoltEconomys.commodity;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class CommodityRepositorySql implements CommodityRepository {

    private final DataSource ds;

    public CommodityRepositorySql(DataSource ds) {
        this.ds = ds;
    }

    @Override
    public void savePrice(CommodityPrice p) {
        exec("INSERT INTO se_commodity_prices (commodity_id, price, recorded_at) VALUES (?,?,?)",
                ps -> {
                    ps.setString(1, p.commodityId());
                    ps.setBigDecimal(2, p.price());
                    ps.setLong(3, p.recordedAt());
                });
    }

    @Override
    public List<CommodityPrice> getHistory(String commodityId, int limit) {
        String sql = "SELECT commodity_id, price, recorded_at FROM se_commodity_prices " +
                "WHERE commodity_id = ? ORDER BY recorded_at DESC LIMIT ?";
        List<CommodityPrice> result = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, commodityId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new CommodityPrice(
                            rs.getString("commodity_id"),
                            rs.getBigDecimal("price"),
                            rs.getLong("recorded_at")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get commodity history for " + commodityId, e);
        }
        // Retorna do mais antigo para o mais novo (adequado para gráficos)
        Collections.reverse(result);
        return result;
    }

    @Override
    public void recordTransaction(UUID uuid, String commodityId, String type, int quantity,
                                  BigDecimal pricePerUnit, BigDecimal total) {
        exec("INSERT INTO se_commodity_transactions " +
                "(uuid, commodity_id, type, quantity, price, total, executed_at) VALUES (?,?,?,?,?,?,?)",
                ps -> {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, commodityId);
                    ps.setString(3, type);
                    ps.setInt(4, quantity);
                    ps.setBigDecimal(5, pricePerUnit);
                    ps.setBigDecimal(6, total);
                    ps.setLong(7, System.currentTimeMillis());
                });
    }

    @Override
    public void purgeOldPrices(String commodityId, int keep) {
        // Usa subquery derivada para contornar limitação do MySQL no DELETE com LIMIT
        String sql = """
            DELETE FROM se_commodity_prices
            WHERE commodity_id = ?
            AND recorded_at NOT IN (
                SELECT recorded_at FROM (
                    SELECT recorded_at FROM se_commodity_prices
                    WHERE commodity_id = ?
                    ORDER BY recorded_at DESC
                    LIMIT ?
                ) AS tmp
            )
        """;
        exec(sql, ps -> {
            ps.setString(1, commodityId);
            ps.setString(2, commodityId);
            ps.setInt(3, keep);
        });
    }

    // -------------------------------------------------------
    // Helper
    // -------------------------------------------------------

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T t) throws SQLException;
    }

    private void exec(String sql, ThrowingConsumer<PreparedStatement> binder) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            binder.accept(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("CommodityRepositorySql exec failed: " + e.getMessage(), e);
        }
    }
}
