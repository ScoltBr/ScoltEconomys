package me.scoltbr.scoltEconomys.api.event;

import java.util.UUID;

/**
 * Evento disparado quando um jogador conclui uma transação de compra ou venda no Mercado de Ações.
 * <p>
 * Este evento não é cancelável, pois é disparado após a confirmação da transação no banco de dados.
 * </p>
 */
public final class StockTransactionEvent extends ScoltEconomyEvent {

    /** Tipo de transação financeira. */
    public enum TransactionType { BUY, SELL }

    private final UUID uuid;
    private final String stockId;
    private final TransactionType type;
    private final long quantity;
    private final java.math.BigDecimal pricePerShare;
    private final java.math.BigDecimal totalValue;
    private final java.math.BigDecimal fee;

    public StockTransactionEvent(UUID uuid, String stockId, TransactionType type,
                                 long quantity, java.math.BigDecimal pricePerShare, java.math.BigDecimal totalValue, java.math.BigDecimal fee) {
        this.uuid = uuid;
        this.stockId = stockId;
        this.type = type;
        this.quantity = quantity;
        this.pricePerShare = pricePerShare;
        this.totalValue = totalValue;
        this.fee = fee;
    }

    /** @return UUID do investidor. */
    public UUID getUniqueId() { return uuid; }

    /** @return ID da empresa negociada. */
    public String getStockId() { return stockId; }

    /** @return se foi uma COMPRA ou VENDA. */
    public TransactionType getTransactionType() { return type; }

    /** @return quantidade de ações operadas. */
    public long getQuantity() { return quantity; }

    /** @return preço unitário da ação no momento da execução. */
    public java.math.BigDecimal getPricePerShare() { return pricePerShare; }

    /** @return valor total da operação (incluindo ou excluindo taxas conforme o tipo). */
    public java.math.BigDecimal getTotalValue() { return totalValue; }

    /** @return taxa de corretagem retida pelo Tesouro. */
    public java.math.BigDecimal getFee() { return fee; }
}
