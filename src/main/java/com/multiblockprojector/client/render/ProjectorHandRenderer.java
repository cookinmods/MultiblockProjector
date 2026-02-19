package com.multiblockprojector.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.multiblockprojector.UniversalProjector;
import com.multiblockprojector.common.items.ProjectorItem;
import com.multiblockprojector.common.projector.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * Renders the projector item in first-person like a held map — flat in front of
 * the player with both hands visible — but without the vanilla map's pitch-based
 * tilt so the item stays at a fixed position regardless of camera angle.
 */
@EventBusSubscriber(modid = UniversalProjector.MODID, value = Dist.CLIENT)
public class ProjectorHandRenderer {

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (event.getHand() == InteractionHand.MAIN_HAND) {
            // Use event.getItemStack() — it represents the actual stack being
            // rendered this frame, which stays correct during equip animations
            if (event.getItemStack().getItem() instanceof ProjectorItem) {
                event.setCanceled(true);

                // Hide the item completely in projection mode so it doesn't
                // obscure the ghost block preview the player is aiming
                Settings settings = ProjectorItem.getSettings(event.getItemStack());
                if (settings.getMode() != Settings.Mode.PROJECTION) {
                    renderProjectorTwoHanded(event, mc, event.getItemStack());
                }
            }
        } else {
            // Cancel off-hand rendering when main hand has projector to avoid
            // the idle arm overlapping our custom two-handed arm positioning.
            if (mc.player.getMainHandItem().getItem() instanceof ProjectorItem) {
                event.setCanceled(true);
            }
        }
    }

    private static void renderProjectorTwoHanded(RenderHandEvent event, Minecraft mc, ItemStack stack) {
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource bufferSource = event.getMultiBufferSource();
        int packedLight = event.getPackedLight();
        float swingProgress = event.getSwingProgress();
        float equipProgress = event.getEquipProgress();

        poseStack.pushPose();

        // Swing animation offsets (vanilla renderTwoHandedMap constants)
        float sqrtSwing = Mth.sqrt(swingProgress);
        float swingY = -0.2F * Mth.sin(swingProgress * (float) Math.PI);
        float swingZ = -0.4F * Mth.sin(sqrtSwing * (float) Math.PI);
        poseStack.translate(0.0F, -swingY / 2.0F, swingZ);

        // Position: lowered and pulled closer to reduce screen coverage
        poseStack.translate(0.0F, -0.4F + equipProgress * -1.2F, -0.45F);

        // Fixed tilt — angled more away from the player so less face is visible
        poseStack.mulPose(Axis.XP.rotationDegrees(-40.0F));

        // Render both player arms
        if (!mc.player.isInvisible()) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            renderMapHand(poseStack, bufferSource, packedLight, HumanoidArm.RIGHT, mc);
            renderMapHand(poseStack, bufferSource, packedLight, HumanoidArm.LEFT, mc);
            poseStack.popPose();
        }

        // Swing rotation on the item itself
        float swingSin = Mth.sin(sqrtSwing * (float) Math.PI);
        poseStack.mulPose(Axis.XP.rotationDegrees(swingSin * 20.0F));

        // Render the projector item flat
        renderProjectorItem(poseStack, bufferSource, packedLight, stack, mc);

        poseStack.popPose();
    }

    private static void renderMapHand(PoseStack poseStack, MultiBufferSource bufferSource,
                                       int packedLight, HumanoidArm arm, Minecraft mc) {
        PlayerRenderer playerRenderer = (PlayerRenderer) mc.getEntityRenderDispatcher()
                .<AbstractClientPlayer>getRenderer(mc.player);
        poseStack.pushPose();
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        poseStack.mulPose(Axis.YP.rotationDegrees(92.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(45.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * -41.0F));
        poseStack.translate(side * 0.3F, -1.1F, 0.45F);
        if (arm == HumanoidArm.RIGHT) {
            playerRenderer.renderRightHand(poseStack, bufferSource, packedLight, mc.player);
        } else {
            playerRenderer.renderLeftHand(poseStack, bufferSource, packedLight, mc.player);
        }
        poseStack.popPose();
    }

    private static void renderProjectorItem(PoseStack poseStack, MultiBufferSource bufferSource,
                                             int packedLight, ItemStack stack, Minecraft mc) {
        // renderStatic with FIXED context renders the item as a flat quad centered
        // at origin (it internally translates by -0.5,-0.5,-0.5 to center the
        // 1-unit model). The FIXED context faces toward -Z which is toward the
        // viewer in our coordinate space, so no rotation needed.
        // Scale to fit nicely in the two-handed hold position.
        poseStack.scale(0.78F, 0.78F, 0.78F);

        mc.getItemRenderer().renderStatic(
                stack,
                ItemDisplayContext.FIXED,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                mc.level,
                0
        );
    }
}
