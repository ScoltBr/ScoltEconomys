package me.scoltbr.scoltEconomys.account;

import javax.sql.DataSource;

import java.math.BigDecimal;
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
                if (!rs.next())
                    return Optional.empty();

                BigDecimal wallet = rs.getBigDecimal("wallet_balance");
                BigDecimal bank = rs.getBigDecimal("bank_balance");
                long lastUpdate = rs.getLong("last_update");

                return Optional.of(new PlayerAccount(uuid, wallet, bank, Instant.ofEpochMilli(lastUpdate)));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load account " + uuid, e);
        }
    }

    @Override
    public void upsertBatch(List<PlayerAccount> accounts) {
        if (accounts == null || accounts.isEmpty())
            return;

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
                ps.setBigDecimal(2, a.wallet());
                ps.setBigDecimal(3, a.bank());
                ps.setLong(4, a.lastUpdate().toEpochMilli());
                ps.addBatch();
            }

            ps.executeBatch();

        } catch (SQLException e) {
            java.util.logging.Logger.getLogger("ScoltEconomys")
                    .severe("Failed to upsert batch size=" + accounts.size() + ": " + e.getMessage());
            // Evitamos lançar RuntimeException para não quebrar a thread de flush
        }
    }

    @Override
    public List<TopBalanceRow> topTotal(int limit) {
        String sql = """
                    SELECT uuid, player_name, (wallet_balance + bank_balance) AS total
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
                    String name = rs.getString("player_name"); // pode ser null em contas antigas
                    BigDecimal total = rs.getBigDecimal("total");
                    list.add(new TopBalanceRow(uuid, name, total));
                }
                return list;
            }

        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed topTotal", e);
        }
    }

    @Override
    public void updatePlayerName(UUID uuid, String name) {
        String sql = "UPDATE se_accounts SET player_name = ? WHERE uuid = ?";
        try (var c = ds.getConnection();
                var ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to update player name for " + uuid, e);
        }
    }

    @Override
    public Optional<BigDecimal> getWalletBalanceSync(UUID uuid) {
        String sql = "SELECT wallet_balance FROM se_accounts WHERE uuid = ?";
        try (var c = ds.getConnection();
                var ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (var rs = ps.executeQuery()) {
                if (!rs.next())
                    return Optional.empty();
                return Optional.of(rs.getBigDecimal("wallet_balance"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get sync wallet balance for " + uuid, e);
        }
    }

    @Override
    public boolean addWalletBalanceSync(UUID uuid, BigDecimal amount) {
        String updateBalance = "UPDATE se_accounts SET wallet_balance = wallet_balance + ?, last_update = ? WHERE uuid = ? AND (wallet_balance + ?) >= 0";
        try (var c = ds.getConnection();
                var ps = c.prepareStatement(updateBalance)) {

            ps.setBigDecimal(1, amount);
            ps.setLong(2, Instant.now().toEpochMilli());
            ps.setString(3, uuid.toString());
            ps.setBigDecimal(4, amount);

            int updatedRows = ps.executeUpdate();
            return updatedRows > 0;

        } catch (SQLException e) {
            java.util.logging.Logger.getLogger("ScoltEconomys")
                    .severe("Failed to add sync wallet balance for " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public GlobalEconomyData getGlobalEconomyData() {
        String countSql = "SELECT SUM(wallet_balance) AS w, SUM(bank_balance) AS b, COUNT(uuid) AS c FROM se_accounts";

        BigDecimal totalWallet = BigDecimal.ZERO;
        BigDecimal totalBank = BigDecimal.ZERO;
        int totalAccs = 0;

        try (var c = ds.getConnection();
                var ps = c.prepareStatement(countSql);
                var rs = ps.executeQuery()) {
            if (rs.next()) {
                totalWallet = rs.getBigDecimal("w");
                if (totalWallet == null)
                    totalWallet = BigDecimal.ZERO;
                totalBank = rs.getBigDecimal("b");
                if (totalBank == null)
                    totalBank = BigDecimal.ZERO;
                totalAccs = rs.getInt("c");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get global economy count", e);
        }

        int top10Limit = Math.max(1, totalAccs / 10);
        BigDecimal top10Sum = BigDecimal.ZERO;

        String topSql = "SELECT (wallet_balance + bank_balance) as total FROM se_accounts ORDER BY (wallet_balance + bank_balance) DESC LIMIT ?";
        try (var c = ds.getConnection();
                var ps = c.prepareStatement(topSql)) {
            ps.setInt(1, top10Limit);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal t = rs.getBigDecimal("total");
                    if (t != null)
                        top10Sum = top10Sum.add(t);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get global top 10 economy", e);
        }

        return new GlobalEconomyData(totalWallet, totalBank, totalAccs, top10Sum);
    }

}