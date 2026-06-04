package com.aryangpt007.journeymode.data;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface IJourneyData {
    String toJsonString();
    void fromNBT(NBTTagCompound nbt);
    NBTTagCompound toNBT();
    
    int getThreshold(Item item);
    int getThreshold(ItemStack stack);
    boolean depositItem(ItemStack stack);
    boolean isUnlocked(Item item);
    boolean isUnlocked(ItemStack stack);
    boolean isUnlocked(String key);
    int getCollectedCount(Item item);
    int getCollectedCount(ItemStack stack);
    Set<String> getUnlockedItems();
    List<String> getUnlockedItemsSorted();
    Map<String, Long> getUnlockTimestamps();
    int getProgress(Item item);
    int getProgress(ItemStack stack);
    Map<String, Integer> getAllCollectedCounts();
    
    void updateFromSync(Map<String, Integer> counts, Set<String> unlocked, Map<String, Long> timestamps);
    boolean isEnabled();
    void setEnabled(boolean enabled);
    void copyFrom(IJourneyData other);
    void reset();
}
