package com.multiblockprojector.api.adapters;

import com.multiblockprojector.api.IUniversalMultiblock;
import com.multiblockprojector.api.IVariableSizeMultiblock;
import com.multiblockprojector.api.UniversalMultiblockHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for all Mekanism multiblock structures.
 * Supports both base Mekanism and Mekanism Generators multiblocks.
 */
public class MekanismMultiblockAdapter {

    // ============================================
    // Block States - Base Mekanism (MekanismBlocks)
    // ============================================

    // Dynamic Tank
    private static BlockState dynamicTankBlock = null;
    private static BlockState dynamicValveBlock = null;

    // Induction Matrix
    private static BlockState inductionCasingBlock = null;
    private static BlockState inductionPortBlock = null;
    private static BlockState basicInductionCellBlock = null;
    private static BlockState basicInductionProviderBlock = null;

    // Thermoelectric Boiler
    private static BlockState boilerCasingBlock = null;
    private static BlockState boilerValveBlock = null;
    private static BlockState pressureDisperserBlock = null;
    private static BlockState superheatingElementBlock = null;

    // Thermal Evaporation Plant
    private static BlockState thermalEvaporationBlock = null;
    private static BlockState thermalEvaporationControllerBlock = null;
    private static BlockState thermalEvaporationValveBlock = null;

    // SPS
    private static BlockState spsCasingBlock = null;
    private static BlockState spsPortBlock = null;
    private static BlockState superchargedCoilBlock = null;

    // Shared
    private static BlockState structuralGlassBlock = null;

    // ============================================
    // Block States - Mekanism Generators (GeneratorsBlocks)
    // ============================================

    // Fission Reactor
    private static BlockState fissionCasingBlock = null;
    private static BlockState fissionPortBlock = null;
    private static BlockState fissionLogicAdapterBlock = null;
    private static BlockState fissionFuelAssemblyBlock = null;
    private static BlockState fissionControlRodBlock = null;

    // Industrial Turbine
    private static BlockState turbineCasingBlock = null;
    private static BlockState turbineValveBlock = null;
    private static BlockState turbineVentBlock = null;
    private static BlockState turbineRotorBlock = null;
    private static BlockState rotationalComplexBlock = null;
    private static BlockState electromagneticCoilBlock = null;
    private static BlockState saturatingCondenserBlock = null;

    // Fusion Reactor
    private static BlockState fusionFrameBlock = null;
    private static BlockState fusionPortBlock = null;
    private static BlockState fusionControllerBlock = null;
    private static BlockState laserFocusMatrixBlock = null;

    // Shared (generators)
    private static BlockState reactorGlassBlock = null;

    private static boolean blocksInitialized = false;

    /**
     * Register all Mekanism multiblocks with the universal handler.
     */
    public static void registerAllMultiblocks() {
        System.out.println("[MultiblockProjector] Registering Mekanism multiblocks...");

        initializeAllBlocks();

        // Register base Mekanism multiblocks
        UniversalMultiblockHandler.registerMultiblock(new DynamicTankMultiblock());
        UniversalMultiblockHandler.registerMultiblock(new InductionMatrixMultiblock());
        UniversalMultiblockHandler.registerMultiblock(new ThermoelectricBoilerMultiblock());
        UniversalMultiblockHandler.registerMultiblock(new ThermalEvaporationPlantMultiblock());
        UniversalMultiblockHandler.registerMultiblock(new SPSMultiblock());

        // Register Mekanism Generators multiblocks
        UniversalMultiblockHandler.registerMultiblock(new FissionReactorMultiblock());
        UniversalMultiblockHandler.registerMultiblock(new IndustrialTurbineMultiblock());
        UniversalMultiblockHandler.registerMultiblock(new FusionReactorMultiblock());

        System.out.println("[MultiblockProjector] Registered 8 Mekanism multiblocks");
    }

