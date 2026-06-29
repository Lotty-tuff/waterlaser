package com.example.waterlaser;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

public class WaterLaserClient implements ClientModInitializer {
    public static final String MOD_ID = "waterlaser";

    // Toggle state. volatile because the render thread reads it.
    private static volatile boolean enabled = WaterLaserConfig.ENABLED_BY_DEFAULT;

    // The water-surface positions to draw beams above. Always an immutable list,
    // swapped by reference, so the render thread can read it without locking.
    private static volatile List<BlockPos> beamPositions = List.of();

    private static KeyMapping toggleKey;

    private int tickCounter = 0;

    @Override
    public void onInitializeClient() {
        // 1. Register a category + key bind so it shows up in Options > Controls.
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "main")
        );

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.waterlaser.toggle",     // translation key (see lang/en_us.json)
                InputConstants.Type.KEYSYM,  // keyboard key
                GLFW.GLFW_KEY_L,             // default: L (unbound-friendly, change in-game)
                category
        ));

        // 2. Hook the world renderer.
        LaserRenderer.getInstance().register();

        // 3. Handle the toggle + periodically rescan for water each client tick.
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(Minecraft client) {
        while (toggleKey.consumeClick()) {
            enabled = !enabled;
            if (client.player != null) {
                client.player.displayClientMessage(
                        Component.literal("Water lasers: " + (enabled ? "ON" : "OFF")),
                        true // action-bar message
                );
            }
        }

        if (!enabled) {
            // Drop cached positions so nothing renders and the next enable rescans.
            if (!beamPositions.isEmpty()) {
                beamPositions = List.of();
            }
            return;
        }

        if (++tickCounter >= WaterLaserConfig.SCAN_INTERVAL_TICKS) {
            tickCounter = 0;
            scanForWater(client);
        }
    }

    /**
     * Walks every column within HORIZONTAL_RADIUS of the player and records the
     * highest water block in each column (so deep oceans give one beam per column,
     * not one per water block). Runs on the client thread.
     */
    private void scanForWater(Minecraft client) {
        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        if (level == null || player == null) {
            beamPositions = List.of();
            return;
        }

        BlockPos origin = player.blockPosition();
        int radius = WaterLaserConfig.HORIZONTAL_RADIUS;
        int yTop = origin.getY() + WaterLaserConfig.VERTICAL_UP;
        int yBottom = origin.getY() - WaterLaserConfig.VERTICAL_DOWN;

        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        outer:
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;

                // Scan top-down; first water hit is the surface of this column.
                for (int y = yTop; y >= yBottom; y--) {
                    cursor.set(x, y, z);
                    if (level.getBlockState(cursor).getFluidState().is(FluidTags.WATER)) {
                        found.add(new BlockPos(x, y, z));
                        break;
                    }
                }

                if (found.size() >= WaterLaserConfig.MAX_BEAMS) {
                    break outer;
                }
            }
        }

        beamPositions = List.copyOf(found);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static List<BlockPos> getBeamPositions() {
        return beamPositions;
    }
}
