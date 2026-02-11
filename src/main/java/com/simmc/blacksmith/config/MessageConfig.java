package com.simmc.blacksmith.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Message configuration for all plugin messages.
 */
public class MessageConfig {

    // Furnace
    private String furnaceTitle;
    private String bellowsUsed;
    private String bellowsBroke;
    private String heatToolUsed;
    private String smeltingComplete;
    private String smeltingFailed;
    private String bellowsNoFuel;

    // Forge
    private String forgeStarted;
    private String forgeComplete;
    private String forgeRefunded;
    private String forgeSessionActive;
    private String forgeUnknownRecipe;
    private String forgePerfectHit;
    private String forgeGoodHit;
    private String forgeOkayHit;
    private String forgeMiss;

    // Repair
    private String repairSuccess;
    private String repairFailed;
    private String invalidItem;

    // General
    private String noPermission;
    private String conditionNotMet;
    private String missingMaterials;
    private String vanillaAnvil;

    public void load(FileConfiguration config) {
        // Furnace
        furnaceTitle = color(config.getString("furnace.title", "&8Custom Furnace"));
        bellowsUsed = color(config.getString("furnace.bellows_used", "&6Temperature rising..."));
        bellowsBroke = color(config.getString("furnace.bellows_broke", "&cYour bellows broke!"));
        heatToolUsed = color(config.getString("furnace.heat_tool_used", "&6Temperature boosted!"));
        smeltingComplete = color(config.getString("furnace.smelting_complete", "&aSmelting complete!"));
        smeltingFailed = color(config.getString("furnace.smelting_failed", "&cSmelting failed!"));
        this.bellowsNoFuel = color(config.getString("bellows.no_fuel", "&c&l⚠ &cAdd fuel first! Bellows need heat to work."));
        // Forge
        forgeStarted = color(config.getString("forge.started", "&6Forging started!"));
        forgeComplete = color(config.getString("forge.complete", "&aForging complete! Quality: %display%"));
        forgeRefunded = color(config.getString("forge.refunded", "&eMaterials refunded."));
        forgeSessionActive = color(config.getString("forge.session_active", "&cYou already have an active session."));
        forgeUnknownRecipe = color(config.getString("forge.unknown_recipe", "&cUnknown recipe: %recipe%"));
        forgePerfectHit = color(config.getString("forge.perfect_hit", "&a&l★ PERFECT! ★"));
        forgeGoodHit = color(config.getString("forge.good_hit", "&aGood hit!"));
        forgeOkayHit = color(config.getString("forge.okay_hit", "&eOkay hit."));
        forgeMiss = color(config.getString("forge.miss", "&cMissed!"));

        // Repair
        repairSuccess = color(config.getString("repair.success", "&aRepair successful!"));
        repairFailed = color(config.getString("repair.failed", "&cRepair failed!"));
        invalidItem = color(config.getString("repair.invalid_item", "&cThis item cannot be repaired."));

        // General
        noPermission = color(config.getString("general.no_permission", "&cNo permission."));
        conditionNotMet = color(config.getString("general.condition_not_met", "&cRequirements not met."));
        missingMaterials = color(config.getString("general.missing_materials", "&cMissing: %amount%x %item%"));

        // FIX: Use same pattern as other messages
        vanillaAnvil = color(config.getString("general.vanilla_anvil", "&7Using vanilla grindstone..."));
    }

    private String color(String text) {
        return text != null ? text.replace("&", "§") : "";
    }

    // ==================== FURNACE ====================

    public String getFurnaceTitle() { return furnaceTitle; }
    public String getBellowsUsed() { return bellowsUsed; }
    public String getBellowsBroke() { return bellowsBroke; }
    public String getHeatToolUsed() { return heatToolUsed; }
    public String getSmeltingComplete() { return smeltingComplete; }
    public String getSmeltingFailed() { return smeltingFailed; }

    // ==================== FORGE ====================

    public String getForgeStarted() { return forgeStarted; }

    public String getForgeComplete(int stars, String display) {
        return forgeComplete
                .replace("%stars%", String.valueOf(stars))
                .replace("%display%", display)
                .replace("%s", display);
    }

    public String getForgeRefunded() { return forgeRefunded; }
    public String getForgeSessionActive() { return forgeSessionActive; }

    public String getForgeUnknownRecipe(String recipeId) {
        return forgeUnknownRecipe.replace("%recipe%", recipeId);
    }

    public String getForgePerfectHit() { return forgePerfectHit; }
    public String getForgeGoodHit() { return forgeGoodHit; }
    public String getForgeOkayHit() { return forgeOkayHit; }
    public String getForgeMiss() { return forgeMiss; }

    // ==================== REPAIR ====================

    public String getRepairSuccess() { return repairSuccess; }
    public String getRepairFailed() { return repairFailed; }
    public String getInvalidItem() { return invalidItem; }

    // ==================== GENERAL ====================

    public String getNoPermission() { return noPermission; }
    public String getConditionNotMet() { return conditionNotMet; }

    public String getMissingMaterials(int amount, String itemName) {
        return missingMaterials
                .replace("%amount%", String.valueOf(amount))
                .replace("%item%", itemName);
    }

    public String getVanillaAnvil() {
        return vanillaAnvil;
    }

    public String getBellowsNoFuel() {
        return bellowsNoFuel;
    }
}