    /**
     * Initialize all Mekanism blocks via reflection.
     */
    private static void initializeAllBlocks() {
        if (blocksInitialized) return;
        blocksInitialized = true;

        // Load base Mekanism blocks
        try {
            Class<?> mekanismBlocksClass = Class.forName("mekanism.common.registries.MekanismBlocks");

            // Dynamic Tank
            dynamicTankBlock = getBlockStateFromRegistry(mekanismBlocksClass, "DYNAMIC_TANK");
            dynamicValveBlock = getBlockStateFromRegistry(mekanismBlocksClass, "DYNAMIC_VALVE");

            // Induction Matrix
            inductionCasingBlock = getBlockStateFromRegistry(mekanismBlocksClass, "INDUCTION_CASING");
            inductionPortBlock = getBlockStateFromRegistry(mekanismBlocksClass, "INDUCTION_PORT");
            basicInductionCellBlock = getBlockStateFromRegistry(mekanismBlocksClass, "BASIC_INDUCTION_CELL");
            basicInductionProviderBlock = getBlockStateFromRegistry(mekanismBlocksClass, "BASIC_INDUCTION_PROVIDER");

            // Thermoelectric Boiler
            boilerCasingBlock = getBlockStateFromRegistry(mekanismBlocksClass, "BOILER_CASING");
            boilerValveBlock = getBlockStateFromRegistry(mekanismBlocksClass, "BOILER_VALVE");
            pressureDisperserBlock = getBlockStateFromRegistry(mekanismBlocksClass, "PRESSURE_DISPERSER");
            superheatingElementBlock = getBlockStateFromRegistry(mekanismBlocksClass, "SUPERHEATING_ELEMENT");

            // Thermal Evaporation Plant
            thermalEvaporationBlock = getBlockStateFromRegistry(mekanismBlocksClass, "THERMAL_EVAPORATION_BLOCK");
            thermalEvaporationControllerBlock = getBlockStateFromRegistry(mekanismBlocksClass, "THERMAL_EVAPORATION_CONTROLLER");
            thermalEvaporationValveBlock = getBlockStateFromRegistry(mekanismBlocksClass, "THERMAL_EVAPORATION_VALVE");

            // SPS
            spsCasingBlock = getBlockStateFromRegistry(mekanismBlocksClass, "SPS_CASING");
            spsPortBlock = getBlockStateFromRegistry(mekanismBlocksClass, "SPS_PORT");
            superchargedCoilBlock = getBlockStateFromRegistry(mekanismBlocksClass, "SUPERCHARGED_COIL");

            // Shared
            structuralGlassBlock = getBlockStateFromRegistry(mekanismBlocksClass, "STRUCTURAL_GLASS");

            System.out.println("[MultiblockProjector] Loaded base Mekanism blocks");
        } catch (ClassNotFoundException e) {
            System.out.println("[MultiblockProjector] Base Mekanism not found");
        } catch (Exception e) {
            System.err.println("[MultiblockProjector] Failed to load base Mekanism blocks: " + e.getMessage());
        }

        // Load Mekanism Generators blocks
        try {
            Class<?> generatorsBlocksClass = Class.forName("mekanism.generators.common.registries.GeneratorsBlocks");

            // Fission Reactor
            fissionCasingBlock = getBlockStateFromRegistry(generatorsBlocksClass, "FISSION_REACTOR_CASING");
            fissionPortBlock = getBlockStateFromRegistry(generatorsBlocksClass, "FISSION_REACTOR_PORT");
            fissionLogicAdapterBlock = getBlockStateFromRegistry(generatorsBlocksClass, "FISSION_REACTOR_LOGIC_ADAPTER");
            fissionFuelAssemblyBlock = getBlockStateFromRegistry(generatorsBlocksClass, "FISSION_FUEL_ASSEMBLY");
            fissionControlRodBlock = getBlockStateFromRegistry(generatorsBlocksClass, "CONTROL_ROD_ASSEMBLY");

            // Industrial Turbine
            turbineCasingBlock = getBlockStateFromRegistry(generatorsBlocksClass, "TURBINE_CASING");
            turbineValveBlock = getBlockStateFromRegistry(generatorsBlocksClass, "TURBINE_VALVE");
            turbineVentBlock = getBlockStateFromRegistry(generatorsBlocksClass, "TURBINE_VENT");
            turbineRotorBlock = getBlockStateFromRegistry(generatorsBlocksClass, "TURBINE_ROTOR");
            rotationalComplexBlock = getBlockStateFromRegistry(generatorsBlocksClass, "ROTATIONAL_COMPLEX");
            electromagneticCoilBlock = getBlockStateFromRegistry(generatorsBlocksClass, "ELECTROMAGNETIC_COIL");
            saturatingCondenserBlock = getBlockStateFromRegistry(generatorsBlocksClass, "SATURATING_CONDENSER");

            // Fusion Reactor
            fusionFrameBlock = getBlockStateFromRegistry(generatorsBlocksClass, "FUSION_REACTOR_FRAME");
            fusionPortBlock = getBlockStateFromRegistry(generatorsBlocksClass, "FUSION_REACTOR_PORT");
            fusionControllerBlock = getBlockStateFromRegistry(generatorsBlocksClass, "FUSION_REACTOR_CONTROLLER");
            laserFocusMatrixBlock = getBlockStateFromRegistry(generatorsBlocksClass, "LASER_FOCUS_MATRIX");

            // Shared (generators)
            reactorGlassBlock = getBlockStateFromRegistry(generatorsBlocksClass, "REACTOR_GLASS");

            System.out.println("[MultiblockProjector] Loaded Mekanism Generators blocks");
        } catch (ClassNotFoundException e) {
            System.out.println("[MultiblockProjector] Mekanism Generators not found");
        } catch (Exception e) {
            System.err.println("[MultiblockProjector] Failed to load Mekanism Generators blocks: " + e.getMessage());
        }

        // Apply fallbacks
        applyFallbackBlocks();
    }

