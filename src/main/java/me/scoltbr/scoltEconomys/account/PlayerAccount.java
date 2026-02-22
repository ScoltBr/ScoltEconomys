package me.scoltbr.scoltEconomys.account;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PlayerAccount {

    private final UUID uuid;

    private double wallet;
    private double bank;

    private Instant lastUpdate;
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public PlayerAccount(UUID uuid, double wallet, double bank, Instant lastUpdate) {
        this.uuid = uuid;
        this.wallet = wallet;
        this.bank = bank;
        this.lastUpdate = lastUpdate;
    }

    public UUID uuid() { return uuid; }

    public double wallet() { return wallet; }
    public double bank() { return bank; }

    public Instant lastUpdate() { return lastUpdate; }

    public boolean isDirty() { return dirty.get(); }
    public void markDirty() { dirty.set(true); }
    public void clearDirty() { dirty.set(false); }

    public void setWallet(double wallet) { this.wallet = wallet; touch(); }
    public void setBank(double bank) { this.bank = bank; touch(); }

    public void addWallet(double amount) { this.wallet += amount; touch(); }
    public void addBank(double amount) { this.bank += amount; touch(); }

    private void touch() {
        this.lastUpdate = Instant.now();
        markDirty();
    }
}