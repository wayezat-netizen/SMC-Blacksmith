package com.simmc.blacksmith.forge;

/**
 * Represents the output item for a specific star rating.
 */
public record ForgeResult(String id, String type, int amount) {

    public ForgeResult {
        if (id == null) id = "";
        if (type == null) type = "minecraft";
        if (amount < 1) amount = 1;
    }

    public boolean isValid() {
        return !id.isEmpty();
    }
}