    /**
     * Apply fallback blocks for any that failed to load.
     */
    private static void applyFallbackBlocks() {
        BlockState ironBlock = Blocks.IRON_BLOCK.defaultBlockState();
        BlockState glassBlock = Blocks.GLASS.defaultBlockState();
        BlockState coalBlock = Blocks.COAL_BLOCK.defaultBlockState();
        BlockState redstoneBlock = Blocks.REDSTONE_BLOCK.defaultBlockState();
        BlockState goldBlock = Blocks.GOLD_BLOCK.defaultBlockState();
        BlockState diamondBlock = Blocks.DIAMOND_BLOCK.defaultBlockState();
        BlockState emeraldBlock = Blocks.EMERALD_BLOCK.defaultBlockState();
        BlockState lapisBlock = Blocks.LAPIS_BLOCK.defaultBlockState();
        BlockState copperBlock = Blocks.COPPER_BLOCK.defaultBlockState();

        // Dynamic Tank
        if (dynamicTankBlock == null) dynamicTankBlock = ironBlock;
        if (dynamicValveBlock == null) dynamicValveBlock = ironBlock;

        // Induction Matrix
        if (inductionCasingBlock == null) inductionCasingBlock = ironBlock;
        if (inductionPortBlock == null) inductionPortBlock = ironBlock;
        if (basicInductionCellBlock == null) basicInductionCellBlock = diamondBlock;
        if (basicInductionProviderBlock == null) basicInductionProviderBlock = emeraldBlock;

        // Thermoelectric Boiler
        if (boilerCasingBlock == null) boilerCasingBlock = ironBlock;
        if (boilerValveBlock == null) boilerValveBlock = ironBlock;
        if (pressureDisperserBlock == null) pressureDisperserBlock = copperBlock;
        if (superheatingElementBlock == null) superheatingElementBlock = redstoneBlock;

        // Thermal Evaporation Plant
        if (thermalEvaporationBlock == null) thermalEvaporationBlock = ironBlock;
        if (thermalEvaporationControllerBlock == null) thermalEvaporationControllerBlock = goldBlock;
        if (thermalEvaporationValveBlock == null) thermalEvaporationValveBlock = ironBlock;

        // SPS
        if (spsCasingBlock == null) spsCasingBlock = ironBlock;
        if (spsPortBlock == null) spsPortBlock = ironBlock;
        if (superchargedCoilBlock == null) superchargedCoilBlock = lapisBlock;

        // Shared
        if (structuralGlassBlock == null) structuralGlassBlock = glassBlock;

        // Fission Reactor
        if (fissionCasingBlock == null) fissionCasingBlock = ironBlock;
        if (fissionPortBlock == null) fissionPortBlock = ironBlock;
        if (fissionLogicAdapterBlock == null) fissionLogicAdapterBlock = ironBlock;
        if (fissionFuelAssemblyBlock == null) fissionFuelAssemblyBlock = coalBlock;
        if (fissionControlRodBlock == null) fissionControlRodBlock = redstoneBlock;

        // Industrial Turbine
        if (turbineCasingBlock == null) turbineCasingBlock = ironBlock;
        if (turbineValveBlock == null) turbineValveBlock = ironBlock;
        if (turbineVentBlock == null) turbineVentBlock = ironBlock;
        if (turbineRotorBlock == null) turbineRotorBlock = coalBlock;
        if (rotationalComplexBlock == null) rotationalComplexBlock = goldBlock;
        if (electromagneticCoilBlock == null) electromagneticCoilBlock = copperBlock;
        if (saturatingCondenserBlock == null) saturatingCondenserBlock = lapisBlock;

        // Fusion Reactor
        if (fusionFrameBlock == null) fusionFrameBlock = ironBlock;
        if (fusionPortBlock == null) fusionPortBlock = ironBlock;
        if (fusionControllerBlock == null) fusionControllerBlock = goldBlock;
        if (laserFocusMatrixBlock == null) laserFocusMatrixBlock = diamondBlock;

        // Shared (generators)
        if (reactorGlassBlock == null) reactorGlassBlock = glassBlock;
    }

    /**
     * Get a BlockState from a Mekanism block registry using reflection.
     */
    private static BlockState getBlockStateFromRegistry(Class<?> registryClass, String fieldName) {
        try {
            Object registryObject = registryClass.getField(fieldName).get(null);
            if (registryObject != null) {
                Object block = registryObject.getClass().getMethod("get").invoke(registryObject);
                if (block instanceof Block b) {
                    return b.defaultBlockState();
                }
            }
        } catch (Exception e) {
            // Silent fail - will use fallback
        }
        return null;
    }

    // ============================================
    // Utility Methods for Structure Generation
    // ============================================

    /**
     * Check if position is on an edge (where 2+ faces meet).
     */
    private static boolean isEdge(int x, int y, int z, int width, int height, int depth) {
        int surfaceCount = 0;
        if (x == 0 || x == width - 1) surfaceCount++;
        if (y == 0 || y == height - 1) surfaceCount++;
        if (z == 0 || z == depth - 1) surfaceCount++;
        return surfaceCount >= 2;
    }

    /**
     * Check if position is interior (no surfaces).
     */
    private static boolean isInterior(int x, int y, int z, int width, int height, int depth) {
        return x > 0 && x < width - 1 && y > 0 && y < height - 1 && z > 0 && z < depth - 1;
    }

    // ============================================
    // 1. Dynamic Tank Multiblock
    // ============================================

    private static class DynamicTankMultiblock implements IVariableSizeMultiblock {
        private static final List<SizePreset> SIZE_PRESETS = List.of(
            new SizePreset("small", new Vec3i(3, 3, 3)),
            new SizePreset("small_medium", new Vec3i(6, 6, 6)),
            new SizePreset("medium", new Vec3i(9, 9, 9)),
            new SizePreset("medium_large", new Vec3i(13, 13, 13)),
            new SizePreset("large", new Vec3i(18, 18, 18))
        );

        @Override
        public ResourceLocation getUniqueName() {
            return ResourceLocation.fromNamespaceAndPath("mekanism", "dynamic_tank");
        }

        @Override
        public Component getDisplayName() {
            return Component.literal("Dynamic Tank");
        }

        @Override
        public List<SizePreset> getSizePresets() {
            return SIZE_PRESETS;
        }

