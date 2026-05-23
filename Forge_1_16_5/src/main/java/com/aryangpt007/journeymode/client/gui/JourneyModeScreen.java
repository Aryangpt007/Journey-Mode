package com.aryangpt007.journeymode.client.gui;

import net.minecraft.util.text.StringTextComponent;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import com.aryangpt007.journeymode.network.NetworkHandler;
import com.aryangpt007.journeymode.network.packets.DeleteCarriedPacket;
import com.aryangpt007.journeymode.network.packets.RequestItemPacket;
import com.aryangpt007.journeymode.network.packets.SyncTabPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;

import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Journey Mode GUI with tabs for deposit and retrieval.
 * Ported to Forge 1.20.1.
 */
public class JourneyModeScreen extends ContainerScreen<JourneyModeMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(JourneyMode.MODID, "textures/gui/journey_mode.png");
    
    private enum Tab {
        DEPOSIT,
        JOURNEY
    }
    
    private Tab currentTab = Tab.DEPOSIT;
    private int scrollOffset = 0;
    private static final int ITEMS_PER_ROW = 9;
    private static final int VISIBLE_ROWS = 3;
    
    private TextFieldWidget searchBox;
    private String searchQuery = "";

    public JourneyModeScreen(JourneyModeMenu menu, PlayerInventory playerInventory, ITextComponent title) {
        super(menu, playerInventory, title);
        this.imageHeight = 204; // Increased to accommodate search box and proper spacing
        this.imageWidth = 176;
    }

    @Override
    protected void init() {
        super.init();
        // Move title much higher to avoid overlap with tabs
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.titleLabelY = -30; // Move title higher above the tabs
        this.inventoryLabelY = this.imageHeight - 104; // Position inventory label with proper spacing
        
        // Create search box for Journey tab (positioned with proper spacing above inventory label)
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        this.searchBox = new TextFieldWidget(this.font, x + 8, y + 86, 160, 12, new StringTextComponent("Search"));
        this.searchBox.setMaxLength(50);
        this.searchBox.setBordered(true);
        this.searchBox.setVisible(false);
        this.searchBox.setTextColor(0xFFFFFF);
        this.searchBox.setResponder(query -> {
            this.searchQuery = query.toLowerCase();
            this.scrollOffset = 0; // Reset scroll when search changes
        });
        this.children.add(this.searchBox);
        
        // Sync initial tab state to menu and server
        this.menu.setInJourneyTab(currentTab == Tab.JOURNEY);
        NetworkHandler.CHANNEL.sendToServer(new SyncTabPacket(currentTab == Tab.JOURNEY));
    }


    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(matrixStack);
        super.render(matrixStack, mouseX, mouseY, partialTick);
        this.renderTooltip(matrixStack, mouseX, mouseY);
        if (this.searchBox != null && this.searchBox.visible) {
            this.searchBox.render(matrixStack, mouseX, mouseY, partialTick);
        }
        
        // Update search box visibility based on current tab
        if (this.searchBox != null) {
            this.searchBox.setVisible(currentTab == Tab.JOURNEY);
        }
    }

    @Override
    protected void renderBg(MatrixStack matrixStack, float partialTick, int mouseX, int mouseY) {
        this.minecraft.getTextureManager().bind(TEXTURE);
        // RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Draw main background
        fill(matrixStack, x, y, x + this.imageWidth, y + this.imageHeight, 0xFFC6C6C6);
        fill(matrixStack, x + 1, y + 1, x + this.imageWidth - 1, y + this.imageHeight - 1, 0xFF8B8B8B);

        // Draw tabs
        drawTab(matrixStack, x + 10, y - 20, "Deposit", currentTab == Tab.DEPOSIT);
        drawTab(matrixStack, x + 80, y - 20, "Journey", currentTab == Tab.JOURNEY);

        if (currentTab == Tab.DEPOSIT) {
            renderDepositTab(matrixStack, x, y);
        } else {
            renderJourneyTab(matrixStack, x, y, mouseX, mouseY);
        }
        
        // Draw inventory slot backgrounds
        renderSlotBackgrounds(matrixStack, x, y);
    }
    
    private void renderSlotBackgrounds(MatrixStack matrixStack, int x, int y) {
        // Draw deposit slot background if in deposit tab
        if (currentTab == Tab.DEPOSIT) {
            int slotX = x + 80;
            int slotY = y + 18;
            // Slot border (darker)
            fill(matrixStack, slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF373737);
            // Slot background (lighter)
            fill(matrixStack, slotX, slotY, slotX + 16, slotY + 16, 0xFF8B8B8B);
        }
        
        // Draw player inventory slot backgrounds (updated to match new positions)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                int slotX = x + 8 + col * 18;
                int slotY = y + 110 + row * 18; // Updated from y + 84
                // Slot border
                fill(matrixStack, slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF373737);
                // Slot background
                fill(matrixStack, slotX, slotY, slotX + 16, slotY + 16, 0xFF8B8B8B);
            }
        }
        
        // Draw hotbar slot backgrounds (updated to match new positions)
        for (int col = 0; col < 9; ++col) {
            int slotX = x + 8 + col * 18;
            int slotY = y + 168; // Updated from y + 142
            // Slot border
            fill(matrixStack, slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF373737);
            // Slot background
            fill(matrixStack, slotX, slotY, slotX + 16, slotY + 16, 0xFF8B8B8B);
        }
    }

    private void drawTab(MatrixStack matrixStack, int x, int y, String label, boolean selected) {
        int color = selected ? 0xFFFFFFFF : 0xFFA0A0A0;
        int bgColor = selected ? 0xFF8B8B8B : 0xFF606060;
        
        fill(matrixStack, x, y, x + 60, y + 20, bgColor);
        this.font.draw(matrixStack, label, x + 5, y + 6, color);
    }

    private void renderDepositTab(MatrixStack matrixStack, int x, int y) {
        JourneyDataAttachment data = this.menu.getJourneyData();
        
        // Draw instruction text above deposit slot
        this.font.draw(matrixStack, "Place items to unlock:", x + 40, y + 6, 0x404040);
        
        // Deposit slot is rendered automatically by the container at y + 18
        // Draw submit button (at x + 110, y + 18)
        int buttonX = x + 110;
        int buttonY = y + 18;
        int buttonWidth = 50;
        int buttonHeight = 16;
        
        // Check if there's an item in the deposit slot
        boolean hasItem = this.menu.slots.get(0).hasItem();
        
        // Button background
        int buttonColor = hasItem ? 0xFF4CAF50 : 0xFF808080; // Green if has item, gray otherwise
        fill(matrixStack, buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, buttonColor);
        fill(matrixStack, buttonX + 1, buttonY + 1, buttonX + buttonWidth - 1, buttonY + buttonHeight - 1, 0xFF2E7D32);
        
        // Button text
        String buttonText = "Submit";
        int textX = buttonX + (buttonWidth - this.font.width(buttonText)) / 2;
        int textY = buttonY + 4;
        this.font.draw(matrixStack, buttonText, textX, textY, hasItem ? 0xFFFFFFFF : 0xFFA0A0A0);
        
        // Show item info if item is in slot
        int infoY = y + 42;
        if (hasItem) {
            ItemStack slotItem = this.menu.slots.get(0).getItem();
            if (!slotItem.isEmpty()) {
                data.initializeCalculator(
                    this.minecraft.level.getRecipeManager()
                );
                
                int threshold = data.getThreshold(slotItem.getItem());
                int collected = data.getCollectedCount(slotItem.getItem());
                boolean alreadyUnlocked = data.isUnlocked(slotItem.getItem());
                
                if (alreadyUnlocked) {
                    this.font.draw(matrixStack, "§a✓ Already Unlocked!", x + 8, infoY, 0x00FF00);
                } else {
                    this.font.draw(matrixStack, "Required: " + threshold + " items", x + 8, infoY, 0x404040);
                    this.font.draw(matrixStack, "Collected: " + collected + "/" + threshold, x + 8, infoY + 12, 0x404040);
                    
                    int progress = data.getProgress(slotItem.getItem());
                    this.font.draw(matrixStack, "Progress: " + progress + "%", x + 8, infoY + 24, 0x606060);
                }
            }
        } else {
            // General info when no item
            this.font.draw(matrixStack, "Unlocked: " + data.getUnlockedItems().size() + " items", x + 8, infoY, 0x404040);
        }
    }

    private void renderJourneyTab(MatrixStack matrixStack, int x, int y, int mouseX, int mouseY) {
        JourneyDataAttachment data = this.menu.getJourneyData();
        
        // Get sorted and filtered items
        List<String> unlockedItems = getFilteredAndSortedItems(data);

        if (unlockedItems.isEmpty()) {
            if (searchQuery.isEmpty()) {
                this.font.draw(matrixStack, "No items unlocked yet!", x + 30, y + 30, 0x404040);
                this.font.draw(matrixStack, "Deposit items in the Deposit tab", x + 16, y + 45, 0x606060);
                this.font.draw(matrixStack, "(Threshold varies by item)", x + 24, y + 57, 0x606060);
            } else {
                this.font.draw(matrixStack, "No items match search", x + 35, y + 30, 0x404040);
            }
            return;
        }

        // Draw unlocked items grid
        int startIndex = scrollOffset * ITEMS_PER_ROW;
        int endIndex = Math.min(startIndex + (VISIBLE_ROWS * ITEMS_PER_ROW), unlockedItems.size());

        for (int i = startIndex; i < endIndex; i++) {
            String itemId = unlockedItems.get(i);
            Item item = Registry.ITEM.get(new ResourceLocation(itemId));
            
            int gridIndex = i - startIndex;
            int row = gridIndex / ITEMS_PER_ROW;
            int col = gridIndex % ITEMS_PER_ROW;
            
            int itemX = x + 8 + col * 18;
            int itemY = y + 18 + row * 18;

            // Draw item slot border (darker outline)
            fill(matrixStack, itemX - 1, itemY - 1, itemX + 17, itemY + 17, 0xFF373737);
            // Draw item slot background
            fill(matrixStack, itemX, itemY, itemX + 16, itemY + 16, 0xFF8B8B8B);
            
            // Render item
            ItemStack stack = new ItemStack(item);
            this.itemRenderer.renderAndDecorateItem(stack, itemX, itemY);
            this.itemRenderer.renderGuiItemDecorations(this.font, stack, itemX, itemY);
            
            // Check if hovering for highlight
            if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) {
                fill(matrixStack, itemX, itemY, itemX + 16, itemY + 16, 0x80FFFFFF);
            }
        }
    }
    
    /**
     * Get unlocked items filtered by search query and sorted by unlock time (most recent first)
     */
    private List<String> getFilteredAndSortedItems(JourneyDataAttachment data) {
        // Start with sorted items (most recent first)
        List<String> items = data.getUnlockedItemsSorted();
        
        // Filter by search query if present
        if (!searchQuery.isEmpty()) {
            List<String> filtered = new ArrayList<>();
            for (String itemId : items) {
                Item item = Registry.ITEM.get(new ResourceLocation(itemId));
                String itemName = new ItemStack(item).getHoverName().getString().toLowerCase();
                if (itemName.contains(searchQuery)) {
                    filtered.add(itemId);
                }
            }
            return filtered;
        }
        
        return items;
    }

    @Override
    protected void renderTooltip(MatrixStack matrixStack, int mouseX, int mouseY) {
        super.renderTooltip(matrixStack, mouseX, mouseY);
        
        if (currentTab == Tab.JOURNEY) {
            JourneyDataAttachment data = this.menu.getJourneyData();
            List<String> unlockedItems = getFilteredAndSortedItems(data);
            
            int x = (this.width - this.imageWidth) / 2;
            int y = (this.height - this.imageHeight) / 2;
            int startIndex = scrollOffset * ITEMS_PER_ROW;
            int endIndex = Math.min(startIndex + (VISIBLE_ROWS * ITEMS_PER_ROW), unlockedItems.size());

            for (int i = startIndex; i < endIndex; i++) {
                String itemId = unlockedItems.get(i);
                Item item = Registry.ITEM.get(new ResourceLocation(itemId));
                
                int gridIndex = i - startIndex;
                int row = gridIndex / ITEMS_PER_ROW;
                int col = gridIndex % ITEMS_PER_ROW;
                
                int itemX = x + 8 + col * 18;
                int itemY = y + 18 + row * 18;

                if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) {
                    ItemStack stack = new ItemStack(item);
                    this.renderTooltip(matrixStack, stack, mouseX, mouseY);
                    break;
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Check tab clicks
        if (mouseY >= y - 20 && mouseY < y) {
            if (mouseX >= x + 10 && mouseX < x + 70) {
                currentTab = Tab.DEPOSIT;
                this.menu.setDepositSlotEnabled(true);
                this.menu.setInJourneyTab(false);
                NetworkHandler.CHANNEL.sendToServer(new SyncTabPacket(false));
                return true;
            } else if (mouseX >= x + 80 && mouseX < x + 140) {
                currentTab = Tab.JOURNEY;
                this.menu.setDepositSlotEnabled(false);
                this.menu.setInJourneyTab(true);
                NetworkHandler.CHANNEL.sendToServer(new SyncTabPacket(true));
                return true;
            }
        }

        // Handle submit button click in Deposit tab
        if (currentTab == Tab.DEPOSIT && button == 0) {
            int buttonX = x + 110;
            int buttonY = y + 18;
            int buttonWidth = 50;
            int buttonHeight = 16;
            
            if (mouseX >= buttonX && mouseX < buttonX + buttonWidth &&
                mouseY >= buttonY && mouseY < buttonY + buttonHeight) {
                // Check if there's an item in deposit slot
                if (this.menu.slots.get(0).hasItem()) {
                    // Trigger submit
                    this.menu.submitDeposit();
                    return true;
                }
            }
        }

        // Handle item clicks in Journey tab
        if (currentTab == Tab.JOURNEY && button == 0) { // Left click
            JourneyDataAttachment data = this.menu.getJourneyData();
            List<String> unlockedItems = getFilteredAndSortedItems(data);
            
            int startIndex = scrollOffset * ITEMS_PER_ROW;
            int endIndex = Math.min(startIndex + (VISIBLE_ROWS * ITEMS_PER_ROW), unlockedItems.size());

            for (int i = startIndex; i < endIndex; i++) {
                String itemId = unlockedItems.get(i);
                
                int gridIndex = i - startIndex;
                int row = gridIndex / ITEMS_PER_ROW;
                int col = gridIndex % ITEMS_PER_ROW;
                
                int itemX = x + 8 + col * 18;
                int itemY = y + 18 + row * 18;

                if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) {
                    // Check if player is holding/carrying an item
                    ItemStack carriedStack = this.minecraft.player.inventory.getCarried();
                    if (!carriedStack.isEmpty()) {
                        String carriedItemId = Registry.ITEM.getKey(carriedStack.getItem()).toString();
                        if (carriedItemId.equals(itemId)) {
                            // Send packet to delete carried item
                            NetworkHandler.CHANNEL.sendToServer(new DeleteCarriedPacket());
                            this.minecraft.player.inventory.setCarried(ItemStack.EMPTY);
                            return true;
                        }
                        return false; // Clicking with a different item does nothing
                    }
                    
                    // Request item from server
                    int count = hasShiftDown() ? 64 : 1;
                    NetworkHandler.CHANNEL.sendToServer(new RequestItemPacket(itemId, count));
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (currentTab == Tab.JOURNEY) {
            JourneyDataAttachment data = this.menu.getJourneyData();
            List<String> unlockedItems = getFilteredAndSortedItems(data);
            int totalItems = unlockedItems.size();
            int maxScroll = Math.max(0, (totalItems - 1) / ITEMS_PER_ROW - VISIBLE_ROWS + 1);
            
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
    
    @Override
    public void removed() {
        super.removed();
        // The server-side menu will handle returning items via removed() method
        // No need to do anything client-side
    }
}
