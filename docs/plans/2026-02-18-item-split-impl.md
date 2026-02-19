# Item Split Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Split the single Multiblock Projector into four specialized items (base Projector, Creative Projector, Multiblock Fabricator, Battery Powered MBF) with energy system, animated fabrication, GUI requirements panel, and new networking.

**Architecture:** Extract shared projector logic into `AbstractProjectorItem`, with four subclasses overriding interaction behavior. Server-side `FabricationManager` handles animated placement via tick events. `RequirementsPanel` GUI widget conditionally appears in `ProjectorScreen` for fabricator items.

**Tech Stack:** NeoForge 21.1.176, Minecraft 1.21.1, Java 21, NeoForge capability system for IEnergyStorage

---

## Phase 1: Refactor Base — Extract AbstractProjectorItem

### Task 1: Create AbstractProjectorItem and refactor ProjectorItem

**Files:**
- Create: `src/main/java/com/multiblockprojector/common/items/AbstractProjectorItem.java`
- Modify: `src/main/java/com/multiblockprojector/common/items/ProjectorItem.java`

**Step 1: Create AbstractProjectorItem**

Extract the shared logic from `ProjectorItem` into a new abstract base class. The base class owns:
- Constructor with `stacksTo(1)` (accepting `Item.Properties` for subclass customization)
- `getName()` override with mode-based coloring
- `getSettings()` static helper
- `openGUI()` method
- `createProjection()`, `clearProjection()`, `updateProjection()` methods
- `useOn()` returning PASS
- Default `appendHoverText()` with basic description

The `use()` method should handle shared behaviors (NOTHING_SELECTED → open GUI, BUILDING → cancel) and delegate PROJECTION-mode right-click to an abstract method.

```java
package com.multiblockprojector.common.items;

import com.multiblockprojector.common.projector.Settings;
import com.multiblockprojector.common.projector.MultiblockProjection;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public abstract class AbstractProjectorItem extends Item {

    public AbstractProjectorItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public AbstractProjectorItem() {
        this(new Item.Properties());
    }

    @Override
    @Nonnull
    public Component getName(@Nonnull ItemStack stack) {
        String selfKey = getDescriptionId(stack);
        if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) {
            Settings settings = getSettings(stack);
            Settings.Mode mode = settings.getMode();

            return switch (mode) {
                case MULTIBLOCK_SELECTION ->
                    Component.translatable(selfKey + ".selecting").withStyle(ChatFormatting.GOLD);
                case PROJECTION ->
                    Component.translatable(selfKey + ".projection_mode").withStyle(ChatFormatting.AQUA);
                case BUILDING ->
                    Component.translatable(selfKey + ".building_mode").withStyle(ChatFormatting.DARK_PURPLE);
                default ->
                    Component.translatable(selfKey).withStyle(ChatFormatting.GOLD);
            };
        }
        return Component.translatable(selfKey).withStyle(ChatFormatting.GOLD);
    }

    @Override
    @Nonnull
    public InteractionResultHolder<ItemStack> use(Level world, Player player, @Nonnull InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        Settings settings = getSettings(held);

        if (world.isClientSide) {
            switch (settings.getMode()) {
                case NOTHING_SELECTED:
                    settings.setMode(Settings.Mode.MULTIBLOCK_SELECTION);
                    settings.applyTo(held);
                    settings.sendPacketToServer(hand);
                    openGUI(hand, held);
                    break;

                case MULTIBLOCK_SELECTION:
                    break;

                case PROJECTION:
                    handleProjectionRightClick(player, settings, held, hand);
                    break;

                case BUILDING:
                    clearProjection(settings);
                    com.multiblockprojector.client.BlockValidationManager.clearValidation(settings.getPos());
                    settings.setMode(Settings.Mode.NOTHING_SELECTED);
                    settings.setMultiblock(null);
                    settings.setPos(null);
                    settings.setPlaced(false);
                    settings.applyTo(held);
                    settings.sendPacketToServer(hand);
                    player.displayClientMessage(Component.literal("Building mode cancelled"), true);
                    break;
            }
        }

        return InteractionResultHolder.success(held);
    }

    /**
     * Called when the player right-clicks while in PROJECTION mode.
     * Subclasses override to define the primary action (place, auto-build, fabricate).
     * This is the "deliberate action" click — left-click handles rotation.
     */
    protected abstract void handleProjectionRightClick(Player player, Settings settings, ItemStack held, InteractionHand hand);

    @Override
    @Nonnull
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.PASS;
    }

    public static Settings getSettings(@Nullable ItemStack stack) {
        return new Settings(stack);
    }

    protected void openGUI(InteractionHand hand, ItemStack held) {
        if (net.minecraft.client.Minecraft.getInstance() != null) {
            net.minecraft.client.Minecraft.getInstance().setScreen(
                new com.multiblockprojector.client.gui.ProjectorScreen(held, hand)
            );
        }
    }

    protected void createProjection(BlockPos pos, Settings settings, Level world) {
        if (world.isClientSide && settings.getMultiblock() != null) {
            var variant = MultiblockProjection.getVariantFromSettings(settings.getMultiblock(), settings);
            MultiblockProjection projection = new MultiblockProjection(world, settings.getMultiblock(), variant);
            projection.setRotation(settings.getRotation());
            projection.setFlip(settings.isMirrored());
            com.multiblockprojector.client.ProjectionManager.setProjection(pos, projection);
        }
    }

    protected void clearProjection(Settings settings) {
        if (settings.getPos() != null) {
            com.multiblockprojector.client.ProjectionManager.removeProjection(settings.getPos());
            com.multiblockprojector.client.BlockValidationManager.clearValidation(settings.getPos());
        }
    }

    protected void updateProjection(Settings settings) {
        if (settings.getPos() != null && settings.getMultiblock() != null) {
            Level world = net.minecraft.client.Minecraft.getInstance().level;
            if (world != null) {
                clearProjection(settings);
                createProjection(settings.getPos(), settings, world);
            }
        }
    }
}
```

**Step 2: Refactor ProjectorItem to extend AbstractProjectorItem**

Strip `ProjectorItem` down to only what's unique — the `appendHoverText()` with base-projector-specific instructions, and the `handleProjectionRightClick()` that does sneak+right-click cancel (keeping the existing PROJECTION right-click behavior, but note: the actual "place projection" action will now be triggered via right-click instead of left-click — we handle that in the client handler). Remove `getName()`, `use()` shared logic, `getSettings()`, `useOn()`, helper methods (all now in abstract base).

```java
package com.multiblockprojector.common.items;

import com.multiblockprojector.common.projector.Settings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import javax.annotation.Nonnull;
import java.util.List;

public class ProjectorItem extends AbstractProjectorItem {

    public ProjectorItem() {
        super();
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, TooltipContext ctx, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flagIn) {
        Settings settings = getSettings(stack);
        if (settings.getMultiblock() != null) {
            tooltip.add(Component.translatable("desc.multiblockprojector.info.projector.build0"));
        } else {
            tooltip.add(Component.literal("Creates Projections of multiblock structures")
                .withStyle(ChatFormatting.AQUA));
        }

        if (Screen.hasShiftDown()) {
            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("Default Mode:").withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.literal("Right-click to open multiblock menu").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal(""));

            tooltip.add(Component.literal("Projection Mode:").withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.literal("Left-click to rotate 90 degrees").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Right-click to place projection").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal(""));

            tooltip.add(Component.literal("Build Mode:").withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.literal("Build blocks to match projection").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Right-click with projector in hand to cancel").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Hold Shift for instructions").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    protected void handleProjectionRightClick(Player player, Settings settings, ItemStack held, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            // Sneak + Right Click: Cancel projection
            settings.setMode(Settings.Mode.NOTHING_SELECTED);
            settings.setMultiblock(null);
            settings.setPos(null);
            clearProjection(settings);
            settings.applyTo(held);
            settings.sendPacketToServer(hand);
            player.displayClientMessage(Component.literal("Projection cancelled"), true);
        }
        // Non-sneak right-click in PROJECTION: place projection (handled by ProjectorClientHandler)
    }
}
```

**Step 3: Compile check**

Run: `./gradlew compileJava`
Expected: SUCCESS — no other files reference `ProjectorItem` constructors or non-public methods that changed.

**Step 4: Commit**

```
feat: extract AbstractProjectorItem base class from ProjectorItem
```

---

### Task 2: Swap left/right click in ProjectorClientHandler

**Files:**
- Modify: `src/main/java/com/multiblockprojector/client/ProjectorClientHandler.java`

