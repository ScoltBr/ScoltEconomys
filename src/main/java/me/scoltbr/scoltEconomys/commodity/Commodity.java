package me.scoltbr.scoltEconomys.commodity;

import org.bukkit.Material;

import java.math.BigDecimal;

/**
 * Definição imutável de uma commodity negociável no mercado global.
 *
 * @param id                 Identificador único (chave no config.yml)
 * @param displayName        Nome colorido (MiniMessage)
 * @param sector             Setor econômico — conecta com os boosts de eventos (ex: "energia", "metais", "raros")
 * @param material           Material do Minecraft que representa esta commodity
 * @param initialPrice       Preço inicial / preço de equilíbrio (usado para mean-reversion)
 * @param volatility         Intensidade da variação aleatória por tick (0.01–0.20)
 * @param maxPerTransaction  Quantidade máxima vendável por transação (anti-dump)
 * @param brokerageFee       Taxa de corretagem em decimal enviada ao Tesouro (ex: 0.02 = 2%)
 */
public record Commodity(
        String id,
        String displayName,
        String sector,
        Material material,
        BigDecimal initialPrice,
        double volatility,
        int maxPerTransaction,
        BigDecimal brokerageFee
) {}
