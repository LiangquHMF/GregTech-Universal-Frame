package com.liangqu.gtuf.api.pipelike.pressurepipe;

import com.gregtechceu.gtceu.api.pipenet.LevelPipeNet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import com.liangqu.gtuf.api.data.material.PressurePipeProperties;

/**
 * 服务器等级（Level）的压力管道网络存储：管理当前世界全部 {@link PressurePipeNet}。
 *
 * <p>
 * SavedData 键为 {@code "gtuf_pressure_pipe_net"}。管道方块放置/移除经
 * {@code PipeBlock.onPlace}/{@code onRemove} 调用本类 {@link #addNode}/{@link #removeNode}
 * 自动维护网络（GTUF 不直接调用，走继承路径）。压力仓经
 * {@link #getNetFromPos(net.minecraft.core.BlockPos)} 查询相邻管道所在网络。
 * </p>
 */
public class LevelPressurePipeNet extends LevelPipeNet<PressurePipeProperties, PressurePipeNet> {

    /** SavedData 键。 */
    public static final String DATA_KEY = "gtuf_pressure_pipe_net";

    public static LevelPressurePipeNet getOrCreate(ServerLevel serverLevel) {
        return serverLevel.getDataStorage().computeIfAbsent(tag -> new LevelPressurePipeNet(serverLevel, tag),
                () -> new LevelPressurePipeNet(serverLevel), DATA_KEY);
    }

    public LevelPressurePipeNet(ServerLevel serverLevel) {
        super(serverLevel);
    }

    public LevelPressurePipeNet(ServerLevel serverLevel, CompoundTag tag) {
        super(serverLevel, tag);
    }

    @Override
    protected PressurePipeNet createNetInstance() {
        return new PressurePipeNet(this);
    }
}
