package com.aryangpt007.journeymode.client.gui;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import com.aryangpt007.journeymode.network.FabricNetworkHandler;
import com.aryangpt007.journeymode.network.packets.RequestItemPacket;
import com.aryangpt007.journeymode.network.packets.SyncTabPacket;
import com.aryangpt007.journeymode.network.packets.DeleteCarriedPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Journey Mode GUI with tabs for deposit and retrieval on Fabric
 */
public class JourneyModeScreen extends AbstractContainerScreen<JourneyModeMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(JourneyMode.MODID, "textures/gui/journey_mode.png");
    
    private enum Tab {
        DEPOSIT,
        JOURNEY
    }
    
    private Tab currentTab = Tab.DEPOSIT;
    private int scrollOffset = 0;
    private static final int ITEMS_PER_ROW = 9;
    private static final int VISIBLE_ROWS = 3;
    
    private EditBox searchBox;
    private String searchQuery = "";

    public JourneyModeScreen(JourneyModeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageHeight = 204;
        this.imageWidth = 176;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.titleLabelY = -30;
        this.inventoryLabelY = this.imageHeight - 104;
        
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        this.searchBox = new EditBox(this.font, x + 8, y + 86, 160, 12, Component.literal("Search"));
        this.searchBox.setMaxLength(50);
        this.searchBox.setBordered(true);
        this.searchBox.setVisible(false);
        this.searchBox.setTextColor(0xFFFFFF);
        this.searchBox.setResponder(query -> {
            this.searchQuery = query.toLowerCase();
            this.scrollOffset = 0;
        });
        this.addRenderableWidget(this.searchBox);
        
        // Sync initial tab state to server
        FabricNetworkHandler.sendToServer(new SyncTabPacket(currentTab == Tab.JOURNEY));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
        
        if (this.searchBox != null) {
            this.searchBox.setVisible(currentTab == Tab.JOURNEY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Draw main background
        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFFC6C6C6);
        guiGraphics.fill(x + 1, y + 1, x + this.imageWidth - 1, y + this.imageHeight - 1, 0xFF8B8B8B);

        // Draw tabs
        drawTab(guiGraphics, x + 10, y - 20, "Deposit", currentTab == Tab.DEPOSIT);
        drawTab(guiGraphics, x + 80, y - 20, "Journey", currentTab == Tab.JOURNEY);

        if (currentTab == Tab.DEPOSIT) {
            renderDepositTab(guiGraphics, x, y);
        } else {
            renderJourneyTab(guiGraphics, x, y, mouseX, mouseY);
        }
        
        renderSlotBackgrounds(guiGraphics, x, y);
    }
    
    private void renderSlotBackgrounds(GuiGraphics guiGraphics, int x, int y) {
        if (currentTab == Tab.DEPOSIT) {
            int slotX = x + 80;
            int slotY = y + 18;
            guiGraphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF373737);
            guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF8B8B8B);
        }
        
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                int slotX = x + 8 + col * 18;
                int slotY = y + 110 + row * 18;
                guiGraphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF373737);
                guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF8B8B8B);
            }
        }
        
        for (int col = 0; col < 9; ++col) {
            int slotX = x + 8 + col * 18;
            int slotY = y + 168;
            guiGraphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF373737);
            guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF8B8B8B);
        }
    }

    private void drawTab(GuiGraphics guiGraphics, int x, int y, String label, boolean selected) {
        int color = selected ? 0xFFFFFFFF : 0xFFA0A0A0;
        int bgColor = selected ? 0xFF8B8B8B : 0xFF606060;
        
        guiGraphics.fill(x, y, x + 60, y + 20, bgColor);
        guiGraphics.drawString(this.font, label, x + 5, y + 6, color, false);
    }

    private void renderDepositTab(GuiGraphics guiGraphics, int x, int y) {
        JourneyDataAttachment data = this.menu.getJourneyData();
        
        guiGraphics.drawString(this.font, "Place items to unlock:", x + 40, y + 6, 0x404040, false);
        
        int buttonX = x + 110;
        int buttonY = y + 18;
        int buttonWidth = 50;
        int buttonHeight = 16;
        
        boolean hasItem = this.menu.slots.get(0).hasItem();
        
        int buttonColor = hasItem ? 0xFF4CAF50 : 0xFF808080;
        guiGraphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, buttonColor);
        guiGraphics.fill(buttonX + 1, buttonY + 1, buttonX + buttonWidth - 1, buttonY + buttonHeight - 1, 0xFF2E7D32);
        
        String buttonText = "Submit";
        int textX = buttonX + (buttonWidth - this.font.width(buttonText)) / 2;
        int textY = buttonY + 4;
        guiGraphics.drawString(this.font, buttonText, textX, textY, hasItem ? 0xFFFFFFFF : 0xFFA0A0A0, false);
        
        int infoY = y + 42;
        if (hasItem) {
            ItemStack slotItem = this.menu.slots.get(0).getItem();
            if (!slotItem.isEmpty()) {
                data.initializeCalculator(
                    this.minecraft.level.getRecipeManager(),
                    this.minecraft.level.registryAccess()
                );
                
                int threshold = data.getThreshold(slotItem.getItem());
                int collected = data.getCollectedCount(slotItem, this.minecraft.level.registryAccess());
                boolean alreadyUnlocked = data.isUnlocked(slotItem, this.minecraft.level.registryAccess());
                
                if (alreadyUnlocked) {
                    guiGraphics.drawString(this.font, "§a✓ Already Unlocked!", x + 8, infoY, 0x00FF00, false);
                } else {
                    guiGraphics.drawString(this.font, "Required: " + threshold + " items", x + 8, infoY, 0x404040, false);
                    guiGraphics.drawString(this.font, "Collected: " + collected + "/" + threshold, x + 8, infoY + 12, 0x404040, false);
                    
                    int progress = data.getProgress(slotItem, this.minecraft.level.registryAccess());
                    guiGraphics.drawString(this.font, "Progress: " + progress + "%", x + 8, infoY + 24, 0x606060, false);
                }
            }
        } else {
            guiGraphics.drawString(this.font, "Unlocked: " + data.getUnlockedItems().size() + " items", x + 8, infoY, 0x404040, false);
        }
    }

    private void renderJourneyTab(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        JourneyDataAttachment data = this.menu.getJourneyData();
        List<String> unlockedItems = getFilteredAndSortedItems(data);

        if (unlockedItems.isEmpty()) {
            if (searchQuery.isEmpty()) {
                guiGraphics.drawString(this.font, "No items unlocked yet!", x + 30, y + 30, 0x404040, false);
                guiGraphics.drawString(this.font, "Deposit items in the Deposit tab", x + 16, y + 45, 0x606060, false);
                guiGraphics.drawString(this.font, "(Threshold varies by item)", x + 24, y + 57, 0x606060, false);
            } else {
                guiGraphics.drawString(this.font, "No items match search", x + 35, y + 30, 0x404040, false);
            }
            return;
        }

        int startIndex = scrollOffset * ITEMS_PER_ROW;
        int endIndex = Math.min(startIndex + (VISIBLE_ROWS * ITEMS_PER_ROW), unlockedItems.size());

        for (int i = startIndex; i < endIndex; i++) {
            String key = unlockedItems.get(i);
            ItemStack stack = JourneyDataAttachment.itemStackFromKey(key, this.minecraft.level.registryAccess());
            if (stack.isEmpty()) continue;
            
            int gridIndex = i - startIndex;
            int row = gridIndex / ITEMS_PER_ROW;
            int col = gridIndex % ITEMS_PER_ROW;
            
            int itemX = x + 8 + col * 18;
            int itemY = y + 18 + row * 18;

            guiGraphics.fill(itemX - 1, itemY - 1, itemX + 17, itemY + 17, 0xFF373737);
            guiGraphics.fill(itemX, itemY, itemX + 16, itemY + 16, 0xFF8B8B8B);
            
            guiGraphics.renderItem(stack, itemX, itemY);
            
            if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) {
                guiGraphics.fill(itemX, itemY, itemX + 16, itemY + 16, 0x80FFFFFF);
            }
        }
    }
    
    private List<String> getFilteredAndSortedItems(JourneyDataAttachment data) {
        List<String> items = data.getUnlockedItemsSorted();
        
        if (!searchQuery.isEmpty()) {
            List<String> filtered = new ArrayList<>();
            for (String key : items) {
                ItemStack stack = JourneyDataAttachment.itemStackFromKey(key, this.minecraft.level.registryAccess());
                if (!stack.isEmpty()) {
                    String itemName = stack.getHoverName().getString().toLowerCase();
                    if (itemName.contains(searchQuery)) {
                        filtered.add(key);
                    }
                }
            }
            return filtered;
        }
        
        return items;
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderTooltip(guiGraphics, mouseX, mouseY);
        
        if (currentTab == Tab.JOURNEY) {
            JourneyDataAttachment data = this.menu.getJourneyData();
            List<String> unlockedItems = getFilteredAndSortedItems(data);
            
            int x = (this.width - this.imageWidth) / 2;
            int y = (this.height - this.imageHeight) / 2;
            int startIndex = scrollOffset * ITEMS_PER_ROW;
            int endIndex = Math.min(startIndex + (VISIBLE_ROWS * ITEMS_PER_ROW), unlockedItems.size());

            for (int i = startIndex; i < endIndex; i++) {
                String key = unlockedItems.get(i);
                
                int gridIndex = i - startIndex;
                int row = gridIndex / ITEMS_PER_ROW;
                int col = gridIndex % ITEMS_PER_ROW;
                
                int itemX = x + 8 + col * 18;
                int itemY = y + 18 + row * 18;

                if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) {
                    ItemStack stack = JourneyDataAttachment.itemStackFromKey(key, this.minecraft.level.registryAccess());
                    guiGraphics.renderTooltip(this.font, stack, mouseX, mouseY);
                    break;
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        if (mouseY >= y - 20 && mouseY < y) {
            if (mouseX >= x + 10 && mouseX < x + 70) {
                currentTab = Tab.DEPOSIT;
                this.menu.setDepositSlotEnabled(true);
                FabricNetworkHandler.sendToServer(new SyncTabPacket(false));
                return true;
            } else if (mouseX >= x + 80 && mouseX < x + 140) {
                currentTab = Tab.JOURNEY;
                this.menu.setDepositSlotEnabled(false);
                FabricNetworkHandler.sendToServer(new SyncTabPacket(true));
                return true;
            }
        }

        if (currentTab == Tab.DEPOSIT && button == 0) {
            int buttonX = x + 110;
            int buttonY = y + 18;
            int buttonWidth = 50;
            int buttonHeight = 16;
            
            if (mouseX >= buttonX && mouseX < buttonX + buttonWidth &&
                mouseY >= buttonY && mouseY < buttonY + buttonHeight) {
                if (this.menu.slots.get(0).hasItem()) {
                    this.menu.submitDeposit();
                    return true;
                }
            }
        }

        if (currentTab == Tab.JOURNEY && button == 0) {
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
                    ItemStack carriedStack = this.menu.getCarried();
                    if (!carriedStack.isEmpty()) {
                        String carriedItemId = BuiltInRegistries.ITEM.getKey(carriedStack.getItem()).toString();
                        if (carriedItemId.equals(itemId)) {
                            FabricNetworkHandler.sendToServer(new DeleteCarriedPacket());
                            return true;
                        }
                        return false;
                    }
                    
                    int count = hasShiftDown() ? 64 : 1;
                    FabricNetworkHandler.sendToServer(new RequestItemPacket(itemId, count));
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (currentTab == Tab.JOURNEY) {
            JourneyDataAttachment data = this.menu.getJourneyData();
            List<String> unlockedItems = getFilteredAndSortedItems(data);
            int totalItems = unlockedItems.size();
            int maxScroll = Math.max(0, (totalItems - 1) / ITEMS_PER_ROW - VISIBLE_ROWS + 1);
            
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    
    @Override
    public void removed() {
        super.removed();
    }
    
    @Override
    protected void renderSlot(GuiGraphics guiGraphics, net.minecraft.world.inventory.Slot slot) {
        if (slot.index == 0 && currentTab != Tab.DEPOSIT) {
            return;
        }
        super.renderSlot(guiGraphics, slot);
    }
}
