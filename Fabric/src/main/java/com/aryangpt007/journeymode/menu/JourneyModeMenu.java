package com.aryangpt007.journeymode.menu;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.config.ConfigHandler;
import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import com.aryangpt007.journeymode.data.GlobalDataHandler;
import com.aryangpt007.journeymode.network.packets.SubmitDepositPacket;
import com.aryangpt007.journeymode.network.FabricNetworkHandler;
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
 * Menu for Journey Mode screen with deposit slot on Fabric
 */
public class JourneyModeMenu extends AbstractContainerMenu {
    private final Player player;
    private final JourneyDataAttachment journeyData;
    private boolean depositSlotEnabled = true;
    private boolean inJourneyTab = false;

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
        super(JourneyMode.JOURNEY_MODE_MENU, containerId);
        this.player = playerInventory.player;
        this.journeyData = GlobalDataHandler.getPlayerData(player);

        // Add deposit slot (center top of screen)
        this.addSlot(new ConditionalSlot(this, depositSlot, 0, 80, 18));

        // Add player inventory slots
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 110 + row * 18));
            }
        }

        // Add player hotbar slots
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 168));
        }
        
        // Sync data when menu opens
        if (player instanceof ServerPlayer serverPlayer) {
            GlobalDataHandler.syncToClient(serverPlayer, journeyData);
        }
    }
    
    public void submitDeposit() {
        if (player.level().isClientSide) {
            FabricNetworkHandler.sendToServer(new SubmitDepositPacket());
        }
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
    }
    
    public void processDeposit() {
        if (player.level().isClientSide) return;
        
        ItemStack stack = depositSlot.getItem(0);
        if (!stack.isEmpty()) {
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (ConfigHandler.isBlacklisted(itemId)) {
                player.displayClientMessage(
                    JourneyMode.translatable("blacklist_message", stack.getHoverName()),
                    false
                );
                return;
            }
            
            if (journeyData.isUnlocked(stack.getItem())) {
                player.displayClientMessage(
                    Component.literal("§e" + stack.getHoverName().getString() + " is already unlocked!"),
                    false
                );
                return;
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
                    true
                );
            }
            
            if (player instanceof ServerPlayer serverPlayer) {
                GlobalDataHandler.syncToClient(serverPlayer, journeyData);
                GlobalDataHandler.savePlayerUnlocks(serverPlayer, journeyData);
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

            if (this.inJourneyTab) {
                if (this.journeyData.isUnlocked(slotStack.getItem())) {
                    slot.set(ItemStack.EMPTY);
                    slot.setChanged();
                    return ItemStack.EMPTY;
                } else {
                    return ItemStack.EMPTY;
                }
            }
            
            if (index == 0) {
                if (!this.moveItemStackTo(slotStack, 1, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
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
        if (!player.level().isClientSide) {
            ItemStack depositedItem = this.depositSlot.getItem(0);
            if (!depositedItem.isEmpty()) {
                player.getInventory().placeItemBackInInventory(depositedItem);
                this.depositSlot.setItem(0, ItemStack.EMPTY);
            }
        }
    }

    public JourneyDataAttachment getJourneyData() {
        return journeyData;
    }

    public void setDepositSlotEnabled(boolean enabled) {
        this.depositSlotEnabled = enabled;
    }

    public void setInJourneyTab(boolean inJourneyTab) {
        this.inJourneyTab = inJourneyTab;
    }
}
