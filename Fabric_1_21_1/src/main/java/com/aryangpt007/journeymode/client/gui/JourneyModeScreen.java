package com.aryangpt007.journeymode.client.gui;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.client.CatalogStatsCache;
import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import com.aryangpt007.journeymode.network.FabricNetworkHandler;
import com.aryangpt007.journeymode.network.packets.DepositAllPacket;
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
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Journey Mode GUI with tabs for deposit and retrieval on Fabric
 */
public class JourneyModeScreen extends AbstractContainerScreen<JourneyModeMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(JourneyMode.MODID, "textures/gui/journey_mode.png");
    
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
    // The screen title and the team badge each get their own line above the tab row. They used to
    // share one (the title was hardcoded to -30, the badge drawn at TAB_Y_OFFSET - 10, which is
    // also -30), so any team name long enough to reach the centred "Journey Mode" ran straight
    // through it. Both are derived from TAB_Y_OFFSET rather than re-guessed, so the three rows
    // can never drift apart again.
    private static final int TEAM_BADGE_Y_OFFSET = TAB_Y_OFFSET - 10;
    private static final int TITLE_Y_OFFSET = TEAM_BADGE_Y_OFFSET - 12;

    // §10 Stats tab layout. Every value is measured against the element below it rather than
    // guessed: 1.8.0 hardcoded a 6-row cap with no clearance check at all, so on a modpack with
    // more than a handful of namespaces the per-mod list ran through the "Latest:" footer and on
    // into the vanilla "Inventory" label under the panel. Rows that don't fit now scroll.
    private static final int STATS_ROWS_TOP = 43;      // first per-mod row, relative to panel top
    private static final int STATS_ROW_HEIGHT = 10;
    private static final int STATS_LATEST_Y = 86;      // fixed footer; clears inventoryLabelY (100)
    private static final int STATS_VISIBLE_ROWS = (STATS_LATEST_Y - STATS_ROWS_TOP) / STATS_ROW_HEIGHT;
    private static final int STATS_ROW_WIDTH = 152;    // panel width (176) less matching 12px margins
    private static final int STATS_SCROLLBAR_X = 166;
    private static final int STATS_SCROLLBAR_WIDTH = 4;

    private int statsScrollOffset = 0;
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
        this.titleLabelY = TITLE_Y_OFFSET; // own line, clear of the team badge below it
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
        drawTab(guiGraphics, x + DEPOSIT_TAB_X, y + TAB_Y_OFFSET, "Deposit", currentTab == Tab.DEPOSIT);
        drawTab(guiGraphics, x + JOURNEY_TAB_X, y + TAB_Y_OFFSET, "Journey", currentTab == Tab.JOURNEY);
        drawTab(guiGraphics, x + STATS_TAB_X, y + TAB_Y_OFFSET, "Stats", currentTab == Tab.STATS);

        // §1 Shared Team Catalogs: badge drawn above the tabs so it never competes with any
        // existing tab-body layout (that's exactly where the Deposit-All overlap bug came from).
        String teamName = this.menu.getJourneyData().getTeamDisplayName();
        if (teamName != null) {
            guiGraphics.drawString(this.font, "Team: " + teamName, x + DEPOSIT_TAB_X, y + TEAM_BADGE_Y_OFFSET, 0xFFFFFF55, false);
        }

        if (currentTab == Tab.DEPOSIT) {
            renderDepositTab(guiGraphics, x, y);
        } else if (currentTab == Tab.JOURNEY) {
            renderJourneyTab(guiGraphics, x, y, mouseX, mouseY);
        } else {
            renderStatsTab(guiGraphics, x, y);
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

        guiGraphics.fill(x, y, x + TAB_WIDTH, y + TAB_HEIGHT, bgColor);
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
                
                int threshold = depositThreshold(data, slotItem);
                int collected = data.getCollectedCount(slotItem, this.minecraft.level.registryAccess());
                boolean alreadyUnlocked = data.isUnlocked(slotItem, this.minecraft.level.registryAccess());
                
                if (alreadyUnlocked) {
                    guiGraphics.drawString(this.font, "§aAlready Unlocked!", x + 8, infoY, 0x00FF00, false);
                } else {
                    guiGraphics.drawString(this.font, "Required: " + threshold + " items", x + 8, infoY, 0x404040, false);
                    guiGraphics.drawString(this.font, "Collected: " + collected + "/" + threshold, x + 8, infoY + 12, 0x404040, false);
                    
                    // Derived from the cached threshold rather than a second
                    // getThreshold() call; long math so a very large collected count
                    // can never overflow into a negative percentage.
                    int progress = (int) Math.min(100L, (long) collected * 100L / threshold);
                    guiGraphics.drawString(this.font, "Progress: " + progress + "%", x + 8, infoY + 24, 0x606060, false);
                }
            }
        } else {
            guiGraphics.drawString(this.font, "Unlocked: " + data.getUnlockedCount() + " items", x + 8, infoY, 0x404040, false);
        }

        renderDepositAllButton(guiGraphics, x, y);
    }

    // §6 Deposit-tab threshold cache. A threshold lookup can walk a large slice of the recipe
    // graph the first time it sees an item; 1.8.0 called it once per rendered frame from here,
    // so on any modpack whose recipe graph contains cycles (one reverse-crafting recipe is
    // enough) this pinned the render thread at 100% and the game appeared to hang outright.
    // Recompute only when the slot's item changes, or when a config reload/sync changes the
    // resolved rules (rulesGeneration, the same counter CatalogStatsCache keys off).
    private Item cachedThresholdItem = null;
    private int cachedThresholdGeneration = -1;
    private int cachedThreshold = 1;

    private int depositThreshold(JourneyDataAttachment data, ItemStack slotItem) {
        Item item = slotItem.getItem();
        int generation = com.aryangpt007.journeymode.config.ConfigHandler.getRulesGeneration();
        if (item != cachedThresholdItem || generation != cachedThresholdGeneration) {
            cachedThreshold = Math.max(1, data.getThreshold(item));
            cachedThresholdItem = item;
            cachedThresholdGeneration = generation;
        }
        return cachedThreshold;
    }

    private void renderDepositAllButton(GuiGraphics guiGraphics, int x, int y) {
        if (depositAllArmed && System.currentTimeMillis() > depositAllArmedUntil) {
            depositAllArmed = false;
        }

        int buttonX = x + 8;
        int buttonY = y + 80; // clear of the 3-line info block (infoY=y+42 .. y+66, ~10px tall)
        int buttonWidth = 160;
        int buttonHeight = 16;

        int bg = depositAllArmed ? 0xFFB71C1C : 0xFF616161;
        int border = depositAllArmed ? 0xFF7F0000 : 0xFF3A3A3A;
        guiGraphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, border);
        guiGraphics.fill(buttonX + 1, buttonY + 1, buttonX + buttonWidth - 1, buttonY + buttonHeight - 1, bg);

        String label = depositAllArmed ? "Click again to confirm..." : "Deposit All (main inventory)";
        int textX = buttonX + (buttonWidth - this.font.width(label)) / 2;
        guiGraphics.drawString(this.font, label, textX, buttonY + 4, 0xFFFFFFFF, false);
    }

    /**
     * §10 Catalog Statistics. Entirely client-side (synced data + client registry, no new
     * packets). "Total researchable" is cached in CatalogStatsCache since a full registry scan
     * on every GUI open is exactly the stutter this GUI is built to avoid.
     */
    private void renderStatsTab(GuiGraphics guiGraphics, int x, int y) {
        JourneyDataAttachment data = this.menu.getJourneyData();

        int unlockedCount = data.getUnlockedCount();
        int totalResearchable = com.aryangpt007.journeymode.client.CatalogStatsCache.getTotalResearchable();
        int percent = totalResearchable == 0 ? 0 : (unlockedCount * 100) / totalResearchable;

        guiGraphics.drawString(this.font, "Unlocked: " + unlockedCount + " / " + totalResearchable, x + 8, y + 8, 0x404040, false);
        guiGraphics.drawString(this.font, "Complete: " + percent + "%", x + 8, y + 19, 0x404040, false);
        guiGraphics.drawString(this.font, "By mod:", x + 8, y + 32, 0x606060, false);

        java.util.Map<String, Integer> perNamespaceTotal = com.aryangpt007.journeymode.client.CatalogStatsCache.getPerNamespaceResearchable();
        java.util.Map<String, Integer> perNamespaceUnlocked = com.aryangpt007.journeymode.client.CatalogStatsCache.getPerNamespaceUnlocked(data);

        int maxScroll = Math.max(0, perNamespaceTotal.size() - STATS_VISIBLE_ROWS);
        // A config reload can shrink the namespace list out from under the current offset.
        if (statsScrollOffset > maxScroll) statsScrollOffset = maxScroll;

        int labelWidth = maxScroll > 0 ? STATS_ROW_WIDTH - STATS_SCROLLBAR_WIDTH - 2 : STATS_ROW_WIDTH;

        int index = 0;
        int drawn = 0;
        for (java.util.Map.Entry<String, Integer> entry : perNamespaceTotal.entrySet()) {
            if (index++ < statsScrollOffset) continue;
            if (drawn >= STATS_VISIBLE_ROWS) break;
            String namespace = entry.getKey();
            int nsTotal = entry.getValue();
            Integer nsUnlockedBoxed = perNamespaceUnlocked.get(namespace);
            int nsUnlocked = nsUnlockedBoxed == null ? 0 : nsUnlockedBoxed;
            String label = fitToWidth(namespace + ": " + nsUnlocked + "/" + nsTotal, labelWidth);
            guiGraphics.drawString(this.font, label, x + 12, y + STATS_ROWS_TOP + drawn * STATS_ROW_HEIGHT, 0x404040, false);
            drawn++;
        }

        renderStatsScrollbar(guiGraphics, x, y, perNamespaceTotal.size(), maxScroll);

        String latestKey = com.aryangpt007.journeymode.client.CatalogStatsCache.getMostRecentlyUnlockedKey(data);
        if (latestKey != null) {
            ItemStack latestStack = JourneyDataAttachment.itemStackFromKey(latestKey, this.minecraft.level.registryAccess());
            String latestName = latestStack.isEmpty() ? latestKey : latestStack.getHoverName().getString();
            guiGraphics.drawString(this.font, fitToWidth("Latest: " + latestName, STATS_ROW_WIDTH), x + 8, y + STATS_LATEST_Y, 0x606060, false);
        }
    }

    /**
     * Thin scroll indicator for the per-mod list. Drawn only when the list actually overflows, so
     * a world with a handful of namespaces looks exactly as it did before.
     */
    private void renderStatsScrollbar(GuiGraphics guiGraphics, int x, int y, int totalRows, int maxScroll) {
        if (maxScroll <= 0) return;

        int trackX = x + STATS_SCROLLBAR_X;
        int trackTop = y + STATS_ROWS_TOP - 1;
        int trackBottom = trackTop + STATS_VISIBLE_ROWS * STATS_ROW_HEIGHT;
        guiGraphics.fill(trackX, trackTop, trackX + STATS_SCROLLBAR_WIDTH, trackBottom, 0xFF373737);

        int trackHeight = trackBottom - trackTop;
        int thumbHeight = Math.max(6, trackHeight * STATS_VISIBLE_ROWS / totalRows);
        int thumbY = trackTop + (trackHeight - thumbHeight) * statsScrollOffset / maxScroll;
        guiGraphics.fill(trackX, thumbY, trackX + STATS_SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFFC6C6C6);
    }

    /**
     * Trim a stats line to the width actually available. A long namespace
     * ("sophisticatedbackpacks: 0/61") otherwise runs past the panel edge, and once the scrollbar
     * is drawn it would run underneath that too.
     */
    private String fitToWidth(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) return text;
        int budget = maxWidth - this.font.width("...");
        int end = text.length();
        while (end > 0 && this.font.width(text.substring(0, end)) > budget) end--;
        return text.substring(0, end) + "...";
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
    
    // The search filter used to be re-derived on every call, and a single rendered frame reaches
    // this twice (item grid + tooltip pass) with the mouse handlers on top of that. With a search
    // active each pass rebuilt an ItemStack and resolved a display name for every unlocked key -
    // thousands of allocations per frame on a large catalog. The sorted key list is cheap to
    // recompute (string comparisons only) and doubles as the cache key, so the expensive step only
    // re-runs when the catalog or the query actually changes. Validating against the live list
    // rather than a counter means there is no staleness window at all.
    private String cachedFilterQuery = null;
    private List<String> cachedFilterSource = null;
    private List<String> cachedFilterResult = null;

    private List<String> getFilteredAndSortedItems(JourneyDataAttachment data) {
        // Start with sorted items (most recent first)
        List<String> items = data.getUnlockedItemsSorted();

        if (searchQuery.isEmpty()) {
            return items;
        }

        if (searchQuery.equals(cachedFilterQuery) && items.equals(cachedFilterSource)) {
            return cachedFilterResult;
        }

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

        cachedFilterQuery = searchQuery;
        cachedFilterSource = items;
        cachedFilterResult = filtered;
        return filtered;
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
                FabricNetworkHandler.sendToServer(new SyncTabPacket(false));
                return true;
            } else if (mouseX >= x + JOURNEY_TAB_X && mouseX < x + JOURNEY_TAB_X + TAB_WIDTH) {
                currentTab = Tab.JOURNEY;
                this.menu.setDepositSlotEnabled(false);
                FabricNetworkHandler.sendToServer(new SyncTabPacket(true));
                return true;
            } else if (mouseX >= x + STATS_TAB_X && mouseX < x + STATS_TAB_X + TAB_WIDTH) {
                currentTab = Tab.STATS;
                statsScrollOffset = 0;
                this.menu.setDepositSlotEnabled(false);
                FabricNetworkHandler.sendToServer(new SyncTabPacket(false));
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
                    FabricNetworkHandler.sendToServer(new DepositAllPacket(hasShiftDown()));
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
                    ItemStack carriedStack = this.menu.getCarried();
                    if (!carriedStack.isEmpty()) {
                        String carriedItemId = BuiltInRegistries.ITEM.getKey(carriedStack.getItem()).toString();
                        if (carriedItemId.equals(itemId)) {
                            FabricNetworkHandler.sendToServer(new DeleteCarriedPacket());
                            return true;
                        }
                        return false;
                    }
                    
                    // A "full stack" is per item, not a flat 64: swords stack to 1, potions to
                    // 16, and mods declare their own limits. Ask the reconstructed stack instead
                    // of assuming - the server clamps to the same limit independently, since it
                    // cannot trust a count that arrived over the network.
                    ItemStack requested = JourneyDataAttachment.itemStackFromKey(itemId, this.minecraft.level.registryAccess());
                    int count = hasShiftDown() ? Math.max(1, requested.getMaxStackSize()) : 1;
                    FabricNetworkHandler.sendToServer(new RequestItemPacket(itemId, count));
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (currentTab == Tab.STATS) {
            int maxScroll = Math.max(0, com.aryangpt007.journeymode.client.CatalogStatsCache
                .getPerNamespaceResearchable().size() - STATS_VISIBLE_ROWS);
            statsScrollOffset = Math.max(0, Math.min(maxScroll, statsScrollOffset - (int) scrollY));
            return true;
        }

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
