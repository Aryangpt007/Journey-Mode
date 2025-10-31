package com.aryangpt007.journeymode.platform.neoforge;

import com.aryangpt007.journeymode.core.JourneyData;
import com.mojang.serialization.Codec;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * NeoForge data handler that wraps the common JourneyData with NeoForge's AttachmentType system.
 */
public class NeoForgeDataHandler {
    
    public static final String MODID = "journeymode";
    
    // Registration for attachments (player data)
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = 
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID);
    
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<JourneyDataAttachment>> JOURNEY_DATA = 
        ATTACHMENTS.register("journey_data", () -> 
            AttachmentType.builder(() -> new JourneyDataAttachment())
                .serialize(JourneyDataAttachment.CODEC)
                .build()
        );
    
    /**
     * Wrapper class for AttachmentType serialization
     */
    public static class JourneyDataAttachment {
        
        public static final Codec<JourneyDataAttachment> CODEC = Codec.STRING.xmap(
            jsonString -> {
                // Deserialize: String -> JourneyDataAttachment
                JourneyDataAttachment attachment = new JourneyDataAttachment();
                try {
                    attachment.data = JourneyData.fromJsonString(jsonString);
                } catch (Exception e) {
                    attachment.data = new JourneyData();
                }
                return attachment;
            },
            attachment -> {
                // Serialize: JourneyDataAttachment -> String
                return attachment.data.toJsonString();
            }
        );
        
        private JourneyData data;
        
        public JourneyDataAttachment() {
            this.data = new JourneyData();
        }
        
        public JourneyData getData() {
            return data;
        }
        
        public void setData(JourneyData data) {
            this.data = data;
        }
    }
    
    /**
     * Get Journey Mode data for a player
     */
    public static JourneyData getJourneyData(Player player) {
        JourneyDataAttachment attachment = player.getData(JOURNEY_DATA);
        return attachment.getData();
    }
    
    /**
     * Save Journey Mode data for a player
     */
    public static void saveJourneyData(Player player, JourneyData data) {
        JourneyDataAttachment attachment = player.getData(JOURNEY_DATA);
        attachment.setData(data);
        player.setData(JOURNEY_DATA, attachment);
    }
}
