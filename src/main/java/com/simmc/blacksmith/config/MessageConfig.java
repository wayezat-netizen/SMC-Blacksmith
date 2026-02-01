package com.simmc.blacksmith.config;

import com.simmc.blacksmith.util.ColorUtil;
import org.bukkit.configuration.file.FileConfiguration;

import static com.simmc.blacksmith.util.ColorUtil.colorize;

public class MessageConfig {

    private String furnaceTitle;
    private String anvilTitle;
    private String vanillaAnvil;
    private String repairFailed;
    private String repairSuccess;
    private String noPermission;
    private String invalidItem;
    private String missingMaterials;
    private String activeSessionMessage;
    private String unknownRecipeMessage;
    private String forgeStartedMessage;

    public void load(FileConfiguration config) {
        furnaceTitle = config.getString("furnace_title", "Furnace");
        anvilTitle = config.getString("anvil_title", "Anvil");
        vanillaAnvil = config.getString("vanilla_anvil", "Shift click anvil to use normal anvil");
        repairFailed = config.getString("grindstone_repair_failed", "Repair failed!");
        repairSuccess = config.getString("grindstone_repair_success", "Repair Successful!");
        noPermission = config.getString("grindstone_no_perms", "You do not have permission to do this!");
        invalidItem = config.getString("grindstone_invalid", "This item cannot be repaired");
        missingMaterials = config.getString("grindstone_materials", "Missing materials! You need %d %s");
    }

    public String getFurnaceTitle() {
        return colorize(furnaceTitle);
    }

    public String getAnvilTitle() {
        return colorize(anvilTitle);
    }

    public String getVanillaAnvil() {
        return colorize(vanillaAnvil);
    }

    public String getRepairFailed() {
        return colorize(repairFailed);
    }

    public String getRepairSuccess() {
        return colorize(repairSuccess);
    }

    public String getNoPermission() {
        return colorize(noPermission);
    }

    public String getInvalidItem() {
        return colorize(invalidItem);
    }

    public String getMissingMaterials(int count, String itemName) {
        return colorize(String.format(missingMaterials, count, itemName));
    }

    public String getActiveSessionMessage() {
        return colorize(activeSessionMessage);
    }

    public String getUnknownRecipeMessage(String recipeId) {
        return colorize(unknownRecipeMessage.replace("%recipe%", recipeId));
    }

    public String getForgeStartedMessage() {
        return colorize(forgeStartedMessage);
    }
}