**Step 1: Refactor onMouseClick to swap button assignments**

In the current code at lines 79-130, left-click (button 0) places/auto-builds and right-click (button 1) rotates. Swap these:
- **Left-click (button 0)**: Rotate 90° CW (the old right-click behavior at lines 112-124)
- **Right-click (button 1)**: Dispatch to item-specific action (the old left-click behavior at lines 93-111)

Also update `instanceof ProjectorItem` checks to `instanceof AbstractProjectorItem` throughout the file (lines 47, 87, 142, 306).

Replace the `onMouseClick` method body:

```java
@SubscribeEvent
public static void onMouseClick(InputEvent.MouseButton.Pre event) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null) return;

    Player player = mc.player;
    ItemStack held = player.getMainHandItem();

    if (!(held.getItem() instanceof AbstractProjectorItem)) return;

    Settings settings = AbstractProjectorItem.getSettings(held);

    if (settings.getMode() == Settings.Mode.PROJECTION && settings.getMultiblock() != null) {
        if (event.getAction() == 1) { // Mouse button press
            if (event.getButton() == 0) { // Left click → rotate
                if (lastAimPos != null) {
                    settings.rotateCW();
                    settings.applyTo(held);
                    settings.sendPacketToServer(InteractionHand.MAIN_HAND);

                    updateProjectionAtPos(lastAimPos, settings, player.level());
                    event.setCanceled(true);
                }
            } else if (event.getButton() == 1 && !player.isShiftKeyDown()) { // Right click → item action
                if (lastAimPos != null) {
                    handleProjectionAction(player, held, settings, lastAimPos);
                    event.setCanceled(true);
                }
            }
        }
    }
}
```

Add a new dispatch method that routes based on item type:

```java
private static void handleProjectionAction(Player player, ItemStack held, Settings settings, BlockPos pos) {
    if (held.getItem() instanceof CreativeProjectorItem) {
        autoBuildProjection(player, settings, held, pos);
    } else if (held.getItem() instanceof FabricatorItem || held.getItem() instanceof BatteryFabricatorItem) {
        fabricateProjection(player, settings, held, pos);
    } else {
        // Base ProjectorItem — place projection and enter building mode
        placeProjection(player, settings, held, pos);
    }
}
```

Note: `CreativeProjectorItem`, `FabricatorItem`, `BatteryFabricatorItem`, and `fabricateProjection()` don't exist yet. For now, stub just the dispatch to `placeProjection()` for any `AbstractProjectorItem`, then refine in later tasks. The initial version:

```java
private static void handleProjectionAction(Player player, ItemStack held, Settings settings, BlockPos pos) {
    // Base behavior — place projection and enter building mode
    placeProjection(player, settings, held, pos);
}
```

Also update `onClientTick` (line 47) and `onKeyInput` (line 142) and `checkAllBuildingProjectionsForCompletion` (line 306) to use `AbstractProjectorItem` instead of `ProjectorItem`:

- Line 47: `if (!(held.getItem() instanceof AbstractProjectorItem))`
- Line 56: `Settings settings = AbstractProjectorItem.getSettings(held);`
- Line 87-89: same pattern
- Line 142: `if (held.getItem() instanceof AbstractProjectorItem)`
- Line 143: `Settings settings = AbstractProjectorItem.getSettings(held);`
- Line 306: `if (stack.getItem() instanceof AbstractProjectorItem)`
- Line 307: `Settings settings = AbstractProjectorItem.getSettings(stack);`

Update import: add `AbstractProjectorItem`, keep `ProjectorItem` import for now (may still be needed), add the import for the new items as they're created.

**Step 2: Compile check**

Run: `./gradlew compileJava`
Expected: SUCCESS

**Step 3: Commit**

```
refactor: swap left/right click in projection mode, use AbstractProjectorItem
```

---

### Task 3: Remove creative auto-build from base ProjectorItem tooltips

**Files:**
- Modify: `src/main/java/com/multiblockprojector/common/items/ProjectorItem.java` (already done in Task 1 — the new tooltips reference "Left-click to rotate" / "Right-click to place projection" and omit the old "Creative Mode: Sneak + Left-click for autobuild")
- Modify: `src/main/resources/assets/multiblockprojector/lang/en_us.json`

**Step 1: Update lang file hints**

The hint keys referenced in the old code need updating. Change:
```json
"desc.multiblockprojector.info.projector.hint.projection": "Left Click: Rotate | Right Click: Place | Sneak + Right Click: Cancel | ESC: Cancel"
```

(Remove the old "Right Click: Rotate | Left Click: Place" and "Sneak + Right Click: Cancel" references if present.)

**Step 2: Compile check**

Run: `./gradlew compileJava`
Expected: SUCCESS

**Step 3: Commit**

```
fix: update tooltips and lang for new click swap controls
```

---

## Phase 2: Creative Multiblock Projector

### Task 4: Create CreativeProjectorItem

**Files:**
- Create: `src/main/java/com/multiblockprojector/common/items/CreativeProjectorItem.java`

**Step 1: Implement CreativeProjectorItem**

This item extends `AbstractProjectorItem`. Its `handleProjectionRightClick()` sends `MessageAutoBuild` to the server (no sneak required, no creative check on client — server validates). No BUILDING mode.

```java
package com.multiblockprojector.common.items;

import com.multiblockprojector.common.network.MessageAutoBuild;
import com.multiblockprojector.common.projector.Settings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import javax.annotation.Nonnull;
import java.util.List;

public class CreativeProjectorItem extends AbstractProjectorItem {

    public CreativeProjectorItem() {
        super();
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, TooltipContext ctx, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flagIn) {
        tooltip.add(Component.literal("Instantly builds multiblock structures")
            .withStyle(ChatFormatting.LIGHT_PURPLE));

        if (Screen.hasShiftDown()) {
            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("Default Mode:").withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.literal("Right-click to open multiblock menu").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("Projection Mode:").withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.literal("Left-click to rotate 90 degrees").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Right-click to instantly build").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Hold Shift for instructions").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    protected void handleProjectionRightClick(Player player, Settings settings, ItemStack held, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            // Sneak + Right Click: Cancel projection
            settings.setMode(Settings.Mode.NOTHING_SELECTED);
            settings.setMultiblock(null);
            settings.setPos(null);
            clearProjection(settings);
            settings.applyTo(held);
            settings.sendPacketToServer(hand);
            player.displayClientMessage(Component.literal("Projection cancelled"), true);
        }
        // Non-sneak right-click: auto-build is handled by ProjectorClientHandler dispatch
    }
}
```

**Step 2: Compile check**

Run: `./gradlew compileJava`
Expected: SUCCESS

**Step 3: Commit**

```
feat: add CreativeProjectorItem for instant auto-build
```

---

### Task 5: Register new items in UPContent and update client handler dispatch

**Files:**
- Modify: `src/main/java/com/multiblockprojector/common/UPContent.java`
- Modify: `src/main/java/com/multiblockprojector/client/ProjectorClientHandler.java`
- Modify: `src/main/java/com/multiblockprojector/common/network/MessageAutoBuild.java`

**Step 1: Register CreativeProjectorItem in UPContent**

Add after the existing PROJECTOR registration (line 26):

```java
import com.multiblockprojector.common.items.CreativeProjectorItem;

public static final DeferredHolder<Item, CreativeProjectorItem> CREATIVE_PROJECTOR =
    ITEMS.register("creative_projector", CreativeProjectorItem::new);
```

Update the creative tab `displayItems` lambda (lines 33-35) to include all items:

```java
.displayItems((params, output) -> {
    output.accept(PROJECTOR.get());
    output.accept(CREATIVE_PROJECTOR.get());
})
```

**Step 2: Update ProjectorClientHandler dispatch**

Update the `handleProjectionAction` method (added in Task 2) to dispatch creative projector:

```java
private static void handleProjectionAction(Player player, ItemStack held, Settings settings, BlockPos pos) {
    if (held.getItem() instanceof CreativeProjectorItem) {
        autoBuildProjection(player, settings, held, pos);
    } else {
        // Base ProjectorItem — place projection and enter building mode
        placeProjection(player, settings, held, pos);
    }
}
```

Add import: `import com.multiblockprojector.common.items.CreativeProjectorItem;`

**Step 3: Update MessageAutoBuild server validation**

In `MessageAutoBuild.handleServerSide()` (lines 58-76), change the validation to check item type instead of creative mode:

```java
public static void handleServerSide(MessageAutoBuild packet, Player player) {
    ItemStack stack = player.getItemInHand(packet.hand);
    if (!(stack.getItem() instanceof CreativeProjectorItem)) {
        return;
    }

    Settings settings = AbstractProjectorItem.getSettings(stack);
    if (settings.getMode() != Settings.Mode.PROJECTION || settings.getMultiblock() == null) {
        return;
    }

    performAutoBuild(player, settings, stack, packet.buildPos);
}
```

Add imports: `import com.multiblockprojector.common.items.CreativeProjectorItem;` and `import com.multiblockprojector.common.items.AbstractProjectorItem;`

Remove `import com.multiblockprojector.common.items.ProjectorItem;` if no longer used.

**Step 4: Add resources for creative projector**

Create item model `src/main/resources/assets/multiblockprojector/models/item/creative_projector.json`:

```json
{
  "parent": "item/generated",
  "textures": {
    "layer0": "multiblockprojector:item/projector"
  },
  "display": {
    "thirdperson_righthand": { "rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55] },
    "thirdperson_lefthand": { "rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55] }
  }
}
```

(Uses same texture as base projector for now — can be updated later with a unique texture.)

Add lang entries to `en_us.json`:

```json
"item.multiblockprojector.creative_projector": "Creative Multiblock Projector",
"item.multiblockprojector.creative_projector.selecting": "Creative Multiblock Projector (Selecting)",
"item.multiblockprojector.creative_projector.projection_mode": "Creative Multiblock Projector (Projecting)",
"item.multiblockprojector.creative_projector.building_mode": "Creative Multiblock Projector (Building)"
```

**Step 5: Compile check**

Run: `./gradlew compileJava`
Expected: SUCCESS

**Step 6: Commit**

```
feat: register CreativeProjectorItem, update auto-build validation and dispatch
```

---

## Phase 3: Settings Link Data & Link Packet

### Task 6: Add link data fields to Settings

**Files:**
- Modify: `src/main/java/com/multiblockprojector/common/projector/Settings.java`

**Step 1: Add fields, NBT keys, serialization**

Add new constants after line 36:

```java
public static final String KEY_LINKED_ENERGY = "linked_energy";
public static final String KEY_LINKED_CHEST = "linked_chest";
public static final String KEY_STORED_ENERGY = "stored_energy";
public static final String KEY_DIMENSION = "dim";
```

Add new fields after line 45:

```java
private BlockPos linkedEnergyPos = null;
private ResourceLocation linkedEnergyDim = null;
private BlockPos linkedChestPos = null;
private ResourceLocation linkedChestDim = null;
private int storedEnergy = 0;
```

Add deserialization in the `Settings(CompoundTag)` constructor, after the position handling (after line 93):

```java
if (settingsNbt.contains(KEY_LINKED_ENERGY, Tag.TAG_COMPOUND)) {
    CompoundTag energy = settingsNbt.getCompound(KEY_LINKED_ENERGY);
    this.linkedEnergyPos = new BlockPos(energy.getInt("x"), energy.getInt("y"), energy.getInt("z"));
    if (energy.contains(KEY_DIMENSION, Tag.TAG_STRING)) {
        this.linkedEnergyDim = ResourceLocation.parse(energy.getString(KEY_DIMENSION));
    }
}

if (settingsNbt.contains(KEY_LINKED_CHEST, Tag.TAG_COMPOUND)) {
    CompoundTag chest = settingsNbt.getCompound(KEY_LINKED_CHEST);
    this.linkedChestPos = new BlockPos(chest.getInt("x"), chest.getInt("y"), chest.getInt("z"));
    if (chest.contains(KEY_DIMENSION, Tag.TAG_STRING)) {
        this.linkedChestDim = ResourceLocation.parse(chest.getString(KEY_DIMENSION));
    }
}

this.storedEnergy = settingsNbt.getInt(KEY_STORED_ENERGY);
```

Add serialization in `toNbt()`, before the return statement (before line 204):

```java
if (this.linkedEnergyPos != null) {
    CompoundTag energy = new CompoundTag();
    energy.putInt("x", this.linkedEnergyPos.getX());
    energy.putInt("y", this.linkedEnergyPos.getY());
    energy.putInt("z", this.linkedEnergyPos.getZ());
    if (this.linkedEnergyDim != null) {
        energy.putString(KEY_DIMENSION, this.linkedEnergyDim.toString());
    }
    nbt.put(KEY_LINKED_ENERGY, energy);
}

if (this.linkedChestPos != null) {
    CompoundTag chest = new CompoundTag();
    chest.putInt("x", this.linkedChestPos.getX());
    chest.putInt("y", this.linkedChestPos.getY());
    chest.putInt("z", this.linkedChestPos.getZ());
    if (this.linkedChestDim != null) {
        chest.putString(KEY_DIMENSION, this.linkedChestDim.toString());
    }
    nbt.put(KEY_LINKED_CHEST, chest);
}

if (this.storedEnergy > 0) {
    nbt.putInt(KEY_STORED_ENERGY, this.storedEnergy);
}
```

Add getters/setters after line 181:

```java
@Nullable
public BlockPos getLinkedEnergyPos() { return this.linkedEnergyPos; }
public void setLinkedEnergyPos(@Nullable BlockPos pos) { this.linkedEnergyPos = pos; }

@Nullable
public ResourceLocation getLinkedEnergyDim() { return this.linkedEnergyDim; }
public void setLinkedEnergyDim(@Nullable ResourceLocation dim) { this.linkedEnergyDim = dim; }

@Nullable
public BlockPos getLinkedChestPos() { return this.linkedChestPos; }
public void setLinkedChestPos(@Nullable BlockPos pos) { this.linkedChestPos = pos; }

@Nullable
public ResourceLocation getLinkedChestDim() { return this.linkedChestDim; }
public void setLinkedChestDim(@Nullable ResourceLocation dim) { this.linkedChestDim = dim; }

public int getStoredEnergy() { return this.storedEnergy; }
public void setStoredEnergy(int energy) { this.storedEnergy = Math.max(0, energy); }

public void setLinkedEnergy(@Nullable BlockPos pos, @Nullable ResourceLocation dim) {
    this.linkedEnergyPos = pos;
    this.linkedEnergyDim = dim;
}

public void setLinkedChest(@Nullable BlockPos pos, @Nullable ResourceLocation dim) {
    this.linkedChestPos = pos;
    this.linkedChestDim = dim;
}
```

**Step 2: Compile check**

Run: `./gradlew compileJava`
Expected: SUCCESS

**Step 3: Commit**

```
feat: add linked energy/chest position and stored energy fields to Settings
```

---

### Task 7: Create MessageLinkBlock packet

**Files:**
- Create: `src/main/java/com/multiblockprojector/common/network/MessageLinkBlock.java`
- Modify: `src/main/java/com/multiblockprojector/common/network/NetworkHandler.java`

**Step 1: Create MessageLinkBlock**

```java
package com.multiblockprojector.common.network;

import com.multiblockprojector.common.items.AbstractProjectorItem;
import com.multiblockprojector.common.projector.Settings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.multiblockprojector.UniversalProjector.rl;

public class MessageLinkBlock implements CustomPacketPayload {

    public static final Type<MessageLinkBlock> TYPE = new Type<>(rl("link_block"));

    public static final StreamCodec<FriendlyByteBuf, MessageLinkBlock> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, p -> p.targetPos,
            ByteBufCodecs.idMapper(i -> InteractionHand.values()[i], Enum::ordinal), p -> p.hand,
            ByteBufCodecs.INT, p -> p.linkType,
            MessageLinkBlock::new
        );

    public static final int LINK_ENERGY = 0;
    public static final int LINK_CONTAINER = 1;

    private final BlockPos targetPos;
    private final InteractionHand hand;
    private final int linkType;

    public MessageLinkBlock(BlockPos targetPos, InteractionHand hand, int linkType) {
        this.targetPos = targetPos;
        this.hand = hand;
        this.linkType = linkType;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendToServer(BlockPos targetPos, InteractionHand hand, int linkType) {
        PacketDistributor.sendToServer(new MessageLinkBlock(targetPos, hand, linkType));
    }

    public static void handleServerSide(MessageLinkBlock packet, Player player) {
        ItemStack stack = player.getItemInHand(packet.hand);
        if (!(stack.getItem() instanceof AbstractProjectorItem)) return;

        Settings settings = AbstractProjectorItem.getSettings(stack);
        ResourceLocation dim = player.level().dimension().location();

        if (packet.linkType == LINK_ENERGY) {
            // Verify the target block has an energy capability
            BlockEntity be = player.level().getBlockEntity(packet.targetPos);
            if (be != null) {
                var energyCap = player.level().getCapability(Capabilities.EnergyStorage.BLOCK, packet.targetPos, null);
                if (energyCap != null) {
                    settings.setLinkedEnergy(packet.targetPos, dim);
                    settings.applyTo(stack);
                    player.displayClientMessage(
                        Component.literal("Linked to Energy Source at " +
                            packet.targetPos.getX() + ", " + packet.targetPos.getY() + ", " + packet.targetPos.getZ())
                            .withStyle(net.minecraft.ChatFormatting.GREEN),
                        false
                    );
                    return;
                }
            }
            player.displayClientMessage(
                Component.literal("No energy source found at that position")
                    .withStyle(net.minecraft.ChatFormatting.RED),
                false
            );
        } else if (packet.linkType == LINK_CONTAINER) {
            // Verify the target block has an item handler capability
            var itemCap = player.level().getCapability(Capabilities.ItemHandler.BLOCK, packet.targetPos, null);
            if (itemCap != null) {
                settings.setLinkedChest(packet.targetPos, dim);
                settings.applyTo(stack);
                player.displayClientMessage(
                    Component.literal("Linked to Container at " +
                        packet.targetPos.getX() + ", " + packet.targetPos.getY() + ", " + packet.targetPos.getZ())
                        .withStyle(net.minecraft.ChatFormatting.GREEN),
                    false
                );
            } else {
                player.displayClientMessage(
                    Component.literal("No container found at that position")
                        .withStyle(net.minecraft.ChatFormatting.RED),
                    false
                );
            }
        }
    }
}
```

