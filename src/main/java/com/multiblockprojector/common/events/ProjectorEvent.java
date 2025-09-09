package com.multiblockprojector.common.events;

import com.multiblockprojector.api.IUniversalMultiblock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.Event;

/**
 * Events fired during projector operations
 * Ported from Immersive Petroleum and updated for NeoForge
 */
public class ProjectorEvent extends Event implements ICancellableEvent {
    
    public static class PlaceBlock extends ProjectorEvent {
        public PlaceBlock(IUniversalMultiblock multiblock, Level templateWorld, BlockPos templatePos, 
                         Level world, BlockPos worldPos, BlockState state, Rotation rotation) {
            super(multiblock, templateWorld, templatePos, world, worldPos, state, rotation);
        }
        
        public void setBlockState(BlockState state) {
            this.state = state;
        }
        
        public void setState(Block block) {
            this.state = block.defaultBlockState();
        }
    }
    
    public static class PlaceBlockPost extends ProjectorEvent {
        public PlaceBlockPost(IUniversalMultiblock multiblock, Level templateWorld, BlockPos templatePos, 
                             Level world, BlockPos worldPos, BlockState state, Rotation rotation) {
            super(multiblock, templateWorld, templatePos, world, worldPos, state, rotation);
        }
    }
    
    public static class RenderBlock extends ProjectorEvent {
        public RenderBlock(IUniversalMultiblock multiblock, Level templateWorld, BlockPos templatePos, 
                          Level world, BlockPos worldPos, BlockState state, Rotation rotation) {
            super(multiblock, templateWorld, templatePos, world, worldPos, state, rotation);
        }
        
        public void setState(BlockState state) {
            this.state = state;
        }
        
        public void setState(Block block) {
            this.state = block.defaultBlockState();
        }
    }
    
    protected IUniversalMultiblock multiblock;
    protected Level realWorld;
    protected Level templateWorld;
    protected Rotation rotation;
    protected BlockPos worldPos;
    protected BlockPos templatePos;
    protected BlockState state;
    
    public ProjectorEvent(IUniversalMultiblock multiblock, Level templateWorld, BlockPos templatePos, 
                         Level world, BlockPos worldPos, BlockState state, Rotation rotation) {
        super();
        this.multiblock = multiblock;
        this.realWorld = world;
        this.templateWorld = templateWorld;
        this.worldPos = worldPos;
        this.templatePos = templatePos;
        this.state = state;
        this.rotation = rotation;
    }
    
    public IUniversalMultiblock getMultiblock() { return multiblock; }
    public Level getWorld() { return this.realWorld; }
    public Level getTemplateWorld() { return this.templateWorld; }
    public Rotation getRotation() { return this.rotation; }
    public BlockPos getWorldPos() { return this.worldPos; }
    public BlockPos getTemplatePos() { return this.templatePos; }
    public BlockState getState() { return this.state; }
    
    /** Always returns the BlockState found in the Template */
    public BlockState getTemplateState() {
        return this.templateWorld.getBlockState(this.templatePos);
    }
}