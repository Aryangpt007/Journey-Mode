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
import java.util.Map;
import java.util.Set;

@SideOnly(Side.CLIENT)
public class JourneyModeScreen extends GuiContainer {
    private final JourneyModeMenu menu;

    private enum Tab {
        DEPOSIT,
        JOURNEY,
        STATS
    }

    // Shared by both rendering and click-hit-testing so the two can never drift apart - a
    // mismatch here (button drawn at one Y, hit-tested at another) is exactly what let the
    // Deposit-All button visually collide with the item-info text in a previous release.
    private static final int TAB_WIDTH = 54;
    private static final int TAB_HEIGHT = 20;
    private static final int TAB_Y_TOP = -20; // relative to guiTop
    private static final int DEPOSIT_TAB_X = 6;
    private static final int JOURNEY_TAB_X = 64;
    private static final int STATS_TAB_X = 122;

    // Bug fix: the 3-line item-info block (infoY=y+42, +12, +24; see drawGuiContainerForegroundLayer)
    // actually extends down to roughly y+75 once the ~9px font height of the third line is
    // accounted for. The button used to sit at y+68, squarely on top of that third line - hence
    // the illegible overlapping text reported from screenshots. y+80 gives genuine clearance
    // below the info block and well above the player inventory slots (which start at y+109).
    private static final int DEPOSIT_ALL_BUTTON_X = 8;
    private static final int DEPOSIT_ALL_BUTTON_Y = 80;
    private static final int DEPOSIT_ALL_BUTTON_WIDTH = 160;
    private static final int DEPOSIT_ALL_BUTTON_HEIGHT = 16;

    // Where the player-inventory slot row starts (both as an absolute-space offset in
    // drawGuiContainerBackgroundLayer and as a relative-space ceiling for drawStatsTab's text -
    // one constant, so a text block can never silently grow into the slots below it).
    private static final int PLAYER_INVENTORY_TOP_Y = 109;

    // §1 Shared Team Catalogs: badge drawn ABOVE the tab row (not inside any tab's body content)
    // so it can never collide with existing tab layout - the same discipline that fixed the
    // Deposit-All button overlap bug: one constant, computed from TAB_Y_TOP, rather than a
    // second guessed magic number.
    private static final int TEAM_BADGE_Y_OFFSET = TAB_Y_TOP - 10;

    private Tab currentTab = Tab.DEPOSIT;

    // Search and scrolling in Journey tab
    private GuiTextField searchBox;
    private String searchQuery = "";
    private int scrollOffset = 0;

    private static final int ITEMS_PER_ROW = 9;
    private static final int VISIBLE_ROWS = 2;

    // §8 Deposit All: click once to arm, click again within the window to actually deposit - a
    // lightweight stand-in for a full confirmation dialog, since this action is destructive.
    private boolean depositAllArmed = false;
    private long depositAllArmedUntil = 0L;
    private static final long DEPOSIT_ALL_CONFIRM_WINDOW_MS = 3000L;

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
        // Draw tab labels (relative coordinate space - GuiContainer already translates by
        // guiLeft/guiTop before calling this method, so these use the same TAB_*_X constants as
        // the absolute-space tab backgrounds/click-hit-tests, just without adding guiLeft).
        drawTabLabel(JourneyMode.translatable("tab.deposit").getUnformattedText(), DEPOSIT_TAB_X, currentTab == Tab.DEPOSIT);
        drawTabLabel(JourneyMode.translatable("tab.journey").getUnformattedText(), JOURNEY_TAB_X, currentTab == Tab.JOURNEY);
        drawTabLabel(JourneyMode.translatable("tab.stats").getUnformattedText(), STATS_TAB_X, currentTab == Tab.STATS);

