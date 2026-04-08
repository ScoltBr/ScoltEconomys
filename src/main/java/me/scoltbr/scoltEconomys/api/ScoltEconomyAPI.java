package me.scoltbr.scoltEconomys.api;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * API Pública do ScoltEconomys.
 * <p>
 * Esta interface fornece acesso programático às funcionalidades de economia, banco e mercado de ações.
 * Os desenvolvedores podem utilizá-la para integrar seus próprios plugins ao sistema econômico do servidor.
 * </p>
 *
 * <p>Outros plugins podem obter uma instância via Bukkit ServicesManager:</p>
 * <pre>
 *   var reg = Bukkit.getServicesManager().getRegistration(ScoltEconomyAPI.class);
 *   if (reg != null) {
 *       ScoltEconomyAPI api = reg.getProvider();
 *   }
 * </pre>
 * <p>Ou via o accessor estático simplificado:</p>
 * <pre>
 *   ScoltEconomyAPI api = ScoltAPI.get();
 * </pre>
 */
public interface ScoltEconomyAPI {

    // -------------------------------------------------------
    // Versão
    // -------------------------------------------------------

    /**
     * Retorna a versão da especificação da API.
     *
     * @return versão da API (ex: "1.0").
     */
    String apiVersion();

    // -------------------------------------------------------
    // Carteira (síncrono — dados em cache, sem IO)
    // -------------------------------------------------------

    /**
     * Retorna o saldo da carteira de um jogador de forma síncrona.
     * <p>Note que este método requer que o jogador esteja online (e portanto em cache).
     * Se o jogador estiver offline e não constar no cache, retornará 0.0.
     * Para garantir a leitura de jogadores offline, prefira {@link #getWalletAsync}.</p>
     *
     * @param uuid UUID do jogador.
     * @return saldo atual na carteira, ou 0.0 se não encontrado no cache.
     */
    double getWallet(UUID uuid);

    /**
     * Retorna o saldo do banco de um jogador de forma síncrona.
     * <p>Requer que o jogador esteja em cache.</p>
     *
     * @param uuid UUID do jogador.
     * @return saldo no banco, ou 0.0 se não encontrado no cache.
     */
    double getBank(UUID uuid);

    /**
     * Adiciona uma quantia à carteira do jogador de forma síncrona.
     * <p>Dispara um {@code AccountBalanceChangeEvent}. Operação síncrona exige cache.</p>
     *
     * @param uuid   UUID do jogador.
     * @param amount Quantia positiva a depositar.
     */
    void depositWallet(UUID uuid, double amount);

    /**
     * Retira uma quantia da carteira do jogador de forma síncrona.
     *
     * @param uuid   UUID do jogador.
     * @param amount Quantia positiva a retirar.
     * @return {@code true} se a transação foi bem-sucedida, {@code false} se saldo insuficiente ou cancelada.
     */
    boolean withdrawWallet(UUID uuid, double amount);

    /**
     * Define o saldo da carteira do jogador exatamente para o valor especificado.
     *
     * @param uuid  UUID do jogador.
     * @param value Novo saldo (deve ser >= 0).
     */
    void setWallet(UUID uuid, double value);

    // -------------------------------------------------------
    // Carteira (assíncrono — para jogadores offline)
    // -------------------------------------------------------

    /**
     * Obtém o saldo da carteira de forma assíncrona, garantindo a leitura do banco de dados para jogadores offline.
     *
     * @param uuid     UUID do jogador.
     * @param callback Consumidor que receberá o valor (executado na Main Thread).
     */
    void getWalletAsync(UUID uuid, Consumer<Double> callback);

    // -------------------------------------------------------
    // Transferência
    // -------------------------------------------------------

    /**
     * Realiza uma transferência de valores entre jogadores aplicando as taxas de transferência configuradas.
     * <p>Esta operação é segura e processada na Main Thread após carregar os dados necessários.</p>
     *
     * @param from     UUID do remetente.
     * @param to       UUID do destinatário.
     * @param amount   Valor bruto a ser enviado.
     * @param callback Resultado da operação contendo sucesso, valor líquido e taxas.
     */
    void transfer(UUID from, UUID to, double amount, Consumer<TransferResult> callback);

