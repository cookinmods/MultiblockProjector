package com.multiblockprojector.data;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Data generator that produces example {@code .nbt} structure files matching
 * the test multiblocks from {@code TestMultiblockRegistrar}. These files are
 * shipped in the JAR under {@code assets/multiblockprojector/examples/} and
 * copied to the user's config folder on first launch by
 * {@link com.multiblockprojector.client.schematic.SchematicExampleCopier}.
 */
public class ExampleStructureProvider implements DataProvider {

    private final PackOutput output;

    public ExampleStructureProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        Path outputDir = output.getOutputFolder().resolve("assets/multiblockprojector/examples");

        futures.add(saveStructure(cache, outputDir.resolve("test-furnace.nbt"), buildTestFurnace()));
        futures.add(saveStructure(cache, outputDir.resolve("test-beacon.nbt"), buildTestBeacon()));
        futures.add(saveStructure(cache, outputDir.resolve("test-tower.nbt"), buildTestTower()));
        futures.add(saveStructure(cache, outputDir.resolve("test-wall.nbt"), buildTestWall()));

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "MultiblockProjector Example Structures";
    }

    private CompletableFuture<?> saveStructure(CachedOutput cache, Path path, StructureData data) {
        return CompletableFuture.runAsync(() -> {
            try {
                CompoundTag root = buildNbt(data);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                HashingOutputStream hos = new HashingOutputStream(Hashing.sha1(), baos);
                NbtIo.writeCompressed(root, hos);
                cache.writeIfNeeded(path, baos.toByteArray(), hos.hash());
            } catch (IOException e) {
                throw new RuntimeException("Failed to save structure: " + path, e);
            }
        });
    }

    private CompoundTag buildNbt(StructureData data) {
        // Build palette
        Map<String, Integer> paletteMap = new LinkedHashMap<>();
        ListTag paletteTag = new ListTag();

        for (BlockEntry entry : data.blocks) {
            String blockName = entry.blockName;
            if (!paletteMap.containsKey(blockName)) {
                paletteMap.put(blockName, paletteMap.size());
                CompoundTag paletteEntry = new CompoundTag();
                paletteEntry.putString("Name", blockName);
                paletteTag.add(paletteEntry);
            }
        }

        // Build blocks
        ListTag blocksTag = new ListTag();
        for (BlockEntry entry : data.blocks) {
            CompoundTag blockTag = new CompoundTag();
            blockTag.putInt("state", paletteMap.get(entry.blockName));
            ListTag posTag = new ListTag();
            posTag.add(IntTag.valueOf(entry.x));
            posTag.add(IntTag.valueOf(entry.y));
            posTag.add(IntTag.valueOf(entry.z));
            blockTag.put("pos", posTag);
            blocksTag.add(blockTag);
        }

        // Build size
        ListTag sizeTag = new ListTag();
        sizeTag.add(IntTag.valueOf(data.sizeX));
        sizeTag.add(IntTag.valueOf(data.sizeY));
        sizeTag.add(IntTag.valueOf(data.sizeZ));

        CompoundTag root = new CompoundTag();
        root.put("size", sizeTag);
        root.put("palette", paletteTag);
        root.put("blocks", blocksTag);
        root.put("entities", new ListTag());
        root.putInt("DataVersion", 3953); // 1.21.1

        return root;
    }

    // ---- Structure builders (matching TestMultiblockRegistrar) ----

    /** 3x2x3 furnace surrounded by stone bricks. */
    private StructureData buildTestFurnace() {
        List<BlockEntry> blocks = new ArrayList<>();
        // Bottom layer: stone bricks with furnace in center
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                if (x == 1 && z == 1) {
                    blocks.add(new BlockEntry(x, 0, z, "minecraft:furnace"));
                } else {
                    blocks.add(new BlockEntry(x, 0, z, "minecraft:stone_bricks"));
                }
            }
        }
        // Top layer: stone bricks at corners only
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                if ((x == 0 || x == 2) && (z == 0 || z == 2)) {
                    blocks.add(new BlockEntry(x, 1, z, "minecraft:stone_bricks"));
                }
            }
        }
        return new StructureData(3, 2, 3, blocks);
    }

    /** 3x2x3 beacon base with iron blocks (static, no BlockGroup cycling). */
    private StructureData buildTestBeacon() {
        List<BlockEntry> blocks = new ArrayList<>();
        // 3x3 base of iron blocks
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                blocks.add(new BlockEntry(x, 0, z, "minecraft:iron_block"));
            }
        }
        // Beacon on top center
        blocks.add(new BlockEntry(1, 1, 1, "minecraft:beacon"));
        return new StructureData(3, 2, 3, blocks);
    }

    /** 1x8x1 cobblestone tower (medium height). */
    private StructureData buildTestTower() {
        List<BlockEntry> blocks = new ArrayList<>();
        for (int y = 0; y < 8; y++) {
            blocks.add(new BlockEntry(0, y, 0, "minecraft:cobblestone"));
        }
        return new StructureData(1, 8, 1, blocks);
    }

    /** 5x4x1 wall with stone brick border and glass fill (medium size). */
    private StructureData buildTestWall() {
        List<BlockEntry> blocks = new ArrayList<>();
        int width = 5, height = 4;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                boolean isBorder = x == 0 || x == width - 1 || y == 0 || y == height - 1;
                if (isBorder) {
                    blocks.add(new BlockEntry(x, y, 0, "minecraft:stone_bricks"));
                } else {
                    blocks.add(new BlockEntry(x, y, 0, "minecraft:glass"));
                }
            }
        }
        return new StructureData(width, height, 1, blocks);
    }

    record StructureData(int sizeX, int sizeY, int sizeZ, List<BlockEntry> blocks) {}
    record BlockEntry(int x, int y, int z, String blockName) {}
}
