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
					renderFaceOverlayOnShield(matrixStack, vertexConsumerProvider, light, overlay, skinTextures.texture());
				});
			});
		}
	}

	private void renderFaceOverlayOnShield(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, Identifier skinTexture) {
		matrices.push();

		// Offset by 0.125 to be aligned with the shield's front face
		matrices.translate(0.0, 0.0, 0.125);

		MatrixStack.Entry entry = matrices.peek();

		// Position of player's face on the texture (8, 8 -> 16, 16)
		float minU = 8f / 64f;
		float maxU = 16f / 64f;
		float minV = 8f / 64f;
		float maxV = 16f / 64f;

		// Prevent z-fighting using a tiny offset
		matrices.translate(0.0, 0.0, 0.0005);

		renderFace(vertexConsumers.getBuffer(RenderLayer.getEntityCutout(skinTexture)), entry, minU, minV, maxU, maxV, light, overlay);

		// Position of the hat layer is (40, 8 -> 48, 16);
		minU = 40f / 64f;
		maxU = 48f / 64f;

		// Add another tiny offset for the hat layer
		matrices.translate(0.0, 0.0, 0.0005);

		renderFace(vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(skinTexture)), entry, minU, minV, maxU, maxV, light, overlay);

		matrices.pop();
	}

	private void renderFace(VertexConsumer vc, MatrixStack.Entry entry, float minU, float minV, float maxU, float maxV, int light, int overlay) {

		// Shield is 12 pixels wide with 1 pixel off each edge. So make the width and height be 10 pixels.
		float length = 10.0f / 16.0f;

		float offset = -length / 2.0f;

		Matrix4f matrix = entry.getPositionMatrix();

		// Lower-left
		vc.vertex(matrix, offset, offset, 0.0f).color(255, 255, 255, 255).texture(minU, maxV).overlay(overlay).light(light).normal(entry, 0, 0, 1);

		// Lower-right
		vc.vertex(matrix, offset + length, offset, 0.0f).color(255, 255, 255, 255).texture(maxU, maxV).overlay(overlay).light(light).normal(entry, 0, 0, 1);

		// Upper-right
		vc.vertex(matrix, offset + length, offset + length, 0.0f).color(255, 255, 255, 255).texture(maxU, minV).overlay(overlay).light(light).normal(entry, 0, 0, 1);

		// Upper-left
		vc.vertex(matrix, offset, offset + length, 0.0f).color(255, 255, 255, 255).texture(minU, minV).overlay(overlay).light(light).normal(entry, 0, 0, 1);

	}

}
