package me.scoltbr.scoltEconomys.api;

import org.bukkit.Bukkit;

/**
 * Acesso estático simplificado para a API do ScoltEconomy.
 * <p>
 * Utilize esta classe para obter a instância da API sem precisar lidar com o ServicesManager do Bukkit.
 * </p>
 */
public final class ScoltAPI {
    private ScoltAPI() {}

    /**
     * Obtém a instância registrada da API de Economia.
     *
     * @return a instância de {@link ScoltEconomyAPI}.
     * @throws IllegalStateException se o plugin ainda não estiver habilitado ou a API não estiver registrada.
     */
    public static ScoltEconomyAPI get() {
        var reg = Bukkit.getServicesManager().getRegistration(ScoltEconomyAPI.class);
        if (reg == null) {
            throw new IllegalStateException("ScoltEconomyAPI não está registrada! O plugin está habilitado?");
        }
        return reg.getProvider();
    }
}
