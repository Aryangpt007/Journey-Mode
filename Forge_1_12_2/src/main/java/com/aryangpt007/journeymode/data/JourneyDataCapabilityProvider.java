package com.aryangpt007.journeymode.data;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class JourneyDataCapabilityProvider implements ICapabilitySerializable<NBTTagCompound> {
    @CapabilityInject(IJourneyData.class)
    public static Capability<IJourneyData> JOURNEY_DATA_CAPABILITY = null;

    private final IJourneyData backend = new JourneyData();

    public static class Storage implements Capability.IStorage<IJourneyData> {
        @Nullable
        @Override
        public NBTBase writeNBT(Capability<IJourneyData> capability, IJourneyData instance, EnumFacing side) {
            return instance.toNBT();
        }

        @Override
        public void readNBT(Capability<IJourneyData> capability, IJourneyData instance, EnumFacing side, NBTBase nbt) {
            if (nbt instanceof NBTTagCompound) {
                instance.fromNBT((NBTTagCompound) nbt);
            }
        }
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> cap, @Nullable EnumFacing side) {
        return cap == JOURNEY_DATA_CAPABILITY;
    }

    @Nullable
    @Override
    public <T> T getCapability(@Nonnull Capability<T> cap, @Nullable EnumFacing side) {
        if (cap == JOURNEY_DATA_CAPABILITY) {
            return JOURNEY_DATA_CAPABILITY.cast(backend);
        }
        return null;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        return backend.toNBT();
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        backend.fromNBT(nbt);
    }
}
