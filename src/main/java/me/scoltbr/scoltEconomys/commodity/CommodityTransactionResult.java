package me.scoltbr.scoltEconomys.commodity;

import java.math.BigDecimal;

/**
 * Resultado de uma operação de compra ou venda de commodity ao mercado global.
 *
 * @param success      Se a transação foi concluída com sucesso.
 * @param quantity     Quantidade efetivamente transacionada.
 * @param total        Valor líquido (recebido ou pago).
 * @param fee          Taxa de corretagem (enviada ao Tesouro ou recolhida).
 * @param pricePerUnit Preço unitário no momento da transação.
 * @param reason       Código de falha (null se sucesso).
 */
public record CommodityTransactionResult(
        boolean success,
        int quantity,
        BigDecimal total,
        BigDecimal fee,
        BigDecimal pricePerUnit,
        String reason
) {
    public static CommodityTransactionResult ok(int qty, BigDecimal total, BigDecimal fee, BigDecimal price) {
        return new CommodityTransactionResult(true, qty, total, fee, price, null);
    }

    public static CommodityTransactionResult fail(String reason) {
        return new CommodityTransactionResult(false, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, reason);
    }
}
