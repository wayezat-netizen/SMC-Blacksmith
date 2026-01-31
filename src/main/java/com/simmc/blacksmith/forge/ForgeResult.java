package com.simmc.blacksmith.forge;

public record ForgeResult(
        String id,
        String type,
        int amount
) {
    public ForgeResult {
        if (amount < 1) amount = 1;
    }
}