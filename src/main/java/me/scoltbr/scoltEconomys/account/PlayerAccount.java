package me.scoltbr.scoltEconomys.account;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PlayerAccount {

    private final UUID uuid;

    private BigDecimal wallet;
    private BigDecimal bank;

    private Instant lastUpdate;
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public PlayerAccount(UUID uuid, BigDecimal wallet, BigDecimal bank, Instant lastUpdate) {
        this.uuid = uuid;
        this.wallet = wallet.setScale(2, RoundingMode.HALF_UP);
        this.bank = bank.setScale(2, RoundingMode.HALF_UP);
        this.lastUpdate = lastUpdate;
    }

    public UUID uuid() { return uuid; }

    public BigDecimal wallet() { return wallet; }
    public BigDecimal bank() { return bank; }

    public Instant lastUpdate() { return lastUpdate; }

    public boolean isDirty() { return dirty.get(); }
    public void markDirty() { dirty.set(true); }
    public void clearDirty() { dirty.set(false); }

    public void setWallet(BigDecimal wallet) { 
        this.wallet = wallet.setScale(2, RoundingMode.HALF_UP); 
        touch(); 
    }
    public void setBank(BigDecimal bank) { 
        this.bank = bank.setScale(2, RoundingMode.HALF_UP); 
        touch(); 
    }

    public void addWallet(BigDecimal amount) { 
        this.wallet = this.wallet.add(amount).setScale(2, RoundingMode.HALF_UP); 
        touch(); 
    }
    public void addBank(BigDecimal amount) { 
        this.bank = this.bank.add(amount).setScale(2, RoundingMode.HALF_UP); 
        touch(); 
    }

    private void touch() {
        this.lastUpdate = Instant.now();
        markDirty();
    }
}