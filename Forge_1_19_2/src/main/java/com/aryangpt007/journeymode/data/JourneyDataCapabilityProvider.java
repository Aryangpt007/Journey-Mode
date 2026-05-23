package com.aryangpt007.journeymode.data;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JourneyDataCapabilityProvider implements ICapabilitySerializable<CompoundTag> {
    public static Capability<JourneyDataAttachment> JOURNEY_DATA_CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});

    private final JourneyDataAttachment backend = new JourneyDataAttachment();
    private final LazyOptional<JourneyDataAttachment> optional = LazyOptional.of(() -> backend);

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == JOURNEY_DATA_CAPABILITY) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("data", backend.toJsonString());
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("data")) {
            JourneyDataAttachment loaded = JourneyDataAttachment.fromJsonString(nbt.getString("data"));
            backend.copyFrom(loaded);
        }
    }
}