**Step 2: Register in NetworkHandler**

Add after the existing `MessageAutoBuild` registration (after line 34):

```java
registrar.playToServer(
    MessageLinkBlock.TYPE,
    MessageLinkBlock.STREAM_CODEC,
    NetworkHandler::handleLinkBlockServerSide
);
```

Add handler method:

```java
private static void handleLinkBlockServerSide(MessageLinkBlock packet, IPayloadContext context) {
    context.enqueueWork(() -> {
        if (context.player() != null) {
            MessageLinkBlock.handleServerSide(packet, context.player());
        }
    });
}
```

Add import: `import com.multiblockprojector.common.network.MessageLinkBlock;`

**Step 3: Compile check**

Run: `./gradlew compileJava`
Expected: SUCCESS

**Step 4: Commit**

```
feat: add MessageLinkBlock packet for energy/chest linking
```

---

## Phase 4: Fabricator Items

### Task 8: Create FabricatorItem

**Files:**
- Create: `src/main/java/com/multiblockprojector/common/items/FabricatorItem.java`

**Step 1: Implement FabricatorItem**

This item extends `AbstractProjectorItem`. It overrides `use()` to handle sneak+right-click linking on blocks (before calling super), and `handleProjectionRightClick()` for sneak-cancel. The actual fabrication dispatch is handled by the client handler.

```java
package com.multiblockprojector.common.items;

import com.multiblockprojector.common.network.MessageLinkBlock;
import com.multiblockprojector.common.projector.Settings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;

import javax.annotation.Nonnull;
import java.util.List;

public class FabricatorItem extends AbstractProjectorItem {

    public FabricatorItem() {
        super();
    }

    @Override
    @Nonnull
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        // Sneak + right-click on a block: link energy or container
        if (player.isShiftKeyDown()) {
            BlockPos target = context.getClickedPos();
            Level level = context.getLevel();
            InteractionHand hand = context.getHand();

            if (!level.isClientSide) return InteractionResult.SUCCESS;

            // Check for energy capability first, then container
            var energyCap = level.getCapability(Capabilities.EnergyStorage.BLOCK, target, null);
            if (energyCap != null) {
                MessageLinkBlock.sendToServer(target, hand, MessageLinkBlock.LINK_ENERGY);
                return InteractionResult.SUCCESS;
            }

            var itemCap = level.getCapability(Capabilities.ItemHandler.BLOCK, target, null);
            if (itemCap != null) {
                MessageLinkBlock.sendToServer(target, hand, MessageLinkBlock.LINK_CONTAINER);
                return InteractionResult.SUCCESS;
            }

            // Not a valid target
            player.displayClientMessage(
                Component.literal("Not a valid energy source or container")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResult.FAIL;
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, TooltipContext ctx, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flagIn) {
        tooltip.add(Component.literal("Auto-builds multiblocks using FE and materials")
            .withStyle(ChatFormatting.AQUA));

        Settings settings = getSettings(stack);

        if (Screen.hasShiftDown()) {
            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("Linking:").withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.literal("Sneak + Right-click energy block to link power").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Sneak + Right-click container to link storage").withStyle(ChatFormatting.GRAY));

            if (settings.getLinkedEnergyPos() != null) {
                BlockPos p = settings.getLinkedEnergyPos();
                tooltip.add(Component.literal("Energy: " + p.getX() + ", " + p.getY() + ", " + p.getZ())
                    .withStyle(ChatFormatting.GREEN));
            } else {
                tooltip.add(Component.literal("Energy: Not linked").withStyle(ChatFormatting.RED));
            }

            if (settings.getLinkedChestPos() != null) {
                BlockPos p = settings.getLinkedChestPos();
                tooltip.add(Component.literal("Chest: " + p.getX() + ", " + p.getY() + ", " + p.getZ())
                    .withStyle(ChatFormatting.GREEN));
            } else {
                tooltip.add(Component.literal("Chest: Not linked").withStyle(ChatFormatting.RED));
            }

            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("Projection Mode:").withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.literal("Left-click to rotate 90 degrees").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Right-click to fabricate").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Hold Shift for instructions").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    protected void handleProjectionRightClick(Player player, Settings settings, ItemStack held, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            settings.setMode(Settings.Mode.NOTHING_SELECTED);
            settings.setMultiblock(null);
            settings.setPos(null);
            clearProjection(settings);
            settings.applyTo(held);
            settings.sendPacketToServer(hand);
            player.displayClientMessage(Component.literal("Projection cancelled"), true);
        }
        // Non-sneak right-click: fabrication is handled by ProjectorClientHandler dispatch
    }
}
```

**Step 2: Compile check**

Run: `./gradlew compileJava`
Expected: SUCCESS

**Step 3: Commit**

```
feat: add FabricatorItem with energy/chest linking
```

---

### Task 9: Create BatteryFabricatorItem with IEnergyStorage

**Files:**
- Create: `src/main/java/com/multiblockprojector/common/items/BatteryFabricatorItem.java`

**Step 1: Implement BatteryFabricatorItem**

Extends `FabricatorItem` (shares linking + tooltip logic). Overrides `useOn()` to only link containers (not energy). Adds energy bar rendering and IEnergyStorage capability.

