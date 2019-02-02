package ladysnake.dissolution.client;

import ladysnake.dissolution.api.v1.annotation.CalledThroughReflection;
import ladysnake.dissolution.api.v1.event.client.HudEvent;
import ladysnake.dissolution.client.gui.hud.PossessionHud;
import ladysnake.dissolution.client.network.ClientMessageHandling;
import ladysnake.dissolution.common.entity.PossessableEntityImpl;
import ladysnake.satin.client.event.RenderEvent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.client.render.EntityRendererRegistry;
import net.fabricmc.fabric.events.client.ClientTickEvent;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

@CalledThroughReflection
public class DissolutionClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientMessageHandling.init();
        RenderEvent.SHADER_EFFECT.register(DissolutionEffects.INSTANCE::renderShaders);
        RenderEvent.BLOCK_ENTITIES_RENDER.register(DissolutionEffects.INSTANCE);
        RenderEvent.WINDOW_RESIZED.register(DissolutionEffects.INSTANCE);
        RenderEvent.PICK_ENTITY_SHADER.register(EntityShaders::getShader);
        ClientTickEvent.CLIENT.register(DissolutionEffects.INSTANCE::tick);
        HudEvent.RENDER_HOTBAR.register(PossessionHud.INSTANCE::onRenderHotbar);
        EntityRendererRegistry.INSTANCE.register(PossessableEntityImpl.class, (r, it) -> new BipedEntityRenderer<>(r, new PlayerEntityModel<>(0f, false), .5f));
    }
}