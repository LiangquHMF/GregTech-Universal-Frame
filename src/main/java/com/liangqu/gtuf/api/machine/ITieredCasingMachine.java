package com.liangqu.gtuf.api.machine;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;

import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

/**
 * 可替换外壳的多方块机器（控制器）通用接口。
 *
 * <p>框架级渲染器
 * {@link com.liangqu.gtuf.client.renderer.machine.GTUFTieredPartRender} 只依赖本接口：
 * 成型后把<b>控制器自身</b>与<b>仓室/总线部件</b>都渲染成 {@link #getCasingState()} 返回的
 * 实际外壳方块状态，从而让材质与结构真实使用的外壳一致（不限于蒸汽机的青铜/脱氧钢两级）。</p>
 *
 * <p>整合包作者实现本接口即可为自己的多方块机器获得"随外壳等级换材质"的能力，
 * 例如电力多方块结构按电压等级返回对应机壳方块：
 * <pre>{@code
 * class MyMachine extends ElectricMultiblockMachine implements ITieredCasingMachine {
 *     @Persisted @DescSynced @RequireRerender private int voltageTier = 1; // LV 起
 *
 *     @Override public int getCasingTier() { return voltageTier; }
 *
 *     @Override public @Nullable BlockState getCasingState(int tier) {
 *         return tier >= 2 ? GTBlocks.CASING_ALUMINIUM_SOLID.get().defaultBlockState()
 *                          : GTBlocks.CASING_STEEL_SOLID.get().defaultBlockState();
 *     }
 * }
 * }</pre>
 * 返回 null 表示该等级不参与外壳替换（控制器显示注册时的 base 模型、部件不替换）。</p>
 *
 * <p>等级字段须标 {@code @Persisted @DescSynced @RequireRerender} 同步到客户端（渲染在客户端进行）。</p>
 */
public interface ITieredCasingMachine extends IMultiController {

    /**
     * 当前外壳等级（1 起）。默认 1；子类覆盖为结构成型时从 MatchContext 读到的实际等级。
     */
    default int getCasingTier() {
        return 1;
    }

    /**
     * 指定等级对应的外壳方块状态；null = 该等级不参与外壳替换。
     *
     * @param tier 外壳等级（1 起）
     */
    @Nullable
    BlockState getCasingState(int tier);

    /**
     * 当前外壳方块状态（{@code getCasingState(getCasingTier())} 的便捷方法）。
     */
    @Nullable
    default BlockState getCasingState() {
        return getCasingState(getCasingTier());
    }
}
