package me.scoltbr.scoltEconomys.commodity;

import java.math.BigDecimal;

/**
 * Resultado de uma operação de venda de commodity ao mercado global.
 *
 * @param success      Se a transação foi concluída com sucesso.
 * @param quantity     Quantidade efetivamente vendida.
 * @param net          Valor líquido creditado na carteira (descontada a taxa).
 * @param fee          Taxa de corretagem enviada ao Tesouro.
 * @param pricePerUnit Preço unitário no momento da venda.
 * @param reason       Código de falha (null se sucesso).
 */
public record SellCommodityResult(
        boolean success,
        int quantity,
        BigDecimal net,
        BigDecimal fee,
        BigDecimal pricePerUnit,
        String reason
) {
    public static SellCommodityResult ok(int qty, BigDecimal net, BigDecimal fee, BigDecimal price) {
        return new SellCommodityResult(true, qty, net, fee, price, null);
    }

    public static SellCommodityResult fail(String reason) {
        return new SellCommodityResult(false, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, reason);
    }
}
