package com.aryangpt007.journeymode.menu;

import net.minecraft.util.text.StringTextComponent;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.config.ConfigHandler;
import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider;
import com.aryangpt007.journeymode.network.NetworkHandler;
import com.aryangpt007.journeymode.network.packets.SubmitDepositPacket;
import com.aryangpt007.journeymode.network.packets.SyncJourneyDataPacket;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.network.PacketDistributor;

/**
 * Menu for Journey Mode screen with deposit slot.
 * Ported to Forge 1.20.1.
 */
public class JourneyModeMenu extends Container {
    private final PlayerEntity player;
    private final PlayerInventory playerInventoryRef;
    private final JourneyDataAttachment journeyData;
    private boolean depositSlotEnabled = true;
    private boolean inJourneyTab = false;

    // Custom slot that can be disabled
    private static class ConditionalSlot extends Slot {
        private final JourneyModeMenu menu;

        public ConditionalSlot(JourneyModeMenu menu, IInventory container, int slot, int x, int y) {
            super(container, slot, x, y);
            this.menu = menu;
        }

        @Override
        public boolean isActive() {
            return menu.depositSlotEnabled;
        }
    }

    // Simple single-slot inventory for depositing items
    private final IInventory depositSlot = new IInventory() {
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
        public boolean stillValid(PlayerEntity player) { return true; }

        @Override
        public void clearContent() { stack = ItemStack.EMPTY; }
    };

