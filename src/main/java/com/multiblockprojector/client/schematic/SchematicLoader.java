package com.multiblockprojector.client.schematic;

import com.multiblockprojector.api.BlockEntry;
import com.multiblockprojector.api.MultiblockStructure;
import com.multiblockprojector.api.SingleBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads .nbt structure files (vanilla StructureTemplate format) and converts
 * them to MultiblockStructure for use in the projector.
 */
public class SchematicLoader {

    /**
     * Read just the dimensions from an .nbt file without parsing all blocks.
     * Returns null if the file is unreadable or has no size tag.
     */
    @Nullable
    public static BlockPos readSize(Path nbtFile) {
        try (InputStream is = Files.newInputStream(nbtFile)) {
            CompoundTag root = NbtIo.readCompressed(is, NbtAccounter.unlimitedHeap());
            if (root.contains("size", Tag.TAG_LIST)) {
                ListTag sizeTag = root.getList("size", Tag.TAG_INT);
                if (sizeTag.size() == 3) {
                    return new BlockPos(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2));
                }
            }
        } catch (IOException e) {
            // File unreadable - will be skipped
        }
        return null;
    }

    /**
     * Fully load an .nbt file and convert to MultiblockStructure.
     * Air blocks are included as SingleBlock(AIR) for validation.
     * Structure void blocks are omitted.
     * Returns null if the file is unreadable or produces an empty structure.
     */
    @Nullable
    public static MultiblockStructure load(Path nbtFile) {
        CompoundTag root;
        try (InputStream is = Files.newInputStream(nbtFile)) {
            root = NbtIo.readCompressed(is, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            return null;
        }

        // Parse palette
        if (!root.contains("palette", Tag.TAG_LIST) || !root.contains("blocks", Tag.TAG_LIST)) {
            return null;
        }

        ListTag paletteTag = root.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>();
        for (int i = 0; i < paletteTag.size(); i++) {
            CompoundTag entry = paletteTag.getCompound(i);
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), entry);
            palette.add(state);
        }

        // Parse block positions
        ListTag blocksTag = root.getList("blocks", Tag.TAG_COMPOUND);
        Map<BlockPos, BlockEntry> blocks = new LinkedHashMap<>();
        boolean hasNonAirBlock = false;

        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag blockTag = blocksTag.getCompound(i);
            int stateIndex = blockTag.getInt("state");
            if (stateIndex < 0 || stateIndex >= palette.size()) continue;

            BlockState state = palette.get(stateIndex);

            // Skip structure voids entirely
            if (state.is(Blocks.STRUCTURE_VOID)) continue;

            ListTag posTag = blockTag.getList("pos", Tag.TAG_INT);
            if (posTag.size() != 3) continue;
            BlockPos pos = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));

            // Include air blocks (for validation) and solid blocks
            blocks.put(pos, new SingleBlock(state));

            if (!state.isAir()) {
                hasNonAirBlock = true;
            }
        }

        // Skip structures that are only air/empty
        if (!hasNonAirBlock || blocks.isEmpty()) {
            return null;
        }

        return new MultiblockStructure(blocks);
    }
}
