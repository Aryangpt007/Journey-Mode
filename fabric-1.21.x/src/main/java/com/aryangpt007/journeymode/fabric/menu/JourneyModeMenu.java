package com.aryangpt007.journeymode.fabric.menu;

import com.aryangpt007.journeymode.fabric.JourneyModeFabric;
import com.aryangpt007.journeymode.core.JourneyData;
import com.aryangpt007.journeymode.logic.ConfigHandler;
import com.aryangpt007.journeymode.fabric.platform.FabricDataHandler;
import com.aryangpt007.journeymode.fabric.network.packets.SyncJourneyDataPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for Journey Mode screen with deposit slot
 */
public class JourneyModeMenu extends AbstractContainerMenu {
    private final Player player;
    private JourneyData journeyData;
    private boolean depositSlotEnabled = true;

    // Custom slot that can be disabled
    private static class ConditionalSlot extends Slot {
        private final JourneyModeMenu menu;

        public ConditionalSlot(JourneyModeMenu menu, Container container, int slot, int x, int y) {
            super(container, slot, x, y);
            this.menu = menu;
        }

        @Override
        public boolean isActive() {
            return menu.depositSlotEnabled;
        }
    }

    // Simple single-slot inventory for depositing items
    private final Container depositSlot = new Container() {
        private ItemStack stack = ItemStack.EMPTY;

        @Override
        public int getContainerSize() { return 1; }

        @Override
        public boolean isEmpty() { return stack.isEmpty(); }

        @Override
        public ItemStack getItem(int slot) { return stack; }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack result = stack.split(amount);
            setChanged();
            return result;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack result = stack;
            stack = ItemStack.EMPTY;
            return result;
        }

        @Override
        public void setItem(int slot, ItemStack itemStack) {
            stack = itemStack;
            setChanged();
        }

        @Override
        public void setChanged() {}

        @Override
        public boolean stillValid(Player player) { return true; }

        @Override
        public void clearContent() { stack = ItemStack.EMPTY; }
    };

    public JourneyModeMenu(int containerId, Inventory playerInventory) {
        super(JourneyModeFabric.JOURNEY_MODE_MENU, containerId);
        this.player = playerInventory.player;
        this.journeyData = FabricDataHandler.getJourneyData(player);

        // Add deposit slot (center top of screen) - don't auto-process on change
        this.addSlot(new ConditionalSlot(this, depositSlot, 0, 80, 18));

        // Add player inventory slots (positioned with proper spacing for taller GUI)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 110 + row * 18));
            }
        }

        // Add player hotbar slots (positioned below inventory with proper spacing)
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 168));
        }
        
        // Sync data to client when menu opens
        if (player instanceof ServerPlayer serverPlayer) {
            syncDataToClient(serverPlayer);
        }
    }
    
    private void syncDataToClient(ServerPlayer player) {
        // Get fresh data
        JourneyData data = FabricDataHandler.getJourneyData(player);
        ServerPlayNetworking.send(player, new SyncJourneyDataPacket(
            data.getCollectedCounts(),
            data.getUnlockedItems(),
            data.getUnlockTimestamps()
        ));
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        // Don't auto-process items - only process on submit button click
    }
    
    /**
     * Process the deposit (called from server via packet)
     */
    public void processDeposit() {
        if (player.level().isClientSide) return;
        
        ItemStack stack = depositSlot.getItem(0);
        if (!stack.isEmpty()) {
            // Get fresh journey data
            JourneyData data = FabricDataHandler.getJourneyData(player);
            
            // Check if blacklisted
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (ConfigHandler.isBlacklisted(itemId)) {
                player.displayClientMessage(
                    JourneyModeFabric.translatable("blacklist_message", stack.getHoverName()),
                    false
                );
                return; // Don't consume the item
            }
            
            // Check if already unlocked
            if (data.isUnlocked(itemId)) {
                player.displayClientMessage(
                    Component.literal("§e" + stack.getHoverName().getString() + " is already unlocked!"),
                    false
                );
                return; // Don't consume the item
            }
            
            // Calculate threshold using platform abstraction
            com.aryangpt007.journeymode.logic.ThresholdCalculator calculator = 
                new com.aryangpt007.journeymode.logic.ThresholdCalculator(
                    player.level().getRecipeManager(),
                    player.level().registryAccess()
                );
            int threshold = calculator.calculateThreshold(stack.getItem());
            
            // Deposit the items
            boolean unlocked = data.depositItem(itemId, stack.getCount(), threshold);
            
            // Save the updated data
            FabricDataHandler.saveJourneyData(player, data);
            
            // Clear the deposit slot
            depositSlot.setItem(0, ItemStack.EMPTY);
            
            if (unlocked) {
                player.displayClientMessage(
                    JourneyModeFabric.translatable("unlock_message", stack.getHoverName(), threshold),
                    false
                );
            } else {
                int progress = data.getProgress(itemId, threshold);
                int collected = data.getCollectedCount(itemId);
                player.displayClientMessage(
                    JourneyModeFabric.translatable("deposit_message", stack.getCount(), stack.getHoverName(), collected, threshold, progress),
                    true // Action bar
                );
            }
            
            // Sync updated data to client
            if (player instanceof ServerPlayer serverPlayer) {
                syncDataToClient(serverPlayer);
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        
        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemstack = slotStack.copy();

            // Slot 0 is deposit slot
            // Slots 1-27 are player inventory
            // Slots 28-36 are player hotbar
            
            if (index == 0) {
                // Moving FROM deposit slot TO player inventory
                if (!this.moveItemStackTo(slotStack, 1, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Moving FROM player inventory TO deposit slot
                if (!this.moveItemStackTo(slotStack, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
    
    @Override
    public void removed(Player player) {
        super.removed(player);
        // Return items from deposit slot to player when menu closes
        if (!player.level().isClientSide) {
            ItemStack depositedItem = this.depositSlot.getItem(0);
            if (!depositedItem.isEmpty()) {
                player.getInventory().placeItemBackInInventory(depositedItem);
                this.depositSlot.setItem(0, ItemStack.EMPTY);
            }
        }
    }

    public JourneyData getJourneyData() {
        // Refresh data from attachment
        this.journeyData = FabricDataHandler.getJourneyData(player);
        return journeyData;
    }

    /**
     * Enable or disable the deposit slot (called from client screen when tab changes)
     */
    public void setDepositSlotEnabled(boolean enabled) {
        this.depositSlotEnabled = enabled;
    }
}