```java
package com.multiblockprojector.common.items;

import com.multiblockprojector.common.network.MessageLinkBlock;
import com.multiblockprojector.common.projector.Settings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;

import javax.annotation.Nonnull;
import java.util.List;

public class BatteryFabricatorItem extends AbstractProjectorItem {

    public static final int MAX_ENERGY = 32_000_000;

    public BatteryFabricatorItem() {
        super();
    }

    @Override
    @Nonnull
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        // Sneak + right-click: only link containers (no energy linking — we have internal battery)
        if (player.isShiftKeyDown()) {
            BlockPos target = context.getClickedPos();
            Level level = context.getLevel();
            InteractionHand hand = context.getHand();

            if (!level.isClientSide) return InteractionResult.SUCCESS;

            var itemCap = level.getCapability(Capabilities.ItemHandler.BLOCK, target, null);
            if (itemCap != null) {
                MessageLinkBlock.sendToServer(target, hand, MessageLinkBlock.LINK_CONTAINER);
                return InteractionResult.SUCCESS;
            }

            player.displayClientMessage(
                Component.literal("Not a valid container")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResult.FAIL;
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, TooltipContext ctx, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flagIn) {
        tooltip.add(Component.literal("Auto-builds multiblocks using stored FE and materials")
            .withStyle(ChatFormatting.AQUA));

        Settings settings = getSettings(stack);
        int stored = settings.getStoredEnergy();

        tooltip.add(Component.literal("FE: " + formatEnergy(stored) + " / " + formatEnergy(MAX_ENERGY))
            .withStyle(stored > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));

        if (Screen.hasShiftDown()) {
            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("Linking:").withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.literal("Sneak + Right-click container to link storage").withStyle(ChatFormatting.GRAY));

            if (settings.getLinkedChestPos() != null) {
                BlockPos p = settings.getLinkedChestPos();
                tooltip.add(Component.literal("Chest: " + p.getX() + ", " + p.getY() + ", " + p.getZ())
                    .withStyle(ChatFormatting.GREEN));
            } else {
                tooltip.add(Component.literal("Chest: Not linked").withStyle(ChatFormatting.RED));
            }

            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("Projection Mode:").withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.literal("Left-click to rotate 90 degrees").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Right-click to fabricate").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Hold Shift for instructions").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    protected void handleProjectionRightClick(Player player, Settings settings, ItemStack held, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            settings.setMode(Settings.Mode.NOTHING_SELECTED);
            settings.setMultiblock(null);
            settings.setPos(null);
            clearProjection(settings);
            settings.applyTo(held);
            settings.sendPacketToServer(hand);
            player.displayClientMessage(Component.literal("Projection cancelled"), true);
        }
        // Non-sneak right-click: fabrication is handled by ProjectorClientHandler dispatch
    }

    @Override
    public boolean isBarVisible(@Nonnull ItemStack stack) {
        Settings settings = getSettings(stack);
        return settings.getStoredEnergy() > 0;
    }

    @Override
    public int getBarWidth(@Nonnull ItemStack stack) {
        Settings settings = getSettings(stack);
        return Math.round(13.0F * settings.getStoredEnergy() / (float) MAX_ENERGY);
    }

    @Override
    public int getBarColor(@Nonnull ItemStack stack) {
        Settings settings = getSettings(stack);
        float ratio = (float) settings.getStoredEnergy() / MAX_ENERGY;
        return Mth.hsvToRgb(0.0F, 0.0F, 0.5F + ratio * 0.5F); // White-ish energy bar
    }

    public static String formatEnergy(int energy) {
        if (energy >= 1_000_000) {
            return String.format("%.1fM", energy / 1_000_000.0);
        } else if (energy >= 1_000) {
            return String.format("%.1fK", energy / 1_000.0);
        }
        return String.valueOf(energy);
    }
}
```

**Step 2: Register IEnergyStorage capability**

This needs to be done in the mod's capability registration event. Create or modify the capability setup. Check if there's an existing event handler, or add one.

Add to `UniversalProjector.java` constructor (or create a new handler class). The simplest approach: add a `RegisterCapabilitiesEvent` handler. In `UniversalProjector.java`, add inside the constructor:

```java
modEventBus.addListener(this::registerCapabilities);
```

And add the method:

```java
private void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
    event.registerItem(
        Capabilities.EnergyStorage.ITEM,
        (stack, ctx) -> new BatteryFabricatorEnergyStorage(stack),
        UPContent.BATTERY_FABRICATOR.get()
    );
}
```

Create `src/main/java/com/multiblockprojector/common/items/BatteryFabricatorEnergyStorage.java`:

```java
package com.multiblockprojector.common.items;

import com.multiblockprojector.common.projector.Settings;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.minecraft.world.item.ItemStack;

public class BatteryFabricatorEnergyStorage implements IEnergyStorage {

    private final ItemStack stack;

    public BatteryFabricatorEnergyStorage(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        Settings settings = AbstractProjectorItem.getSettings(stack);
        int stored = settings.getStoredEnergy();
        int accepted = Math.min(BatteryFabricatorItem.MAX_ENERGY - stored, maxReceive);
        if (!simulate && accepted > 0) {
            settings.setStoredEnergy(stored + accepted);
            settings.applyTo(stack);
        }
        return accepted;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        Settings settings = AbstractProjectorItem.getSettings(stack);
        int stored = settings.getStoredEnergy();
        int extracted = Math.min(stored, maxExtract);
        if (!simulate && extracted > 0) {
            settings.setStoredEnergy(stored - extracted);
            settings.applyTo(stack);
        }
        return extracted;
    }

    @Override
    public int getEnergyStored() {
        return AbstractProjectorItem.getSettings(stack).getStoredEnergy();
    }

    @Override
    public int getMaxEnergyStored() {
        return BatteryFabricatorItem.MAX_ENERGY;
    }

    @Override
    public boolean canExtract() { return true; }

    @Override
    public boolean canReceive() { return true; }
}
```

**Step 3: Register both fabricator items in UPContent**

Add after the CREATIVE_PROJECTOR registration:

```java
import com.multiblockprojector.common.items.FabricatorItem;
import com.multiblockprojector.common.items.BatteryFabricatorItem;

public static final DeferredHolder<Item, FabricatorItem> FABRICATOR =
    ITEMS.register("fabricator", FabricatorItem::new);

public static final DeferredHolder<Item, BatteryFabricatorItem> BATTERY_FABRICATOR =
    ITEMS.register("battery_fabricator", BatteryFabricatorItem::new);
```

Update creative tab:

```java
.displayItems((params, output) -> {
    output.accept(PROJECTOR.get());
    output.accept(CREATIVE_PROJECTOR.get());
    output.accept(FABRICATOR.get());
    output.accept(BATTERY_FABRICATOR.get());
})
```

**Step 4: Add resources**

Create `src/main/resources/assets/multiblockprojector/models/item/fabricator.json` and `battery_fabricator.json` (same structure as creative_projector.json, referencing `multiblockprojector:item/projector` texture for now).

Add lang entries to `en_us.json`:

```json
"item.multiblockprojector.fabricator": "Multiblock Fabricator",
"item.multiblockprojector.fabricator.selecting": "Multiblock Fabricator (Selecting)",
"item.multiblockprojector.fabricator.projection_mode": "Multiblock Fabricator (Projecting)",
"item.multiblockprojector.fabricator.building_mode": "Multiblock Fabricator (Building)",

"item.multiblockprojector.battery_fabricator": "Battery Powered MBF",
"item.multiblockprojector.battery_fabricator.selecting": "Battery Powered MBF (Selecting)",
"item.multiblockprojector.battery_fabricator.projection_mode": "Battery Powered MBF (Projecting)",
"item.multiblockprojector.battery_fabricator.building_mode": "Battery Powered MBF (Building)"
```

**Step 5: Compile check**

Run: `./gradlew compileJava`
Expected: SUCCESS

**Step 6: Commit**

```
feat: add FabricatorItem, BatteryFabricatorItem with IEnergyStorage capability
```

---

## Phase 5: Fabrication System

### Task 10: Create FabricationTask and FabricationManager

**Files:**
- Create: `src/main/java/com/multiblockprojector/common/fabrication/FabricationTask.java`
- Create: `src/main/java/com/multiblockprojector/common/fabrication/FabricationManager.java`

**Step 1: Create FabricationTask**

