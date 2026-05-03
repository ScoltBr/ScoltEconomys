package me.scoltbr.scoltEconomys.api.event;

import org.bukkit.event.Cancellable;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Evento disparado quando o saldo de um jogador está prestes a ser alterado.
 * <p>
 * Pode ser usado para monitorar mudanças na Carteira (WALLET) ou no Banco
 * (BANK).
 * Se o evento for cancelado, a alteração de saldo não será aplicada.
 * </p>
 */
public final class AccountBalanceChangeEvent extends ScoltEconomyEvent implements Cancellable {

    /** Define qual tipo de saldo está sendo alterado. */
    public enum BalanceType {
        WALLET, BANK
    }

    private final UUID uuid;
    private final BigDecimal oldBalance;
    private BigDecimal newBalance;
    private final BalanceType type;
    private boolean cancelled;

    public AccountBalanceChangeEvent(UUID uuid, BigDecimal oldBalance, BigDecimal newBalance, BalanceType type) {
        super(false);
        this.uuid = uuid;
        this.oldBalance = oldBalance;
        this.newBalance = newBalance;
        this.type = type;
    }

    /** @return UUID do jogador afetado. */
    public UUID getUniqueId() {
        return uuid;
    }

    /** @return saldo antes da alteração. */
    public BigDecimal getOldBalance() {
        return oldBalance;
    }

    /** @return saldo proposto após a alteração. */
    public BigDecimal getNewBalance() {
        return newBalance;
    }

    /**
     * @param newBalance permite que outros plugins modifiquem o valor final a ser
     *                   aplicado.
     */
    public void setNewBalance(BigDecimal newBalance) {
        this.newBalance = newBalance;
    }

    /** @return se a alteração é na Carteira ou no Banco. */
    public BalanceType getBalanceType() {
        return type;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
