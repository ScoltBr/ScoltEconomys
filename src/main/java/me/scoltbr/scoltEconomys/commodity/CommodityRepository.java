package me.scoltbr.scoltEconomys.commodity;

import java.util.List;

/**
 * Contrato de persistência do Mercado de Commodities.
 * Implementado por {@link CommodityRepositorySql}.
 */
public interface CommodityRepository {

    /** Persiste um snapshot de preço. */
    void savePrice(CommodityPrice price);

    /**
     * Retorna os {@code limit} snapshots mais recentes de uma commodity,
     * ordenados do mais antigo para o mais novo (conveniente para gráficos).
     */
    List<CommodityPrice> getHistory(String commodityId, int limit);

    /** Registra uma transação de venda ou compra para auditoria. */
    void recordTransaction(java.util.UUID uuid,
                           String commodityId,
                           String type,
                           int quantity,
                           java.math.BigDecimal pricePerUnit,
                           java.math.BigDecimal total);

    /** Remove snapshots antigos, mantendo apenas os {@code keep} mais recentes. */
    void purgeOldPrices(String commodityId, int keep);
}
