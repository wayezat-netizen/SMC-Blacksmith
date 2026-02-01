package com.simmc.blacksmith.repair;

public record RepairConfigData(
        String id,
        String permission,
        String itemId,
        String itemType,
        String repairChancePermission,
        String inputId,
        String inputType,
        int inputAmount
) {
    public RepairConfigData {
        if (inputAmount < 1) inputAmount = 1;
    }

    public boolean hasPermission() {
        return permission != null && !permission.isEmpty();
    }

    public boolean hasRepairChancePermission() {
        return repairChancePermission != null && !repairChancePermission.isEmpty();
    }

    public boolean hasInput() {
        return inputId != null && !inputId.isEmpty();
    }
}