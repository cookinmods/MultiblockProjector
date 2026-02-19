package com.multiblockprojector.common.network;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.clipboard.ClipboardBlock;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import com.simibubi.create.content.equipment.clipboard.ClipboardOverrides;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
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

import static com.multiblockprojector.UniversalProjector.rl;

/**
 * Client→Server packet to write block requirements to a Create clipboard in the player's inventory.
 * Produces icon-rich entries matching Create's Material Checklist format.
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

        // Build clipboard entries with item icons, matching Create's Material Checklist format
        List<List<ClipboardEntry>> pages = new ArrayList<>();
        List<ClipboardEntry> currentPage = new ArrayList<>();

        for (EntryData entry : packet.entries) {
            Block block = BuiltInRegistries.BLOCK.get(entry.blockId);
            ItemStack icon = new ItemStack(block);
            boolean checked = entry.have >= entry.needed;
            int amount = entry.needed;

            // Build text: "Block Name x 64 (1s 0)" matching Create's format
            Component nameComponent = Component.translatable(block.getDescriptionId())
                .setStyle(Style.EMPTY.withHoverEvent(
                    new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(icon))));

            Component amountComponent = Component.literal(" x " + amount)
                .withStyle(ChatFormatting.BLACK);

            int stacks = amount / 64;
            int remainder = amount % 64;
            String stackStr = stacks > 0
                ? " (" + stacks + "s " + remainder + ")"
                : "";
            Component stackComponent = Component.literal(stackStr)
                .withStyle(ChatFormatting.GRAY);

            Component text = Component.empty()
                .append(nameComponent.copy().withStyle(checked ? ChatFormatting.DARK_GREEN : ChatFormatting.BLUE))
                .append(amountComponent)
                .append(stackComponent);

            ClipboardEntry clipEntry = new ClipboardEntry(checked, text.copy())
                .displayItem(icon, checked ? 0 : amount);

            currentPage.add(clipEntry);

            // Paginate at MAX_ENTRIES_PER_PAGE
            if (currentPage.size() >= MAX_ENTRIES_PER_PAGE) {
                pages.add(currentPage);
                currentPage = new ArrayList<>();
            }
        }

        // Add remaining entries
        if (!currentPage.isEmpty()) {
            pages.add(currentPage);
        }

        if (pages.isEmpty()) return;

        ClipboardContent content = new ClipboardContent(
            ClipboardOverrides.ClipboardType.WRITTEN,
            pages,
            true // readOnly, matching Create's material checklist
        );
        clipboard.set(AllDataComponents.CLIPBOARD_CONTENT, content);
    }
}
