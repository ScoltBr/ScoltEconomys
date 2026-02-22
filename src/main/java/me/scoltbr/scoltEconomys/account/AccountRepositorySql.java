package me.scoltbr.scoltEconomys.account;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AccountRepositorySql implements AccountRepository {

    private final DataSource ds;

    public AccountRepositorySql(DataSource ds) {
        this.ds = ds;
    }

    @Override
    public Optional<PlayerAccount> load(UUID uuid) {
        String sql = "SELECT wallet_balance, bank_balance, last_update FROM se_accounts WHERE uuid = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();

                double wallet = rs.getDouble("wallet_balance");
                double bank = rs.getDouble("bank_balance");
                long lastUpdate = rs.getLong("last_update");

                return Optional.of(new PlayerAccount(uuid, wallet, bank, Instant.ofEpochMilli(lastUpdate)));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load account " + uuid, e);
        }
    }

    @Override
    public void upsertBatch(List<PlayerAccount> accounts) {
        if (accounts == null || accounts.isEmpty()) return;

        String sql = """
            INSERT INTO se_accounts (uuid, wallet_balance, bank_balance, last_update)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              wallet_balance = VALUES(wallet_balance),
              bank_balance   = VALUES(bank_balance),
              last_update    = VALUES(last_update)
        """;

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            for (PlayerAccount a : accounts) {
                ps.setString(1, a.uuid().toString());
                ps.setDouble(2, a.wallet());
                ps.setDouble(3, a.bank());
                ps.setLong(4, a.lastUpdate().toEpochMilli());
                ps.addBatch();
            }

            ps.executeBatch();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to upsert batch size=" + accounts.size(), e);
        }
    }

    @Override
    public List<TopBalanceRow> topTotal(int limit) {
        String sql = """
        SELECT uuid, (wallet_balance + bank_balance) AS total
        FROM se_accounts
        ORDER BY total DESC
        LIMIT ?
    """;

        try (var c = ds.getConnection();
             var ps = c.prepareStatement(sql)) {

            ps.setInt(1, limit);

            try (var rs = ps.executeQuery()) {
                var list = new java.util.ArrayList<TopBalanceRow>(limit);
                while (rs.next()) {
                    java.util.UUID uuid = java.util.UUID.fromString(rs.getString("uuid"));
                    double total = rs.getDouble("total");
                    list.add(new TopBalanceRow(uuid, total));
                }
                return list;
            }

        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed topTotal", e);
        }
    }


}