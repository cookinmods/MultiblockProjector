package com.multiblockprojector.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.multiblockprojector.UniversalProjector;
import com.multiblockprojector.client.ProjectionManager;
import com.multiblockprojector.common.projector.MultiblockProjection;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;

/**
 * Handles rendering of ghost block projections
 */
@EventBusSubscriber(modid = UniversalProjector.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public class ProjectionRenderer {

    private static final float GHOST_ALPHA = 0.4f;
    private static final int GHOST_LIGHT = 0xF000F0; // Full brightness

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        Camera camera = event.getCamera();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        // Clean up distant projections
        ProjectionManager.cleanupDistantProjections(mc.level, mc.player.blockPosition(), 64.0);

        // Render all active projections
        for (Map.Entry<BlockPos, MultiblockProjection> entry : ProjectionManager.getAllProjections().entrySet()) {
            BlockPos projectionCenter = entry.getKey();
            MultiblockProjection projection = entry.getValue();

            renderProjection(poseStack, bufferSource, camera, mc.level, projectionCenter, projection);
        }

        bufferSource.endBatch();
    }

    private static void renderProjection(PoseStack poseStack, MultiBufferSource bufferSource, Camera camera,
                                       Level level, BlockPos center, MultiblockProjection projection) {

        Vec3 cameraPos = camera.getPosition();
        BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();

        // Use a custom translucent render type for ghost blocks
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.translucent());

        // Get game tick for cycling block display (BlockGroup cycles internally)
        long gameTick = level.getGameTime();

        // Process each layer of the projection
        for (int layer = 0; layer < projection.getLayerCount(); layer++) {
            projection.process(layer, info -> {
                BlockPos worldPos = center.offset(info.tPos);
                BlockState ghostState = info.getDisplayState(level, worldPos, gameTick);
                BlockState worldState = level.getBlockState(worldPos);

                if (ghostState.isAir()) {
                    // Air entry: render red-tinted overlay of world block if something is there
                    if (!worldState.isAir()) {
                        poseStack.pushPose();
                        double x = worldPos.getX() - cameraPos.x;
                        double y = worldPos.getY() - cameraPos.y;
                        double z = worldPos.getZ() - cameraPos.z;
                        poseStack.translate(x, y, z);
                        try {
                            renderGhostBlock(worldState, poseStack, buffer, level, worldPos, true);
                        } catch (Exception e) { }
                        poseStack.popPose();
                    }
                    return false;
                }

                if (!worldState.isAir()) {
                    // Solid entry with block already placed — red tint if wrong
                    if (!info.blockEntry.matches(worldState)) {
                        poseStack.pushPose();
                        double x = worldPos.getX() - cameraPos.x;
                        double y = worldPos.getY() - cameraPos.y;
                        double z = worldPos.getZ() - cameraPos.z;
                        poseStack.translate(x, y, z);
                        try {
                            renderGhostBlock(ghostState, poseStack, buffer, level, worldPos, true);
                        } catch (Exception e) { }
                        poseStack.popPose();
                    }
                    return false;
                }

                poseStack.pushPose();

                // Translate to world position relative to camera
                double x = worldPos.getX() - cameraPos.x;
                double y = worldPos.getY() - cameraPos.y;
                double z = worldPos.getZ() - cameraPos.z;
                poseStack.translate(x, y, z);

                try {
                    // Render the block as a ghost using custom method (normal/missing = not incorrect)
                    renderGhostBlock(ghostState, poseStack, buffer, level, worldPos, false);

                } catch (Exception e) {
                    // Ignore rendering errors for individual blocks
                }

                poseStack.popPose();
                return false; // Continue processing
            });
        }
    }

    private static void renderGhostBlock(BlockState state, PoseStack poseStack, VertexConsumer buffer, Level level, BlockPos pos, boolean isIncorrect) {
        // Always use manual rendering to ensure ghost effect with correct textures
        renderGhostBlockManual(state, poseStack, buffer, level, pos, isIncorrect);
    }

    private static void renderGhostBlockManual(BlockState state, PoseStack poseStack, VertexConsumer buffer, Level level, BlockPos pos, boolean isIncorrect) {
        BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();
        var model = blockRenderer.getBlockModel(state);

        // Render all faces with transparency
        for (Direction direction : Direction.values()) {
            List<BakedQuad> quads = model.getQuads(state, direction, RandomSource.create(), ModelData.EMPTY, null);
            for (BakedQuad quad : quads) {
                renderGhostQuad(poseStack, buffer, quad, GHOST_LIGHT, isIncorrect);
            }
        }

        // Render faces without specific direction (general quads)
        List<BakedQuad> generalQuads = model.getQuads(state, null, RandomSource.create(), ModelData.EMPTY, null);
        for (BakedQuad quad : generalQuads) {
            renderGhostQuad(poseStack, buffer, quad, GHOST_LIGHT, isIncorrect);
        }
    }

    private static void renderGhostQuad(PoseStack poseStack, VertexConsumer buffer, BakedQuad quad, int light, boolean isIncorrect) {
        var last = poseStack.last();
        Matrix4f pose = last.pose();

        // Extract vertex data
        int[] vertexData = quad.getVertices();
        int stride = vertexData.length / 4; // 4 vertices per quad

        // Red tint for incorrect blocks, white for normal ghost blocks
        float r = 1.0f;
        float g = isIncorrect ? 0.3f : 1.0f;
        float b = isIncorrect ? 0.3f : 1.0f;

        for (int i = 0; i < 4; i++) {
            int idx = i * stride;

            // Extract position (first 3 floats)
            float x = Float.intBitsToFloat(vertexData[idx]);
            float y = Float.intBitsToFloat(vertexData[idx + 1]);
            float z = Float.intBitsToFloat(vertexData[idx + 2]);

            // Extract UV coordinates (typically at offset 4 and 5)
            float u = Float.intBitsToFloat(vertexData[idx + 4]);
            float v = Float.intBitsToFloat(vertexData[idx + 5]);

            // Transform position using Vector3f
            Vector3f pos = new Vector3f(x, y, z);
            pose.transformPosition(pos);

            // Get quad normal direction
            Direction dir = quad.getDirection();
            Vector3f normal = new Vector3f(dir.getStepX(), dir.getStepY(), dir.getStepZ());
            last.normal().transform(normal);

            // Add vertex with transparency (red tint if incorrect, white if normal)
            buffer.addVertex(pos.x(), pos.y(), pos.z())
                  .setColor(r, g, b, GHOST_ALPHA)
                  .setUv(u, v)
                  .setLight(light)
                  .setNormal(normal.x(), normal.y(), normal.z());
        }
    }

    @SuppressWarnings("unchecked")
    private static void renderGhostBlockEntity(PoseStack poseStack, MultiBufferSource bufferSource,
                                             BlockEntity blockEntity, BlockPos worldPos, Level level) {
        try {
            BlockEntityRenderer<BlockEntity> renderer = (BlockEntityRenderer<BlockEntity>)
                Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(blockEntity);

            if (renderer != null) {
                // Create a temporary block entity at the world position for rendering
                BlockEntity ghostEntity = blockEntity.getType().create(worldPos, blockEntity.getBlockState());
                if (ghostEntity != null) {
                    ghostEntity.loadWithComponents(blockEntity.saveWithoutMetadata(level.registryAccess()), level.registryAccess());
                    renderer.render(ghostEntity, 0.0f, poseStack, bufferSource,
                        LevelRenderer.getLightColor(level, worldPos), net.minecraft.util.FastColor.ARGB32.opaque(255));
                }
            }
        } catch (Exception e) {
            // Ignore block entity rendering errors
        }
    }
}
