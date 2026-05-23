package com.aryangpt007.journeymode.data;

import net.minecraft.util.Direction;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class JourneyDataCapabilityProvider implements ICapabilitySerializable<CompoundNBT> {
    @CapabilityInject(JourneyDataAttachment.class)
    public static Capability<JourneyDataAttachment> JOURNEY_DATA_CAPABILITY = null;

    private final JourneyDataAttachment backend = new JourneyDataAttachment();
    private final LazyOptional<JourneyDataAttachment> optional = LazyOptional.of(() -> backend);

    public static class Storage implements Capability.IStorage<JourneyDataAttachment> {
        @Nullable
        @Override
        public INBT writeNBT(Capability<JourneyDataAttachment> capability, JourneyDataAttachment instance, Direction side) {
            CompoundNBT nbt = new CompoundNBT();
            nbt.putString("data", instance.toJsonString());
            return nbt;
        }

        @Override
        public void readNBT(Capability<JourneyDataAttachment> capability, JourneyDataAttachment instance, Direction side, INBT nbt) {
            if (nbt instanceof CompoundNBT) {
                CompoundNBT compound = (CompoundNBT) nbt;
                if (compound.contains("data")) {
                    JourneyDataAttachment loaded = JourneyDataAttachment.fromJsonString(compound.getString("data"));
                    instance.copyFrom(loaded);
                }
            }
        }
    }

    @Override
    public @Nonnull <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == JOURNEY_DATA_CAPABILITY) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundNBT serializeNBT() {
        CompoundNBT nbt = new CompoundNBT();
        nbt.putString("data", backend.toJsonString());
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundNBT nbt) {
        if (nbt.contains("data")) {
            JourneyDataAttachment loaded = JourneyDataAttachment.fromJsonString(nbt.getString("data"));
            backend.copyFrom(loaded);
        }
    }
}
