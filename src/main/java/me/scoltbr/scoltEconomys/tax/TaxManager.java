package me.scoltbr.scoltEconomys.tax;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Map;

public final class TaxManager {

    private final Map<TaxType, TaxPolicy> policies = new EnumMap<>(TaxType.class);
    private me.scoltbr.scoltEconomys.event.EventManager eventManager;

    public TaxManager(ConfigurationSection config) {
        if (config == null) return;

        loadTransfer(config.getConfigurationSection("transfer"));
        loadWithdraw(config.getConfigurationSection("withdraw"));
    }

    public void setEventManager(me.scoltbr.scoltEconomys.event.EventManager eventManager) {
        this.eventManager = eventManager;
    }

    private void loadTransfer(ConfigurationSection section) {
        if (section == null) {
            policies.put(TaxType.TRANSFER, new TaxPolicy(false, 0.0, 0.0, 0.0));
            return;
        }

        boolean enabled = section.getBoolean("enabled", true);
        double rate = section.getDouble("rate", 0.0);
        double minFee = section.getDouble("min-fee", 0.0);
        double maxFee = section.getDouble("max-fee", Double.MAX_VALUE);

        policies.put(TaxType.TRANSFER, new TaxPolicy(enabled, rate, minFee, maxFee));
    }

    private void loadWithdraw(ConfigurationSection section) {
        if (section == null) {
            policies.put(TaxType.WITHDRAW, new TaxPolicy(false, 0.0, 0.0, 0.0));
            return;
        }

        boolean enabled = section.getBoolean("enabled", true);
        double rate = section.getDouble("rate", 0.0);
        double minFee = section.getDouble("min-fee", 0.0);
        double maxFee = section.getDouble("max-fee", Double.MAX_VALUE);

        policies.put(TaxType.WITHDRAW, new TaxPolicy(enabled, rate, minFee, maxFee));
    }

    public TaxResult apply(TaxType type, double grossAmount) {
        TaxPolicy policy = policies.get(type);
        if (policy == null || !policy.enabled() || grossAmount <= 0) {
            return new TaxResult(grossAmount, 0.0);
        }

        double fee = grossAmount * policy.rate();
        
        // Aplica multiplicador do evento ativo
        if (eventManager != null) {
            fee *= eventManager.getTaxMultiplier();
        }
        
        fee = Math.max(policy.minFee(), Math.min(policy.maxFee(), fee));

        if (fee > grossAmount) fee = grossAmount;

        double net = grossAmount - fee;

        return new TaxResult(net, fee);
    }

    public TaxPolicy policy(TaxType type) {
        return policies.get(type);
    }

    public void setEnabled(TaxType type, boolean enabled) {
        TaxPolicy p = policies.get(type);
        if (p != null) p.setEnabled(enabled);
    }

    public void setRate(TaxType type, double rate) {
        TaxPolicy p = policies.get(type);
        if (p != null) p.setRate(clamp(rate, 0.0, 1.0));
    }

    public void setMinFee(TaxType type, double minFee) {
        TaxPolicy p = policies.get(type);
        if (p != null) p.setMinFee(Math.max(0.0, minFee));
    }

    public void setMaxFee(TaxType type, double maxFee) {
        TaxPolicy p = policies.get(type);
        if (p != null) p.setMaxFee(Math.max(0.0, maxFee));
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

}