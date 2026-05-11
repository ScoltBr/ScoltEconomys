package me.scoltbr.scoltEconomys.commodity;

import me.scoltbr.scoltEconomys.util.ItemBuilder;
import me.scoltbr.scoltEconomys.util.MoneyFormat;
import me.scoltbr.scoltEconomys.util.SparklineUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CommodityMenuService {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TEX_CLOSE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2VkMWFiYTczZjYzNGY0ZjQ0NjRiNDdhZjJhNWQ0NGMyNGM2MGFjYmQ4ZWIyOGQzMjdjNWMxMWRmYWViYTIzMSJ9fX0=";
    private static final String TEX_NEXT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTliZjMyOTM3MTUyYmI4YmI1NTUyYTliYjI3MjY4OGY1MjkwNDZlOTZiNjE0YTM2ZWMxMWRhYWRhZTQwZDU3NSJ9fX0=";
    private static final String TEX_PREV = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmQ2OWUwNmU1ZGFkZmQ4NGU1ZjNkMWMyMTA2M2YyNTUzYjJmYTk0NWVlMWQ0ZDcxNTJmZGM1NDI1YmMxMmE5In19fQ==";

    private final Plugin plugin;
    private final CommodityMarketService market;
    private final Map<UUID, BukkitRunnable> liveTasks = new HashMap<>();

    public CommodityMenuService(Plugin plugin, CommodityMarketService market) {
        this.plugin = plugin;
        this.market = market;
    }

    public void openMenu(Player player, int page) {
        Inventory inv = Bukkit.createInventory(new CommodityMenuHolder(page), 54, 
                MM.deserialize("<gradient:#ffcc00:#ff6600><b>Mercado de Commodities</b></gradient>"));
        renderPage(inv);
        player.openInventory(inv);
        startLiveRefresh(player);
    }

    public void startLiveRefresh(Player player) {
        cancelLiveRefresh(player);
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                Inventory top = player.getOpenInventory().getTopInventory();
                if (top.getHolder() instanceof CommodityMenuHolder) {
                    renderPage(top);
                } else {
                    cancel();
                }
            }
        };
        task.runTaskTimer(plugin, 20L, 40L); // a cada 2 segundos
        liveTasks.put(player.getUniqueId(), task);
    }

    public void cancelLiveRefresh(Player player) {
        BukkitRunnable task = liveTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    public void renderPage(Inventory inv) {
        if (!(inv.getHolder() instanceof CommodityMenuHolder holder)) return;
        int page = holder.page();

        List<Commodity> list = new ArrayList<>(market.getCommodities().values());
        int maxItemsPerPage = 21; // Usando os slots centrais
        int maxPages = (int) Math.ceil((double) list.size() / maxItemsPerPage);
        if (maxPages == 0) maxPages = 1;

        // Renderiza itens
        int[] slots = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34};
        int startIndex = (page - 1) * maxItemsPerPage;

        for (int i = 0; i < maxItemsPerPage; i++) {
            int listIndex = startIndex + i;
            if (listIndex < list.size()) {
                inv.setItem(slots[i], buildCommodityIcon(list.get(listIndex)));
            } else {
                inv.setItem(slots[i], new ItemStack(Material.AIR));
            }
        }

        // Borda e navegação
        fillBorders(inv, 6);

        if (page > 1) {
            inv.setItem(45, ItemBuilder.of(Material.PLAYER_HEAD).texture(TEX_PREV).name("<yellow><b>Página Anterior</b></yellow>").build());
        } else {
            inv.setItem(45, ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        }

        if (page < maxPages) {
            inv.setItem(53, ItemBuilder.of(Material.PLAYER_HEAD).texture(TEX_NEXT).name("<yellow><b>Próxima Página</b></yellow>").build());
        } else {
            // Se for última página, slot 53 é fechar
            inv.setItem(53, ItemBuilder.of(Material.PLAYER_HEAD).texture(TEX_CLOSE).name("<red><b>Fechar</b></red>").build());
        }

        // Se slot 53 for "Próxima Página", coloca o Fechar no 49
        if (page < maxPages) {
            inv.setItem(49, ItemBuilder.of(Material.PLAYER_HEAD).texture(TEX_CLOSE).name("<red><b>Fechar</b></red>").build());
        }
    }

    private ItemStack buildCommodityIcon(Commodity c) {
        BigDecimal currentPrice = market.currentPrice(c.id());
        BigDecimal initialPrice = c.initialPrice();
        
        double changePct = 0;
        if (initialPrice.compareTo(BigDecimal.ZERO) > 0) {
            changePct = (currentPrice.doubleValue() - initialPrice.doubleValue()) / initialPrice.doubleValue() * 100;
        }

        String trend = changePct >= 0 ? "<green>▲" : "<red>▼";
        String pctStr = String.format("%.1f%%", Math.abs(changePct));
        String colorTheme = changePct >= 0 ? "<green>" : "<red>";

        String spark = SparklineUtil.generate(market.getSparkline(c.id()));

        return ItemBuilder.of(c.material())
                .name("<gold><b>" + c.displayName() + "</b></gold>")
                .lore(
                    "<dark_gray>Setor: " + c.sector().toUpperCase() + "</dark_gray>",
                    "",
                    "<gray>Preço Unitário:</gray> <white><b>$ " + MoneyFormat.format(currentPrice) + "</b></white>",
                    "<gray>Variação:</gray> " + trend + " " + colorTheme + pctStr + "</" + colorTheme.substring(1),
                    !spark.isEmpty() ? "<dark_gray>" + spark + "</dark_gray>" : "",
                    "<gray>Taxa (Compra/Venda):</gray> <yellow>" + String.format("%.1f%%", c.brokerageFee().doubleValue() * 100) + "</yellow>",
                    "",
                    "<gray>Limite por transação: <white>" + c.maxPerTransaction() + "x</white></gray>",
                    "",
                    "<green><b>Botão Esquerdo:</b></green> <gray>Vender 1x</gray>",
                    "<aqua><b>Shift + Clique Esq:</b></aqua> <gray>Vender tudo</gray>",
                    "<red><b>Botão Direito:</b></red> <gray>Comprar 1x</gray>",
                    "<gold><b>Shift + Clique Dir:</b></gold> <gray>Comprar 64x</gray>",
                    "",
                    "<dark_gray>ID: " + c.id() + "</dark_gray>"
                )
                .build();
    }

    private void fillBorders(Inventory inv, int rows) {
        ItemStack borderOuter = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        ItemStack borderInner = ItemBuilder.of(Material.ORANGE_STAINED_GLASS_PANE).name(" ").build();

        for (int i = 0; i < rows * 9; i++) {
            int r = i / 9;
            int c = i % 9;
            if (r == 0 || r == rows - 1 || c == 0 || c == 8) {
                if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                    inv.setItem(i, borderOuter);
                }
            } else if (r == 1 || r == rows - 2 || c == 1 || c == 7) {
                if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                    inv.setItem(i, borderInner);
                }
            }
        }
    }
}
