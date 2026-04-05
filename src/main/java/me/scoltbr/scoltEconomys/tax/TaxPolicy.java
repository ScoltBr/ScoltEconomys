package me.scoltbr.scoltEconomys.tax;

public final class TaxPolicy {

    private volatile boolean enabled;
    private volatile double rate;
    private volatile double minFee;
    private volatile double maxFee;

    public TaxPolicy(boolean enabled, double rate, double minFee, double maxFee) {
        this.enabled = enabled;
        this.rate = rate;
        this.minFee = minFee;
        this.maxFee = maxFee;
    }

    public boolean enabled() { return enabled; }
    public double rate() { return rate; }
    public double minFee() { return minFee; }
    public double maxFee() { return maxFee; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setRate(double rate) { this.rate = rate; }
    public void setMinFee(double minFee) { this.minFee = minFee; }
    public void setMaxFee(double maxFee) { this.maxFee = maxFee; }
}