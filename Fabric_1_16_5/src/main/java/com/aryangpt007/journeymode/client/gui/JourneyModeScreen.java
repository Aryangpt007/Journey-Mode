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
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

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
        JOURNEY,
        STATS
    }

    // Shared by both rendering and click-hit-testing so the two can never drift apart (a
    // previous mismatch here is what caused the Deposit-All button to overlap other text).
    private static final int TAB_WIDTH = 54;
    private static final int TAB_HEIGHT = 20;
    private static final int TAB_Y_OFFSET = -20;
    private static final int DEPOSIT_TAB_X = 6;
    private static final int JOURNEY_TAB_X = 64;
    private static final int STATS_TAB_X = 122;

    private Tab currentTab = Tab.DEPOSIT;
    private int scrollOffset = 0;
    private static final int ITEMS_PER_ROW = 9;
    private static final int VISIBLE_ROWS = 3;
    
    private EditBox searchBox;
    private String searchQuery = "";

    // §8 Deposit All: click once to arm, click again within the window to actually deposit -
    // a lightweight stand-in for a full confirmation dialog, since this action is destructive.
    private boolean depositAllArmed = false;
    private long depositAllArmedUntil = 0L;
    private static final long DEPOSIT_ALL_CONFIRM_WINDOW_MS = 3000L;

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
        this.searchBox = new EditBox(this.font, x + 8, y + 86, 160, 12, new TextComponent("Search"));
        this.searchBox.setMaxLength(50);
        this.searchBox.setBordered(true);
        this.searchBox.setVisible(false);
        this.searchBox.setTextColor(0xFFFFFF);
        this.searchBox.setResponder(query -> {
            this.searchQuery = query.toLowerCase();
            this.scrollOffset = 0;
        });
        this.children.add(this.searchBox);

        // Sync initial tab state to menu and server
        this.menu.setInJourneyTab(currentTab == Tab.JOURNEY);
        FabricNetworkHandler.syncTab(currentTab == Tab.JOURNEY);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        this.renderTooltip(poseStack, mouseX, mouseY);
        
        if (this.searchBox != null) {
            this.searchBox.setVisible(currentTab == Tab.JOURNEY);
            if (currentTab == Tab.JOURNEY) {
                this.searchBox.render(poseStack, mouseX, mouseY, partialTick);
            }
        }
    }

    @Override
    protected void renderBg(PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
        this.minecraft.getTextureManager().bind(TEXTURE);
        
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Draw main background
        fill(poseStack, x, y, x + this.imageWidth, y + this.imageHeight, 0xFFC6C6C6);
        fill(poseStack, x + 1, y + 1, x + this.imageWidth - 1, y + this.imageHeight - 1, 0xFF8B8B8B);

        // Draw tabs
        drawTab(poseStack, x + DEPOSIT_TAB_X, y + TAB_Y_OFFSET, "Deposit", currentTab == Tab.DEPOSIT);
        drawTab(poseStack, x + JOURNEY_TAB_X, y + TAB_Y_OFFSET, "Journey", currentTab == Tab.JOURNEY);
        drawTab(poseStack, x + STATS_TAB_X, y + TAB_Y_OFFSET, "Stats", currentTab == Tab.STATS);

        // §1 Shared Team Catalogs: badge drawn above the tabs so it never competes with any
        // existing tab-body layout (that's exactly where the Deposit-All overlap bug came from).
        String teamName = this.menu.getJourneyData().getTeamDisplayName();
        if (teamName != null) {
            this.font.draw(poseStack, "Team: " + teamName, x + DEPOSIT_TAB_X, y + TAB_Y_OFFSET - 10, 0xFFFFFF55);
        }

        if (currentTab == Tab.DEPOSIT) {
            renderDepositTab(poseStack, x, y);
        } else if (currentTab == Tab.JOURNEY) {
            renderJourneyTab(poseStack, x, y, mouseX, mouseY);
        } else {
            renderStatsTab(poseStack, x, y);
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

        fill(poseStack, x, y, x + TAB_WIDTH, y + TAB_HEIGHT, bgColor);
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
                int collected = data.getCollectedCount(slotItem);
                boolean alreadyUnlocked = data.isUnlocked(slotItem);
                
                if (alreadyUnlocked) {
                    this.font.draw(poseStack, "§aAlready Unlocked!", x + 8, infoY, 0x00FF00);
                } else {
                    this.font.draw(poseStack, "Required: " + threshold + " items", x + 8, infoY, 0x404040);
                    this.font.draw(poseStack, "Collected: " + collected + "/" + threshold, x + 8, infoY + 12, 0x404040);
                    
                    int progress = data.getProgress(slotItem);
                    this.font.draw(poseStack, "Progress: " + progress + "%", x + 8, infoY + 24, 0x606060);
                }
            }
        } else {
            this.font.draw(poseStack, "Unlocked: " + data.getUnlockedItems().size() + " items", x + 8, infoY, 0x404040);
        }

        renderDepositAllButton(poseStack, x, y);
    }

    private void renderDepositAllButton(PoseStack poseStack, int x, int y) {
        if (depositAllArmed && System.currentTimeMillis() > depositAllArmedUntil) {
            depositAllArmed = false;
        }

        int buttonX = x + 8;
        int buttonY = y + 80; // clear of the 3-line info block (infoY=y+42 .. y+66, ~10px tall)
        int buttonWidth = 160;
        int buttonHeight = 16;

        int bg = depositAllArmed ? 0xFFB71C1C : 0xFF616161;
        int border = depositAllArmed ? 0xFF7F0000 : 0xFF3A3A3A;
        fill(poseStack, buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, border);
        fill(poseStack, buttonX + 1, buttonY + 1, buttonX + buttonWidth - 1, buttonY + buttonHeight - 1, bg);

        String label = depositAllArmed ? "Click again to confirm..." : "Deposit All (main inventory)";
        int textX = buttonX + (buttonWidth - this.font.width(label)) / 2;
        this.font.draw(poseStack, label, textX, buttonY + 4, 0xFFFFFFFF);
    }

    /**
     * §10 Catalog Statistics. Entirely client-side (synced data + client registry, no new
     * packets). "Total researchable" is cached in CatalogStatsCache since a full registry scan
     * on every GUI open is exactly the stutter 1.7.0 was built to avoid.
     */
    private void renderStatsTab(PoseStack poseStack, int x, int y) {
        JourneyDataAttachment data = this.menu.getJourneyData();

        int unlockedCount = data.getUnlockedItems().size();
        int totalResearchable = com.aryangpt007.journeymode.client.CatalogStatsCache.getTotalResearchable();
        int percent = totalResearchable == 0 ? 0 : (unlockedCount * 100) / totalResearchable;

        int lineY = y + 10;
        this.font.draw(poseStack, "Unlocked: " + unlockedCount + " / " + totalResearchable, x + 8, lineY, 0x404040);
        lineY += 12;
        this.font.draw(poseStack, "Complete: " + percent + "%", x + 8, lineY, 0x404040);
        lineY += 16;

        this.font.draw(poseStack, "By mod:", x + 8, lineY, 0x606060);
        lineY += 11;

        java.util.Map<String, Integer> perNamespaceTotal = com.aryangpt007.journeymode.client.CatalogStatsCache.getPerNamespaceResearchable();
        java.util.Map<String, Integer> perNamespaceUnlocked = com.aryangpt007.journeymode.client.CatalogStatsCache.getPerNamespaceUnlocked(data);

        int rows = 0;
        for (java.util.Map.Entry<String, Integer> entry : perNamespaceTotal.entrySet()) {
            if (rows >= 6) break; // room-limited; a scrollable list is future polish, not this pass
            String namespace = entry.getKey();
            int nsTotal = entry.getValue();
            Integer nsUnlockedBoxed = perNamespaceUnlocked.get(namespace);
            int nsUnlocked = nsUnlockedBoxed == null ? 0 : nsUnlockedBoxed;
            this.font.draw(poseStack, namespace + ": " + nsUnlocked + "/" + nsTotal, x + 12, lineY, 0x404040);
            lineY += 10;
            rows++;
        }

        if (!data.getUnlockTimestamps().isEmpty()) {
            long latest = 0L;
            String latestKey = null;
            for (java.util.Map.Entry<String, Long> entry : data.getUnlockTimestamps().entrySet()) {
                if (entry.getValue() > latest) {
                    latest = entry.getValue();
                    latestKey = entry.getKey();
                }
            }
            if (latestKey != null) {
                ItemStack latestStack = JourneyDataAttachment.itemStackFromKey(latestKey);
                String latestName = latestStack.isEmpty() ? latestKey : latestStack.getHoverName().getString();
                this.font.draw(poseStack, "Latest: " + latestName, x + 8, y + 96, 0x606060);
            }
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
            String key = unlockedItems.get(i);
            ItemStack stack = JourneyDataAttachment.itemStackFromKey(key);
            if (stack.isEmpty()) continue;
            
            int gridIndex = i - startIndex;
            int row = gridIndex / ITEMS_PER_ROW;
            int col = gridIndex % ITEMS_PER_ROW;
            
            int itemX = x + 8 + col * 18;
            int itemY = y + 18 + row * 18;

            fill(poseStack, itemX - 1, itemY - 1, itemX + 17, itemY + 17, 0xFF373737);
            fill(poseStack, itemX, itemY, itemX + 16, itemY + 16, 0xFF8B8B8B);
            
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
            for (String key : items) {
                ItemStack stack = JourneyDataAttachment.itemStackFromKey(key);
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
                String key = unlockedItems.get(i);
                
                int gridIndex = i - startIndex;
                int row = gridIndex / ITEMS_PER_ROW;
                int col = gridIndex % ITEMS_PER_ROW;
                
                int itemX = x + 8 + col * 18;
                int itemY = y + 18 + row * 18;

                if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) {
                    ItemStack stack = JourneyDataAttachment.itemStackFromKey(key);
                    this.renderTooltip(poseStack, stack, mouseX, mouseY);
                    break;
                }
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // EditBox.keyPressed only consumes control keys (backspace/arrows/select-all/etc.) and
        // returns false for plain character keys, since those are handled by charTyped instead.
        // Left unguarded, that lets a letter key bound to a vanilla keybind (e.g. "E" for
        // inventory-close) bubble past the field and close this screen mid-type. Swallow
        // everything except Escape while the search box has focus.
        if (this.searchBox != null && this.searchBox.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            this.searchBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        if (mouseY >= y + TAB_Y_OFFSET && mouseY < y) {
            if (mouseX >= x + DEPOSIT_TAB_X && mouseX < x + DEPOSIT_TAB_X + TAB_WIDTH) {
                currentTab = Tab.DEPOSIT;
                this.menu.setDepositSlotEnabled(true);
                this.menu.setInJourneyTab(false);
                FabricNetworkHandler.syncTab(false);
                return true;
            } else if (mouseX >= x + JOURNEY_TAB_X && mouseX < x + JOURNEY_TAB_X + TAB_WIDTH) {
                currentTab = Tab.JOURNEY;
                this.menu.setDepositSlotEnabled(false);
                this.menu.setInJourneyTab(true);
                FabricNetworkHandler.syncTab(true);
                return true;
            } else if (mouseX >= x + STATS_TAB_X && mouseX < x + STATS_TAB_X + TAB_WIDTH) {
                currentTab = Tab.STATS;
                this.menu.setDepositSlotEnabled(false);
                this.menu.setInJourneyTab(false);
                FabricNetworkHandler.syncTab(false);
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

            int allButtonX = x + 8;
            int allButtonY = y + 80;
            if (mouseX >= allButtonX && mouseX < allButtonX + 160 &&
                mouseY >= allButtonY && mouseY < allButtonY + 16) {
                if (depositAllArmed) {
                    depositAllArmed = false;
                    FabricNetworkHandler.sendDepositAll(hasShiftDown());
                } else {
                    depositAllArmed = true;
                    depositAllArmedUntil = System.currentTimeMillis() + DEPOSIT_ALL_CONFIRM_WINDOW_MS;
                }
                return true;
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
                    ItemStack carriedStack = this.minecraft.player.inventory.getCarried();
                    if (!carriedStack.isEmpty()) {
                        String carriedItemId = JourneyDataAttachment.getItemKey(carriedStack);
                        if (carriedItemId.equals(itemId)) {
                            FabricNetworkHandler.deleteCarried();
                            this.minecraft.player.inventory.setCarried(ItemStack.EMPTY);
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
