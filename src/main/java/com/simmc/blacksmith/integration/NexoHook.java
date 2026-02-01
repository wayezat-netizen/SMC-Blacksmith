package com.simmc.blacksmith.integration;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

public class NexoHook {

    private final JavaPlugin plugin;
    private final boolean available;

    public NexoHook(JavaPlugin plugin) {
        this.plugin = plugin;
        this.available = plugin.getServer().getPluginManager().getPlugin("Nexo") != null;
    }

    public boolean isAvailable() {
        return available;
    }

    public ItemStack getItem(String id, int amount) {
        if (!available || id == null) {
            return null;
        }

        try {
            Class<?> nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
            Method itemByIdMethod = nexoItemsClass.getMethod("itemById", String.class);
            Object itemBuilder = itemByIdMethod.invoke(null, id);

            if (itemBuilder == null) {
                return null;
            }

            Method buildMethod = itemBuilder.getClass().getMethod("build");
            Object result = buildMethod.invoke(itemBuilder);

            if (result instanceof ItemStack item) {
                item.setAmount(Math.max(1, amount));
                return item;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get Nexo item '" + id + "': " + e.getMessage());
        }

        return null;
    }

    public String getItemId(ItemStack item) {
        if (!available || item == null) {
            return null;
        }

        try {
            Class<?> nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
            Method idFromItemMethod = nexoItemsClass.getMethod("idFromItem", ItemStack.class);
            Object result = idFromItemMethod.invoke(null, item);

            if (result instanceof String str) {
                return str;
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    public boolean matches(ItemStack item, String id) {
        if (!available || item == null || id == null) {
            return false;
        }

        String itemId = getItemId(item);
        return id.equalsIgnoreCase(itemId);
    }
}