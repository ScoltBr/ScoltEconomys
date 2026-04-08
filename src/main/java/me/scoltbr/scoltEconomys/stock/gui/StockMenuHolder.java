package me.scoltbr.scoltEconomys.stock.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** InventoryHolder que identifica os menus do Mercado de Ações. */
public final class StockMenuHolder implements InventoryHolder {

    public enum MenuType {
        MARKET,         // /bolsa — visão geral de todas as empresas
        COMPANY_DETAIL, // /bolsa info <id> — detalhe + gráfico + buy/sell
        PORTFOLIO,      // /bolsa carteira — portfólio do jogador
        TOP_HOLDERS     // /bolsa top <id> — maiores acionistas
    }

    private final MenuType type;
    private final String stockId;  // null para MARKET e PORTFOLIO

    public StockMenuHolder(MenuType type, String stockId) {
        this.type = type;
        this.stockId = stockId;
    }

    public MenuType type()    { return type; }
    public String  stockId()  { return stockId; }

    @Override
    public Inventory getInventory() { return null; }
}
