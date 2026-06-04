package com.aryangpt007.journeymode.client.gui;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.config.ConfigHandler;
import com.aryangpt007.journeymode.data.IJourneyData;
import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import com.aryangpt007.journeymode.network.NetworkHandler;
import com.aryangpt007.journeymode.network.packets.*;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SideOnly(Side.CLIENT)
public class JourneyModeScreen extends GuiContainer {
    private final JourneyModeMenu menu;

    private enum Tab {
        DEPOSIT,
        JOURNEY
    }

    private Tab currentTab = Tab.DEPOSIT;

    // Search and scrolling in Journey tab
    private GuiTextField searchBox;
    private String searchQuery = "";
    private int scrollOffset = 0;

    private static final int ITEMS_PER_ROW = 9;
    private static final int VISIBLE_ROWS = 2;

    public JourneyModeScreen(JourneyModeMenu menu) {
        super(menu);
        this.menu = menu;
        this.xSize = 176;
        this.ySize = 192;
    }

    @Override
    public void initGui() {
        super.initGui();
        int x = this.guiLeft;
        int y = this.guiTop;

        // Search text field
        this.searchBox = new GuiTextField(0, this.fontRenderer, x + 8, y + 58, 160, 12);
        this.searchBox.setMaxStringLength(32);
        this.searchBox.setEnableBackgroundDrawing(true);
        this.searchBox.setVisible(false);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        
        if (currentTab == Tab.JOURNEY) {
            this.searchBox.drawTextBox();
        }
        
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // Draw tab labels (Deposit and Journey)
        this.fontRenderer.drawString(
            JourneyMode.translatable("tab.deposit").getUnformattedText(), 
            18, 
            -14, 
            (currentTab == Tab.DEPOSIT) ? 0x000000 : 0xAAAAAA
        );
        
        this.fontRenderer.drawString(
            JourneyMode.translatable("tab.journey").getUnformattedText(), 
            88, 
            -14, 
            (currentTab == Tab.JOURNEY) ? 0x000000 : 0xAAAAAA
        );

        if (currentTab == Tab.DEPOSIT) {
            this.fontRenderer.drawString("Deposit Item", 8, 6, 0x404040);
            
            boolean hasItem = this.menu.inventorySlots.get(0).getHasStack();
            int infoY = 42;
            IJourneyData data = this.menu.getJourneyData();
            
            if (hasItem && data != null) {
                ItemStack slotItem = this.menu.inventorySlots.get(0).getStack();
                if (!slotItem.isEmpty()) {
                    int threshold = data.getThreshold(slotItem);
                    int collected = data.getCollectedCount(slotItem);
                    boolean alreadyUnlocked = data.isUnlocked(slotItem);
                    
                    if (alreadyUnlocked) {
                        String text = "Already Unlocked!";
                        int textWidth = this.fontRenderer.getStringWidth(text);
                        this.fontRenderer.drawString(text, 88 - textWidth / 2, infoY, 0x007F00);
                    } else {
                        String reqText = "Required: " + threshold;
                        String collText = "Collected: " + collected + " / " + threshold;
                        int progress = data.getProgress(slotItem);
                        String progText = "Progress: " + progress + "%";
                        
                        this.fontRenderer.drawString(reqText, 88 - this.fontRenderer.getStringWidth(reqText) / 2, infoY, 0x404040);
                        this.fontRenderer.drawString(collText, 88 - this.fontRenderer.getStringWidth(collText) / 2, infoY + 12, 0x404040);
                        this.fontRenderer.drawString(progText, 88 - this.fontRenderer.getStringWidth(progText) / 2, infoY + 24, 0x555555);
                    }
                }
            } else if (data != null) {
                String text = "Unlocked: " + data.getUnlockedItems().size() + " items";
                this.fontRenderer.drawString(text, 88 - this.fontRenderer.getStringWidth(text) / 2, infoY, 0x404040);
            }
        } else {
            this.fontRenderer.drawString("Unlocked Items", 8, 6, 0x404040);
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        
        int x = this.guiLeft;
        int y = this.guiTop;

        // Draw main body background
        drawRect(x, y, x + xSize, y + ySize, 0xFFC6C6C6);

        // Draw tabs
        int depositColor = (currentTab == Tab.DEPOSIT) ? 0xFFC6C6C6 : 0xFF555555;
        int journeyColor = (currentTab == Tab.JOURNEY) ? 0xFFC6C6C6 : 0xFF555555;

        drawRect(x + 10, y - 20, x + 70, y, depositColor);
        drawRect(x + 80, y - 20, x + 140, y, journeyColor);

        // Outline tabs
        drawRect(x + 9, y - 20, x + 10, y, 0xFF000000);
        drawRect(x + 70, y - 20, x + 71, y, 0xFF000000);
        drawRect(x + 9, y - 21, x + 71, y - 20, 0xFF000000);

        drawRect(x + 79, y - 20, x + 80, y, 0xFF000000);
        drawRect(x + 140, y - 20, x + 141, y, 0xFF000000);
        drawRect(x + 79, y - 21, x + 141, y - 20, 0xFF000000);

        // Main body border
        drawRect(x - 1, y - 1, x, y + ySize + 1, 0xFF000000);
        drawRect(x + xSize, y - 1, x + xSize + 1, y + ySize + 1, 0xFF000000);
        drawRect(x - 1, y - 1, x + xSize + 1, y, 0xFF000000);
        drawRect(x - 1, y + ySize, x + xSize + 1, y + ySize + 1, 0xFF000000);

        // Draw slot outlines for player inventory (positioned with proper spacing for taller GUI)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                int px = x + 7 + col * 18;
                int py = y + 109 + row * 18;
                drawRect(px, py, px + 18, py + 18, 0xFF8B8B8B);
                drawRect(px + 1, py + 1, px + 17, py + 17, 0xFF373737);
            }
        }