```java
package com.multiblockprojector.common.fabrication;

import com.multiblockprojector.api.MultiblockDefinition;
import com.multiblockprojector.common.items.AbstractProjectorItem;
import com.multiblockprojector.common.network.MessageFabricationProgress;
import com.multiblockprojector.common.projector.MultiblockProjection;
import com.multiblockprojector.common.projector.Settings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class FabricationTask {

    private static final int TICKS_PER_BLOCK = 4;

    private final UUID playerId;
    private final Level level;
    private final BlockPos origin;
    private final InteractionHand hand;
    private final List<PlacementEntry> queue;
    private int currentIndex = 0;
    private int tickCounter = 0;
    private boolean completed = false;

    public record PlacementEntry(BlockPos worldPos, BlockState state) {}

    public FabricationTask(ServerPlayer player, Level level, BlockPos origin, InteractionHand hand,
                           MultiblockDefinition multiblock, Settings settings) {
        this.playerId = player.getUUID();
        this.level = level;
        this.origin = origin;
        this.hand = hand;

        // Build the placement queue from the projection
        var variant = MultiblockProjection.getVariantFromSettings(multiblock, settings);
        MultiblockProjection projection = new MultiblockProjection(level, multiblock, variant);
        projection.setRotation(settings.getRotation());
        projection.setFlip(settings.isMirrored());

        List<PlacementEntry> entries = new ArrayList<>();
        projection.processAll((layer, info) -> {
            BlockPos worldPos = origin.offset(info.tPos);
            BlockState targetState = info.getDisplayState(level, worldPos, 0);
            if (!targetState.isAir()) {
                entries.add(new PlacementEntry(worldPos, targetState));
            }
            return false;
        });

        // Sort bottom-to-top (ascending Y), then by X, then by Z within each layer
        entries.sort(Comparator.comparingInt((PlacementEntry e) -> e.worldPos.getY())
            .thenComparingInt(e -> e.worldPos.getX())
            .thenComparingInt(e -> e.worldPos.getZ()));

        this.queue = entries;
    }

    /**
     * Tick the task. Returns true if the task is complete.
     */
    public boolean tick(ServerPlayer player) {
        if (completed || currentIndex >= queue.size()) {
            complete(player);
            return true;
        }

        tickCounter++;
        if (tickCounter >= TICKS_PER_BLOCK) {
            tickCounter = 0;

            PlacementEntry entry = queue.get(currentIndex);
            if (level.isInWorldBounds(entry.worldPos) && level.getWorldBorder().isWithinBounds(entry.worldPos)) {
                level.setBlock(entry.worldPos, entry.state, 3);
                level.updateNeighborsAt(entry.worldPos, entry.state.getBlock());
            }
            currentIndex++;

            // Send progress update
            MessageFabricationProgress.sendToClient(player, currentIndex, queue.size());

            if (currentIndex >= queue.size()) {
                complete(player);
                return true;
            }
        }
        return false;
    }

    /**
     * Complete the task instantly (e.g., player logout).
     */
    public void completeInstantly() {
        for (int i = currentIndex; i < queue.size(); i++) {
            PlacementEntry entry = queue.get(i);
            if (level.isInWorldBounds(entry.worldPos) && level.getWorldBorder().isWithinBounds(entry.worldPos)) {
                level.setBlock(entry.worldPos, entry.state, 3);
                level.updateNeighborsAt(entry.worldPos, entry.state.getBlock());
            }
        }
        completed = true;
    }

    private void complete(ServerPlayer player) {
        if (!completed) {
            completed = true;

            // Reset the projector settings
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof AbstractProjectorItem) {
                Settings settings = AbstractProjectorItem.getSettings(stack);
                settings.setMode(Settings.Mode.NOTHING_SELECTED);
                settings.setPos(null);
                settings.setPlaced(false);
                settings.applyTo(stack);
            }

            player.displayClientMessage(
                Component.literal("Fabrication complete! Placed " + queue.size() + " blocks.")
                    .withStyle(net.minecraft.ChatFormatting.GREEN),
                true
            );
        }
    }

    public UUID getPlayerId() { return playerId; }
    public boolean isCompleted() { return completed; }
    public int getTotalBlocks() { return queue.size(); }
}
```

**Step 2: Create FabricationManager**

```java
package com.multiblockprojector.common.fabrication;

import com.multiblockprojector.UniversalProjector;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.*;

@EventBusSubscriber(modid = UniversalProjector.MODID)
public class FabricationManager {

    private static final Map<UUID, FabricationTask> ACTIVE_TASKS = new HashMap<>();

    public static void addTask(ServerPlayer player, FabricationTask task) {
        // Cancel any existing task for this player
        FabricationTask existing = ACTIVE_TASKS.get(player.getUUID());
        if (existing != null && !existing.isCompleted()) {
            existing.completeInstantly();
        }
        ACTIVE_TASKS.put(player.getUUID(), task);
    }

    public static boolean hasActiveTask(UUID playerId) {
        FabricationTask task = ACTIVE_TASKS.get(playerId);
        return task != null && !task.isCompleted();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE_TASKS.isEmpty()) return;

        Iterator<Map.Entry<UUID, FabricationTask>> it = ACTIVE_TASKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, FabricationTask> entry = it.next();
            FabricationTask task = entry.getValue();

            if (task.isCompleted()) {
                it.remove();
                continue;
            }

            // Find the player on the server
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                // Player disconnected — complete instantly
                task.completeInstantly();
                it.remove();
                continue;
            }

            boolean done = task.tick(player);
            if (done) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FabricationTask task = ACTIVE_TASKS.remove(event.getEntity().getUUID());
        if (task != null && !task.isCompleted()) {
            task.completeInstantly();
        }
    }

    public static void clearAll() {
        ACTIVE_TASKS.clear();
    }
}
```

**Step 3: Compile check**

Run: `./gradlew compileJava`
Expected: May fail because `MessageFabricationProgress` doesn't exist yet — create stub in next task.

**Step 4: Commit** (after Task 11 compiles)

---

### Task 11: Create MessageFabricate and MessageFabricationProgress packets

**Files:**
- Create: `src/main/java/com/multiblockprojector/common/network/MessageFabricate.java`
- Create: `src/main/java/com/multiblockprojector/common/network/MessageFabricationProgress.java`
- Modify: `src/main/java/com/multiblockprojector/common/network/NetworkHandler.java`

**Step 1: Create MessageFabricate**

This packet is sent client→server when a player right-clicks with a fabricator item in PROJECTION mode. The server handles pre-validation, resource reservation, and task creation.

```java
package com.multiblockprojector.common.network;

import com.multiblockprojector.api.BlockEntry;
import com.multiblockprojector.api.MultiblockDefinition;
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
import net.minecraft.server.level.ServerLevel;
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

        // Build block requirement list from projection
        var variant = MultiblockProjection.getVariantFromSettings(multiblock, settings);
        MultiblockProjection projection = new MultiblockProjection(level, multiblock, variant);
        projection.setRotation(settings.getRotation());
        projection.setFlip(settings.isMirrored());

        // Collect required blocks and calculate FE cost
        Map<net.minecraft.world.level.block.Block, Integer> requiredBlocks = new LinkedHashMap<>();
        int totalNonAir = 0;
        List<BlockState> blockStates = new ArrayList<>();

        projection.processAll((layerIdx, info) -> {
            BlockPos worldPos = packet.buildPos.offset(info.tPos);
            BlockState state = info.getDisplayState(level, worldPos, 0);
            if (!state.isAir()) {
                requiredBlocks.merge(state.getBlock(), 1, Integer::sum);
                blockStates.add(state);
            }
            return false;
        });
        totalNonAir = blockStates.size();

        // Calculate total FE cost
        double totalFE = 0;
        for (BlockState state : blockStates) {
            float hardness = Math.max(state.getDestroySpeed(level, BlockPos.ZERO), 0.1f);
            double perBlock = 800.0 * hardness * (1.0 + 0.0008 * totalNonAir);
            totalFE += perBlock;
        }
        int totalFENeeded = (int) Math.ceil(totalFE);

        // === PRE-VALIDATION ===

        // 1. Check FE
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

        // 2. Check blocks in inventory + linked chest
        Map<net.minecraft.world.level.block.Block, Integer> available = countAvailableBlocks(player, settings, level);
        List<String> missing = new ArrayList<>();
        for (var entry : requiredBlocks.entrySet()) {
            int have = available.getOrDefault(entry.getKey(), 0);
            if (have < entry.getValue()) {
                missing.add(entry.getKey().getName().getString() + " (" + have + "/" + entry.getValue() + ")");
            }
        }
        if (!missing.isEmpty()) {
            player.displayClientMessage(
                Component.literal("Missing blocks: " + String.join(", ", missing.subList(0, Math.min(3, missing.size()))))
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        // === RESOURCE RESERVATION ===

        // Extract FE
        energySource.extractEnergy(totalFENeeded, false);

        // Remove items from inventory + linked chest
        consumeBlocks(player, settings, level, requiredBlocks);

        // === CREATE FABRICATION TASK ===
        FabricationTask task = new FabricationTask(serverPlayer, level, packet.buildPos, packet.hand, multiblock, settings);
        FabricationManager.addTask(serverPlayer, task);

        player.displayClientMessage(
            Component.literal("Fabrication started! Building " + totalNonAir + " blocks...")
                .withStyle(ChatFormatting.GOLD), true);
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
```

**Step 2: Create MessageFabricationProgress**

```java
package com.multiblockprojector.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.multiblockprojector.UniversalProjector.rl;

public class MessageFabricationProgress implements CustomPacketPayload {

    public static final Type<MessageFabricationProgress> TYPE = new Type<>(rl("fabrication_progress"));

    public static final StreamCodec<FriendlyByteBuf, MessageFabricationProgress> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, p -> p.current,
            ByteBufCodecs.INT, p -> p.total,
            MessageFabricationProgress::new
        );

    private final int current;
    private final int total;

    public MessageFabricationProgress(int current, int total) {
        this.current = current;
        this.total = total;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void sendToClient(ServerPlayer player, int current, int total) {
        PacketDistributor.sendToPlayer(player, new MessageFabricationProgress(current, total));
    }

    public static void handleClientSide(MessageFabricationProgress packet, Player player) {
        player.displayClientMessage(
            Component.literal("Building... " + packet.current + "/" + packet.total + " blocks")
                .withStyle(net.minecraft.ChatFormatting.GOLD),
            true
        );
    }
}
```

**Step 3: Register packets in NetworkHandler**

Add after the existing registrations:

