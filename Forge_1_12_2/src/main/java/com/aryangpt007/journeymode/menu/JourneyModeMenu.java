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
    private final InventoryPlayer playerInventoryRef;
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
        this.playerInventoryRef = playerInventory;
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
        // Delegates to GlobalDataHandler so the team-vs-personal resolution (§1) lives in one
        // place instead of being duplicated here.
        GlobalDataHandler.syncToClient(player, journeyData);
    }

    /**
     * §1 Shared Team Catalogs: if this player is on a team, deposits/unlocks/progress-checks
     * resolve against the team's shared TeamData instead of their personal IJourneyData - both
     * deposits and unlocks pool, per the resolved design decision. Returns null if the player
     * isn't on a team (the common case), so call sites fall back to personal data.
     */
    private com.aryangpt007.journeymode.data.TeamData resolveTeam() {
        if (journeyData.getTeamId() == null) return null;
        return com.aryangpt007.journeymode.data.TeamDataHandler.getTeamForPlayer(player.getUniqueID());
    }

    /**
     * Process the deposit (called from server via packet)
     */
    public void processDeposit() {
        if (player.world.isRemote) return;

        ItemStack stack = depositSlot.getStackInSlot(0);
        if (!stack.isEmpty()) {
            if (stack.getItem().getRegistryName() == null) return;
            if (ConfigHandler.isBlacklisted(stack.getItem())) {
                player.sendMessage(JourneyMode.translatable("blacklist_message", stack.getDisplayName()));
                return;
            }

            com.aryangpt007.journeymode.data.TeamData team = resolveTeam();

            boolean alreadyUnlocked = team != null ? team.isUnlocked(stack) : journeyData.isUnlocked(stack);
            if (alreadyUnlocked) {
                player.sendMessage(new TextComponentString("§e" + stack.getDisplayName() + " is already unlocked!"));
                return;
            }

            boolean unlocked = team != null ? team.depositItem(stack.copy()) : journeyData.depositItem(stack.copy());
            depositSlot.setInventorySlotContents(0, ItemStack.EMPTY);

            int threshold = journeyData.getThreshold(stack); // item-based only, same regardless of team

            if (unlocked) {
                player.sendMessage(JourneyMode.translatable("unlock_message", stack.getDisplayName(), threshold));
            } else {
                int progress = team != null ? team.getProgress(stack) : journeyData.getProgress(stack);
                int collected = team != null ? team.getCollectedCount(stack) : journeyData.getCollectedCount(stack);
                player.sendStatusMessage(
                    JourneyMode.translatable("deposit_message", stack.getCount(), stack.getDisplayName(), collected, threshold, progress),
                    true // Action bar
                );
            }

            if (player instanceof EntityPlayerMP) {
                EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
                syncDataToClient(serverPlayer);
                GlobalDataHandler.savePlayerUnlocks(serverPlayer, journeyData);
                if (team != null) {
                    com.aryangpt007.journeymode.data.TeamDataHandler.saveAfterDeposit(serverPlayer.getServer());
                }
            }
        }
    }

    /**
     * §8 Deposit All: main inventory only (slots 9-35) by default; hotbar (0-8) included only
     * when the player held shift when clicking the button. Armor/offhand are never touched -
     * they live in InventoryPlayer.armorInventory/offHandInventory, separate arrays from
     * mainInventory, so they're excluded by construction, not by a special-case check.
     * Already-unlocked item types are skipped (depositing into them would be wasted).
     */
    public void processDepositAll(boolean includeHotbar) {
        if (player.world.isRemote) return;
        if (!(player instanceof EntityPlayerMP)) return;
        EntityPlayerMP serverPlayer = (EntityPlayerMP) player;

        int firstSlot = includeHotbar ? 0 : 9;
        int lastSlotExclusive = 36;

        com.aryangpt007.journeymode.data.TeamData team = resolveTeam();

        int typesDeposited = 0;
        int itemsDeposited = 0;
        int typesSkippedUnlocked = 0;
        int typesUnlocked = 0;

        for (int i = firstSlot; i < lastSlotExclusive; i++) {
            ItemStack stack = playerInventoryRef.mainInventory.get(i);
            if (stack.isEmpty() || stack.getItem().getRegistryName() == null) continue;

            if (ConfigHandler.isBlacklisted(stack.getItem())) continue;

            boolean alreadyUnlocked = team != null ? team.isUnlocked(stack) : journeyData.isUnlocked(stack);
            if (alreadyUnlocked) {
                typesSkippedUnlocked++;
                continue;
            }

            boolean unlocked = team != null ? team.depositItem(stack.copy()) : journeyData.depositItem(stack.copy());
            itemsDeposited += stack.getCount();
            typesDeposited++;
            playerInventoryRef.mainInventory.set(i, ItemStack.EMPTY);

            if (unlocked) {
                // Counted, not announced per item. A Deposit All that crosses twenty thresholds
                // used to push twenty separate chat lines on top of the batched action-bar
                // celebration the client already shows for the same event - JM_Project.md §12 is
                // explicit that simultaneous unlocks batch into one message rather than spamming
                // one per item.
                typesUnlocked++;
            }
        }

        if (typesDeposited == 0 && typesSkippedUnlocked == 0) {
            player.sendStatusMessage(new TextComponentString("Nothing to deposit."), true);
        } else {
            player.sendMessage(new TextComponentString(
                "Deposited " + itemsDeposited + " items across " + typesDeposited + " types. " +
                "Unlocked " + typesUnlocked + ". Skipped " + typesSkippedUnlocked + " already-unlocked types."
            ));
        }

        syncDataToClient(serverPlayer);
        GlobalDataHandler.savePlayerUnlocks(serverPlayer, journeyData);
        if (team != null) {
            com.aryangpt007.journeymode.data.TeamDataHandler.saveAfterDeposit(serverPlayer.getServer());
        }
        this.detectAndSendChanges();
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.inventorySlots.get(index);

        if (slot != null && slot.getHasStack()) {
            ItemStack slotStack = slot.getStack();
            itemstack = slotStack.copy();

            if (this.inJourneyTab) {
                com.aryangpt007.journeymode.data.TeamData team = resolveTeam();
                boolean unlocked = team != null ? team.isUnlocked(slotStack) : this.journeyData.isUnlocked(slotStack);
                if (unlocked) {
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