        @Override
        public List<StructureBlockInfo> getStructureAtSize(@Nonnull Level world, Vec3i size) {
            List<StructureBlockInfo> blocks = new ArrayList<>();
            int width = size.getX(), height = size.getY(), depth = size.getZ();
            int centerX = width / 2, centerZ = depth / 2;

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    for (int z = 0; z < depth; z++) {
                        if (isInterior(x, y, z, width, height, depth)) continue;

                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState block;

                        if (isEdge(x, y, z, width, height, depth)) {
                            block = dynamicTankBlock;
                        } else if (y == 0) {
                            block = dynamicTankBlock;
                        } else if (y == 1 && x == centerX && z == 0) {
                            block = dynamicValveBlock;
                        } else if (y == 1 && x == centerX && z == depth - 1) {
                            block = dynamicValveBlock;
                        } else {
                            block = structuralGlassBlock;
                        }

                        blocks.add(new StructureBlockInfo(pos, block, null));
                    }
                }
            }
            return blocks;
        }

        @Override
        public float getManualScale() { return 0.8f; }

        @Override
        public String getModId() { return "mekanism"; }

        @Override
        public String getCategory() { return "storage"; }
    }

    // ============================================
    // 2. Induction Matrix Multiblock
    // ============================================

    private static class InductionMatrixMultiblock implements IVariableSizeMultiblock {
        // Minimum 4x4x4 to have room for both cells and provider in interior (2x2x2 = 8 blocks)
        private static final List<SizePreset> SIZE_PRESETS = List.of(
            new SizePreset("small", new Vec3i(4, 4, 4)),
            new SizePreset("small_medium", new Vec3i(6, 6, 6)),
            new SizePreset("medium", new Vec3i(9, 9, 9)),
            new SizePreset("medium_large", new Vec3i(13, 13, 13)),
            new SizePreset("large", new Vec3i(18, 18, 18))
        );

        @Override
        public ResourceLocation getUniqueName() {
            return ResourceLocation.fromNamespaceAndPath("mekanism", "induction_matrix");
        }

        @Override
        public Component getDisplayName() {
            return Component.literal("Induction Matrix");
        }

        @Override
        public List<SizePreset> getSizePresets() {
            return SIZE_PRESETS;
        }

        @Override
        public List<StructureBlockInfo> getStructureAtSize(@Nonnull Level world, Vec3i size) {
            List<StructureBlockInfo> blocks = new ArrayList<>();
            int width = size.getX(), height = size.getY(), depth = size.getZ();
            int centerX = width / 2, centerZ = depth / 2;

            // Track if we've placed the single provider yet
            boolean providerPlaced = false;

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    for (int z = 0; z < depth; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState block;

                        if (isInterior(x, y, z, width, height, depth)) {
                            // Interior: fill with Basic Induction Cells, place 1 Basic Induction Provider
                            // Place provider at first interior position (1,1,1)
                            if (!providerPlaced && x == 1 && y == 1 && z == 1) {
                                block = basicInductionProviderBlock;
                                providerPlaced = true;
                            } else {
                                block = basicInductionCellBlock;
                            }
                        } else if (isEdge(x, y, z, width, height, depth)) {
                            block = inductionCasingBlock;
                        } else if (y == 0) {
                            // Bottom face - all casing
                            block = inductionCasingBlock;
                        } else if (y == 1 && x == centerX && z == 0) {
                            // Front port (input)
                            block = inductionPortBlock;
                        } else if (y == 1 && x == centerX && z == depth - 1) {
                            // Back port (output)
                            block = inductionPortBlock;
                        } else {
                            // Other face positions - structural glass
                            block = structuralGlassBlock;
                        }

                        blocks.add(new StructureBlockInfo(pos, block, null));
                    }
                }
            }
            return blocks;
        }

        @Override
        public float getManualScale() { return 0.8f; }

        @Override
        public String getModId() { return "mekanism"; }

        @Override
        public String getCategory() { return "power"; }
    }

    // ============================================
    // 3. Thermoelectric Boiler Multiblock
    // ============================================

    private static class ThermoelectricBoilerMultiblock implements IVariableSizeMultiblock {
        private static final List<SizePreset> SIZE_PRESETS = List.of(
            new SizePreset("small", new Vec3i(3, 4, 3)),
            new SizePreset("small_medium", new Vec3i(6, 7, 6)),
            new SizePreset("medium", new Vec3i(9, 10, 9)),
            new SizePreset("medium_large", new Vec3i(13, 14, 13)),
            new SizePreset("large", new Vec3i(18, 18, 18))
        );

        @Override
        public ResourceLocation getUniqueName() {
            return ResourceLocation.fromNamespaceAndPath("mekanism", "thermoelectric_boiler");
        }

        @Override
        public Component getDisplayName() {
            return Component.literal("Thermoelectric Boiler");
        }

        @Override
        public List<SizePreset> getSizePresets() {
            return SIZE_PRESETS;
        }

        @Override
        public List<StructureBlockInfo> getStructureAtSize(@Nonnull Level world, Vec3i size) {
            List<StructureBlockInfo> blocks = new ArrayList<>();
            int width = size.getX(), height = size.getY(), depth = size.getZ();
            int centerX = width / 2, centerZ = depth / 2;

            // Interior layout (from wiki):
            // - Top section: Steam cavity (air only)
            // - Middle: Pressure Disperser layer (exactly 1 block tall, full layer)
            // - Bottom section: Water cavity with Superheating Elements (must be contiguous!)
            //
            // Disperser layer should be near top to maximize water capacity
            // For height H, interior is y=1 to y=H-2
            // Put disperser at y = height - 3 (one layer below top interior)
            int disperserY = height - 3;
            if (disperserY < 2) disperserY = 2; // Minimum: at least 1 layer of water below

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    for (int z = 0; z < depth; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState block = null;

                        if (isInterior(x, y, z, width, height, depth)) {
                            if (y == disperserY) {
                                // Full layer of pressure dispersers
                                block = pressureDisperserBlock;
                            } else if (y < disperserY) {
                                // Water section: superheating elements must be CONTIGUOUS
                                // Place them as a solid mass at the bottom of water cavity
                                if (y == 1) {
                                    // Bottom interior layer: solid floor of superheating elements
                                    block = superheatingElementBlock;
                                }
                                // Other water layers are air (null)
                            }
                            // Steam section (y > disperserY) is air (null)

                            if (block != null) {
                                blocks.add(new StructureBlockInfo(pos, block, null));
                            }
                            continue;
                        }

                        if (isEdge(x, y, z, width, height, depth)) {
                            block = boilerCasingBlock;
                        } else if (y == 0) {
                            // Bottom face - all casing
                            block = boilerCasingBlock;
                        } else if (y == 1 && x == centerX && z == 0) {
                            // Water input valve (front, low)
                            block = boilerValveBlock;
                        } else if (y == 1 && x == centerX && z == depth - 1) {
                            // Heated water output valve (back, low)
                            block = boilerValveBlock;
                        } else if (y == height - 2 && x == 0 && z == centerZ) {
                            // Steam output valve (left side, high - in steam section)
                            block = boilerValveBlock;
                        } else {
                            block = structuralGlassBlock;
                        }

                        blocks.add(new StructureBlockInfo(pos, block, null));
                    }
                }
            }
            return blocks;
        }

        @Override
        public float getManualScale() { return 0.7f; }

        @Override
        public String getModId() { return "mekanism"; }

        @Override
        public String getCategory() { return "processing"; }
    }

    // ============================================
    // 4. Industrial Turbine Multiblock
    // ============================================

    private static class IndustrialTurbineMultiblock implements IVariableSizeMultiblock {
        // From FTB wiki efficiency chart - optimal builds with saturating condensers
        // Width must be ODD (5-17), height up to 18
        private static final List<SizePreset> SIZE_PRESETS = List.of(
            new SizePreset("small", new Vec3i(5, 9, 5)),        // 4 rotors, 2.93 MJ/t
            new SizePreset("small_medium", new Vec3i(7, 13, 7)), // 6 rotors, 17.14 MJ/t
            new SizePreset("medium", new Vec3i(9, 17, 9)),       // 8 rotors, 44.80 MJ/t
            new SizePreset("medium_large", new Vec3i(13, 18, 13)), // 9 rotors, 88.25 MJ/t
            new SizePreset("large", new Vec3i(17, 18, 17))       // 10 rotors, 133.71 MJ/t
        );

        // Optimal rotor counts from efficiency chart
        private static int getOptimalRotorCount(int width, int height) {
            // Based on FTB wiki efficiency chart
            if (width == 5 && height >= 9) return 4;
            if (width == 7 && height >= 13) return 6;
            if (width == 9 && height >= 17) return 8;
            if (width == 11 && height >= 18) return 9;
            if (width == 13 && height >= 18) return 9;
            if (width == 15 && height >= 18) return 10;
            if (width == 17 && height >= 18) return 10;

            // Fallback calculation for non-standard sizes
            int interiorWidth = width - 2;
            int maxRotor = 2 * interiorWidth - 1;
            return Math.min(maxRotor, height - 5); // Leave room for disperser, coils, condensers, top
        }

        // Coil count: 1 coil supports 4 blades, blades = rotors * 2
        private static int getCoilCount(int rotorCount) {
            int blades = rotorCount * 2;
            return (blades + 3) / 4; // Ceiling division
        }

        @Override
        public ResourceLocation getUniqueName() {
            return ResourceLocation.fromNamespaceAndPath("mekanism", "industrial_turbine");
        }

        @Override
        public Component getDisplayName() {
            return Component.literal("Industrial Turbine");
        }

        @Override
        public List<SizePreset> getSizePresets() {
            return SIZE_PRESETS;
        }

        @Override
        public List<StructureBlockInfo> getStructureAtSize(@Nonnull Level world, Vec3i size) {
            List<StructureBlockInfo> blocks = new ArrayList<>();
            int width = size.getX(), height = size.getY(), depth = size.getZ();
            int centerX = width / 2, centerZ = depth / 2;

            // Industrial Turbine rules (from wiki):
            // - Square base, odd width 5-17, height up to 18
            // - Edges: Turbine Casing only
            // - Faces: Turbine Casing, Turbine Valve, or Structural Glass
            // - Rotors: Single column in center, max length = 2 * interiorWidth - 1
            // - Rotational Complex: On top of rotor column
            // - Dispersers: Fill ENTIRE interior layer at Rotational Complex level (except center)
            // - Vents: Replace casings at/above Rotational Complex layer, EXCEPT edges, exterior only
            // - Coils: Directly above Rotational Complex, must be connected (vertical stack)
            // - Condensers: Fill remaining interior above Rotational Complex layer

            int rotorCount = getOptimalRotorCount(width, height);
            int coilCount = getCoilCount(rotorCount);

            // Y positions:
            // y=0: Floor (casing)
            // y=1 to y=rotorCount: Rotor column
            // y=rotorCount+1: Rotational Complex + Dispersers
            // y=rotorCount+2 to y=rotorCount+1+coilCount: Coils (center) + Condensers (rest)
            // y=rotorCount+2+coilCount to y=height-2: More condensers
            // y=height-1: Top (vents on non-edges)

            int disperserY = rotorCount + 1;
            int coilStartY = rotorCount + 2;
            int coilEndY = coilStartY + coilCount - 1;

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    for (int z = 0; z < depth; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState block = null;

                        boolean isOnEdge = isEdge(x, y, z, width, height, depth);
                        boolean isInteriorBlock = isInterior(x, y, z, width, height, depth);
                        boolean isOnFace = !isInteriorBlock && !isOnEdge;
                        boolean isOnTop = (y == height - 1);
                        boolean isOnBottom = (y == 0);
                        boolean isCenter = (x == centerX && z == centerZ);

                        // ===== INTERIOR BLOCKS =====
                        if (isInteriorBlock) {
                            if (isCenter) {
                                // Center column
                                if (y >= 1 && y <= rotorCount) {
                                    // Rotor shaft
                                    block = turbineRotorBlock;
                                } else if (y == disperserY) {
                                    // Rotational Complex on top of rotor
                                    block = rotationalComplexBlock;
                                } else if (y >= coilStartY && y <= coilEndY) {
                                    // Coils directly above Rotational Complex (vertical stack)
                                    block = electromagneticCoilBlock;
                                }
                                // Above coils at center: air or could be condenser
                            } else {
                                // Non-center interior positions
                                if (y == disperserY) {
                                    // Dispersers fill entire interior layer at Rotational Complex level
                                    block = pressureDisperserBlock;
                                } else if (y > disperserY && y < height - 1) {
                                    // Condensers fill remaining interior above disperser layer
                                    block = saturatingCondenserBlock;
                                }
                                // Below disperser layer (rotor area): air for blade clearance
                            }

                            if (block != null) {
                                blocks.add(new StructureBlockInfo(pos, block, null));
                            }
                            continue;
                        }

                        // ===== EDGES (always Turbine Casing) =====
                        if (isOnEdge) {
                            block = turbineCasingBlock;
                        }
                        // ===== FACES (non-edge exterior) =====
                        else if (isOnFace) {
                            if (isOnBottom) {
                                // Bottom face: casing
                                block = turbineCasingBlock;
                            } else if (isOnTop) {
                                // Top face (non-edge): Vents
                                block = turbineVentBlock;
                            } else {
                                // Side walls
                                if (y >= disperserY) {
                                    // At and above Rotational Complex layer: Vents allowed
                                    block = turbineVentBlock;
                                } else {
                                    // Below Rotational Complex layer: Casing, Valve, or Glass
                                    // Place valves for steam input (need minimum 2)
                                    if (y == 1 && z == 0 && x == centerX) {
                                        block = turbineValveBlock;
                                    } else if (y == 1 && z == depth - 1 && x == centerX) {
                                        block = turbineValveBlock;
                                    } else {
                                        // Structural glass for visibility
                                        block = structuralGlassBlock;
                                    }
                                }
                            }
                        }

                        if (block != null) {
                            blocks.add(new StructureBlockInfo(pos, block, null));
                        }
                    }
                }
            }
            return blocks;
        }

        @Override
        public float getManualScale() { return 0.6f; }

        @Override
        public String getModId() { return "mekanism"; }

        @Override
        public String getCategory() { return "power"; }
    }

    // ============================================
    // 5. Thermal Evaporation Plant Multiblock
    // ============================================

    private static class ThermalEvaporationPlantMultiblock implements IVariableSizeMultiblock {
        private static final List<SizePreset> SIZE_PRESETS = List.of(
            new SizePreset("small", new Vec3i(4, 3, 4)),
            new SizePreset("small_medium", new Vec3i(4, 6, 4)),
            new SizePreset("medium", new Vec3i(4, 9, 4)),
            new SizePreset("medium_large", new Vec3i(4, 14, 4)),
            new SizePreset("large", new Vec3i(4, 18, 4))
        );

        @Override
        public ResourceLocation getUniqueName() {
            return ResourceLocation.fromNamespaceAndPath("mekanism", "thermal_evaporation_plant");
        }

        @Override
        public Component getDisplayName() {
            return Component.literal("Thermal Evaporation Plant");
        }

        @Override
        public List<SizePreset> getSizePresets() {
            return SIZE_PRESETS;
        }

        @Override
        public List<StructureBlockInfo> getStructureAtSize(@Nonnull Level world, Vec3i size) {
            List<StructureBlockInfo> blocks = new ArrayList<>();
            int height = size.getY();

            // Thermal Evaporation Plant structure:
            // - Fixed 4x4 footprint (full rectangle)
            // - Base layer (y=0): Full 4x4 solid floor
            // - Middle layers: 4x4 perimeter walls with hollow 2x2 interior
            // - Top layer: Perimeter solid, center open (same as middle)
            // - All 3 ports (1 controller + 2 valves) at y=1

            for (int x = 0; x < 4; x++) {
                for (int y = 0; y < height; y++) {
                    for (int z = 0; z < 4; z++) {
                        boolean isInterior = (x == 1 || x == 2) && (z == 1 || z == 2);
                        boolean isBottom = (y == 0);

                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState block;

                        // Base layer (y=0): Full 4x4 solid floor
                        if (isBottom) {
                            blocks.add(new StructureBlockInfo(pos, thermalEvaporationBlock, null));
                            continue;
                        }

                        // All layers above base (including top): interior is hollow/open
                        if (isInterior) {
                            continue;
                        }

                        // Wall positions (perimeter of 4x4)
                        // All 3 ports at y=1 (row just above bottom)

                        // Controller on front wall at y=1
                        if (z == 0 && x == 1 && y == 1) {
                            block = thermalEvaporationControllerBlock;
                        }
                        // Input valve on front wall at y=1
                        else if (z == 0 && x == 2 && y == 1) {
                            block = thermalEvaporationValveBlock;
                        }
                        // Output valve on back wall at y=1
                        else if (z == 3 && x == 1 && y == 1) {
                            block = thermalEvaporationValveBlock;
                        }
                        // All other wall positions: regular blocks
                        else {
                            block = thermalEvaporationBlock;
                        }

                        blocks.add(new StructureBlockInfo(pos, block, null));
                    }
                }
            }
            return blocks;
        }

        @Override
        public float getManualScale() { return 0.8f; }

        @Override
        public String getModId() { return "mekanism"; }

        @Override
        public String getCategory() { return "processing"; }
    }

    // ============================================
    // 6. SPS (Supercritical Phase Shifter) Multiblock
    // ============================================

    private static class SPSMultiblock implements IUniversalMultiblock {

        // Exact grid from Mekanism source code (SPSValidator.java)
        // 0 = not part of structure, 1 = frame (SPS Casing), 2 = side (Glass/Port)
        private static final byte[][] ALLOWED_GRID = {
            {0, 0, 1, 1, 1, 0, 0},
            {0, 1, 2, 2, 2, 1, 0},
            {1, 2, 2, 2, 2, 2, 1},
            {1, 2, 2, 2, 2, 2, 1},
            {1, 2, 2, 2, 2, 2, 1},
            {0, 1, 2, 2, 2, 1, 0},
            {0, 0, 1, 1, 1, 0, 0}
        };

        @Override
        public ResourceLocation getUniqueName() {
            return ResourceLocation.fromNamespaceAndPath("mekanism", "sps");
        }

        @Override
        public Component getDisplayName() {
            return Component.literal("Supercritical Phase Shifter");
        }

        @Override
        public Vec3i getSize(@Nonnull Level world) {
            return new Vec3i(7, 7, 7);
        }

        @Override
        public List<StructureBlockInfo> getStructure(@Nonnull Level world) {
            List<StructureBlockInfo> blocks = new ArrayList<>();

            // SPS is a 7x7x7 structure with cross-shaped faces
            // Uses ALLOWED_GRID from Mekanism source for exact validation
            // Ports on all 6 faces (4 sides + top + bottom), with coils attached inside
            // Extra 2 ports on front face for energy input

            for (int x = 0; x < 7; x++) {
                for (int y = 0; y < 7; y++) {
                    for (int z = 0; z < 7; z++) {
                        BlockState block = null;

                        // Check each face of the structure
                        if (z == 0) {
                            block = getSPSFaceBlock(x, y, "front");
                        } else if (z == 6) {
                            block = getSPSFaceBlock(x, y, "back");
                        } else if (x == 0) {
                            block = getSPSFaceBlock(z, y, "left");
                        } else if (x == 6) {
                            block = getSPSFaceBlock(z, y, "right");
                        } else if (y == 0) {
                            block = getSPSFaceBlock(x, z, "bottom");
                        } else if (y == 6) {
                            block = getSPSFaceBlock(x, z, "top");
                        } else {
                            // Interior - place Supercharged Coils attached to ports
                            // Each face center port needs a coil inside

                            // Coil for left port (0,3,3) -> inside at (1,3,3)
                            if (x == 1 && y == 3 && z == 3) {
                                block = superchargedCoilBlock;
                            }
                            // Coil for right port (6,3,3) -> inside at (5,3,3)
                            else if (x == 5 && y == 3 && z == 3) {
                                block = superchargedCoilBlock;
                            }
                            // Coil for front port (3,3,0) -> inside at (3,3,1)
                            else if (x == 3 && y == 3 && z == 1) {
                                block = superchargedCoilBlock;
                            }
                            // Coil for back port (3,3,6) -> inside at (3,3,5)
                            else if (x == 3 && y == 3 && z == 5) {
                                block = superchargedCoilBlock;
                            }
                            // Coil for top port (3,6,3) -> inside at (3,5,3)
                            else if (x == 3 && y == 5 && z == 3) {
                                block = superchargedCoilBlock;
                            }
                            // Coil for bottom port (3,0,3) -> inside at (3,1,3)
                            else if (x == 3 && y == 1 && z == 3) {
                                block = superchargedCoilBlock;
                            }
                            // Rest of interior is air (hollow)
                        }

                        if (block != null) {
                            blocks.add(new StructureBlockInfo(new BlockPos(x, y, z), block, null));
                        }
                    }
                }
            }
            return blocks;
        }

        private BlockState getSPSFaceBlock(int a, int b, String face) {
            // Use exact grid from Mekanism source
            byte gridValue = ALLOWED_GRID[b][a];

            if (gridValue == 0) {
                // Not part of structure (corner regions)
                return null;
            }

            if (gridValue == 1) {
                // Frame position - must be SPS Casing
                return spsCasingBlock;
            }

            // gridValue == 2: Side position - can be Glass or Port

            // Center ports on all 6 faces
            if (a == 3 && b == 3) {
                return spsPortBlock;
            }

            // Extra 2 ports on front face at row 1 (near top), cols 2 and 4
            // These are for additional energy input capacity
            if (face.equals("front") && b == 1 && (a == 2 || a == 4)) {
                return spsPortBlock;
            }

            // All other side positions use Structural Glass
            return structuralGlassBlock;
        }

        @Override
        public float getManualScale() { return 0.6f; }

        @Override
        public String getModId() { return "mekanism"; }

        @Override
        public String getCategory() { return "processing"; }
    }

    // ============================================
    // 7. Fission Reactor Multiblock
    // ============================================

    private static class FissionReactorMultiblock implements IVariableSizeMultiblock {
        private static final List<SizePreset> SIZE_PRESETS = List.of(
            new SizePreset("small", new Vec3i(3, 4, 3)),
            new SizePreset("small_medium", new Vec3i(6, 7, 6)),
            new SizePreset("medium", new Vec3i(9, 11, 9)),
            new SizePreset("medium_large", new Vec3i(13, 14, 13)),
            new SizePreset("large", new Vec3i(18, 18, 18))
        );

        @Override
        public ResourceLocation getUniqueName() {
            return ResourceLocation.fromNamespaceAndPath("mekanism", "fission_reactor");
        }

        @Override
        public Component getDisplayName() {
            return Component.literal("Fission Reactor");
        }

        @Override
        public List<SizePreset> getSizePresets() {
            return SIZE_PRESETS;
        }

        @Override
        public List<StructureBlockInfo> getStructureAtSize(@Nonnull Level world, Vec3i size) {
            List<StructureBlockInfo> blocks = new ArrayList<>();
            int width = size.getX(), height = size.getY(), depth = size.getZ();
            int centerX = width / 2, centerZ = depth / 2;
            boolean addControlRods = width >= 5 && height >= 5 && depth >= 5;
            int interiorMaxY = height - 2;

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    for (int z = 0; z < depth; z++) {
                        BlockPos pos = new BlockPos(x, y, z);

                        if (isInterior(x, y, z, width, height, depth)) {
                            if (addControlRods && (x + z) % 2 == 0) {
                                if (y < interiorMaxY) {
                                    blocks.add(new StructureBlockInfo(pos, fissionFuelAssemblyBlock, null));
                                } else {
                                    blocks.add(new StructureBlockInfo(pos, fissionControlRodBlock, null));
                                }
                            }
                            continue;
                        }

                        BlockState block;

                        if (isEdge(x, y, z, width, height, depth)) {
                            block = fissionCasingBlock;
                        } else if (y == 0) {
                            block = fissionCasingBlock;
                        }
                        else if (y == 1 && z == 0 && x == centerX) {
                            block = fissionLogicAdapterBlock;
                        }
                        else if (y == 1 && x == 0 && z == centerZ - 1 && centerZ > 1) {
                            block = fissionPortBlock;
                        }
                        else if (y == 1 && x == 0 && z == centerZ && depth > 3) {
                            block = fissionPortBlock;
                        }
                        else if (y == 1 && x == width - 1 && z == centerZ - 1 && centerZ > 1) {
                            block = fissionPortBlock;
                        }
                        else if (y == 1 && x == width - 1 && z == centerZ && depth > 3) {
                            block = fissionPortBlock;
                        }
                        else {
                            block = reactorGlassBlock;
                        }

                        blocks.add(new StructureBlockInfo(pos, block, null));
                    }
                }
            }
            return blocks;
        }

        @Override
        public float getManualScale() { return 0.8f; }

        @Override
        public String getModId() { return "mekanism"; }

        @Override
        public String getCategory() { return "power"; }
    }

    // ============================================
    // 8. Fusion Reactor Multiblock
    // ============================================

    private static class FusionReactorMultiblock implements IUniversalMultiblock {

        @Override
        public ResourceLocation getUniqueName() {
            return ResourceLocation.fromNamespaceAndPath("mekanism", "fusion_reactor");
        }

        @Override
        public Component getDisplayName() {
            return Component.literal("Fusion Reactor");
        }

        @Override
        public Vec3i getSize(@Nonnull Level world) {
            return new Vec3i(5, 5, 5);
        }

        @Override
        public List<StructureBlockInfo> getStructure(@Nonnull Level world) {
            List<StructureBlockInfo> blocks = new ArrayList<>();

            // Fusion Reactor is a fixed 5-layer structure
            // Legend: F=Frame, P=Port, C=Controller, L=Laser Focus, G=Glass, .=air

            // Layer 0 (bottom) - Solid plus/star shape of Frame
            // Pattern:
            //   . . F . .
            //   . F F F .
            //   F F F F F
            //   . F F F .
            //   . . F . .
            int[][] layer0 = {
                {0, 0, 1, 0, 0},
                {0, 1, 1, 1, 0},
                {1, 1, 1, 1, 1},
                {0, 1, 1, 1, 0},
                {0, 0, 1, 0, 0}
            };
            addFusionLayerSimple(blocks, 0, layer0, fusionFrameBlock);

            // Layer 1 - Square perimeter WITHOUT corners, centers can be ports
            // Pattern:
            //   . F P F .
            //   F . . . F
            //   P . . . P
            //   F . . . F
            //   . F P F .
            int[][] layer1 = {
                {0, 1, 2, 1, 0},
                {1, 0, 0, 0, 1},
                {2, 0, 0, 0, 2},
                {1, 0, 0, 0, 1},
                {0, 1, 2, 1, 0}
            };
            addFusionLayerWithTypes(blocks, 1, layer1);

            // Layer 2 (middle) - Full square perimeter, corners=Frame, non-corners can be Port/Glass/Laser
            // Pattern:
            //   F G P G F
            //   G . . . G
            //   P . . . L  (Laser Focus on one side for ignition)
            //   G . . . G
            //   F G P G F
            int[][] layer2 = {
                {1, 3, 2, 3, 1},
                {3, 0, 0, 0, 3},
                {2, 0, 0, 0, 4},
                {3, 0, 0, 0, 3},
                {1, 3, 2, 3, 1}
            };
            addFusionLayerWithTypes(blocks, 2, layer2);

            // Layer 3 - Same as Layer 1 (square without corners)
            addFusionLayerWithTypes(blocks, 3, layer1);

            // Layer 4 (top) - Solid plus/star shape with Controller in center
            // Pattern:
            //   . . F . .
            //   . F F F .
            //   F F C F F
            //   . F F F .
            //   . . F . .
            int[][] layer4 = {
                {0, 0, 1, 0, 0},
                {0, 1, 1, 1, 0},
                {1, 1, 5, 1, 1},
                {0, 1, 1, 1, 0},
                {0, 0, 1, 0, 0}
            };
            addFusionLayerWithTypes(blocks, 4, layer4);

            return blocks;
        }

        private void addFusionLayerSimple(List<StructureBlockInfo> blocks, int y, int[][] pattern, BlockState block) {
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    if (pattern[z][x] == 1) {
                        blocks.add(new StructureBlockInfo(new BlockPos(x, y, z), block, null));
                    }
                }
            }
        }

        private void addFusionLayerWithTypes(List<StructureBlockInfo> blocks, int y, int[][] pattern) {
            // 0=air, 1=Frame, 2=Port, 3=Glass, 4=Laser Focus, 5=Controller
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    int val = pattern[z][x];
                    if (val == 0) continue;

                    BlockState block = switch (val) {
                        case 1 -> fusionFrameBlock;
                        case 2 -> fusionPortBlock;
                        case 3 -> reactorGlassBlock;
                        case 4 -> laserFocusMatrixBlock;
                        case 5 -> fusionControllerBlock;
                        default -> fusionFrameBlock;
                    };
                    blocks.add(new StructureBlockInfo(new BlockPos(x, y, z), block, null));
                }
            }
        }

        @Override
        public float getManualScale() { return 0.8f; }

        @Override
        public String getModId() { return "mekanism"; }

        @Override
        public String getCategory() { return "power"; }
    }
}
