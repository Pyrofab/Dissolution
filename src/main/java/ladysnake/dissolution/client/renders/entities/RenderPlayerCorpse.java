package ladysnake.dissolution.client.renders.entities;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Random;

import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import ladysnake.dissolution.client.models.ModelMinionZombie;
import ladysnake.dissolution.client.models.ModelPlayerCorpse;
import ladysnake.dissolution.client.renders.ShaderHelper;
import ladysnake.dissolution.common.Reference;
import ladysnake.dissolution.common.entity.EntityPlayerCorpse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;

public class RenderPlayerCorpse extends RenderBiped<EntityPlayerCorpse> {
	
	private static final ResourceLocation PLAYER_EXPLODING_TEXTURES = new ResourceLocation(Reference.MOD_ID + ":textures/entity/player_corpse/player.png");
	
	public RenderPlayerCorpse(RenderManager rendermanagerIn) {
		super(rendermanagerIn, new ModelPlayerCorpse(0.0F, true), 0.5F);
		LayerBipedArmor layerbipedarmor = new LayerBipedArmor(this)
        {
            protected void initArmor()
            {
                this.modelLeggings = new ModelMinionZombie(0.5F, true);
                this.modelArmor = new ModelMinionZombie(1.0F, true);
            }
        };
        this.addLayer(layerbipedarmor);
	}
	
	@Override
	protected ResourceLocation getEntityTexture(EntityPlayerCorpse entity) {
		return DefaultPlayerSkin.getDefaultSkinLegacy();
	}
	
	@Override
	protected void preRenderCallback(EntityPlayerCorpse entitylivingbaseIn, float partialTickTime) {
	}
	
	@Override
	public void doRender(EntityPlayerCorpse entity, double x, double y, double z, float entityYaw, float partialTicks) {
		ShaderHelper.useShader(ShaderHelper.corpseDissolution);
		super.doRender(entity, x, y, z, entityYaw, partialTicks);
		ShaderHelper.revert();
	}

}
