package com.liangqu.gtuf.api.machine;

import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import com.liangqu.gtuf.api.pattern.GTUF_PatternPredicates;
import org.jetbrains.annotations.Nullable;

/**
 * 可替换外壳的多方块机器（控制器）通用接口。
 *
 * <p>
 * 框架级渲染器
 * {@link com.liangqu.gtuf.client.renderer.machine.GTUFTieredPartRender} 只依赖本接口，
 * 把<b>控制器外观</b>与<b>仓室外壳</b>两条路径解耦：
 * </p>
 * <ul>
 * <li><b>仓室/总线部件</b>：渲染成 {@link #getCasingState()} 返回的结构实际外壳方块
 * （结构铺什么方块、部件就渲染成什么方块）；</li>
 * <li><b>控制器自身</b>：渲染成 {@link #getControllerAppearanceState()} 返回的注册外观方块
 * （{@code appearanceBlock}），不被结构外壳覆盖。</li>
 * </ul>
 *
 * <p>
 * 结构实际外壳由 {@link GTUF_PatternPredicates#Casing} 谓词在成型时写入 MatchContext，
 * 实现类通过 {@link #getStructureCasingId()}/{@link #setStructureCasingId(String)} 持有该注册名，
 * 在 {@code onStructureFormed} 调用 {@link #readStructureCasing()}、
 * {@code onStructureInvalid} 调用 {@link #resetStructureCasing()} 完成生命周期同步。
 * 外壳/部件外观与控制器外观的取用逻辑（{@link #getCasingState()}/{@link #getPartAppearance}）
 * 已在本接口提供默认实现，实现类只需维护等级与外壳注册名字段。
 * </p>
 *
 * <p>
 * 整合包作者实现本接口即可为自己的多方块机器获得"随外壳等级换材质"的能力，
 * 例如电力多方块结构按电压等级返回对应机壳方块：
 *
 * <pre>
 *
 * {
 *     &#64;code
 *     class MyMachine extends ElectricMultiblockMachine implements ITieredCasingMachine {
 *
 *         &#64;Persisted
 *         &#64;DescSynced
 *         &#64;RequireRerender
 *         private int voltageTier = 1; // LV 起
 *
 *         &#64;Override
 *         public int getCasingTier() {
 *             return voltageTier;
 *         }
 *
 *         @Override
 *         public @Nullable BlockState getCasingState(int tier) {
 *             return tier >= 2 ? GTBlocks.CASING_ALUMINIUM_SOLID.get().defaultBlockState() :
 *                     GTBlocks.CASING_STEEL_SOLID.get().defaultBlockState();
 *         }
 *     }
 * }
 * </pre>
 *
 * 返回 null 表示该等级不参与外壳替换（控制器显示注册时的 base 模型、部件不替换）。
 * </p>
 *
 * <p>
 * 等级字段须标 {@code @Persisted @DescSynced @RequireRerender} 同步到客户端（渲染在客户端进行）。
 * </p>
 */
public interface ITieredCasingMachine extends IMultiController {

    /**
     * 当前外壳等级（1 起）。默认 1；子类覆盖为结构成型时从 MatchContext 读到的实际等级。
     *
     * <p>
     * 约定返回 <b>0 = 未解析</b>（结构未使用等级外壳系统）：仓室回退到注册
     * {@code appearanceBlock}（{@code SteamMultiBlockBase} 即此行为），
     * 适用于 KJS 直接以普通机壳方块铺结构、靠外观方块定材质的机器。
     * </p>
     */
    default int getCasingTier() {
        return 1;
    }

    /**
     * 指定等级对应的外壳方块状态；null = 该等级不参与外壳替换。
     * 实现方应对 {@code tier <= 0}（未解析）返回外观方块
     * （{@code getDefinition().getAppearance().get()}）。
     *
     * @param tier 外壳等级（1 起；0 = 未解析）
     */
    @Nullable
    BlockState getCasingState(int tier);

    /**
     * 结构实际使用的外壳方块注册名（由 {@link GTUF_PatternPredicates#Casing} 写入
     * MatchContext、成型时读取）。实现类以 {@code @Persisted @DescSynced @RequireRerender}
     * 字段持有，在 {@code onStructureFormed}/{@code onStructureInvalid} 里分别调用
     * {@link #readStructureCasing()}/{@link #resetStructureCasing()} 维护。
     */
    @Nullable
    String getStructureCasingId();

