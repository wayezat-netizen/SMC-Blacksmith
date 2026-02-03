package com.simmc.blacksmith.repair;

public record RepairConfigData(
        String id,
        String itemId,
        String itemType,
        String condition,
        String successChancePlaceholder,
        String repairAmountPlaceholder,
        String inputId,
        String inputType,
        int inputAmount
) {
    public RepairConfigData {
        if (inputAmount < 1) inputAmount = 1;
        if (itemType == null) itemType = "minecraft";
        if (inputType == null) inputType = "minecraft";
    }

    public boolean hasCondition() {
        return condition != null && !condition.isEmpty();
    }

    public boolean hasSuccessChancePlaceholder() {
        return successChancePlaceholder != null && !successChancePlaceholder.isEmpty();
    }

    public boolean hasRepairAmountPlaceholder() {
        return repairAmountPlaceholder != null && !repairAmountPlaceholder.isEmpty();
    }

    public boolean hasInput() {
        return inputId != null && !inputId.isEmpty();
    }
}