package dev.tempest.render;

import dev.tempest.modules.LowFireModule;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Ground part of Low Fire. Wraps the fire / soul fire block models (installed in TempestClient through Fabric's
 * model-loading API) and squashes their quads toward the ground while chunks are built. Fabric's renderer and
 * Sodium both build terrain through emitQuads, so this works with and without Sodium.
 */
public final class LowFireModel implements BlockStateModel {
    private final BlockStateModel wrapped;

    public LowFireModel(BlockStateModel wrapped) { this.wrapped = wrapped; }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockRenderView blockView, BlockPos pos, BlockState state, Random random,
                          Predicate<Direction> cullTest) {
        float h = LowFireModule.groundHeight();
        if (h <= 0f) return;                                        // 0 %: no fire blocks at all
        if (h >= 1f || LowFireModule.MATRIX_SCALED.get()) {         // vanilla height, or already squashed by the block mesher hook
            wrapped.emitQuads(emitter, blockView, pos, state, random, cullTest);
            return;
        }
        emitter.pushTransform(quad -> {
            for (int i = 0; i < 4; i++) quad.pos(i, quad.x(i), quad.y(i) * h, quad.z(i));
            return true;
        });
        try {
            wrapped.emitQuads(emitter, blockView, pos, state, random, cullTest);
        } finally {
            emitter.popTransform();
        }
    }

    @Override
    public @Nullable Object createGeometryKey(BlockRenderView blockView, BlockPos pos, BlockState state, Random random) {
        return null;   // geometry depends on the slider: never cache it
    }

    @Override
    public Sprite particleSprite(BlockRenderView blockView, BlockPos pos, BlockState state) {
        return wrapped.particleSprite(blockView, pos, state);
    }

    // vanilla path (used where nothing calls emitQuads): unchanged parts; BlockRenderManagerMixin squashes those
    @Override public void addParts(Random random, List<BlockModelPart> parts) { wrapped.addParts(random, parts); }
    @Override public Sprite particleSprite() { return wrapped.particleSprite(); }
}