    // -------------------------------------------------------
    // Banco
    // -------------------------------------------------------

    /**
     * Transfere dinheiro da carteira (Wallet) para o banco do jogador.
     *
     * @param uuid     UUID do jogador.
     * @param amount   Quantia a depositar no banco.
     * @param callback Resultado da operação bancária.
     */
    void depositToBank(UUID uuid, double amount, Consumer<BankResult> callback);

    /**
     * Retira dinheiro do banco para a carteira (Wallet), aplicando taxa de saque se configurado.
     *
     * @param uuid     UUID do jogador.
     * @param amount   Quantia bruta a retirar do banco.
     * @param callback Resultado da operação contendo o valor líquido recebido na carteira.
     */
    void withdrawFromBank(UUID uuid, double amount, Consumer<BankResult> callback);

    // -------------------------------------------------------
    // Tesouro
    // -------------------------------------------------------

    /**
     * Obtém o saldo acumulado no Tesouro do Servidor (vindo de impostos e taxas).
     *
     * @return saldo total do tesouro.
     */
    double getTreasuryBalance();

    // -------------------------------------------------------
    // Evento Econômico Ativo
    // -------------------------------------------------------

    /**
     * Retorna detalhes sobre o Evento Econômico que está ocorrendo no momento (ex: Juros Dobrados).
     *
     * @return Optional contendo {@link EcoEventInfo} se houver evento ativo, vazio do contrário.
     */
    Optional<EcoEventInfo> getActiveEvent();

    // -------------------------------------------------------
    // Mercado de Ações
    // -------------------------------------------------------

    /**
     * Verifica se o sistema de Mercado de Ações está habilitado no servidor.
     *
     * @return {@code true} se ativo.
     */
    boolean isStockMarketEnabled();

    /**
     * Obtém o preço atual de uma ação específica.
     *
     * @param stockId ID da empresa no config (ex: "SCOLT").
     * @return preço corrente, ou 0.0 se não existir ou módulo inativo.
     */
    double getStockPrice(String stockId);

    /**
     * Retorna a quantidade de ações ainda disponíveis para compra por jogadores.
     *
     * @param stockId ID da empresa.
     * @return quantidade disponível (supply restante).
     */
    long getStockAvailableShares(String stockId);

    /**
     * Obtém o portfólio completo de um jogador de forma assíncrona.
     *
     * @param uuid     UUID do investidor.
     * @param callback Mapa contendo {@link StockHoldingInfo} indexado pelo ID da empresa.
     */
    void getStockPortfolio(UUID uuid, Consumer<Map<String, StockHoldingInfo>> callback);

    // -------------------------------------------------------
    // DTOs públicos
    // -------------------------------------------------------

    /**
     * Resultado padronizado para operações de transferência entre jogadores.
     */
    record TransferResult(boolean success, double net, double fee, String reason) {
        public static TransferResult ok(double net, double fee) {
            return new TransferResult(true, net, fee, null);
        }
        public static TransferResult fail(String reason) {
            return new TransferResult(false, 0, 0, reason);
        }
    }

    /**
     * Resultado padronizado para depósitos e saques bancários.
     */
    record BankResult(boolean success, double net, double fee, String reason) {
        public static BankResult ok(double net, double fee) {
            return new BankResult(true, net, fee, null);
        }
        public static BankResult fail(String reason) {
            return new BankResult(false, 0, 0, reason);
        }
    }

    /**
     * Snapshot de um evento econômico ativo.
     */
    record EcoEventInfo(
            String id,
            String displayName,
            double interestMultiplier,
            double taxMultiplier,
            long remainingSeconds
    ) {}

    /**
     * Representação de uma posse de ações por um jogador.
     */
    record StockHoldingInfo(
            String stockId,
            long quantity,
            double avgPrice,
            double currentPrice
    ) {
        /** Calc o lucro/prejuízo monetário bruto da posição. */
        public double unrealizedPnl() {
            return (currentPrice - avgPrice) * quantity;
        }

        /** Calc a variação percentual em relação ao preço médio de compra. */
        public double pnlPercent() {
            if (avgPrice <= 0) return 0;
            return ((currentPrice - avgPrice) / avgPrice) * 100.0;
        }
    }
}
