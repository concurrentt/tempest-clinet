package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CoordinatesModule extends HudModule {
    private final BoolSetting direction = add(new BoolSetting("direction", "Facing", true));
    private final BoolSetting biome = add(new BoolSetting("biome", "Biome", true));
    private final BoolSetting converted = add(new BoolSetting("converted", "Nether/Overworld Coords", true));

    public CoordinatesModule() { super("coordinates", "Coordinates", "XYZ, facing, biome and converted coords.", Category.HUD, false, 6, 330); }

    @Override
    protected void renderHud(DrawContext ctx, boolean preview) {
        MinecraftClient c = MinecraftClient.getInstance();
        List<String> lines = new ArrayList<>();
        if (preview || c.player == null || c.world == null) {
            lines.add("XYZ: 120 64 -300");
            if (direction.get()) lines.add("Facing: North");
            if (biome.get()) lines.add("Biome: Plains");
            if (converted.get()) lines.add("Nether: 15 -37");
        } else if (StreamerModeModule.hideCoords()) {
            lines.add("XYZ: hidden");
        } else {
            BlockPos p = c.player.getBlockPos();
            lines.add("XYZ: " + p.getX() + " " + p.getY() + " " + p.getZ());
            if (direction.get()) lines.add("Facing: " + cap(c.player.getHorizontalFacing().asString()));
            if (biome.get()) {
                String b = c.world.getBiome(p).getKey().map(k -> pretty(k.getValue().getPath())).orElse("Unknown");
                lines.add("Biome: " + b);
            }
            if (converted.get()) {
                if (c.world.getRegistryKey() == World.OVERWORLD) lines.add(String.format(Locale.ROOT, "Nether: %d %d", p.getX() / 8, p.getZ() / 8));
                else if (c.world.getRegistryKey() == World.NETHER) lines.add(String.format(Locale.ROOT, "Overworld: %d %d", p.getX() * 8, p.getZ() * 8));
            }
        }
        drawLines(ctx, lines);
    }

    private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    private static String pretty(String path) {
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(cap(part));
        }
        return sb.toString();
    }
}
