package com.aryangpt007.journeymode.client.gui;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import com.aryangpt007.journeymode.network.FabricNetworkHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Journey Mode GUI with tabs for deposit and retrieval.
 * Ported to Fabric 1.20.1.
 */
public class JourneyModeScreen extends AbstractContainerScreen<JourneyModeMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(JourneyMode.MODID, "textures/gui/journey_mode.png");
    
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
        FabricNetworkHandler.syncTab(currentTab == Tab.JOURNEY);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        this.renderTooltip(poseStack, mouseX, mouseY);
        
        if (this.searchBox != null) {
            this.searchBox.setVisible(currentTab == Tab.JOURNEY);
        }
    }

    @Override
    protected void renderBg(PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Draw main background
        fill(poseStack, x, y, x + this.imageWidth, y + this.imageHeight, 0xFFC6C6C6);
        fill(poseStack, x + 1, y + 1, x + this.imageWidth - 1, y + this.imageHeight - 1, 0xFF8B8B8B);

        // Draw tabs
        drawTab(poseStack, x + 10, y - 20, "Deposit", currentTab == Tab.DEPOSIT);
        drawTab(poseStack, x + 80, y - 20, "Journey", currentTab == Tab.JOURNEY);

        if (currentTab == Tab.DEPOSIT) {
            renderDepositTab(poseStack, x, y);
        } else {
            renderJourneyTab(poseStack, x, y, mouseX, mouseY);
        }
        
        // Draw inventory slot backgrounds
        renderSlotBackgrounds(poseStack, x, y);
    }
    
    private void renderSlotBackgrounds(PoseStack poseStack, int x, int y) {
        if (currentTab == Tab.DEPOSIT) {
            int slotX = x + 80;
            int slotY = y + 18;
            fill(poseStack, slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF373737);
            fill(poseStack, slotX, slotY, slotX + 16, slotY + 16, 0xFF8B8B8B);
        }
        
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                int slotX = x + 8 + col * 18;
                int slotY = y + 110 + row * 18;
                fill(poseStack, slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF373737);
                fill(poseStack, slotX, slotY, slotX + 16, slotY + 16, 0xFF8B8B8B);
            }
        }
        
        for (int col = 0; col < 9; ++col) {
            int slotX = x + 8 + col * 18;
            int slotY = y + 168;
            fill(poseStack, slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF373737);
            fill(poseStack, slotX, slotY, slotX + 16, slotY + 16, 0xFF8B8B8B);
        }
    }

    private void drawTab(PoseStack poseStack, int x, int y, String label, boolean selected) {
        int color = selected ? 0xFFFFFFFF : 0xFFA0A0A0;
        int bgColor = selected ? 0xFF8B8B8B : 0xFF606060;
        
        fill(poseStack, x, y, x + 60, y + 20, bgColor);
        this.font.draw(poseStack, label, x + 5, y + 6, color);
    }

    private void renderDepositTab(PoseStack poseStack, int x, int y) {
        JourneyDataAttachment data = this.menu.getJourneyData();
        
        this.font.draw(poseStack, "Place items to unlock:", x + 40, y + 6, 0x404040);
        
        int buttonX = x + 110;
        int buttonY = y + 18;
        int buttonWidth = 50;
        int buttonHeight = 16;
        
        boolean hasItem = this.menu.slots.get(0).hasItem();
        
        int buttonColor = hasItem ? 0xFF4CAF50 : 0xFF808080;
        fill(poseStack, buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, buttonColor);
        fill(poseStack, buttonX + 1, buttonY + 1, buttonX + buttonWidth - 1, buttonY + buttonHeight - 1, 0xFF2E7D32);
        
        String buttonText = "Submit";
        int textX = buttonX + (buttonWidth - this.font.width(buttonText)) / 2;
        int textY = buttonY + 4;
        this.font.draw(poseStack, buttonText, textX, textY, hasItem ? 0xFFFFFFFF : 0xFFA0A0A0);
        
        int infoY = y + 42;
        if (hasItem) {
            ItemStack slotItem = this.menu.slots.get(0).getItem();
            if (!slotItem.isEmpty()) {
                data.initializeCalculator(
                    this.minecraft.level.getRecipeManager(),
                    this.minecraft.level.registryAccess()
                );
                
                int threshold = data.getThreshold(slotItem.getItem());
                int collected = data.getCollectedCount(slotItem.getItem());
                boolean alreadyUnlocked = data.isUnlocked(slotItem.getItem());
                
                if (alreadyUnlocked) {
                    this.font.draw(poseStack, "§a✓ Already Unlocked!", x + 8, infoY, 0x00FF00);
                } else {
                    this.font.draw(poseStack, "Required: " + threshold + " items", x + 8, infoY, 0x404040);
                    this.font.draw(poseStack, "Collected: " + collected + "/" + threshold, x + 8, infoY + 12, 0x404040);
                    
                    int progress = data.getProgress(slotItem.getItem());
                    this.font.draw(poseStack, "Progress: " + progress + "%", x + 8, infoY + 24, 0x606060);
                }
            }
        } else {
            this.font.draw(poseStack, "Unlocked: " + data.getUnlockedItems().size() + " items", x + 8, infoY, 0x404040);
        }
    }

    private void renderJourneyTab(PoseStack poseStack, int x, int y, int mouseX, int mouseY) {
        JourneyDataAttachment data = this.menu.getJourneyData();
        List<String> unlockedItems = getFilteredAndSortedItems(data);

        if (unlockedItems.isEmpty()) {
            if (searchQuery.isEmpty()) {
                this.font.draw(poseStack, "No items unlocked yet!", x + 30, y + 30, 0x404040);
                this.font.draw(poseStack, "Deposit items in the Deposit tab", x + 16, y + 45, 0x606060);
                this.font.draw(poseStack, "(Threshold varies by item)", x + 24, y + 57, 0x606060);
            } else {
                this.font.draw(poseStack, "No items match search", x + 35, y + 30, 0x404040);
            }
            return;
        }

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

            fill(poseStack, itemX - 1, itemY - 1, itemX + 17, itemY + 17, 0xFF373737);
            fill(poseStack, itemX, itemY, itemX + 16, itemY + 16, 0xFF8B8B8B);
            
            ItemStack stack = new ItemStack(item);
            this.itemRenderer.renderAndDecorateItem(stack, itemX, itemY);
            this.itemRenderer.renderGuiItemDecorations(this.font, stack, itemX, itemY);
            
            if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) {
                fill(poseStack, itemX, itemY, itemX + 16, itemY + 16, 0x80FFFFFF);
            }
        }
    }
    
    private List<String> getFilteredAndSortedItems(JourneyDataAttachment data) {
        List<String> items = data.getUnlockedItemsSorted();
        
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
    protected void renderTooltip(PoseStack poseStack, int mouseX, int mouseY) {
        super.renderTooltip(poseStack, mouseX, mouseY);
        
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
                    this.renderTooltip(poseStack, stack, mouseX, mouseY);
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
                FabricNetworkHandler.syncTab(false);
                return true;
            } else if (mouseX >= x + 80 && mouseX < x + 140) {
                currentTab = Tab.JOURNEY;
                this.menu.setDepositSlotEnabled(false);
                FabricNetworkHandler.syncTab(true);
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
                        String carriedItemId = Registry.ITEM.getKey(carriedStack.getItem()).toString();
                        if (carriedItemId.equals(itemId)) {
                            FabricNetworkHandler.deleteCarried();
                            return true;
                        }
                        return false;
                    }
                    
                    int count = hasShiftDown() ? 64 : 1;
                    FabricNetworkHandler.requestItem(itemId, count);
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
}
