package com.liangqu.gtuf.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import com.liangqu.gtuf.api.machine.ITieredCasingMachine;
import com.liangqu.gtuf.api.pattern.GTUF_PatternPredicates;
import com.liangqu.gtuf.config.GTUF_Config;

import java.util.List;

import javax.annotation.Nullable;

/**
 * 可增强电力多方块机器：由结构方块类型驱动三种机制，外壳必用，框架/管道可选。
 * <ul>
 * <li><b>外壳等级 (UniversalCasing)</b>：钢=1、铝=2、不锈钢=3、钛=4、钨钢=5，
 * 决定最大并行数 = 初始并行数 × 并行倍率^(外壳等级-1)（并行倍率由 config 控制，默认 2：
 * 初始并行数默认 4 → 钢=4、铝=8、不锈钢=16）</li>
 * <li><b>框架等级 (UniversalFrame)</b>（构造器 useFrame=true 时启用）：钢=1、铝=2、不锈钢=3…
 * 决定能耗倍率 = 1 - (框架等级-1) × 框架能耗步长（config 控制，默认 0.05：
 * 钢=100%、铝=95%、不锈钢=90%）</li>
 * <li><b>管道等级 (UniversalPipe)</b>（构造器 usePipe=true 时启用）：青铜=1、钢=2、钛=3、钨钢=4…
 * 决定配方时长倍率 = 1 - (管道等级-1) × 管道时长步长（config 控制，默认 0.1：
 * 青铜=1、钢=0.9、钛=0.7、钨钢=0.6）</li>
 * </ul>
 * 等级在结构成形时从 {@link com.gregtechceu.gtceu.api.pattern.util.PatternMatchContext} 读取。
 * 外壳等级需持久化并同步客户端：仓室按
 * {@link ITieredCasingMachine#getCasingState()} 渲染成结构实际使用的外壳方块，
 * 控制器自身显示注册外观（{@link ITieredCasingMachine#getControllerAppearanceState()}）。
 *
 * <p>
 * 注册时挂载 {@code .recipeModifier(EnhanceableElectricMachine::recipeModifier, true)}，
 * 先用 {@code createTieredMachineModel} 模型工厂（成型后部件/控制器匹配外壳材质）。
 * </p>
 */
