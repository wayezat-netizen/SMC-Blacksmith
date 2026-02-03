package com.simmc.blacksmith.quench;

import com.simmc.blacksmith.forge.ForgeRecipe;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class QuenchingSession {

    private final UUID playerId;
    private final ItemStack forgedItem;
    private final int starRating;
    private final Location anvilLocation;
    private final ForgeRecipe recipe;
    private final long startTime;

    private boolean pickedUp;
    private String customName;

    public QuenchingSession(UUID playerId, ItemStack forgedItem, int starRating,
                            Location anvilLocation, ForgeRecipe recipe) {
        this.playerId = playerId;
        this.forgedItem = forgedItem.clone();
        this.starRating = starRating;
        this.anvilLocation = anvilLocation.clone();
        this.recipe = recipe;
        this.startTime = System.currentTimeMillis();
        this.pickedUp = false;
        this.customName = null;
    }

    public void pickup() {
        this.pickedUp = true;
    }

    public void setCustomName(String name) {
        this.customName = name;
    }

    public UUID getPlayerId() { return playerId; }
    public ItemStack getForgedItem() { return forgedItem.clone(); }
    public int getStarRating() { return starRating; }
    public Location getAnvilLocation() { return anvilLocation.clone(); }
    public ForgeRecipe getRecipe() { return recipe; }
    public long getStartTime() { return startTime; }
    public boolean isPickedUp() { return pickedUp; }
    public String getCustomName() { return customName; }

    public long getElapsedTime() {
        return System.currentTimeMillis() - startTime;
    }
}