    /**
     * 设置结构实际使用的外壳方块注册名。
     *
     * @param id 外壳方块注册名；null 表示未记录
     */
    void setStructureCasingId(@Nullable String id);

    /**
     * 当前外壳方块状态：优先返回结构<b>实际使用</b>的外壳方块
     * （{@link #getStructureCasingId()} 记录的注册名解析），否则回退
     * {@link #getCasingState(int)}（等级映射/注册外观）。仓室/总线部件渲染与外观
     * （{@link #getPartAppearance}）走本方法，从而让"结构用什么方块、部件就渲染成什么方块"
     * 对所有部件类型通用生效。
     */
    @Nullable
    default BlockState getCasingState() {
        String structureCasingId = getStructureCasingId();
        if (structureCasingId != null) {
            ResourceLocation id = ResourceLocation.tryParse(structureCasingId);
            Block block = id == null ? null : ForgeRegistries.BLOCKS.getValue(id);
            if (block != null && block != Blocks.AIR) return block.defaultBlockState();
        }
        return getCasingState(getCasingTier());
    }

    /**
     * 控制器成型后显示的外观方块 = 注册时指定的 {@code appearanceBlock}，
     * 与仓室跟随的结构外壳（{@link #getCasingState()}）解耦。
     *
     * <p>
     * <b>仅当结构使用 {@link GTUF_PatternPredicates#Casing} 谓词时生效</b>
     * （{@link #getStructureCasingId()} 非 null）：此时结构外壳与外观方块是两块不同的方块
     * （如 Casing=stone、外观=bronze_plated_bricks），控制器按注册外观显示、不被结构外壳覆盖。
     * 可替换外壳/等级外壳机器（{@code structureCasingId == null}，如
     * {@code EnhanceableSteamMachine}/{@code EnhanceableElectricMachine}）返回 null，由渲染器
     * 回退到 {@link #getCasingState()}——控制器跟随结构外壳等级，避免把等级外壳错误地替换成
     * 注册外观方块（如 BRONZE_HULL）。
     * </p>
     *
     * <p>
     * 渲染器（{@code GTUFTieredPartRender}/{@code GTUFTieredBoilerPartRender}）的控制器路径
     * 与 {@code getBlockAppearance} 走本方法；返回 null 时渲染器回退 {@link #getCasingState()}。
     * 外观方块为机器方块自身（未显式指定时的默认值）时返回 null，避免渲染控制器自身的
     * 动态模型产生递归。
     * </p>
     */
    @Nullable
    default BlockState getControllerAppearanceState() {
        // 仅 Casing 谓词机器（structureCasingId 非 null）的控制器显示注册外观方块；
        // 等级外壳机器（null）由渲染器回退到结构外壳（getCasingState()）。
        if (getStructureCasingId() == null) return null;
        var appearance = self().getDefinition().getAppearance();
        if (appearance == null) return null;
        BlockState state = appearance.get();
        if (state == null || state.getBlock() instanceof IMachineBlock) return null;
        return state;
    }

    /**
     * 成型时读取结构实际外壳方块注册名：从 MatchContext 取出
     * {@link GTUF_PatternPredicates#Casing} 写入的值存入 {@link #getStructureCasingId()}。
     * 在 {@code onStructureFormed} 中调用（与等级读取顺序无关）。
     */
    default void readStructureCasing() {
        Object value = self().getMultiblockState().getMatchContext()
                .get(GTUF_PatternPredicates.STRUCTURE_CASING_KEY);
        setStructureCasingId(value instanceof String id ? id : null);
    }

    /**
     * 结构失效时清除记录的结构外壳注册名。在 {@code onStructureInvalid} 中调用。
     */
    default void resetStructureCasing() {
        setStructureCasingId(null);
    }

    /**
     * 成型后部件（仓/总线）外观 = 结构实际使用的外壳（{@link #getCasingState()}），
     * 使部件与相邻外壳的连接纹理（CTM）/面剔除无缝；未成型时沿用
     * {@link IMultiController} 默认行为（注册外观）。
     */
    @Override
    default BlockState getPartAppearance(IMultiPart part, Direction side, BlockState sourceState, BlockPos sourcePos) {
        if (isFormed()) {
            BlockState state = getCasingState();
            if (state != null) return state;
        }
        return IMultiController.super.getPartAppearance(part, side, sourceState, sourcePos);
    }
}
