package com.aryangpt007.journeymode.menu;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.config.ConfigHandler;
import com.aryangpt007.journeymode.data.JourneyDataAttachment;
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
    private final Inventory playerInventoryRef;
    private final JourneyDataAttachment journeyData;
    private boolean depositSlotEnabled = true;
    private boolean inJourneyTab = false;

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
        super(JourneyMode.JOURNEY_MODE_MENU.get(), containerId);
        this.player = playerInventory.player;
        this.playerInventoryRef = playerInventory;
        this.journeyData = player.getData(JourneyMode.JOURNEY_DATA);

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
        // Delegates to GlobalDataHandler so the team-vs-personal resolution (§1) lives in one
        // place instead of being duplicated here.
        com.aryangpt007.journeymode.data.GlobalDataHandler.syncToClient(player, journeyData);
    }

    @Override
    public void slotsChanged(Container container) {
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
        if (player.level().isClientSide) return;

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
            boolean alreadyUnlocked = team != null
                ? team.isUnlocked(stack, player.level().registryAccess())
                : journeyData.isUnlocked(stack, player.level().registryAccess());
            if (alreadyUnlocked) {
                player.displayClientMessage(
                    Component.literal("§e" + stack.getHoverName().getString() + " is already unlocked!"),
                    false
                );
                return; // Don't consume the item
            }

            boolean unlocked = team != null
                ? team.depositItem(stack.copy(), player.level().getRecipeManager(), player.level().registryAccess())
                : journeyData.depositItem(stack.copy(), player.level().getRecipeManager(), player.level().registryAccess());
            depositSlot.setItem(0, ItemStack.EMPTY);

            int threshold = journeyData.getThreshold(stack.getItem()); // item-based only, same regardless of team

            if (unlocked) {
                player.displayClientMessage(
                    JourneyMode.translatable("unlock_message", stack.getHoverName(), threshold),
                    false
                );
            } else {
                int progress = team != null ? team.getProgress(stack, player.level().registryAccess()) : journeyData.getProgress(stack, player.level().registryAccess());
                int collected = team != null ? team.getCollectedCount(stack, player.level().registryAccess()) : journeyData.getCollectedCount(stack, player.level().registryAccess());
                player.displayClientMessage(
                    JourneyMode.translatable("deposit_message", stack.getCount(), stack.getHoverName(), collected, threshold, progress),
                    true // Action bar
                );
            }

            // Sync updated data to client & save to global file in real-time
            if (player instanceof ServerPlayer serverPlayer) {
                syncDataToClient(serverPlayer);
                com.aryangpt007.journeymode.data.GlobalDataHandler.savePlayerUnlocks(serverPlayer, journeyData);
                if (team != null) {
                    com.aryangpt007.journeymode.data.TeamDataHandler.saveAfterDeposit(serverPlayer.getServer());
                }
            }
        }
    }

    /**
     * §8 Deposit All: main inventory only (slots 9-35) by default; hotbar (0-8) included only
     * when the player held shift when clicking the button. Armor/offhand are never touched -
     * they aren't part of Inventory.items at all, so they're excluded by construction, not by
     * a special-case check. Already-unlocked item types are skipped (their deposit is wasted).
     */
    public void processDepositAll(boolean includeHotbar) {
        if (player.level().isClientSide) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

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

            boolean alreadyUnlocked = team != null
                ? team.isUnlocked(stack, player.level().registryAccess())
                : journeyData.isUnlocked(stack, player.level().registryAccess());
            if (alreadyUnlocked) {
                typesSkippedUnlocked++;
                continue;
            }

            boolean unlocked = team != null
                ? team.depositItem(stack.copy(), player.level().getRecipeManager(), player.level().registryAccess())
                : journeyData.depositItem(stack.copy(), player.level().getRecipeManager(), player.level().registryAccess());
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
            player.displayClientMessage(Component.literal("Nothing to deposit."), true);
        } else {
            player.displayClientMessage(Component.literal(
                "Deposited " + itemsDeposited + " items across " + typesDeposited + " types. " +
                "Skipped " + typesSkippedUnlocked + " unlocked types."
            ), false);
        }

        syncDataToClient(serverPlayer);
        com.aryangpt007.journeymode.data.GlobalDataHandler.savePlayerUnlocks(serverPlayer, journeyData);
        if (team != null) {
            com.aryangpt007.journeymode.data.TeamDataHandler.saveAfterDeposit(serverPlayer.getServer());
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        
        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemstack = slotStack.copy();

            // If we are in the Journey tab, shift-clicking acts as a dump
            if (this.inJourneyTab) {
                com.aryangpt007.journeymode.data.TeamData team = resolveTeam();
                boolean unlocked = team != null
                    ? team.isUnlocked(slotStack, player.level().registryAccess())
                    : this.journeyData.isUnlocked(slotStack, player.level().registryAccess());
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
