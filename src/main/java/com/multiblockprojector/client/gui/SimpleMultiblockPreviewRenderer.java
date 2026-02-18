package com.multiblockprojector.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import com.multiblockprojector.api.BlockEntry;
import com.multiblockprojector.api.MultiblockDefinition;
import com.multiblockprojector.api.MultiblockDefinition.SizeVariant;
import com.multiblockprojector.api.MultiblockStructure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaternionf;

public class SimpleMultiblockPreviewRenderer {
    private MultiblockDefinition multiblock;
    private MultiblockStructure structure;
    private BlockPos size;

    private float scale = 50f;
    private float rotationX = 25f;
    private float rotationY = -45f;
    private boolean canTick = true;
    private long lastStep = -1;
    private int blockIndex;
    private int maxBlockIndex;
    private final ClientLevel level;

    public SimpleMultiblockPreviewRenderer() {
        this.level = Minecraft.getInstance().level;
    }

    public void setMultiblock(MultiblockDefinition multiblock) {
        setMultiblock(multiblock, multiblock != null ? multiblock.getDefaultVariant() : null);
    }

    /**
     * Set the multiblock to preview with an optional specific size variant.
     * @param multiblock The multiblock to preview
     * @param variant For variable-size multiblocks, the variant to render at. If null, uses default variant.
     */
    public void setMultiblock(MultiblockDefinition multiblock, SizeVariant variant) {
        boolean changed = this.multiblock != multiblock;
        boolean sizeChanged = variant != null && this.structure != null && !variant.dimensions().equals(this.size);

        if (changed || sizeChanged) {
            this.multiblock = multiblock;
            if (multiblock != null && variant != null) {
                try {
                    this.structure = multiblock.structureProvider().create(variant, level);
                    this.size = structure.size();
                    if (!structure.blocks().isEmpty()) {
                        this.maxBlockIndex = structure.blocks().size();
                        this.blockIndex = maxBlockIndex;
                        calculateScale();
                    } else {
                        this.structure = null;
                        this.size = null;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    this.structure = null;
                    this.size = null;
                }
            } else {
                this.structure = null;
                this.size = null;
            }
        }
    }

    private void calculateScale() {
        if (size == null) return;

        float diagLength = (float)Math.sqrt(
            size.getY() * size.getY() +
            size.getX() * size.getX() +
            size.getZ() * size.getZ()
        );

        scale = Math.min(200f / diagLength, 50f);
    }

    public void render(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY, float partialTicks) {
        if (multiblock == null || structure == null || structure.blocks().isEmpty()) {
            renderNoPreview(graphics, x, y, width, height);
            return;
        }

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();

        try {
            long currentTime = System.currentTimeMillis();
            if (lastStep < 0) {
                lastStep = currentTime;
            } else if (canTick && currentTime - lastStep > 1000) {
                step();
                lastStep = currentTime;
            }

            int centerX = x + width / 2;
            int centerY = y + height / 2;

            // Enable blend for transparency
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.8f);

            poseStack.translate(centerX, centerY, 100);
            poseStack.scale(scale, -scale, scale);

            Transformation transform = new Transformation(
                null,
                new Quaternionf().rotateXYZ((float)Math.toRadians(rotationX), 0, 0),
                null,
                new Quaternionf().rotateXYZ(0, (float)Math.toRadians(rotationY), 0)
            );
            poseStack.pushTransformation(transform);

            if (size != null) {
                poseStack.translate(-size.getX() / 2f, -size.getY() / 2f, -size.getZ() / 2f);
            }

            renderMultiblock(graphics, poseStack);

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            poseStack.popPose();
        }

        renderInfo(graphics, x, y, width, height);
    }

    private void renderMultiblock(GuiGraphics graphics, PoseStack poseStack) {
        BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();
        MultiBufferSource.BufferSource buffers = graphics.bufferSource();

        long tick = System.currentTimeMillis() / 50; // ~20 ticks/sec

        int count = 0;
        for (var entry : structure.blocks().entrySet()) {
            if (count >= blockIndex) break;
            BlockPos pos = entry.getKey();
            BlockEntry blockEntry = entry.getValue();
            BlockState state = blockEntry.displayState(tick);

            if (!state.isAir()) {
                poseStack.pushPose();
                poseStack.translate(pos.getX(), pos.getY(), pos.getZ());

                int overlay = OverlayTexture.NO_OVERLAY;
                ModelData modelData = ModelData.EMPTY;

                try {
                    blockRenderer.renderSingleBlock(state, poseStack, buffers,
                        0xF000F0, overlay, modelData, null);
                } catch (Exception e) {
                    // Silently ignore render errors
                }

                poseStack.popPose();
            }
            count++;
        }
    }

    private void renderNoPreview(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0x40000000);

        String text = "Select a multiblock to preview";
        int textWidth = Minecraft.getInstance().font.width(text);
        graphics.drawString(Minecraft.getInstance().font, text,
            x + (width - textWidth) / 2, y + height / 2 - 4, 0xFFFFFF);
    }

    private void renderInfo(GuiGraphics graphics, int x, int y, int width, int height) {
        if (multiblock == null) return;

        int infoY = y + height - 40;
        graphics.drawString(Minecraft.getInstance().font, "Name: " + multiblock.displayName().getString(),
            x + 5, infoY, 0xFFFFFF);
        graphics.drawString(Minecraft.getInstance().font, "Mod: " + multiblock.modId(),
            x + 5, infoY + 10, 0xAAAAAA);

        if (size != null) {
            String sizeText = String.format("Size: %dx%dx%d",
                size.getX(), size.getY(), size.getZ());
            graphics.drawString(Minecraft.getInstance().font, sizeText,
                x + 5, infoY + 20, 0xAAAAAA);
        }
    }

    public void onMouseDragged(double mouseX, double mouseY, double deltaX, double deltaY) {
        rotationY += (float)(deltaX * 0.5);
        rotationX = Mth.clamp(rotationX + (float)(deltaY * 0.5), -90f, 90f);
    }

    public void setAnimationEnabled(boolean enabled) {
        this.canTick = enabled;
        if (!enabled) {
            lastStep = -1;
        }
    }

    private void step() {
        int start = blockIndex;
        do {
            if (++blockIndex > maxBlockIndex) {
                blockIndex = 1;
            }
        } while (blockIndex != start);
    }
}
