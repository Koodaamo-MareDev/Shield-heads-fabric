package dev.koodaamo.mixin.client;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.authlib.GameProfile;

import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.model.special.ShieldModelRenderer;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Identifier;

@Mixin(ShieldModelRenderer.class)
public class MixinShieldModelRenderer {

	@Inject(method = "render", at = @At("TAIL"), cancellable = true)
	public void render(@Nullable ComponentMap componentMap, ItemDisplayContext itemDisplayContext, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int light, int overlay, boolean bl, CallbackInfo ci) {
		if (componentMap != null) {
			ProfileComponent component = componentMap.get(DataComponentTypes.PROFILE);
			if (component == null)
				return;

			// While this technically should contain the proper info to load the skin, we'll have to later fetch it using SkullBlockEntity#fetchProfileByName
			GameProfile compProfile = component.gameProfile();

			// Don't bother processing empty names - also acts as a way to disable the display.
			if (compProfile.getName().isEmpty())
				return;

			// Fetch the profile so that the texture gets loaded.
			SkullBlockEntity.fetchProfileByName(compProfile.getName()).getNow(Optional.empty()).ifPresent((profile) -> {

				// Fetch the texture itself using the skin provider
				MinecraftClient client = MinecraftClient.getInstance();
				PlayerSkinProvider skinProvider = client.getSkinProvider();
				skinProvider.fetchSkinTextures(profile).getNow(Optional.empty()).ifPresent((skinTextures) -> {
					renderFaceOverlayOnShield(matrixStack, vertexConsumerProvider, light, skinTextures.texture());
				});
			});
		}
	}

	private void renderFaceOverlayOnShield(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, Identifier skinTexture) {
		VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(skinTexture));

		matrices.push();

		// Shield is 12 pixels wide with 1 pixel off each edge. So make the width and height be 10 pixels.
		float width = 10.0f / 16.0f;
		float height = 10.0f / 16.0f;

		float xOffset = -width / 2.0f;
		float yOffset = -height / 2.0f;

		// Offset by 0.125 to be aligned with the shield's front face
		// Have to add a small (0.001) Z offset to avoid z-fighting
		matrices.translate(0.0, 0.0, 0.126);

		MatrixStack.Entry entry = matrices.peek();
		Matrix4f matrix = entry.getPositionMatrix();

		// Position of player's face on the texture (8, 8 -> 16, 16)
		float minU = 8f / 64f;
		float maxU = 16f / 64f;
		float minV = 8f / 64f;
		float maxV = 16f / 64f;

		// Lower-left
		vc.vertex(matrix, xOffset, yOffset, 0.0f).color(255, 255, 255, 255).texture(minU, maxV).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 0, 1);

		// Lower-right
		vc.vertex(matrix, xOffset + width, yOffset, 0.0f).color(255, 255, 255, 255).texture(maxU, maxV).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 0, 1);

		// Upper-right
		vc.vertex(matrix, xOffset + width, yOffset + height, 0.0f).color(255, 255, 255, 255).texture(maxU, minV).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 0, 1);

		// Upper-left
		vc.vertex(matrix, xOffset, yOffset + height, 0.0f).color(255, 255, 255, 255).texture(minU, minV).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 0, 1);

		matrices.pop();
	}

}
