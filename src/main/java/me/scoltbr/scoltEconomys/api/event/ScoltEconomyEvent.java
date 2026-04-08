package me.scoltbr.scoltEconomys.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for all ScoltEconomy custom events.
 */
public abstract class ScoltEconomyEvent extends Event {

    public ScoltEconomyEvent() {
        super();
    }

    public ScoltEconomyEvent(boolean isAsync) {
        super(isAsync);
    }

    private static final HandlerList HANDLERS = new HandlerList();

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
