package com.multiblockprojector.common.network;

import com.multiblockprojector.api.BlockEntry;
import com.multiblockprojector.api.BlockGroup;
import com.multiblockprojector.api.MultiblockDefinition;
import com.multiblockprojector.api.SingleBlock;
import com.multiblockprojector.common.fabrication.FabricationManager;
import com.multiblockprojector.common.fabrication.FabricationTask;
import com.multiblockprojector.common.items.AbstractProjectorItem;
import com.multiblockprojector.common.items.BatteryFabricatorItem;
import com.multiblockprojector.common.items.BatteryFabricatorEnergyStorage;
import com.multiblockprojector.common.items.FabricatorItem;
import com.multiblockprojector.common.projector.MultiblockProjection;
import com.multiblockprojector.common.projector.Settings;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

import static com.multiblockprojector.UniversalProjector.rl;

public class MessageFabricate implements CustomPacketPayload {

    public static final Type<MessageFabricate> TYPE = new Type<>(rl("fabricate"));

    public static final StreamCodec<FriendlyByteBuf, MessageFabricate> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, p -> p.buildPos,
            ByteBufCodecs.idMapper(i -> InteractionHand.values()[i], Enum::ordinal), p -> p.hand,
            MessageFabricate::new
        );

    private final BlockPos buildPos;
    private final InteractionHand hand;

    public MessageFabricate(BlockPos buildPos, InteractionHand hand) {
        this.buildPos = buildPos;
        this.hand = hand;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void sendToServer(BlockPos buildPos, InteractionHand hand) {
        PacketDistributor.sendToServer(new MessageFabricate(buildPos, hand));
    }

    public static void handleServerSide(MessageFabricate packet, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        ItemStack stack = player.getItemInHand(packet.hand);
        if (!(stack.getItem() instanceof FabricatorItem) && !(stack.getItem() instanceof BatteryFabricatorItem)) {
            return;
        }

        Settings settings = AbstractProjectorItem.getSettings(stack);
        if (settings.getMode() != Settings.Mode.PROJECTION || settings.getMultiblock() == null) {
            return;
        }

        // Check for existing active task
        if (FabricationManager.hasActiveTask(player.getUUID())) {
            player.displayClientMessage(
                Component.literal("A fabrication is already in progress!")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        MultiblockDefinition multiblock = settings.getMultiblock();
        Level level = player.level();

        // Build projection
        var variant = MultiblockProjection.getVariantFromSettings(multiblock, settings);
        MultiblockProjection projection = new MultiblockProjection(level, multiblock, variant);
        projection.setRotation(settings.getRotation());
        projection.setFlip(settings.isMirrored());

        // Collect positions and their block entries
        record PositionEntry(BlockPos worldPos, BlockEntry blockEntry, BlockState displayState) {}
        List<PositionEntry> positions = new ArrayList<>();
        projection.processAll((layerIdx, info) -> {
            BlockPos worldPos = packet.buildPos.offset(info.tPos);
            BlockState display = info.getDisplayState(level, worldPos, 0);
            if (!display.isAir()) {
                positions.add(new PositionEntry(worldPos, info.blockEntry, display));
            }
            return false;
        });

        // Resolve each position to an actual block from inventory.
        // For SingleBlock entries, requires the exact block.
        // For BlockGroup entries, picks the first available matching block.
        Map<net.minecraft.world.level.block.Block, Integer> available = countAvailableBlocks(player, settings, level);
        List<FabricationTask.PlacementEntry> resolvedPlacements = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (PositionEntry pos : positions) {
            net.minecraft.world.level.block.Block resolved = resolveBlock(pos.blockEntry, available);
            if (resolved != null) {
                // Decrement available count
                int remaining = available.get(resolved) - 1;
                if (remaining <= 0) {
                    available.remove(resolved);
                } else {
                    available.put(resolved, remaining);
                }
                resolvedPlacements.add(new FabricationTask.PlacementEntry(pos.worldPos, resolved.defaultBlockState()));
            } else {
                // No matching block found
                String name = switch (pos.blockEntry) {
                    case SingleBlock sb -> sb.state().getBlock().getName().getString();
                    case BlockGroup bg -> bg.label().getString();
                    default -> "Unknown";
                };
                if (missing.stream().noneMatch(s -> s.startsWith(name))) {
                    missing.add(name);
                }
            }
        }

        if (!missing.isEmpty()) {
            player.displayClientMessage(
                Component.literal("Missing blocks: " + String.join(", ", missing.subList(0, Math.min(3, missing.size()))))
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        // Calculate total FE cost from resolved blocks
        int totalNonAir = resolvedPlacements.size();
        double totalFE = 0;
        for (var entry : resolvedPlacements) {
            float hardness = Math.max(entry.state().getDestroySpeed(level, BlockPos.ZERO), 0.1f);
            double perBlock = 800.0 * hardness * (1.0 + 0.0008 * totalNonAir);
            totalFE += perBlock;
        }
        int totalFENeeded = (int) Math.ceil(totalFE);

        // === PRE-VALIDATION: Check FE ===
        IEnergyStorage energySource = getEnergySource(stack, settings, level);
        if (energySource == null) {
            player.displayClientMessage(
                Component.literal("Energy source not available!")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        int availableFE = energySource.getEnergyStored();
        if (availableFE < totalFENeeded) {
            player.displayClientMessage(
                Component.literal("Not enough FE! Need " + BatteryFabricatorItem.formatEnergy(totalFENeeded) +
                    ", have " + BatteryFabricatorItem.formatEnergy(availableFE))
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        // === RESOURCE RESERVATION ===

        // Build consumption map from resolved placements
        Map<net.minecraft.world.level.block.Block, Integer> toConsume = new LinkedHashMap<>();
        for (var entry : resolvedPlacements) {
            toConsume.merge(entry.state().getBlock(), 1, Integer::sum);
        }

        energySource.extractEnergy(totalFENeeded, false);
        consumeBlocks(player, settings, level, toConsume);

        // === CREATE FABRICATION TASK ===
        FabricationTask task = new FabricationTask(serverPlayer, level, packet.hand, resolvedPlacements);
        FabricationManager.addTask(serverPlayer, task);

        // Reset fabricator to default mode
        settings.setMode(Settings.Mode.NOTHING_SELECTED);
        settings.setPos(null);
        settings.setPlaced(false);
        settings.applyTo(stack);

        player.displayClientMessage(
            Component.literal("Fabrication started! Building " + totalNonAir + " blocks...")
                .withStyle(ChatFormatting.GOLD), true);
    }

    /**
     * Find an available block in inventory that satisfies the given BlockEntry.
     * Returns the Block to use, or null if no match is available.
     */
    private static net.minecraft.world.level.block.Block resolveBlock(
            BlockEntry entry, Map<net.minecraft.world.level.block.Block, Integer> available) {
        if (entry instanceof SingleBlock sb) {
            net.minecraft.world.level.block.Block block = sb.state().getBlock();
            return available.getOrDefault(block, 0) > 0 ? block : null;
        } else if (entry instanceof BlockGroup bg) {
            for (BlockState option : bg.options()) {
                net.minecraft.world.level.block.Block block = option.getBlock();
                if (available.getOrDefault(block, 0) > 0) {
                    return block;
                }
            }
            return null;
        }
        return null;
    }

    private static IEnergyStorage getEnergySource(ItemStack stack, Settings settings, Level level) {
        if (stack.getItem() instanceof BatteryFabricatorItem) {
            return new BatteryFabricatorEnergyStorage(stack);
        }

        // External energy source
        BlockPos energyPos = settings.getLinkedEnergyPos();
        ResourceLocation energyDim = settings.getLinkedEnergyDim();
        if (energyPos == null) return null;

        // Check same dimension
        if (energyDim != null && !level.dimension().location().equals(energyDim)) return null;

        // Check chunk loaded
        if (!level.isLoaded(energyPos)) return null;

        return level.getCapability(Capabilities.EnergyStorage.BLOCK, energyPos, null);
    }

    private static Map<net.minecraft.world.level.block.Block, Integer> countAvailableBlocks(
            Player player, Settings settings, Level level) {
        Map<net.minecraft.world.level.block.Block, Integer> counts = new HashMap<>();

        // Count from player inventory
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack invStack = inv.getItem(i);
            if (!invStack.isEmpty() && invStack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
                counts.merge(blockItem.getBlock(), invStack.getCount(), Integer::sum);
            }
        }

        // Count from linked chest
        BlockPos chestPos = settings.getLinkedChestPos();
        ResourceLocation chestDim = settings.getLinkedChestDim();
        if (chestPos != null && (chestDim == null || level.dimension().location().equals(chestDim)) && level.isLoaded(chestPos)) {
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, chestPos, null);
            if (handler != null) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack slotStack = handler.getStackInSlot(i);
                    if (!slotStack.isEmpty() && slotStack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
                        counts.merge(blockItem.getBlock(), slotStack.getCount(), Integer::sum);
                    }
                }
            }
        }

        return counts;
    }

    private static void consumeBlocks(Player player, Settings settings, Level level,
                                       Map<net.minecraft.world.level.block.Block, Integer> required) {
        Map<net.minecraft.world.level.block.Block, Integer> remaining = new HashMap<>(required);

        // Consume from player inventory first
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && !remaining.isEmpty(); i++) {
            ItemStack invStack = inv.getItem(i);
            if (!invStack.isEmpty() && invStack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
                Integer needed = remaining.get(blockItem.getBlock());
                if (needed != null && needed > 0) {
                    int take = Math.min(needed, invStack.getCount());
                    invStack.shrink(take);
                    int left = needed - take;
                    if (left <= 0) {
                        remaining.remove(blockItem.getBlock());
                    } else {
                        remaining.put(blockItem.getBlock(), left);
                    }
                }
            }
        }

        // Consume from linked chest
        BlockPos chestPos = settings.getLinkedChestPos();
        ResourceLocation chestDim = settings.getLinkedChestDim();
        if (chestPos != null && !remaining.isEmpty() &&
            (chestDim == null || level.dimension().location().equals(chestDim)) && level.isLoaded(chestPos)) {
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, chestPos, null);
            if (handler != null) {
                for (int i = 0; i < handler.getSlots() && !remaining.isEmpty(); i++) {
                    ItemStack slotStack = handler.getStackInSlot(i);
                    if (!slotStack.isEmpty() && slotStack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
                        Integer needed = remaining.get(blockItem.getBlock());
                        if (needed != null && needed > 0) {
                            int take = Math.min(needed, slotStack.getCount());
                            handler.extractItem(i, take, false);
                            int left = needed - take;
                            if (left <= 0) {
                                remaining.remove(blockItem.getBlock());
                            } else {
                                remaining.put(blockItem.getBlock(), left);
                            }
                        }
                    }
                }
            }
        }
    }
}