public class EnhanceableElectricMachine extends WorkableElectricMultiblockMachine implements ITieredCasingMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            EnhanceableElectricMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** 初始并行数（外壳 Tier1 时的并行数，默认 4 → 钢=4、铝=8、不锈钢=16）。 */
    public static final int DEFAULT_BASE_PARALLEL = 4;

    /** 外壳等级（钢=1，铝=2，不锈钢=3，钛=4，钨钢=5）。持久化并同步客户端：成型后部件外观按此匹配外壳。 */
    @Persisted
    @DescSynced
    @RequireRerender
    private int casingTier = 1;

    /** 框架等级（钢=1，铝=2，不锈钢=3…）。构造器 useFrame=false 时恒为 1。 */
    @Persisted
    @DescSynced
    private int frameTier = 1;

    /** 管道等级（青铜=1，钢=2，钛=3，钨钢=4…）。构造器 usePipe=false 时恒为 1。 */
    @Persisted
    @DescSynced
    private int pipeTier = 1;

    /**
     * 结构实际使用的外壳方块注册名（由 {@link GTUF_PatternPredicates#Casing} 写入
     * MatchContext、成型时读取）。仓室/总线部件按它渲染外壳——结构铺什么方块，
     * 部件就渲染成什么方块；控制器自身显示注册外观（{@code appearanceBlock}）不受影响；
     * 未设置（null）时仓室回退等级映射。
     */
    @Persisted
    @DescSynced
    @RequireRerender
    @Nullable
    private String structureCasingId = null;

    @Nullable
    @Override
    public String getStructureCasingId() {
        return structureCasingId;
    }

    @Override
    public void setStructureCasingId(@Nullable String structureCasingId) {
        this.structureCasingId = structureCasingId;
    }

    private final int baseParallel;
    private final boolean useFrame;
    private final boolean usePipe;

    /**
     * 便捷构造器：初始并行数取默认值 {@link #DEFAULT_BASE_PARALLEL}。
     *
     * @param useFrame 是否启用框架（能耗）增强
     * @param usePipe  是否启用管道（速度）增强
     */
    public EnhanceableElectricMachine(IMachineBlockEntity holder, boolean useFrame, boolean usePipe) {
        this(holder, DEFAULT_BASE_PARALLEL, useFrame, usePipe);
    }

    /**
     * 完整构造器。
     *
     * @param baseParallel 外壳 Tier1 时的并行数
     * @param useFrame     是否启用框架（能耗）增强
     * @param usePipe      是否启用管道（速度）增强
     */
    public EnhanceableElectricMachine(IMachineBlockEntity holder, int baseParallel, boolean useFrame, boolean usePipe,
                                      Object... args) {
        super(holder, args);
        this.baseParallel = Math.max(1, baseParallel);
        this.useFrame = useFrame;
        this.usePipe = usePipe;
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    //////////////////////////////////////
    // *** Multiblock LifeCycle ***//
    //////////////////////////////////////

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        var ctx = getMultiblockState().getMatchContext();
        if (ctx.get(GTUF_PatternPredicates.UNIVERSAL_CASING_TIER_KEY) instanceof Integer tier) casingTier = tier;
        if (useFrame && ctx.get(GTUF_PatternPredicates.UNIVERSAL_FRAME_TIER_KEY) instanceof Integer tier) {
            frameTier = tier;
        }
        if (usePipe && ctx.get(GTUF_PatternPredicates.UNIVERSAL_PIPE_TIER_KEY) instanceof Integer tier) {
            pipeTier = tier;
        }
        // 读取结构实际使用的外壳方块（Casing 谓词写入），供仓室渲染匹配。
        readStructureCasing();
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        casingTier = 1;
        frameTier = 1;
        pipeTier = 1;
        resetStructureCasing();
    }

    //////////////////////////////////////
    // *** 等级与外观（ITieredCasingMachine）***//
    //////////////////////////////////////

    @Override
    public int getCasingTier() {
        return casingTier;
    }

    /**
     * 外壳等级 → 外壳方块状态（供控制器自身与仓室渲染匹配外壳）：
     * 钢机壳=1、铝防霜=2、不锈钢洁净=3、钛稳定=4、钨钢坚固=5。
     */
    @Nullable
    @Override
    public BlockState getCasingState(int tier) {
        return switch (tier) {
            case 2 -> GTBlocks.CASING_ALUMINIUM_FROSTPROOF.get().defaultBlockState();
            case 3 -> GTBlocks.CASING_STAINLESS_CLEAN.get().defaultBlockState();
            case 4 -> GTBlocks.CASING_TITANIUM_STABLE.get().defaultBlockState();
            case 5 -> GTBlocks.CASING_TUNGSTENSTEEL_ROBUST.get().defaultBlockState();
            default -> GTBlocks.CASING_STEEL_SOLID.get().defaultBlockState();
        };
    }

    /**
     * 成型后控制器外观 = 注册时指定的外观方块（{@link #getControllerAppearanceState()}），
     * 与仓室跟随的结构外壳（{@link #getCasingState()}）分离。
     *
     * <p>
     * 本机器是等级外壳机器（无 Casing 谓词），{@code getControllerAppearanceState()} 恒为 null，
     * 回退 {@link #getCasingState()}——控制器渲染成结构外壳等级方块、相邻面剔除按外壳材质处理。
     * </p>
     */
    @Override
    public BlockState getBlockAppearance(BlockState state, BlockAndTintGetter level, BlockPos pos, Direction side,
                                         BlockState sourceState, BlockPos sourcePos) {
        if (isFormed()) {
            BlockState appearance = getControllerAppearanceState();
            if (appearance == null) appearance = getCasingState();
            if (appearance != null) return appearance;
        }
        return super.getBlockAppearance(state, level, pos, side, sourceState, sourcePos);
    }

    //////////////////////////////////////
    // *** 增强公式（可覆盖） ***//
    //////////////////////////////////////

    /**
     * 最大并行数 = 初始并行数 × 并行倍率^(外壳等级-1)。
     * 并行倍率由 {@link GTUF_Config#getElectricParallelBase()} 控制（默认 2）。
     * 初始并行数 4 时：钢(1)=4、铝(2)=8、不锈钢(3)=16、钛(4)=32、钨钢(5)=64。
     */
    public int getMaxParallel() {
        return baseParallel * (int) Math.pow(GTUF_Config.getElectricParallelBase(), casingTier - 1);
    }

    /** 是否启用框架（能耗）增强。 */
    public boolean isUseFrame() {
        return useFrame;
    }

    /** 是否启用管道（速度）增强。 */
    public boolean isUsePipe() {
        return usePipe;
    }

    /**
     * 能耗倍率：1 - (框架等级-1) × 框架能耗步长（下限 0，避免高等级配大步长出现负倍率）。
     * 步长由 {@link GTUF_Config#getElectricFrameEnergyStep()} 控制（默认 0.05）。
     * 钢(1)=100%、铝(2)=95%、不锈钢(3)=90%。如需修改公式直接覆盖本方法。
     */
    public double getEnergyMultiplier() {
        return Math.max(0.0, 1.0 - (frameTier - 1) * GTUF_Config.getElectricFrameEnergyStep());
    }

    /**
     * 配方时长倍率：1 - (管道等级-1) × 管道时长步长（下限 0，避免高等级配大步长出现负倍率）。
     * 步长由 {@link GTUF_Config#getElectricPipeSpeedStep()} 控制（默认 0.1）。
     * 青铜(1)=100%、钢(2)=90%、钛(3)=70%、钨钢(4)=60%。如需修改公式直接覆盖本方法。
     */
    public double getSpeedMultiplier() {
        return Math.max(0.0, 1.0 - (pipeTier - 1) * GTUF_Config.getElectricPipeSpeedStep());
    }

    //////////////////////////////////////
    // *** 配方修改器 ***//
    //////////////////////////////////////

    /**
     * 配方修改器：先并行放大（外壳），再应用框架能耗倍率与管道时长倍率，最后完美过时钟。
     * 注册时用 {@code .recipeModifier(EnhanceableElectricMachine::recipeModifier, true)} 挂载。
     *
     * <p>
     * 能耗倍率通过 {@code ModifierFunction#eutMultiplier} 应用（只作用于 tick 能量 content，
     * 且并行与能耗合并为单一倍率 {@code parallels × energyMultiplier} 一次设置）；
     * 时长倍率通过 {@code ModifierFunction#durationMultiplier} 应用。
     * </p>
     */
    public static ModifierFunction recipeModifier(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof EnhanceableElectricMachine enhanceable)) return ModifierFunction.NULL;

        int parallels = ParallelLogic.getParallelAmount(machine, recipe, enhanceable.getMaxParallel());
        if (parallels == 0) return ModifierFunction.NULL;

        ModifierFunction base = ModifierFunction.builder()
                .modifyAllContents(ContentModifier.multiplier(parallels))
                .eutMultiplier(parallels * enhanceable.getEnergyMultiplier())
                .durationMultiplier(enhanceable.getSpeedMultiplier())
                .parallels(parallels)
                .build();

        // 先并行 + 能耗/时长调整放大配方，再过时钟（OC 基于调整后的配方计算）
        return recipe1 -> {
            GTRecipe paralleled = base.apply(recipe1);
            if (paralleled == null) return null;
            return GTRecipeModifiers.OC_PERFECT.getModifier(machine, paralleled).apply(paralleled);
        };
    }

    //////////////////////////////////////
    // *** GUI 显示 ***//
    //////////////////////////////////////

    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
        if (isFormed()) {
            textList.add(Component.translatable("gtuf.multiblock.casing_tier", casingTier)
                    .withStyle(ChatFormatting.GRAY));
            textList.add(Component.translatable("gtuf.multiblock.parallel_amount", getMaxParallel())
                    .withStyle(ChatFormatting.GOLD));
            if (useFrame) {
                textList.add(Component.translatable("gtuf.multiblock.frame_tier", frameTier)
                        .withStyle(ChatFormatting.GRAY));
                textList.add(Component.translatable("gtuf.multiblock.energy_multiplier",
                        (int) Math.round(getEnergyMultiplier() * 100) + "%")
                        .withStyle(ChatFormatting.GOLD));
            }
            if (usePipe) {
                textList.add(Component.translatable("gtuf.multiblock.pipe_tier", pipeTier)
                        .withStyle(ChatFormatting.GRAY));
                textList.add(Component.translatable("gtuf.multiblock.speed_multiplier",
                        String.format("%.1f", getSpeedMultiplier()))
                        .withStyle(ChatFormatting.GOLD));
            }
        }
    }
}
