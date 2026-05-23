package com.aryangpt007.journeymode;

import com.aryangpt007.journeymode.client.gui.JourneyModeScreen;
import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

import javax.annotation.Nullable;

public class GuiHandler implements IGuiHandler {
    public static final int JOURNEY_MODE_GUI_ID = 0;

    @Nullable
    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID == JOURNEY_MODE_GUI_ID) {
            return new JourneyModeMenu(player.inventory);
        }
        return null;
    }

    @Nullable
    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID == JOURNEY_MODE_GUI_ID) {
            return new JourneyModeScreen(new JourneyModeMenu(player.inventory));
        }
        return null;
    }
}
