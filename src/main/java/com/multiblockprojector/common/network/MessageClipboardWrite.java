package com.multiblockprojector.common.network;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.clipboard.ClipboardBlock;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import com.simibubi.create.content.equipment.clipboard.ClipboardOverrides;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.multiblockprojector.UniversalProjector.rl;

/**
 * Client→Server packet to write block requirements to a Create clipboard in the player's inventory.
 * Produces entries matching Create's Material Checklist format exactly.
 */
public class MessageClipboardWrite implements CustomPacketPayload {

    public static final Type<MessageClipboardWrite> TYPE = new Type<>(rl("clipboard_write"));
    private static final int MAX_ENTRIES_PER_PAGE = 7;

    public record EntryData(ResourceLocation blockId, int needed, int have) {}

    private static final StreamCodec<FriendlyByteBuf, EntryData> ENTRY_CODEC =
        StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, EntryData::blockId,
            ByteBufCodecs.INT, EntryData::needed,
            ByteBufCodecs.INT, EntryData::have,
            EntryData::new
        );

    public static final StreamCodec<FriendlyByteBuf, MessageClipboardWrite> STREAM_CODEC =
        StreamCodec.composite(
            ENTRY_CODEC.apply(ByteBufCodecs.list()), p -> p.entries,
            MessageClipboardWrite::new
        );

    private final List<EntryData> entries;

    public MessageClipboardWrite(List<EntryData> entries) {
        this.entries = entries;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendToServer(List<EntryData> entries) {
        PacketDistributor.sendToServer(new MessageClipboardWrite(entries));
    }

    public static void handleServerSide(MessageClipboardWrite packet, Player player) {
        var inv = player.getInventory();
        int clipboardSlot = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ClipboardBlock) {
                clipboardSlot = i;
                break;
            }
        }

        if (clipboardSlot == -1) {
            player.displayClientMessage(
                Component.literal("No clipboard found in inventory").withStyle(ChatFormatting.RED),
                true
            );
            return;
        }

        ItemStack clipboard = inv.getItem(clipboardSlot);

        // Sort entries alphabetically by display name, incomplete first then completed
        List<EntryData> incomplete = new ArrayList<>();
        List<EntryData> completed = new ArrayList<>();

        for (EntryData entry : packet.entries) {
            int remaining = entry.needed - entry.have;
            if (remaining <= 0) {
                completed.add(entry);
            } else {
                incomplete.add(entry);
            }
        }

        incomplete.sort((a, b) -> {
            String nameA = getItemName(a.blockId).toLowerCase(Locale.ENGLISH);
            String nameB = getItemName(b.blockId).toLowerCase(Locale.ENGLISH);
            return nameA.compareTo(nameB);
        });
        completed.sort((a, b) -> {
            String nameA = getItemName(a.blockId).toLowerCase(Locale.ENGLISH);
            String nameB = getItemName(b.blockId).toLowerCase(Locale.ENGLISH);
            return nameA.compareTo(nameB);
        });

        // Build clipboard pages matching Create's MaterialChecklist.createWrittenClipboard()
        List<List<ClipboardEntry>> pages = new ArrayList<>();
        List<ClipboardEntry> currentPage = new ArrayList<>();
        int itemsWritten = 0;

        // Incomplete items first (unchecked)
        for (EntryData entry : incomplete) {
            if (itemsWritten == MAX_ENTRIES_PER_PAGE) {
                itemsWritten = 0;
                currentPage.add(new ClipboardEntry(false,
                    Component.literal(">>>").withStyle(ChatFormatting.DARK_GRAY)));
                pages.add(currentPage);
                currentPage = new ArrayList<>();
            }
            itemsWritten++;

            int remaining = entry.needed - entry.have;
            ItemStack icon = new ItemStack(BuiltInRegistries.BLOCK.get(entry.blockId));
            currentPage.add(
                new ClipboardEntry(false, formatEntry(icon, remaining, true))
                    .displayItem(icon, remaining)
            );
        }

        // Completed items second (checked)
        for (EntryData entry : completed) {
            if (itemsWritten == MAX_ENTRIES_PER_PAGE) {
                itemsWritten = 0;
                currentPage.add(new ClipboardEntry(true,
                    Component.literal(">>>").withStyle(ChatFormatting.DARK_GREEN)));
                pages.add(currentPage);
                currentPage = new ArrayList<>();
            }
            itemsWritten++;

            ItemStack icon = new ItemStack(BuiltInRegistries.BLOCK.get(entry.blockId));
            currentPage.add(
                new ClipboardEntry(true, formatEntry(icon, entry.needed, false))
                    .displayItem(icon, 0)
            );
        }

        if (!currentPage.isEmpty()) {
            pages.add(currentPage);
        }

        if (pages.isEmpty()) return;

        ClipboardContent content = new ClipboardContent(
            ClipboardOverrides.ClipboardType.WRITTEN,
            pages,
            true
        );
        clipboard.set(AllDataComponents.CLIPBOARD_CONTENT, content);
        clipboard.set(DataComponents.CUSTOM_NAME,
            Component.translatable("create.materialChecklist")
                .setStyle(Style.EMPTY.withItalic(false)));
    }

    /**
     * Formats a clipboard entry's text to match Create's MaterialChecklist.entry() output.
     * Multi-line: item name (with hover tooltip) + newline + " x<amount> | <stacks>▤ +<remainder>"
     */
    private static MutableComponent formatEntry(ItemStack item, int amount, boolean unfinished) {
        MutableComponent tc = Component.empty();

        tc.append(Component.translatable(item.getDescriptionId())
            .setStyle(Style.EMPTY.withHoverEvent(
                new HoverEvent(HoverEvent.Action.SHOW_ITEM,
                    new HoverEvent.ItemStackInfo(item)))));

        if (!unfinished) {
            tc.withStyle(ChatFormatting.DARK_GREEN);
        }

        return tc.append(Component.literal("\n x" + amount).withStyle(ChatFormatting.BLACK));
    }

    private static String getItemName(ResourceLocation blockId) {
        Block block = BuiltInRegistries.BLOCK.get(blockId);
        return new ItemStack(block).getHoverName().getString();
    }
}