```java
registrar.playToServer(
    MessageFabricate.TYPE,
    MessageFabricate.STREAM_CODEC,
    NetworkHandler::handleFabricateServerSide
);

registrar.playToClient(
    MessageFabricationProgress.TYPE,
    MessageFabricationProgress.STREAM_CODEC,
    NetworkHandler::handleFabricationProgressClientSide
);
```

Add handler methods:

```java
private static void handleFabricateServerSide(MessageFabricate packet, IPayloadContext context) {
    context.enqueueWork(() -> {
        if (context.player() != null) {
            MessageFabricate.handleServerSide(packet, context.player());
        }
    });
}

private static void handleFabricationProgressClientSide(MessageFabricationProgress packet, IPayloadContext context) {
    context.enqueueWork(() -> {
        if (context.player() != null) {
            MessageFabricationProgress.handleClientSide(packet, context.player());
        }
    });
}
```

**Step 4: Compile check**

Run: `./gradlew compileJava`
Expected: SUCCESS

**Step 5: Commit**

```
feat: add fabrication system with pre-validation, resource reservation, and animated placement
```

---

### Task 12: Wire fabrication into ProjectorClientHandler

**Files:**
- Modify: `src/main/java/com/multiblockprojector/client/ProjectorClientHandler.java`

**Step 1: Update handleProjectionAction and add fabricateProjection**

Update the `handleProjectionAction` dispatch method to the full version:

```java
private static void handleProjectionAction(Player player, ItemStack held, Settings settings, BlockPos pos) {
    if (held.getItem() instanceof CreativeProjectorItem) {
        autoBuildProjection(player, settings, held, pos);
    } else if (held.getItem() instanceof FabricatorItem || held.getItem() instanceof BatteryFabricatorItem) {
        fabricateProjection(player, settings, held, pos);
    } else {
        placeProjection(player, settings, held, pos);
    }
}
```

Add the `fabricateProjection` method:

```java
private static void fabricateProjection(Player player, Settings settings, ItemStack held, BlockPos pos) {
    player.swing(InteractionHand.MAIN_HAND, true);

    // Send fabrication request to server
    MessageFabricate.sendToServer(pos, InteractionHand.MAIN_HAND);

    // Clear ghost projections — server will handle the animated placement
    if (lastAimPos != null) {
        ProjectionManager.removeProjection(lastAimPos);
        lastAimPos = null;
    }
}
```

Add imports:

```java
import com.multiblockprojector.common.items.CreativeProjectorItem;
import com.multiblockprojector.common.items.FabricatorItem;
import com.multiblockprojector.common.items.BatteryFabricatorItem;
import com.multiblockprojector.common.network.MessageFabricate;
```

**Step 2: Compile check**

Run: `./gradlew compileJava`
Expected: SUCCESS

**Step 3: Commit**

```
feat: wire fabrication dispatch into ProjectorClientHandler
```

---

## Phase 6: GUI Requirements Panel

### Task 13: Create RequirementsPanel widget

**Files:**
- Create: `src/main/java/com/multiblockprojector/client/gui/RequirementsPanel.java`

**Step 1: Implement RequirementsPanel**

A renderable widget that shows block requirements, FE status, and link info. Not an `AbstractWidget` — just a helper class that the screen calls `render()` on.

```java
package com.multiblockprojector.client.gui;

import com.multiblockprojector.api.MultiblockDefinition;
import com.multiblockprojector.common.items.AbstractProjectorItem;
import com.multiblockprojector.common.items.BatteryFabricatorItem;
import com.multiblockprojector.common.projector.MultiblockProjection;
import com.multiblockprojector.common.projector.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class RequirementsPanel {

    private final Font font;
    private List<BlockRequirement> requirements = List.of();
    private int totalFENeeded = 0;
    private int availableFE = 0;
    private boolean isBattery = false;
    private BlockPos linkedEnergyPos = null;
    private BlockPos linkedChestPos = null;

    public record BlockRequirement(Block block, String name, int needed, int have) {}

    public RequirementsPanel(Font font) {
        this.font = font;
    }

    public void update(MultiblockDefinition multiblock, int sizePresetIndex, ItemStack fabricatorStack) {
        Settings settings = AbstractProjectorItem.getSettings(fabricatorStack);
        isBattery = fabricatorStack.getItem() instanceof BatteryFabricatorItem;
        linkedEnergyPos = settings.getLinkedEnergyPos();
        linkedChestPos = settings.getLinkedChestPos();

        // Get selected variant
        var variant = multiblock.variants().get(Math.min(sizePresetIndex, multiblock.variants().size() - 1));
        var structure = multiblock.structureProvider().create(variant, null);

        // Count required blocks
        Map<Block, Integer> required = new LinkedHashMap<>();
        int totalNonAir = 0;
        List<Float> hardnesses = new ArrayList<>();

        for (var entry : structure.blocks().entrySet()) {
            BlockState display = entry.getValue().displayState(0);
            if (!display.isAir()) {
                required.merge(display.getBlock(), 1, Integer::sum);
                totalNonAir++;
                hardnesses.add(Math.max(display.getDestroySpeed(null, BlockPos.ZERO), 0.1f));
            }
        }

        // Calculate FE cost
        double totalFE = 0;
        for (float hardness : hardnesses) {
            totalFE += 800.0 * hardness * (1.0 + 0.0008 * totalNonAir);
        }
        this.totalFENeeded = (int) Math.ceil(totalFE);

        // Count available blocks from player inventory
        // (Client-side: we can only check local player inventory, not linked chest)
        Map<Block, Integer> available = new HashMap<>();
        var player = Minecraft.getInstance().player;
        if (player != null) {
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack invStack = inv.getItem(i);
                if (!invStack.isEmpty() && invStack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
                    available.merge(blockItem.getBlock(), invStack.getCount(), Integer::sum);
                }
            }
        }

        // Get available FE
        if (isBattery) {
            this.availableFE = settings.getStoredEnergy();
        } else {
            // Can't check linked energy source from client — show "Linked" status instead
            this.availableFE = -1; // -1 = unknown (linked source)
        }

        // Build requirements list
        List<BlockRequirement> reqs = new ArrayList<>();
        for (var entry : required.entrySet()) {
            String name = entry.getKey().getName().getString();
            int have = available.getOrDefault(entry.getKey(), 0);
            reqs.add(new BlockRequirement(entry.getKey(), name, entry.getValue(), have));
        }
        this.requirements = reqs;
    }

    public void clear() {
        this.requirements = List.of();
        this.totalFENeeded = 0;
        this.availableFE = 0;
    }

    /**
     * Render the requirements panel.
     * @return the total height used for rendering
     */
    public int render(GuiGraphics graphics, int x, int y, int width, int maxHeight) {
        if (requirements.isEmpty()) return 0;

        int currentY = y;
        int lineHeight = 12;

        // Title
        graphics.drawString(font, "Requirements:", x + 4, currentY, 0xFFFFFF);
        currentY += lineHeight + 2;

        // Block requirements (scrollable within available space)
        int maxBlockLines = (maxHeight - 50) / lineHeight; // Reserve space for FE and link info
        int shown = 0;
        for (BlockRequirement req : requirements) {
            if (shown >= maxBlockLines) break;

            boolean sufficient = req.have >= req.needed;
            int color = sufficient ? 0x55FF55 : 0xFF5555;
            String icon = sufficient ? "\u2713" : "\u2717"; // checkmark or x
            String text = icon + " " + req.name;
            String count = req.have + "/" + req.needed;

            graphics.drawString(font, text, x + 6, currentY, color);
            graphics.drawString(font, count, x + width - font.width(count) - 6, currentY, color);
            currentY += lineHeight;
            shown++;
        }

        if (shown < requirements.size()) {
            graphics.drawString(font, "+" + (requirements.size() - shown) + " more...", x + 6, currentY, 0x888888);
            currentY += lineHeight;
        }

        currentY += 4;

        // FE line
        String feText;
        int feColor;
        if (isBattery) {
            boolean sufficient = availableFE >= totalFENeeded;
            feColor = sufficient ? 0x55FF55 : 0xFF5555;
            feText = "FE: " + BatteryFabricatorItem.formatEnergy(availableFE) + " / " +
                     BatteryFabricatorItem.formatEnergy(totalFENeeded) + " needed";
        } else if (availableFE == -1) {
            feText = "FE: " + BatteryFabricatorItem.formatEnergy(totalFENeeded) + " needed";
            feColor = linkedEnergyPos != null ? 0xFFFF55 : 0xFF5555;
        } else {
            boolean sufficient = availableFE >= totalFENeeded;
            feColor = sufficient ? 0x55FF55 : 0xFF5555;
            feText = "FE: " + BatteryFabricatorItem.formatEnergy(availableFE) + " / " +
                     BatteryFabricatorItem.formatEnergy(totalFENeeded) + " needed";
        }
        graphics.drawString(font, feText, x + 4, currentY, feColor);
        currentY += lineHeight;

        // Energy source line
        if (isBattery) {
            graphics.drawString(font, "Internal: " + BatteryFabricatorItem.formatEnergy(availableFE) + " FE",
                x + 4, currentY, 0xAAAAFF);
        } else if (linkedEnergyPos != null) {
            graphics.drawString(font, "Energy: (" + linkedEnergyPos.getX() + ", " +
                linkedEnergyPos.getY() + ", " + linkedEnergyPos.getZ() + ")",
                x + 4, currentY, 0x55FF55);
        } else {
            graphics.drawString(font, "Energy: Not linked", x + 4, currentY, 0xFF5555);
        }
        currentY += lineHeight;

        // Chest line
        if (linkedChestPos != null) {
            graphics.drawString(font, "Chest: (" + linkedChestPos.getX() + ", " +
                linkedChestPos.getY() + ", " + linkedChestPos.getZ() + ")",
                x + 4, currentY, 0x55FF55);
        } else {
            graphics.drawString(font, "Chest: Not linked", x + 4, currentY, 0xFF5555);
        }
        currentY += lineHeight;

        return currentY - y;
    }

    public boolean hasRequirements() {
        return !requirements.isEmpty();
    }
}
```

