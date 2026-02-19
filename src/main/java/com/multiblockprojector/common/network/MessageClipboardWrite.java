package com.multiblockprojector.common.network;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.clipboard.ClipboardBlock;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import com.simibubi.create.content.equipment.clipboard.ClipboardOverrides;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

import static com.multiblockprojector.UniversalProjector.rl;

/**
 * Client→Server packet to write block requirements to a Create clipboard in the player's inventory.
 * Each entry is: blockName, needed count, have count (checked = have >= needed).
 */
public class MessageClipboardWrite implements CustomPacketPayload {

    public static final Type<MessageClipboardWrite> TYPE = new Type<>(rl("clipboard_write"));

    public record EntryData(String blockName, int needed, int have) {}

    private static final StreamCodec<FriendlyByteBuf, EntryData> ENTRY_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, EntryData::blockName,
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
        // Find a Create clipboard in the player's inventory
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

        // Build clipboard entries from packet data
        List<ClipboardEntry> page = new ArrayList<>();
        for (EntryData entry : packet.entries) {
            boolean checked = entry.have >= entry.needed;
            ClipboardEntry clipEntry = new ClipboardEntry(checked,
                Component.literal(entry.needed + "x " + entry.blockName));
            page.add(clipEntry);
        }

        // Create content and set on the clipboard
        ClipboardContent content = new ClipboardContent(
            ClipboardOverrides.ClipboardType.WRITTEN,
            List.of(page),
            false
        );
        clipboard.set(AllDataComponents.CLIPBOARD_CONTENT, content);
    }
}
