package me.scoltbr.scoltEconomys.stock;

/**
 * Definição imutável de uma empresa listada na bolsa virtual.
 *
 * @param id            Identificador único (config key)
 * @param displayName   Nome colorido (MiniMessage)
 * @param sector        Setor econômico (ex: "bancario", "comercio")
 * @param initialPrice  Preço de abertura / preço reset
 * @param volatility    Desvio-padrão base para o drift aleatório (0.01–0.30)
 * @param totalShares   Oferta total de ações (finita)
 * @param brokerageFee  Taxa de corretagem em decimal (ex: 0.01 = 1%)
 */
public record Stock(
        String id,
        String displayName,
        String sector,
        double initialPrice,
        double volatility,
        long totalShares,
        double brokerageFee
) {}