**Step 2: Compile check**

Run: `./gradlew compileJava`
Expected: SUCCESS

**Step 3: Commit**

```
feat: add RequirementsPanel GUI widget for fabricator items
```

---

### Task 14: Integrate RequirementsPanel into ProjectorScreen

**Files:**
- Modify: `src/main/java/com/multiblockprojector/client/gui/ProjectorScreen.java`

**Step 1: Add RequirementsPanel field and item type awareness**

Add to the fields section (after line 62):

```java
private RequirementsPanel requirementsPanel;
private final boolean isFabricator;
```

Update constructor to detect item type and initialize panel:

In the constructor (line 64), after `this.previewRenderer = ...`:

```java
this.isFabricator = projectorStack.getItem() instanceof FabricatorItem
    || projectorStack.getItem() instanceof BatteryFabricatorItem;
if (isFabricator) {
    this.requirementsPanel = new RequirementsPanel(Minecraft.getInstance().font);
}
```

Add imports:

```java
import com.multiblockprojector.common.items.FabricatorItem;
import com.multiblockprojector.common.items.BatteryFabricatorItem;
```

**Step 2: Adjust list height when requirements panel is visible**

In `init()`, modify the list height calculation. Currently (lines 127-129):

```java
listStartY = TAB_SELECTOR_Y + TAB_SELECTOR_HEIGHT + 4;
int selectButtonY = this.height - 30;
int listHeight = selectButtonY - listStartY - 6;
```

Change to:

```java
listStartY = TAB_SELECTOR_Y + TAB_SELECTOR_HEIGHT + 4;
int selectButtonY = this.height - 30;
int requirementsPanelHeight = (isFabricator && selectedMultiblock != null) ? 140 : 0;
int listHeight = selectButtonY - listStartY - 6 - requirementsPanelHeight;
```

Store `requirementsPanelHeight` and `selectButtonY` as instance fields so `render()` can use them:

```java
private int selectButtonY;
private int requirementsPanelHeight;
```

**Step 3: Update requirements panel when multiblock selection changes**

In `selectMultiblockForPreview()` (line 219), add at the end (before the closing brace):

```java
if (isFabricator && requirementsPanel != null) {
    requirementsPanel.update(multiblock, currentSizePresetIndex, projectorStack);
    rebuildWidgets(); // Rebuild to adjust list height
}
```

In `updatePreviewWithSize()` (line 214), also update requirements:

```java
private void updatePreviewWithSize(MultiblockDefinition multiblock) {
    var variant = multiblock.variants().get(currentSizePresetIndex);
    previewRenderer.setMultiblock(multiblock, variant);
    if (isFabricator && requirementsPanel != null) {
        requirementsPanel.update(multiblock, currentSizePresetIndex, projectorStack);
    }
}
```

In `selectTab()` (line 99), clear requirements when tab changes:

```java
private void selectTab(String tabId) {
    selectedTab = tabId;
    lastSelectedTab = tabId;
    updateFilteredMultiblocks();
    selectedMultiblock = null;
    previewRenderer.setMultiblock(null);
    if (requirementsPanel != null) requirementsPanel.clear();
    rebuildWidgets();
}
```

**Step 4: Render the requirements panel**

In `render()`, after rendering the list and before the preview rendering (after `super.render()` at line 282), add:

```java
// Render requirements panel for fabricator items
if (isFabricator && requirementsPanel != null && requirementsPanel.hasRequirements()) {
    int reqY = listStartY + multiblockList.getHeight() + 4;
    int reqWidth = leftPanelWidth - MARGIN * 2;
    int reqMaxHeight = selectButtonY - reqY - 4;
    requirementsPanel.render(guiGraphics, MARGIN, reqY, reqWidth, reqMaxHeight);
}
```

**Step 5: Compile check**

Run: `./gradlew compileJava`
Expected: SUCCESS

**Step 6: Commit**

```
feat: integrate RequirementsPanel into ProjectorScreen for fabricator items
```

---

## Phase 7: Final Polish

### Task 15: Add all remaining lang entries and compile

**Files:**
- Modify: `src/main/resources/assets/multiblockprojector/lang/en_us.json`

**Step 1: Add all new lang entries**

Ensure all new item names, states, and GUI strings are present. Review and add any that were missed in earlier tasks.

**Step 2: Full build**

Run: `./gradlew build`
Expected: SUCCESS — full build including resource processing.

**Step 3: Commit**

```
chore: add remaining lang entries and finalize build
```

---

### Task 16: In-game verification

**Step 1: Run the development client**

Run: `./gradlew runClient`

**Step 2: Verify checklist**

- [ ] All four items appear in creative tab
- [ ] Base Projector: right-click opens GUI, left-click rotates in projection mode, right-click places projection
- [ ] Base Projector: no auto-build capability in creative or survival
- [ ] Creative Projector: right-click in projection mode instantly builds
- [ ] Fabricator: sneak+right-click on energy block shows "Linked to Energy Source"
- [ ] Fabricator: sneak+right-click on chest shows "Linked to Container"
- [ ] Battery MBF: has FE bar, charges on Mekanism chargepad
- [ ] Battery MBF: sneak+right-click on chest links container, sneak+right-click on energy block does nothing
- [ ] Requirements panel shows when multiblock selected with a fabricator
- [ ] Fabrication pre-validates (fails with message if missing blocks/FE)
- [ ] Animated placement works (blocks appear one-by-one bottom-to-top)
- [ ] Progress bar shows "Building... X/Y blocks"
- [ ] ESC cancels projection for all items

**Step 3: Fix any issues found**

**Step 4: Final commit**

```
test: verify all four projector items in-game
```

---

## Summary

| Phase | Tasks | New Files | Modified Files |
|-------|-------|-----------|---------------|
| 1. Refactor Base | 1-3 | `AbstractProjectorItem.java` | `ProjectorItem.java`, `ProjectorClientHandler.java`, `en_us.json` |
| 2. Creative Projector | 4-5 | `CreativeProjectorItem.java`, `creative_projector.json` | `UPContent.java`, `ProjectorClientHandler.java`, `MessageAutoBuild.java`, `en_us.json` |
| 3. Settings + Link | 6-7 | `MessageLinkBlock.java` | `Settings.java`, `NetworkHandler.java` |
| 4. Fabricator Items | 8-9 | `FabricatorItem.java`, `BatteryFabricatorItem.java`, `BatteryFabricatorEnergyStorage.java`, model JSONs | `UPContent.java`, `UniversalProjector.java`, `en_us.json` |
| 5. Fabrication System | 10-12 | `FabricationTask.java`, `FabricationManager.java`, `MessageFabricate.java`, `MessageFabricationProgress.java` | `NetworkHandler.java`, `ProjectorClientHandler.java` |
| 6. GUI Panel | 13-14 | `RequirementsPanel.java` | `ProjectorScreen.java` |
| 7. Polish | 15-16 | — | `en_us.json` |

Total: **16 tasks**, **~12 new files**, **~8 modified files**, **7 commits minimum**
