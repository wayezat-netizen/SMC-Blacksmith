package com.simmc.blacksmith.quench;

import com.simmc.blacksmith.forge.ForgeRecipe;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Represents an active quenching/naming session.
 * Tracks the forged item, star rating, and naming state.
 */
public class QuenchingSession {

    public enum State {
        GUI_OPEN,       // Player viewing the naming GUI
        AWAITING_NAME,  // Player typing name in chat
        COMPLETED       // Session complete
    }

    private final UUID playerId;
    private final ItemStack forgedItem;
    private final int starRating;
    private final Location anvilLocation;
    private final ForgeRecipe recipe;
    private final long startTime;

    private State state;
    private String customName;
    private long chatInputStartTime;

    public QuenchingSession(UUID playerId, ItemStack forgedItem, int starRating,
                            Location anvilLocation, ForgeRecipe recipe) {
        this.playerId = playerId;
        this.forgedItem = forgedItem.clone();
        this.starRating = Math.max(0, Math.min(5, starRating));
        this.anvilLocation = anvilLocation != null ? anvilLocation.clone() : null;
        this.recipe = recipe;
        this.startTime = System.currentTimeMillis();
        this.state = State.GUI_OPEN;
        this.customName = null;
        this.chatInputStartTime = 0;
    }

    // ==================== STATE TRANSITIONS ====================

    /**
     * Transitions to awaiting name input state.
     */
    public void awaitNameInput() {
        if (state != State.COMPLETED) {
            this.state = State.AWAITING_NAME;
            this.chatInputStartTime = System.currentTimeMillis();
        }
    }


    /**
     * Sets the custom name.
     */
    public void setCustomName(String name) {
        this.customName = name != null ? name.trim() : null;
    }

    /**
     * Marks the session as completed.
     */
    public void complete() {
        this.state = State.COMPLETED;
    }

    // ==================== STATE CHECKS ====================

    public boolean isAwaitingName() {
        return state == State.AWAITING_NAME;
    }

    public boolean isGuiOpen() {
        return state == State.GUI_OPEN;
    }

    public boolean isCompleted() {
        return state == State.COMPLETED;
    }

    public boolean isActive() {
        return state != State.COMPLETED;
    }

    public boolean hasCustomName() {
        return customName != null && !customName.isEmpty();
    }

    // ==================== GETTERS ====================

    public UUID getPlayerId() { return playerId; }
    public ItemStack getForgedItem() { return forgedItem.clone(); }
    public int getStarRating() { return starRating; }
    public ForgeRecipe getRecipe() { return recipe; }
    public long getStartTime() { return startTime; }
    public State getState() { return state; }
    public String getCustomName() { return customName; }
    public long getChatInputStartTime() { return chatInputStartTime; }

    public Location getAnvilLocation() {
        return anvilLocation != null ? anvilLocation.clone() : null;
    }

    public long getElapsedTime() {
        return System.currentTimeMillis() - startTime;
    }

    public long getElapsedSeconds() {
        return getElapsedTime() / 1000;
    }
}