package twilightforest.world.components.structures.courtyard;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.feature.NoiseEffect;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import twilightforest.TwilightForestMod;
import twilightforest.world.components.processors.MossyCobbleTemplateProcessor;
import twilightforest.world.components.structures.TwilightDoubleTemplateStructurePiece;

import java.util.Random;

public class CourtyardWallPadder extends TwilightDoubleTemplateStructurePiece {
    public CourtyardWallPadder(ServerLevel level, CompoundTag nbt) {
        super(NagaCourtyardPieces.TFNCWP,
                nbt,
                level,
                readSettings(nbt).addProcessor(CourtyardMain.WALL_PROCESSOR).addProcessor(CourtyardMain.WALL_INTEGRITY_PROCESSOR).addProcessor(BlockIgnoreProcessor.AIR),
                readSettings(nbt).addProcessor(MossyCobbleTemplateProcessor.INSTANCE).addProcessor(CourtyardMain.WALL_DECAY_PROCESSOR)
        );
    }

    public CourtyardWallPadder(int i, int x, int y, int z, Rotation rotation, StructureManager structureManager) {
        super(NagaCourtyardPieces.TFNCWP,
                i,
                structureManager,
                TwilightForestMod.prefix("courtyard/courtyard_wall_padding"),
                makeSettings(rotation).addProcessor(CourtyardMain.WALL_PROCESSOR).addProcessor(CourtyardMain.WALL_INTEGRITY_PROCESSOR).addProcessor(BlockIgnoreProcessor.AIR),
                TwilightForestMod.prefix("courtyard/courtyard_wall_padding_decayed"),
                makeSettings(rotation).addProcessor(MossyCobbleTemplateProcessor.INSTANCE).addProcessor(CourtyardMain.WALL_DECAY_PROCESSOR),
                new BlockPos(x, y, z)
        );
    }

    @Override
    protected void handleDataMarker(String label, BlockPos pos, ServerLevelAccessor levelAccessor, Random random, BoundingBox boundingBox) {

    }

    @Override
    public NoiseEffect getNoiseEffect() {
        return NoiseEffect.BEARD;
    }
}