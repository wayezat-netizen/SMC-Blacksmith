package com.simmc.blacksmith.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Handles all plugin messages with localization support.
 */
public class MessageConfig {

    // Furnace messages
    private String furnaceTitle;
    private String bellowsUsed;
    private String heatToolUsed;
    private String furnaceCreated;
    private String smeltingComplete;
    private String smeltingFailed;

    // Forge messages
    private String forgeStarted;
    private String forgeComplete;
    private String forgeRefunded;
    private String forgeSessionActive;
    private String forgeUnknownRecipe;
    private String forgePerfectHit;
    private String forgeGoodHit;
    private String forgeOkayHit;
    private String forgeMiss;

    // General messages
    private String noPermission;
    private String conditionNotMet;
    private String missingMaterials;

    public void load(FileConfiguration config) {
        // Furnace
        furnaceTitle = color(config.getString("furnace.title", "&8Custom Furnace"));
        bellowsUsed = color(config.getString("furnace.bellows_used", "&6Bellows pumped! Temperature rising..."));
        heatToolUsed = color(config.getString("furnace.heat_tool_used", "&6Heat tool used! Temperature boosted!"));
        furnaceCreated = color(config.getString("furnace.created", "&aFurnace created."));
        smeltingComplete = color(config.getString("furnace.smelting_complete", "&aSmelting complete!"));
        smeltingFailed = color(config.getString("furnace.smelting_failed", "&cSmelting failed - temperature was unstable."));

        // Forge
        forgeStarted = color(config.getString("forge.started", "&6Forging started! Strike the anvil!"));
        forgeComplete = color(config.getString("forge.complete", "&aForging complete! %stars% Rating: %display%"));
        forgeRefunded = color(config.getString("forge.refunded", "&eMaterials refunded."));
        forgeSessionActive = color(config.getString("forge.session_active", "&cYou already have an active forging session."));
        forgeUnknownRecipe = color(config.getString("forge.unknown_recipe", "&cUnknown recipe: %recipe%"));
        forgePerfectHit = color(config.getString("forge.perfect_hit", "&a&l★ PERFECT! ★"));
        forgeGoodHit = color(config.getString("forge.good_hit", "&aGood hit!"));
        forgeOkayHit = color(config.getString("forge.okay_hit", "&eOkay hit."));
        forgeMiss = color(config.getString("forge.miss", "&cMissed!"));

        // General
        noPermission = color(config.getString("general.no_permission", "&cYou don't have permission to do this."));
        conditionNotMet = color(config.getString("general.condition_not_met", "&cYou don't meet the requirements for this."));
        missingMaterials = color(config.getString("general.missing_materials", "&cMissing materials: %amount%x %item%"));
    }

    private String color(String text) {
        if (text == null) return "";
        return text.replace("&", "§");
    }

    // ==================== GETTERS ====================

    public String getFurnaceTitle() { return furnaceTitle; }
    public String getBellowsUsed() { return bellowsUsed; }
    public String getHeatToolUsed() { return heatToolUsed; }
    public String getFurnaceCreated() { return furnaceCreated; }
    public String getSmeltingComplete() { return smeltingComplete; }
    public String getSmeltingFailed() { return smeltingFailed; }

    public String getForgeStarted() { return forgeStarted; }

    public String getForgeComplete(int stars, String display) {
        return forgeComplete
                .replace("%stars%", String.valueOf(stars))
                .replace("%display%", display);
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

    public String getNoPermission() { return noPermission; }
    public String getConditionNotMet() { return conditionNotMet; }

    public String getMissingMaterials(int amount, String itemName) {
        return missingMaterials
                .replace("%amount%", String.valueOf(amount))
                .replace("%item%", itemName);
    }
}