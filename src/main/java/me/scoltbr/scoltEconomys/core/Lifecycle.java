package me.scoltbr.scoltEconomys.core;

import java.util.concurrent.atomic.AtomicBoolean;

public class Lifecycle {

    private final AtomicBoolean shuttingDown = new  AtomicBoolean(false);

    public boolean isShuttingDown() { return shuttingDown.get(); }

    public void shutDown() { shuttingDown.set(true); }

}
