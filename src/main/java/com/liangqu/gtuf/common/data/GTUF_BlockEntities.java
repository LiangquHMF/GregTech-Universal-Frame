package com.liangqu.gtuf.common.data;

import com.liangqu.gtuf.common.blockentity.PressurePipeBlockEntity;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;

import static com.liangqu.gtuf.api.registry.GTUF_Registries.REGISTRATE;

/**
 * GTUF 方块实体注册表。
 *
 * <p>
 * 压力管道方块实体经 {@link #register()} 在 {@code GTUF_PressureBlocks.generate()} 建好
 * 方块表后注册（validBlocks 需要已生成的方块表），故不在静态初始化时注册，而是由
 * {@code CommonProxy.modifyMaterials(PostMaterialEvent)} 触发生成后调用。
 * </p>
 */
public final class GTUF_BlockEntities {

    private GTUF_BlockEntities() {}

    /** 压力管道方块实体。 */
    public static BlockEntityEntry<PressurePipeBlockEntity> PRESSURE_PIPE;

    @SuppressWarnings("unchecked")
    public static void register() {
        PRESSURE_PIPE = REGISTRATE
                .blockEntity("pressure_pipe", PressurePipeBlockEntity::new)
                .validBlocks(GTUF_PressureBlocks.PRESSURE_PIPE_BLOCKS.values().toArray(BlockEntry[]::new))
                .register();
    }
}
