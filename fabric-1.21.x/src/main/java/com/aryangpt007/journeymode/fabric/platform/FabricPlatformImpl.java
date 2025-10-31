package com.aryangpt007.journeymode.fabric.platform;

import com.aryangpt007.journeymode.core.JourneyData;
import com.aryangpt007.journeymode.logic.ConfigHandler;
import com.aryangpt007.journeymode.platform.PlatformHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Fabric implementation of the platform abstraction layer.
 */
public class FabricPlatformImpl implements PlatformHelper {
    
    @Override
    public JourneyData getPlayerData(Object player) {
        if (!(player instanceof Player mcPlayer)) {
            throw new IllegalArgumentException("Expected Player, got " + player.getClass());
        }
        
        return FabricDataHandler.getJourneyData(mcPlayer);
    }
    
    @Override
    public void savePlayerData(Object player, JourneyData data) {
        if (!(player instanceof Player mcPlayer)) {
            throw new IllegalArgumentException("Expected Player, got " + player.getClass());
        }
        
        FabricDataHandler.saveJourneyData(mcPlayer, data);
    }
    
    @Override
    public void sendToClient(Object player, Object packet) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            throw new IllegalArgumentException("Expected ServerPlayer, got " + player.getClass());
        }
        
        FabricNetworkHandler.sendToClient(serverPlayer, packet);
    }
    
    @Override
    public void sendToServer(Object packet) {
        FabricNetworkHandler.sendToServer(packet);
    }
    
    @Override
    public void openJourneyGui(Object player) {
        if (!(player instanceof Player mcPlayer)) {
            throw new IllegalArgumentException("Expected Player, got " + player.getClass());
        }
        
        FabricGuiHandler.openJourneyGui(mcPlayer);
    }
    
    @Override
    public Object getAllRecipes(Object level) {
        if (!(level instanceof Level mcLevel)) {
            throw new IllegalArgumentException("Expected Level, got " + level.getClass());
        }
        
        return mcLevel.getRecipeManager().getRecipes();
    }
    
    @Override
    public Object getRecipeManager(Object level) {
        if (!(level instanceof Level mcLevel)) {
            throw new IllegalArgumentException("Expected Level, got " + level.getClass());
        }
        
        return mcLevel.getRecipeManager();
    }
    
    @Override
    public Object getRegistryAccess(Object level) {
        if (!(level instanceof Level mcLevel)) {
            throw new IllegalArgumentException("Expected Level, got " + level.getClass());
        }
        
        return mcLevel.registryAccess();
    }
    
    @Override
    public String getItemId(Object item) {
        if (item instanceof Item mcItem) {
            return BuiltInRegistries.ITEM.getKey(mcItem).toString();
        } else if (item instanceof net.minecraft.world.item.ItemStack stack) {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }
        throw new IllegalArgumentException("Expected Item or ItemStack, got " + item.getClass());
    }
    
    @Override
    public Object getItemById(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return null;
        }
        return BuiltInRegistries.ITEM.get(id);
    }
    
    @Override
    public int getMaxStackSize(Object item) {
        if (!(item instanceof Item mcItem)) {
            throw new IllegalArgumentException("Expected Item, got " + item.getClass());
        }
        
        return mcItem.getDefaultMaxStackSize();
    }
    
    @Override
    public boolean isBlacklisted(String itemId) {
        return ConfigHandler.isBlacklisted(itemId);
    }
    
    @Override
    public Integer getThresholdOverride(String itemId) {
        return ConfigHandler.getThresholdOverride(itemId);
    }
    
    @Override
    public String getPlatformName() {
        return "fabric";
    }
    
    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }
    
    @Override
    public boolean isServer() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
    }
}
