package com.multiblockprojector.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import com.multiblockprojector.api.IUniversalMultiblock;
import com.multiblockprojector.api.IVariableSizeMultiblock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaternionf;

import java.util.List;

public class SimpleMultiblockPreviewRenderer {
    private IUniversalMultiblock multiblock;
    private List<StructureBlockInfo> structure;
    private Vec3i size;
    
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
    
    public void setMultiblock(IUniversalMultiblock multiblock) {
        setMultiblock(multiblock, null);
    }

    /**
     * Set the multiblock to preview with an optional specific size.
     * @param multiblock The multiblock to preview
     * @param specificSize For variable-size multiblocks, the size to render at. If null, uses default size.
     */
    public void setMultiblock(IUniversalMultiblock multiblock, Vec3i specificSize) {
        // Force refresh if size changed, even for same multiblock
        boolean sizeChanged = specificSize != null && !specificSize.equals(this.size);

        if (this.multiblock != multiblock || sizeChanged) {
            this.multiblock = multiblock;
            if (multiblock != null && level != null) {
                try {
                    // Use specific size for variable-size multiblocks
                    if (specificSize != null && multiblock instanceof IVariableSizeMultiblock varMultiblock) {
                        this.structure = varMultiblock.getStructureAtSize(level, specificSize);
                        this.size = specificSize;
                    } else {
                        this.structure = multiblock.getStructure(level);
                        this.size = multiblock.getSize(level);
                    }

                    if (structure != null && !structure.isEmpty()) {
                        this.maxBlockIndex = structure.size();
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
        if (multiblock == null || structure == null || structure.isEmpty()) {
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
        
        for (int i = 0; i < Math.min(blockIndex, structure.size()); i++) {
            StructureBlockInfo blockInfo = structure.get(i);
            BlockPos pos = blockInfo.pos();
            BlockState state = blockInfo.state();
            
            if (!state.isAir()) {
                poseStack.pushPose();
                poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                
                int overlay = OverlayTexture.NO_OVERLAY;
                ModelData modelData = ModelData.EMPTY;
                
                BakedModel model = blockRenderer.getBlockModel(state);
                
                try {
                    blockRenderer.renderSingleBlock(state, poseStack, buffers, 
                        0xF000F0, overlay, modelData, null);
                } catch (Exception e) {
                    // Silently ignore render errors for individual blocks
                }
                
                poseStack.popPose();
            }
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
        graphics.drawString(Minecraft.getInstance().font, "Name: " + multiblock.getDisplayName().getString(), 
            x + 5, infoY, 0xFFFFFF);
        graphics.drawString(Minecraft.getInstance().font, "Mod: " + multiblock.getModId(), 
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