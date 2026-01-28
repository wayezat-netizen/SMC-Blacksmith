package com.simmc.blacksmith.config;

import com.simmc.blacksmith.util.ColorUtil;
import org.bukkit.configuration.file.FileConfiguration;

public class MessageConfig {

    private String furnaceTitle;
    private String anvilTitle;
    private String vanillaAnvil;
    private String repairFailed;
    private String repairSuccess;
    private String noPermission;
    private String invalidItem;
    private String missingMaterials;

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
        return ColorUtil.colorize(furnaceTitle);
    }

    public String getAnvilTitle() {
        return ColorUtil.colorize(anvilTitle);
    }

    public String getVanillaAnvil() {
        return ColorUtil.colorize(vanillaAnvil);
    }

    public String getRepairFailed() {
        return ColorUtil.colorize(repairFailed);
    }

    public String getRepairSuccess() {
        return ColorUtil.colorize(repairSuccess);
    }

    public String getNoPermission() {
        return ColorUtil.colorize(noPermission);
    }

    public String getInvalidItem() {
        return ColorUtil.colorize(invalidItem);
    }

    public String getMissingMaterials(int count, String itemName) {
        return ColorUtil.colorize(String.format(missingMaterials, count, itemName));
    }
}