    public JourneyModeMenu(int containerId, PlayerInventory playerInventory) {
        super(JourneyMode.JOURNEY_MODE_MENU.get(), containerId);
        this.player = playerInventory.player;
        this.playerInventoryRef = playerInventory;
        this.journeyData = player.getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY).orElseGet(JourneyDataAttachment::new);

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
        if (player instanceof ServerPlayerEntity) {
            syncDataToClient((ServerPlayerEntity) player);
        }
    }
    
    /**
     * Called when submit button is clicked
     */
    public void submitDeposit() {
        if (player.level.isClientSide) {
            // Client-side: send packet to server
            NetworkHandler.CHANNEL.sendToServer(new SubmitDepositPacket());
        }
    }
    
    private void syncDataToClient(ServerPlayerEntity player) {
        // Delegates to GlobalDataHandler so the team-vs-personal resolution (§1) lives in one
        // place instead of being duplicated here.
        com.aryangpt007.journeymode.data.GlobalDataHandler.syncToClient(player, journeyData);
    }

    @Override
    public void slotsChanged(IInventory container) {
        super.slotsChanged(container);
        // Don't auto-process items - only process on submit button click
    }
    
    /**
     * §1 Shared Team Catalogs: if this player is on a team, deposits/unlocks/progress-checks
     * resolve against the team's shared TeamData instead of their personal JourneyDataAttachment
     * - both deposits and unlocks pool, per the resolved design decision. Returns null if the
     * player isn't on a team (the common case), so call sites fall back to personal data.
     */
    private com.aryangpt007.journeymode.data.TeamData resolveTeam() {
        if (journeyData.getTeamId() == null) return null;
        return com.aryangpt007.journeymode.data.TeamDataHandler.getTeamForPlayer(player.getUUID()).orElse(null);
    }

    /**
     * Process the deposit (called from server via packet)
     */
    public void processDeposit() {
        if (player.level.isClientSide) return;

        ItemStack stack = depositSlot.getItem(0);
        if (!stack.isEmpty()) {
            // Check if blacklisted (exact id, tag, or regex/wildcard rule)
            if (ConfigHandler.isBlacklisted(stack.getItem())) {
                player.displayClientMessage(
                    JourneyMode.translatable("blacklist_message", stack.getHoverName()),
                    false
                );
                return; // Don't consume the item
            }

            com.aryangpt007.journeymode.data.TeamData team = resolveTeam();

            // Check if already unlocked (team-wide if on a team, personal otherwise)
            boolean alreadyUnlocked = team != null ? team.isUnlocked(stack) : journeyData.isUnlocked(stack);
            if (alreadyUnlocked) {
                player.displayClientMessage(
                    new StringTextComponent("§e" + stack.getHoverName().getString() + " is already unlocked!"),
                    false
                );
                return; // Don't consume the item
            }

            boolean unlocked = team != null
                ? team.depositItem(stack.copy(), player.level.getRecipeManager())
                : journeyData.depositItem(stack.copy(), player.level.getRecipeManager());
            depositSlot.setItem(0, ItemStack.EMPTY);

            int threshold = journeyData.getThreshold(stack.getItem()); // item-based only, same regardless of team

            if (unlocked) {
                player.displayClientMessage(
                    JourneyMode.translatable("unlock_message", stack.getHoverName(), threshold),
                    false
                );
            } else {
                int progress = team != null ? team.getProgress(stack) : journeyData.getProgress(stack);
                int collected = team != null ? team.getCollectedCount(stack) : journeyData.getCollectedCount(stack);
                player.displayClientMessage(
                    JourneyMode.translatable("deposit_message", stack.getCount(), stack.getHoverName(), collected, threshold, progress),
                    true // Action bar
                );
            }

            // Sync updated data to client & save to global file in real-time
            if (player instanceof ServerPlayerEntity) {
                ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
                syncDataToClient(serverPlayer);
                com.aryangpt007.journeymode.data.GlobalDataHandler.savePlayerUnlocks(serverPlayer, journeyData);
                if (team != null) {
                    com.aryangpt007.journeymode.data.TeamDataHandler.saveAfterDeposit(serverPlayer.server);
                }
            }
        }
    }

    /**
     * §8 Deposit All: main inventory only (slots 9-35) by default; hotbar (0-8) included only
     * when the player held shift when clicking the button. Armor/offhand are never touched -
     * they aren't part of PlayerInventory.items at all, so they're excluded by construction, not
     * by a special-case check. Already-unlocked item types are skipped (their deposit is wasted).
     */
    public void processDepositAll(boolean includeHotbar) {
        if (player.level.isClientSide) return;
        if (!(player instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;

        int firstSlot = includeHotbar ? 0 : 9;
        int lastSlotExclusive = 36;

        com.aryangpt007.journeymode.data.TeamData team = resolveTeam();

        int typesDeposited = 0;
        int itemsDeposited = 0;
        int typesSkippedUnlocked = 0;

        for (int i = firstSlot; i < lastSlotExclusive; i++) {
            ItemStack stack = playerInventoryRef.getItem(i);
            if (stack.isEmpty()) continue;

            if (ConfigHandler.isBlacklisted(stack.getItem())) continue;

            boolean alreadyUnlocked = team != null ? team.isUnlocked(stack) : journeyData.isUnlocked(stack);
            if (alreadyUnlocked) {
                typesSkippedUnlocked++;
                continue;
            }

            boolean unlocked = team != null
                ? team.depositItem(stack.copy(), player.level.getRecipeManager())
                : journeyData.depositItem(stack.copy(), player.level.getRecipeManager());
            itemsDeposited += stack.getCount();
            typesDeposited++;
            playerInventoryRef.setItem(i, ItemStack.EMPTY);

            if (unlocked) {
                int threshold = journeyData.getThreshold(stack.getItem());
                player.displayClientMessage(
                    JourneyMode.translatable("unlock_message", stack.getHoverName(), threshold),
                    false
                );
            }
        }

        if (typesDeposited == 0 && typesSkippedUnlocked == 0) {
            player.displayClientMessage(new StringTextComponent("Nothing to deposit."), true);
        } else {
            player.displayClientMessage(new StringTextComponent(
                "Deposited " + itemsDeposited + " items across " + typesDeposited + " types. " +
                "Skipped " + typesSkippedUnlocked + " unlocked types."
            ), false);
        }

        syncDataToClient(serverPlayer);
        com.aryangpt007.journeymode.data.GlobalDataHandler.savePlayerUnlocks(serverPlayer, journeyData);
        if (team != null) {
            com.aryangpt007.journeymode.data.TeamDataHandler.saveAfterDeposit(serverPlayer.server);
        }
    }

    @Override
    public ItemStack quickMoveStack(PlayerEntity player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        
        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemstack = slotStack.copy();

            // If we are in the Journey tab, shift-clicking acts as a dump
            if (this.inJourneyTab) {
                com.aryangpt007.journeymode.data.TeamData team = resolveTeam();
                boolean unlocked = team != null ? team.isUnlocked(slotStack) : this.journeyData.isUnlocked(slotStack);
                if (unlocked) {
                    slot.set(ItemStack.EMPTY);
                    slot.setChanged();
                    return ItemStack.EMPTY; // Deleted/consumed
                } else {
                    return ItemStack.EMPTY; // Do nothing if not unlocked yet
                }
            }

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
    public boolean stillValid(PlayerEntity player) {
        return true;
    }

    @Override
    public void removed(PlayerEntity player) {
        super.removed(player);
        // Return items from deposit slot to player when menu closes
        if (!player.level.isClientSide) {
            ItemStack depositedItem = this.depositSlot.getItem(0);
            if (!depositedItem.isEmpty()) {
                player.inventory.placeItemBackInInventory(player.level, depositedItem);
                this.depositSlot.setItem(0, ItemStack.EMPTY);
            }
        }
    }

    public JourneyDataAttachment getJourneyData() {
        return journeyData;
    }

    /**
     * Enable or disable the deposit slot (called from client screen when tab changes)
     */
    public void setDepositSlotEnabled(boolean enabled) {
        this.depositSlotEnabled = enabled;
    }

    /**
     * Sync tab state from client
     */
    public void setInJourneyTab(boolean inJourneyTab) {
        this.inJourneyTab = inJourneyTab;
    }
}
