package me.scoltbr.scoltEconomys.stats;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.Optional;

public final class EconomyDailyRepositorySql implements EconomyDailyRepository {

    private final DataSource ds;

    public EconomyDailyRepositorySql(DataSource ds) {
        this.ds = ds;
    }

    @Override
    public void upsert(LocalDate day, EconomySnapshot s) {
        String sql = """
            INSERT INTO se_economy_daily
              (day, total_coins, total_wallet, total_bank, active_players, top_concentration, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              total_coins = VALUES(total_coins),
              total_wallet = VALUES(total_wallet),
              total_bank = VALUES(total_bank),
              active_players = VALUES(active_players),
              top_concentration = VALUES(top_concentration),
              updated_at = VALUES(updated_at)
        """;

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setDate(1, Date.valueOf(day));
            ps.setDouble(2, s.totalCoins());
            ps.setDouble(3, s.totalWallet());
            ps.setDouble(4, s.totalBank());
            ps.setInt(5, s.activePlayers());
            ps.setDouble(6, s.top10Concentration());
            ps.setLong(7, s.at().toEpochMilli());

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed upsert economy daily " + day, e);
        }
    }

    @Override
    public Optional<EconomyDailyRow> load(LocalDate day) {
        String sql = """
            SELECT day, total_coins, total_wallet, total_bank, active_players, top_concentration, updated_at
            FROM se_economy_daily
            WHERE day = ?
        """;

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setDate(1, Date.valueOf(day));

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();

                return Optional.of(new EconomyDailyRow(
                        rs.getDate("day").toLocalDate(),
                        rs.getDouble("total_coins"),
                        rs.getDouble("total_wallet"),
                        rs.getDouble("total_bank"),
                        rs.getInt("active_players"),
                        rs.getDouble("top_concentration"),
                        rs.getLong("updated_at")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed load economy daily " + day, e);
        }
    }
}
