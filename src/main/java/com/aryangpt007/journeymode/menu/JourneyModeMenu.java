package com.aryangpt007.journeymode.menu;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import com.aryangpt007.journeymode.network.packets.SyncJourneyDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Menu for Journey Mode screen with deposit slot
 */
public class JourneyModeMenu extends AbstractContainerMenu {
    private final Player player;
    private final JourneyDataAttachment journeyData;

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
        super(JourneyMode.JOURNEY_MODE_MENU.get(), containerId);
        this.player = playerInventory.player;
        this.journeyData = player.getData(JourneyMode.JOURNEY_DATA);

        // Add deposit slot (center top of screen) - don't auto-process on change
        this.addSlot(new Slot(depositSlot, 0, 80, 18));

        // Add player inventory slots
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Add player hotbar slots
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
        
        // Sync data to client when menu opens
        if (player instanceof ServerPlayer serverPlayer) {
            syncDataToClient(serverPlayer);
        }
    }
    
    /**
     * Called when submit button is clicked
     */
    public void submitDeposit() {
        if (player.level().isClientSide) {
            // Client-side: send packet to server
            PacketDistributor.sendToServer(new com.aryangpt007.journeymode.network.packets.SubmitDepositPacket());
        }
    }
    
    private void syncDataToClient(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncJourneyDataPacket(
            journeyData.getAllCollectedCounts(),
            journeyData.getUnlockedItems()
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
            // Check if already unlocked
            if (journeyData.isUnlocked(stack.getItem())) {
                player.displayClientMessage(
                    Component.literal("§e" + stack.getHoverName().getString() + " is already unlocked!"),
                    false
                );
                return; // Don't consume the item
            }
            
            boolean unlocked = journeyData.depositItem(
                stack.copy(), 
                player.level().getRecipeManager(),
                player.level().registryAccess()
            );
            depositSlot.setItem(0, ItemStack.EMPTY);
            
            int threshold = journeyData.getThreshold(stack.getItem());
            
            if (unlocked) {
                player.displayClientMessage(
                    JourneyMode.translatable("unlock_message", stack.getHoverName(), threshold),
                    false
                );
            } else {
                int progress = journeyData.getProgress(stack.getItem());
                int collected = journeyData.getCollectedCount(stack.getItem());
                player.displayClientMessage(
                    JourneyMode.translatable("deposit_message", stack.getCount(), stack.getHoverName(), collected, threshold, progress),
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

    public JourneyDataAttachment getJourneyData() {
        return journeyData;
    }
}