        // Draw slot outlines for player hotbar
        for (int col = 0; col < 9; ++col) {
            int px = x + 7 + col * 18;
            int py = y + 167;
            drawRect(px, py, px + 18, py + 18, 0xFF8B8B8B);
            drawRect(px + 1, py + 1, px + 17, py + 17, 0xFF373737);
        }

        if (currentTab == Tab.DEPOSIT) {
            // Draw deposit slot background outline (center top of screen)
            drawRect(x + 79, y + 17, x + 97, y + 35, 0xFF8B8B8B);
            drawRect(x + 80, y + 18, x + 96, y + 34, 0xFF373737);

            // Draw submit button
            int buttonX = x + 110;
            int buttonY = y + 18;
            int buttonWidth = 50;
            int buttonHeight = 16;
            
            drawRect(buttonX - 1, buttonY - 1, buttonX + buttonWidth + 1, buttonY + buttonHeight + 1, 0xFF000000);
            drawRect(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, 0xFF555555);
            this.fontRenderer.drawString("Submit", buttonX + 8, buttonY + 4, 0xFFFFFF);
        } else {
            // Journey tab: draw unlocked items grid and search
            drawGrid(x, y, mouseX, mouseY);
        }
    }

    private void drawGrid(int x, int y, int mouseX, int mouseY) {
        IJourneyData data = this.menu.getJourneyData();
        List<String> unlockedItems = getFilteredAndSortedItems(data);
        
        int startIndex = scrollOffset * ITEMS_PER_ROW;
        int endIndex = Math.min(startIndex + (VISIBLE_ROWS * ITEMS_PER_ROW), unlockedItems.size());

        // Step 1: Draw all the slot backgrounds (flat 2D, unlit)
        for (int i = startIndex; i < endIndex; i++) {
            String key = unlockedItems.get(i);
            ItemStack stack = com.aryangpt007.journeymode.data.JourneyData.itemStackFromKey(key);
            if (stack.isEmpty()) continue;
            
            int gridIndex = i - startIndex;
            int row = gridIndex / ITEMS_PER_ROW;
            int col = gridIndex % ITEMS_PER_ROW;
            
            int itemX = x + 8 + col * 18;
            int itemY = y + 18 + row * 18;

            drawRect(itemX - 1, itemY - 1, itemX + 17, itemY + 17, 0xFF373737);
            drawRect(itemX, itemY, itemX + 16, itemY + 16, 0xFF8B8B8B);
        }

        // Step 2: Enable GUI standard item lighting and render all the items
        net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
        for (int i = startIndex; i < endIndex; i++) {
            String key = unlockedItems.get(i);
            ItemStack stack = com.aryangpt007.journeymode.data.JourneyData.itemStackFromKey(key);
            if (stack.isEmpty()) continue;
            
            int gridIndex = i - startIndex;
            int row = gridIndex / ITEMS_PER_ROW;
            int col = gridIndex % ITEMS_PER_ROW;
            
            int itemX = x + 8 + col * 18;
            int itemY = y + 18 + row * 18;
            
            this.itemRender.renderItemAndEffectIntoGUI(stack, itemX, itemY);
            this.itemRender.renderItemOverlays(this.fontRenderer, stack, itemX, itemY);
        }
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();

        // Step 3: Draw hover highlights (flat 2D, semi-transparent overlay)
        for (int i = startIndex; i < endIndex; i++) {
            String key = unlockedItems.get(i);
            ItemStack stack = com.aryangpt007.journeymode.data.JourneyData.itemStackFromKey(key);
            if (stack.isEmpty()) continue;
            
            int gridIndex = i - startIndex;
            int row = gridIndex / ITEMS_PER_ROW;
            int col = gridIndex % ITEMS_PER_ROW;
            
            int itemX = x + 8 + col * 18;
            int itemY = y + 18 + row * 18;

            if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) {
                drawRect(itemX, itemY, itemX + 16, itemY + 16, 0x80FFFFFF);
            }
        }
    }
    
    private List<String> getFilteredAndSortedItems(IJourneyData data) {
        List<String> items = data.getUnlockedItemsSorted();
        
        if (!searchQuery.isEmpty()) {
            List<String> filtered = new ArrayList<>();
            for (String key : items) {
                ItemStack stack = com.aryangpt007.journeymode.data.JourneyData.itemStackFromKey(key);
                if (!stack.isEmpty()) {
                    String itemName = stack.getDisplayName().toLowerCase();
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
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        super.renderHoveredToolTip(mouseX, mouseY);
        
        if (currentTab == Tab.JOURNEY) {
            IJourneyData data = this.menu.getJourneyData();
            List<String> unlockedItems = getFilteredAndSortedItems(data);
            
            int x = this.guiLeft;
            int y = this.guiTop;
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
                    ItemStack stack = com.aryangpt007.journeymode.data.JourneyData.itemStackFromKey(key);
                    this.renderToolTip(stack, mouseX, mouseY);
                    break;
                }
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (currentTab == Tab.JOURNEY && this.searchBox.isFocused()) {
            if (keyCode == 1) { // Escape
                this.mc.player.closeScreen();
            } else {
                this.searchBox.textboxKeyTyped(typedChar, keyCode);
                this.searchQuery = this.searchBox.getText().toLowerCase();
                this.scrollOffset = 0; // Reset scroll when search changes
            }
        } else {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (currentTab == Tab.JOURNEY) {
            this.searchBox.mouseClicked(mouseX, mouseY, mouseButton);
        }
        
        int x = this.guiLeft;
        int y = this.guiTop;

        // Check tab clicks
        if (mouseY >= y - 20 && mouseY < y) {
            if (mouseX >= x + 10 && mouseX < x + 70) {
                currentTab = Tab.DEPOSIT;
                this.menu.setDepositSlotEnabled(true);
                this.menu.setInJourneyTab(false);
                this.searchBox.setVisible(false);
                this.searchBox.setFocused(false);
                NetworkHandler.sendToServer(new SyncTabPacket(false));
            } else if (mouseX >= x + 80 && mouseX < x + 140) {
                currentTab = Tab.JOURNEY;
                this.menu.setDepositSlotEnabled(false);
                this.menu.setInJourneyTab(true);
                this.searchBox.setVisible(true);
                NetworkHandler.sendToServer(new SyncTabPacket(true));
            }
        }

        // Handle submit button click in Deposit tab
        if (currentTab == Tab.DEPOSIT && mouseButton == 0) {
            int buttonX = x + 110;
            int buttonY = y + 18;
            int buttonWidth = 50;
            int buttonHeight = 16;
            
            if (mouseX >= buttonX && mouseX < buttonX + buttonWidth &&
                mouseY >= buttonY && mouseY < buttonY + buttonHeight) {
                if (this.menu.inventorySlots.get(0).getHasStack()) {
                    this.menu.submitDeposit();
                }
            }
        }

        // Handle item clicks in Journey tab
        if (currentTab == Tab.JOURNEY && mouseButton == 0) {
            IJourneyData data = this.menu.getJourneyData();
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
                    ItemStack carriedStack = this.mc.player.inventory.getItemStack();
                    if (!carriedStack.isEmpty()) {
                        String carriedKey = com.aryangpt007.journeymode.data.JourneyData.getItemKey(carriedStack);
                        if (carriedKey.equals(itemId)) {
                            NetworkHandler.sendToServer(new DeleteCarriedPacket());
                            this.mc.player.inventory.setItemStack(ItemStack.EMPTY);
                        }
                        return;
                    }
                    
                    int count = isShiftKeyDown() ? 64 : 1;
                    NetworkHandler.sendToServer(new RequestItemPacket(itemId, count));
                    return;
                }
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int delta = org.lwjgl.input.Mouse.getEventDWheel();
        if (delta != 0 && currentTab == Tab.JOURNEY) {
            int scroll = delta > 0 ? 1 : -1;
            
            IJourneyData data = this.menu.getJourneyData();
            List<String> unlockedItems = getFilteredAndSortedItems(data);
            int totalItems = unlockedItems.size();
            int maxScroll = Math.max(0, (totalItems - 1) / ITEMS_PER_ROW - VISIBLE_ROWS + 1);
            
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - scroll));
        }
    }
}
