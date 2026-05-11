package me.scoltbr.scoltEconomys.commodity;

import me.scoltbr.scoltEconomys.util.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class NewsService {

    private final Plugin plugin;
    private final CommodityMarketService market;
    private final List<NewsEvent> possibleEvents;

    public NewsService(Plugin plugin, CommodityMarketService market) {
        this.plugin = plugin;
        this.market = market;
        this.possibleEvents = List.of(
            new NewsEvent("AGRO", "<green>Safra Recorde!</green>", "A colheita superou as expectativas. <red>Oferta subiu, preços tendem a cair.</red>", -0.05),
            new NewsEvent("AGRO", "<red>Praga nas Plantações!</red>", "Insetos devastam campos de trigo. <green>Preços em alta devido à escassez.</green>", 0.08),
            new NewsEvent("MINING", "<aqua>Nova Veia de Minérios!</aqua>", "Mineradores descobriram depósitos massivos. <red>Preços de minérios em queda.</red>", -0.06),
            new NewsEvent("MINING", "<red>Desabamento em Minas!</red>", "Principais minas foram interditadas. <green>Produção paralisada, preços sobem.</green>", 0.09),
            new NewsEvent("ENERGY", "<yellow>Crise Energética!</yellow>", "O custo do combustível disparou globalmente. <green>Inflação em todos os setores.</green>", 0.04),
            new NewsEvent("LUXURY", "<light_purple>Leilão de Magnatas!</light_purple>", "A demanda por itens de luxo explodiu este mês. <green>Preços subindo!</green>", 0.07)
        );
    }

    public void start() {
        // Dispara uma notícia a cada 15-30 minutos
        long interval = 20 * 60 * 20; // 20 minutos base
        Bukkit.getScheduler().runTaskTimer(plugin, this::triggerRandomNews, interval, interval);
    }

    public void triggerRandomNews() {
        NewsEvent event = possibleEvents.get(ThreadLocalRandom.current().nextInt(possibleEvents.size()));
        broadcastNews(event);
        
        // Aplica o boost temporário ao setor (Fase 4/Refinamento)
        // Por enquanto, o boost de setor no CommodityMarketService é fixo ou vem do EventManager.
        // Vou integrar com o EventManager ou adicionar um método de boost dinâmico no MarketService.
        market.applyTemporarySectorBoost(event.sector(), event.impact(), 6000); // 5 minutos de impacto
    }

    private void broadcastNews(NewsEvent event) {
        Bukkit.broadcast(MessageUtils.parseRaw(""));
        Bukkit.broadcast(MessageUtils.parseRaw("<gold><bold>📰 NOTÍCIAS ECONÔMICAS</bold></gold>"));
        Bukkit.broadcast(MessageUtils.parseRaw("<gray>Setor:</gray> " + event.sectorDisplayName()));
        Bukkit.broadcast(MessageUtils.parseRaw("<white><b>" + event.headline() + "</b></white>"));
        Bukkit.broadcast(MessageUtils.parseRaw("<italic><gray>" + event.description() + "</gray></italic>"));
        Bukkit.broadcast(MessageUtils.parseRaw(""));
    }

    private record NewsEvent(String sector, String headline, String description, double impact) {
        String sectorDisplayName() {
            return switch (sector) {
                case "AGRO" -> "<green>Agricultura</green>";
                case "MINING" -> "<aqua>Mineração</aqua>";
                case "ENERGY" -> "<yellow>Energia</yellow>";
                case "LUXURY" -> "<light_purple>Luxo</light_purple>";
                default -> sector;
            };
        }
    }
}
