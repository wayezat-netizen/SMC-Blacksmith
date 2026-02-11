package com.simmc.blacksmith.repair;

/**
 * Immutable configuration data for a repair recipe.
 * Supports PAPI placeholders for dynamic success chance and repair amount.
 */
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
    /**
     * Compact constructor with validation and defaults.
     */
    public RepairConfigData {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Repair config id cannot be null or empty");
        }
        if (itemId == null || itemId.isEmpty()) {
            throw new IllegalArgumentException("Item id cannot be null or empty");
        }

        itemType = normalizeType(itemType);
        inputType = normalizeType(inputType);
        inputAmount = Math.max(1, inputAmount);
    }

    private static String normalizeType(String type) {
        if (type == null || type.isEmpty()) {
            return "minecraft";
        }
        return type.toLowerCase();
    }

    // ==================== CHECKS ====================

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

    public boolean requiresMaterials() {
        return hasInput() && inputAmount > 0;
    }

    // ==================== DISPLAY ====================

    /**
     * Gets a formatted display name for the input material.
     */
    public String getInputDisplayName() {
        if (!hasInput()) return "None";
        return inputAmount + "x " + formatId(inputId);
    }

    private String formatId(String id) {
        if (id == null) return "Unknown";
        return id.replace("_", " ");
    }
}