package com.aryangpt007.journeymode.menu;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.config.ConfigHandler;
import com.aryangpt007.journeymode.data.GlobalDataHandler;
import com.aryangpt007.journeymode.data.IJourneyData;
import com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider;
import com.aryangpt007.journeymode.network.NetworkHandler;
import com.aryangpt007.journeymode.network.packets.SubmitDepositPacket;
import com.aryangpt007.journeymode.network.packets.SyncJourneyDataPacket;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.*;

public class JourneyModeMenu extends Container {
    private final EntityPlayer player;
    private final IJourneyData journeyData;
    private boolean depositSlotEnabled = true;
    private boolean inJourneyTab = false;

    // Custom 1-slot inventory for the deposit slot
    private final IInventory depositSlot = new IInventory() {
        private ItemStack stack = ItemStack.EMPTY;

        @Override
        public int getSizeInventory() { return 1; }

        @Override
        public boolean isEmpty() { return stack.isEmpty(); }

        @Override
        public ItemStack getStackInSlot(int slot) { return stack; }

        @Override
        public ItemStack decrStackSize(int slot, int amount) {
            ItemStack result = stack.splitStack(amount);
            markDirty();
            return result;
        }

        @Override
        public ItemStack removeStackFromSlot(int slot) {
            ItemStack result = stack;
            stack = ItemStack.EMPTY;
            return result;
        }

        @Override
        public void setInventorySlotContents(int slot, ItemStack itemStack) {
            stack = itemStack;
            markDirty();
        }

        @Override
        public int getInventoryStackLimit() { return 64; }

        @Override
        public void markDirty() {}

        @Override
        public boolean isUsableByPlayer(EntityPlayer player) { return true; }

        @Override
        public void openInventory(EntityPlayer player) {}

        @Override
        public void closeInventory(EntityPlayer player) {}

        @Override
        public boolean isItemValidForSlot(int index, ItemStack stack) { return true; }

        @Override
        public int getField(int id) { return 0; }

        @Override
        public void setField(int id, int value) {}

        @Override
        public int getFieldCount() { return 0; }

        @Override
        public void clear() { stack = ItemStack.EMPTY; }

        @Override
        public String getName() { return "deposit"; }

        @Override
        public boolean hasCustomName() { return false; }

        @Override
        public ITextComponent getDisplayName() {
            return new TextComponentString(getName());
        }
    };

    public JourneyModeMenu(InventoryPlayer playerInventory) {
        this.player = playerInventory.player;
        this.journeyData = player.getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY, null);

        // Add deposit slot (center top of screen)
        this.addSlotToContainer(new ConditionalSlot(this, depositSlot, 0, 80, 18));

        // Add player inventory slots
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlotToContainer(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 110 + row * 18));
            }
        }

        // Add player hotbar slots
        for (int col = 0; col < 9; ++col) {
            this.addSlotToContainer(new Slot(playerInventory, col, 8 + col * 18, 168));
        }
        
        // Sync data to client when menu opens
        if (player instanceof EntityPlayerMP) {
            syncDataToClient((EntityPlayerMP) player);
        }
    }
    
    /**
     * Called when submit button is clicked
     */
    public void submitDeposit() {
        if (player.world.isRemote) {
            NetworkHandler.sendToServer(new SubmitDepositPacket());
        }
    }
    
    private void syncDataToClient(EntityPlayerMP player) {
        NetworkHandler.sendTo(
            new SyncJourneyDataPacket(
                journeyData.getAllCollectedCounts(),
                journeyData.getUnlockedItems(),
                journeyData.getUnlockTimestamps()
            ),
            player
        );
    }
    
    /**
     * Process the deposit (called from server via packet)
     */
    public void processDeposit() {
        if (player.world.isRemote) return;
        
        ItemStack stack = depositSlot.getStackInSlot(0);
        if (!stack.isEmpty()) {
            if (stack.getItem().getRegistryName() == null) return;
            String itemId = stack.getItem().getRegistryName().toString();
            if (ConfigHandler.isBlacklisted(itemId)) {
                player.sendMessage(JourneyMode.translatable("blacklist_message", stack.getDisplayName()));
                return;
            }
            
            if (journeyData.isUnlocked(stack)) {
                player.sendMessage(new TextComponentString("§e" + stack.getDisplayName() + " is already unlocked!"));
                return;
            }
            
            boolean unlocked = journeyData.depositItem(stack.copy());
            depositSlot.setInventorySlotContents(0, ItemStack.EMPTY);
            
            int threshold = journeyData.getThreshold(stack);
            
            if (unlocked) {
                player.sendMessage(JourneyMode.translatable("unlock_message", stack.getDisplayName(), threshold));
            } else {
                int progress = journeyData.getProgress(stack);
                int collected = journeyData.getCollectedCount(stack);
                player.sendStatusMessage(
                    JourneyMode.translatable("deposit_message", stack.getCount(), stack.getDisplayName(), collected, threshold, progress),
                    true // Action bar
                );
            }
            
            if (player instanceof EntityPlayerMP) {
                EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
                syncDataToClient(serverPlayer);
                GlobalDataHandler.savePlayerUnlocks(serverPlayer, journeyData);
            }
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.inventorySlots.get(index);
        
        if (slot != null && slot.getHasStack()) {
            ItemStack slotStack = slot.getStack();
            itemstack = slotStack.copy();

            if (this.inJourneyTab) {
                if (this.journeyData.isUnlocked(slotStack)) {
                    slot.putStack(ItemStack.EMPTY);
                    slot.onSlotChanged();
                    return ItemStack.EMPTY; // Deleted/consumed
                } else {
                    return ItemStack.EMPTY; // Do nothing if not unlocked yet
                }
            }
            
            if (index == 0) {
                // Moving FROM deposit slot TO player inventory
                if (!this.mergeItemStack(slotStack, 1, this.inventorySlots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Moving FROM player inventory TO deposit slot
                if (!this.mergeItemStack(slotStack, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) {
                slot.putStack(ItemStack.EMPTY);
            } else {
                slot.onSlotChanged();
            }
        }

        return itemstack;
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return true;
    }

    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        super.onContainerClosed(playerIn);
        if (!playerIn.world.isRemote) {
            ItemStack depositedItem = this.depositSlot.getStackInSlot(0);
            if (!depositedItem.isEmpty()) {
                playerIn.inventory.placeItemBackInInventory(playerIn.world, depositedItem);
                this.depositSlot.setInventorySlotContents(0, ItemStack.EMPTY);
            }
        }
    }

    public IJourneyData getJourneyData() {
        return journeyData;
    }

    public void setDepositSlotEnabled(boolean enabled) {
        this.depositSlotEnabled = enabled;
    }

    public void setInJourneyTab(boolean inJourneyTab) {
        this.inJourneyTab = inJourneyTab;
    }

    public static class ConditionalSlot extends Slot {
        private final JourneyModeMenu menu;
        public ConditionalSlot(JourneyModeMenu menu, IInventory inventoryIn, int index, int xPosition, int yPosition) {
            super(inventoryIn, index, xPosition, yPosition);
            this.menu = menu;
        }
        @Override
        public boolean canTakeStack(EntityPlayer playerIn) {
            return menu.depositSlotEnabled;
        }
        @Override
        public boolean isItemValid(ItemStack stack) {
            return menu.depositSlotEnabled;
        }
        @Override
        @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
        public boolean isEnabled() {
            return menu.depositSlotEnabled;
        }
    }
}