        // §1 Shared Team Catalogs: badge drawn above the tabs so it never competes with any
        // tab's own content (deposit item-info block, journey grid, or stats text).
        String teamName = this.menu.getJourneyData() != null ? this.menu.getJourneyData().getTeamDisplayName() : null;
        if (teamName != null) {
            this.fontRenderer.drawString("Team: " + teamName, DEPOSIT_TAB_X, TEAM_BADGE_Y_OFFSET, 0xFFFF55);
        }

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
                        // Matches the reference's exact wording/color code - plain ASCII, no
                        // unicode symbol (see bug fix #2 in TooltipHandler for the same class of issue).
                        String text = "§aAlready Unlocked!";
                        int textWidth = this.fontRenderer.getStringWidth(text);
                        this.fontRenderer.drawString(text, 88 - textWidth / 2, infoY, 0x00FF00);
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
        } else if (currentTab == Tab.JOURNEY) {
            this.fontRenderer.drawString("Unlocked Items", 8, 6, 0x404040);
        } else {
            drawStatsTab();
        }
    }

    private void drawTabLabel(String label, int tabX, boolean selected) {
        int textWidth = this.fontRenderer.getStringWidth(label);
        int centerX = tabX + (TAB_WIDTH - textWidth) / 2;
        this.fontRenderer.drawString(label, centerX, TAB_Y_TOP + 6, selected ? 0x000000 : 0xAAAAAA);
    }

    /**
     * §10 Catalog Statistics. Entirely client-side (synced data + client registry, no new
     * packets). "Total researchable" is cached in CatalogStatsCache since a full item registry
     * scan on every GUI open is exactly the stutter this project avoids elsewhere.
     * Relative-space coordinates (see drawGuiContainerForegroundLayer's translated call site).
     *
     * Layout is measured, not assumed: every line's Y comes from the previous line's actual
     * drawn position plus its own line height, and the namespace-row loop stops once it would run
     * into the reserved "Latest unlocked" line or PLAYER_INVENTORY_TOP_Y (whichever is tighter) -
     * exactly the discipline that would have prevented the Deposit-All button bug, applied here
     * since this is new-in-this-pass content sharing the same GUI.
     */
    private void drawStatsTab() {
        IJourneyData data = this.menu.getJourneyData();
        if (data == null) return;

        int unlockedCount = data.getUnlockedItems().size();
        int totalResearchable = com.aryangpt007.journeymode.client.CatalogStatsCache.getTotalResearchable();
        int percent = totalResearchable == 0 ? 0 : (unlockedCount * 100) / totalResearchable;

        int lineY = 6;
        this.fontRenderer.drawString("Unlocked: " + unlockedCount + " / " + totalResearchable, 8, lineY, 0x404040);
        lineY += 12;
        this.fontRenderer.drawString("Complete: " + percent + "%", 8, lineY, 0x404040);
        lineY += 14;

        this.fontRenderer.drawString("By mod:", 8, lineY, 0x606060);
        lineY += 10;

        Map<String, Integer> perNamespaceTotal = com.aryangpt007.journeymode.client.CatalogStatsCache.getPerNamespaceResearchable();
        Map<String, Integer> perNamespaceUnlocked = com.aryangpt007.journeymode.client.CatalogStatsCache.getPerNamespaceUnlocked(data);

        String latestName = findLatestUnlockedName(data);
        // Reserve room for the "Latest" line (if there is one) before computing how far the
        // namespace rows are allowed to grow - never a fixed row count assumed to "just fit".
        int reservedForLatest = latestName == null ? 0 : 11;
        int maxRowBottom = PLAYER_INVENTORY_TOP_Y - reservedForLatest;

        int rows = 0;
        for (Map.Entry<String, Integer> entry : perNamespaceTotal.entrySet()) {
            if (rows >= 6) break; // soft cap; a scrollable list is future polish, not this pass
            if (lineY + 9 > maxRowBottom) break; // hard cap: genuine measured clearance, not a guess
            String namespace = entry.getKey();
            int nsTotal = entry.getValue();
            Integer nsUnlockedBoxed = perNamespaceUnlocked.get(namespace);
            int nsUnlocked = nsUnlockedBoxed == null ? 0 : nsUnlockedBoxed;
            this.fontRenderer.drawString(namespace + ": " + nsUnlocked + "/" + nsTotal, 12, lineY, 0x404040);
            lineY += 9;
            rows++;
        }

        if (latestName != null) {
            this.fontRenderer.drawString("Latest: " + latestName, 8, lineY + 2, 0x606060);
        }
    }

    @javax.annotation.Nullable
    private String findLatestUnlockedName(IJourneyData data) {
        Map<String, Long> timestamps = data.getUnlockTimestamps();
        if (timestamps.isEmpty()) return null;

        long latest = 0L;
        String latestKey = null;
        for (Map.Entry<String, Long> entry : timestamps.entrySet()) {
            if (entry.getValue() > latest) {
                latest = entry.getValue();
                latestKey = entry.getKey();
            }
        }
        if (latestKey == null) return null;

        ItemStack latestStack = com.aryangpt007.journeymode.data.JourneyData.itemStackFromKey(latestKey);
        return latestStack.isEmpty() ? latestKey : latestStack.getDisplayName();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        
        int x = this.guiLeft;
        int y = this.guiTop;

        // Draw main body background
        drawRect(x, y, x + xSize, y + ySize, 0xFFC6C6C6);

        // Draw tabs (three non-overlapping regions using the shared TAB_*_X/TAB_WIDTH constants -
        // also reused by mouseClicked's hit-test and drawTabLabel, so render and click can never drift apart)
        drawTabBackground(x, y, DEPOSIT_TAB_X, currentTab == Tab.DEPOSIT);
        drawTabBackground(x, y, JOURNEY_TAB_X, currentTab == Tab.JOURNEY);
        drawTabBackground(x, y, STATS_TAB_X, currentTab == Tab.STATS);

        // Main body border
        drawRect(x - 1, y - 1, x, y + ySize + 1, 0xFF000000);
        drawRect(x + xSize, y - 1, x + xSize + 1, y + ySize + 1, 0xFF000000);
        drawRect(x - 1, y - 1, x + xSize + 1, y, 0xFF000000);
        drawRect(x - 1, y + ySize, x + xSize + 1, y + ySize + 1, 0xFF000000);

        // Draw slot outlines for player inventory (positioned with proper spacing for taller GUI)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                int px = x + 7 + col * 18;
                int py = y + PLAYER_INVENTORY_TOP_Y + row * 18;
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

            drawDepositAllButton(x, y);
        } else if (currentTab == Tab.JOURNEY) {
            // Journey tab: draw unlocked items grid and search
            drawGrid(x, y, mouseX, mouseY);
        }
        // Stats tab has no background-layer content of its own - its text is drawn in
        // drawGuiContainerForegroundLayer (see drawStatsTab), same as the Deposit tab's item-info block.
    }

    private void drawTabBackground(int x, int y, int tabX, boolean selected) {
        int color = selected ? 0xFFC6C6C6 : 0xFF555555;
        int left = x + tabX;
        int right = left + TAB_WIDTH;
        int top = y + TAB_Y_TOP;
        int bottom = y;

        drawRect(left, top, right, bottom, color);

        // Outline (left/right/top edges only - the bottom edge merges into the main body border)
        drawRect(left - 1, top - 1, left, bottom, 0xFF000000);
        drawRect(right, top - 1, right + 1, bottom, 0xFF000000);
        drawRect(left - 1, top - 1, right + 1, top, 0xFF000000);
    }

    private void drawDepositAllButton(int x, int y) {
        if (depositAllArmed && System.currentTimeMillis() > depositAllArmedUntil) {
            depositAllArmed = false;
        }

        int buttonX = x + DEPOSIT_ALL_BUTTON_X;
        int buttonY = y + DEPOSIT_ALL_BUTTON_Y;
        int buttonWidth = DEPOSIT_ALL_BUTTON_WIDTH;
        int buttonHeight = DEPOSIT_ALL_BUTTON_HEIGHT;

        int border = depositAllArmed ? 0xFF7F0000 : 0xFF3A3A3A;
        int bg = depositAllArmed ? 0xFFB71C1C : 0xFF616161;
        drawRect(buttonX - 1, buttonY - 1, buttonX + buttonWidth + 1, buttonY + buttonHeight + 1, border);
        drawRect(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, bg);

        String label = depositAllArmed ? "Click again to confirm..." : "Deposit All (main inventory)";
        int textX = buttonX + (buttonWidth - this.fontRenderer.getStringWidth(label)) / 2;
        this.fontRenderer.drawString(label, textX, buttonY + 4, 0xFFFFFF);
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

    /**
     * B2 (search bar leaks keybinds, e.g. "E" closes GUI mid-typing) analysis for 1.12.2:
     * unlike 1.13+'s Screen, which splits input into keyPressed(int,int,int) (control keys) and
     * charTyped(char,int) (printable keys) - letting a printable key bound to a vanilla keybind
     * bubble past an EditBox that only consumes control keys - GuiScreen on 1.12.2 has a single
     * keyTyped(char, int) hook for every key, control or printable. This override already
     * swallows every key except Escape while the search box is focused (never delegating to
     * super.keyTyped, which is where GuiContainer's inventory-close keybind check lives), so the
     * architectural gap the bug exploits on other versions does not exist here. No further fix
     * needed for 1.12.2 - kept as-is, verified against the pre-existing behavior below.
     */
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

        // Check tab clicks - same TAB_*_X/TAB_WIDTH/TAB_Y_TOP constants used to draw the tabs
        // in drawTabBackground/drawTabLabel, so the clickable region can never drift from what's
        // actually drawn.
        if (mouseY >= y + TAB_Y_TOP && mouseY < y) {
            if (mouseX >= x + DEPOSIT_TAB_X && mouseX < x + DEPOSIT_TAB_X + TAB_WIDTH) {
                currentTab = Tab.DEPOSIT;
                this.menu.setDepositSlotEnabled(true);
                this.menu.setInJourneyTab(false);
                this.searchBox.setVisible(false);
                this.searchBox.setFocused(false);
                NetworkHandler.sendToServer(new SyncTabPacket(false));
            } else if (mouseX >= x + JOURNEY_TAB_X && mouseX < x + JOURNEY_TAB_X + TAB_WIDTH) {
                currentTab = Tab.JOURNEY;
                this.menu.setDepositSlotEnabled(false);
                this.menu.setInJourneyTab(true);
                this.searchBox.setVisible(true);
                NetworkHandler.sendToServer(new SyncTabPacket(true));
            } else if (mouseX >= x + STATS_TAB_X && mouseX < x + STATS_TAB_X + TAB_WIDTH) {
                currentTab = Tab.STATS;
                this.menu.setDepositSlotEnabled(false);
                this.menu.setInJourneyTab(false);
                this.searchBox.setVisible(false);
                this.searchBox.setFocused(false);
                NetworkHandler.sendToServer(new SyncTabPacket(false));
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

            // Same DEPOSIT_ALL_BUTTON_* constants used by drawDepositAllButton - render and
            // click-hit-test can never drift apart (this drift is what caused the Deposit-All
            // button to overlap other text in a previous release).
            int allButtonX = x + DEPOSIT_ALL_BUTTON_X;
            int allButtonY = y + DEPOSIT_ALL_BUTTON_Y;
            if (mouseX >= allButtonX && mouseX < allButtonX + DEPOSIT_ALL_BUTTON_WIDTH &&
                mouseY >= allButtonY && mouseY < allButtonY + DEPOSIT_ALL_BUTTON_HEIGHT) {
                if (depositAllArmed) {
                    depositAllArmed = false;
                    NetworkHandler.sendToServer(new DepositAllPacket(isShiftKeyDown()));
                } else {
                    depositAllArmed = true;
                    depositAllArmedUntil = System.currentTimeMillis() + DEPOSIT_ALL_CONFIRM_WINDOW_MS;
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
