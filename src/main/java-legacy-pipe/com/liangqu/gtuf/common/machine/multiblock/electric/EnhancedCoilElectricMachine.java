package com.liangqu.gtuf.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 线圈增强电力多方块机器：继承 {@link CoilWorkableElectricMultiblockMachine}，
 * 后三项参数 {@code <= 0} 均视为该功能关闭（0 = 关闭，沿用整合包平衡惯例，一并拦截负数）。
 * <p>
 * 配方修改器在注册时用 {@code .recipeModifier(EnhancedCoilElectricMachine::recipeModifier, true)}
 * 挂载：先按最大并行（含线圈额外并行）放大配方，乘能耗减免，乘线圈提速，最后按创建期选定的
 * 完美/非完美OC（{@link #perfectOC}）。四功能全关时退化为纯并行 + 过时钟。
 * </p>
 * <p>
 * <b>版本分源（GTM ≤ 7.4）</b>：本文件位于 {@code src/main/java-legacy-pipe}，供 GTM 7.1.4~7.4.1
 * 构建使用（7.1.4~7.4.1 共用）。7.1.4 的 {@link ModifierFunction} 无
 * {@code cancel(Component)}/{@code getFailReason()} API，温度/电压门控失败只能返回
 * {@link ModifierFunction#NULL}，控制器 GUI 显示笼统的「Recipe Modifier Fail」。
 * GTM 7.5.x 见 {@code src/main/java75} 下的同名类（携带具体失败原因）。
 * </p>
 */
public class EnhancedCoilElectricMachine extends CoilWorkableElectricMultiblockMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            EnhancedCoilElectricMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** 默认初始并行数（线圈等级 0 / level 1 时的基础并行）。 */
    public static final int DEFAULT_BASE_PARALLEL = 4;
    /** 默认线圈提速步长：时长倍率 = 1 - speedStep × 线圈等级（0 = 关闭）。 */
    public static final double DEFAULT_SPEED_STEP = 0.1;
    /** 默认线圈额外并行：每线圈 level 增加 parallelPerLevel 并行（0 = 关闭）。 */
    public static final double DEFAULT_PARALLEL_PER_LEVEL = 8.0;
    /** 默认线圈能耗减免步长：能耗倍率 = 1 - energyStep × 线圈等级（0 = 关闭）。 */
    public static final double DEFAULT_ENERGY_STEP = 0.05;
    /** 默认过时钟方式：true = 完美过时钟（OC_PERFECT），false = 非完美（OC_NON_PERFECT）。 */
    public static final boolean DEFAULT_PERFECT_OC = true;

    /** 时长倍率下限（避免高线圈等级配大步长把耗时压到 0）。 */
    public static final double MIN_DURATION_MULTIPLIER = 0.05;
    /** 能耗倍率下限。 */
    public static final double MIN_ENERGY_MULTIPLIER = 0.1;

    /** 初始并行数（创建期设置，final 运行时只读）。 */
    private final int baseParallel;
    /** 线圈提速步长（0 或负数 = 关闭）。 */
    private final double speedStep;
    /** 线圈额外并行参数（0 或负数 = 关闭）。 */
    private final double parallelPerLevel;
    /** 线圈能耗减免步长（0 或负数 = 关闭）。 */
    private final double energyStep;
    /** 过时钟方式：true = 完美，false = 非完美。 */
    private final boolean perfectOC;

    /** 结构成型时的线圈等级（0-7），持久化给客户端显示（基类 coilType 非持久化）。 */
    @Persisted
    @DescSynced
    private int coilTier = 0;

    /** 便捷构造器：全部特性取默认值（初始并行 4、三项线圈增强全开、完美过时钟）。 */
    public EnhancedCoilElectricMachine(IMachineBlockEntity holder) {
        this(holder, DEFAULT_BASE_PARALLEL, DEFAULT_SPEED_STEP, DEFAULT_PARALLEL_PER_LEVEL,
                DEFAULT_ENERGY_STEP, DEFAULT_PERFECT_OC);
    }

    /**
     * 完整构造器：四项特性全部在创建期设定。
     *
     * @param baseParallel     初始并行数（≥1，恒启用）
     * @param speedStep        线圈提速步长（≤0 关闭提速）
     * @param parallelPerLevel 线圈额外并行参数（≤0 关闭线圈并行）
     * @param energyStep       线圈能耗减免步长（≤0 关闭减免）
     * @param perfectOC        过时钟方式（true 完美 / false 非完美）
     */
    public EnhancedCoilElectricMachine(IMachineBlockEntity holder, int baseParallel, double speedStep,
                                       double parallelPerLevel, double energyStep, boolean perfectOC) {
        super(holder);
        this.baseParallel = Math.max(1, baseParallel);
        this.speedStep = speedStep;
        this.parallelPerLevel = parallelPerLevel;
        this.energyStep = energyStep;
        this.perfectOC = perfectOC;
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
        this.coilTier = getCoilTier();
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        this.coilTier = 0;
    }

    //////////////////////////////////////
    // *** 线圈增强公式 ***//
    //////////////////////////////////////

    /** 初始并行数（创建期设置）。 */
    public int getBaseParallel() {
        return baseParallel;
    }

    /** 最大并行数 = 初始并行数 + 线圈额外并行（线圈并行关闭时仅初始并行）。 */
    public int getMaxParallel() {
        int coilParallel = parallelPerLevel <= 0 ? 0 : (int) (parallelPerLevel * getCoilType().getLevel());
        return Math.max(1, baseParallel + coilParallel);
    }

    /** 线圈提速步长（≤0 = 提速关闭）。 */
    public double getSpeedStep() {
        return speedStep;
    }

    /**
     * 时长倍率：{@code max(MIN_DURATION_MULTIPLIER, 1 - speedStep × 线圈等级)}。
     * 关闭（speedStep ≤ 0）时为 1.0（不改变耗时）。线圈等级 0（铜镍）恒为 1.0。
     */
    public double getDurationMultiplier() {
        return speedStep <= 0 ? 1.0 : Math.max(MIN_DURATION_MULTIPLIER, 1.0 - speedStep * getCoilTier());
    }

    /** 线圈额外并行参数（≤0 = 线圈并行关闭）。 */
    public double getParallelPerLevel() {
        return parallelPerLevel;
    }

    /** 线圈能耗减免步长（≤0 = 减免关闭）。 */
    public double getEnergyStep() {
        return energyStep;
    }

    /**
     * 能耗倍率：{@code max(MIN_ENERGY_MULTIPLIER, 1 - energyStep × 线圈等级)}。
     * 关闭（energyStep ≤ 0）时为 1.0（不减免）。线圈等级 0（铜镍）恒为 1.0。
     */
    public double getEnergyMultiplier() {
        return energyStep <= 0 ? 1.0 : Math.max(MIN_ENERGY_MULTIPLIER, 1.0 - energyStep * getCoilTier());
    }

    /** 过时钟方式：true = 完美（OC_PERFECT），false = 非完美（OC_NON_PERFECT）。 */
    public boolean isPerfectOC() {
        return perfectOC;
    }

    //////////////////////////////////////
    // *** 配方修改器 ***//
    //////////////////////////////////////

    /**
     * 配方修改器：先并行放大（初始并行 + 线圈额外并行），叠加线圈能耗减免与提速，
     * 最后按创建期选定的过时钟方式 OC（基于调整后的配方计算）。
     * 注册时用 {@code .recipeModifier(EnhancedCoilElectricMachine::recipeModifier, true)} 挂载。
     *
     * <p>
     * 包含温度/等级门控（镜像 {@link GTRecipeModifiers#ebfOverclock}）：对于带 {@code ebf_temp}
     * 字段的配方（如电力高炉），校验线圈温度和机器等级是否足够；不满足则返回
     * {@link ModifierFunction#NULL}（配方不启动）。不带 {@code ebf_temp} 的配方跳过此检查。
     * <b>注意</b>：GTM 7.1.4~7.4.1 无 {@code ModifierFunction.cancel(Component)} API，
     * 失败原因无法携带，GUI 显示笼统的「Recipe Modifier Fail」。
     * </p>
     */
    public static ModifierFunction recipeModifier(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof EnhancedCoilElectricMachine coilMachine)) return ModifierFunction.NULL;

        // 温度/等级门控（镜像 GTRecipeModifiers.ebfOverclock，防止低线圈处理高温度配方）
        if (recipe.data.contains("ebf_temp")) {
            int blastFurnaceTemperature = coilMachine.getCoilType().getCoilTemperature() +
                    (100 * Math.max(0, coilMachine.getTier() - GTValues.MV));
            int recipeTemp = recipe.data.getInt("ebf_temp");
            if (recipeTemp > blastFurnaceTemperature) return ModifierFunction.NULL;
            if (RecipeHelper.getRecipeEUtTier(recipe) > coilMachine.getTier()) return ModifierFunction.NULL;
        }

        int parallels = ParallelLogic.getParallelAmount(machine, recipe, coilMachine.getMaxParallel());
        if (parallels == 0) return ModifierFunction.NULL;

        ModifierFunction base = ModifierFunction.builder()
                .modifyAllContents(ContentModifier.multiplier(parallels))
                .eutMultiplier(parallels * coilMachine.getEnergyMultiplier())
                .durationMultiplier(coilMachine.getDurationMultiplier())
                .parallels(parallels)
                .build();

        return recipe1 -> {
            GTRecipe modified = base.apply(recipe1);
            if (modified == null) return null;
            ModifierFunction oc = coilMachine.isPerfectOC() ?
                    GTRecipeModifiers.OC_PERFECT.getModifier(machine, modified) :
                    GTRecipeModifiers.OC_NON_PERFECT.getModifier(machine, modified);
            return oc.apply(modified);
        };
    }

    //////////////////////////////////////
    // *** GUI 显示 ***//
    //////////////////////////////////////

    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
        if (isFormed()) {
            int blastFurnaceTemperature = getCoilType().getCoilTemperature() +
                    (100 * Math.max(0, getTier() - GTValues.MV));
            textList.add(Component.translatable("gtuf.multiblock.coil_temperature",
                    blastFurnaceTemperature + "K")
                    .withStyle(ChatFormatting.GOLD));
            textList.add(Component.translatable("gtuf.multiblock.parallel_amount", getMaxParallel())
                    .withStyle(ChatFormatting.GOLD));
            if (speedStep > 0) {
                textList.add(Component.translatable("gtuf.multiblock.speed_multiplier",
                        (int) Math.round(getDurationMultiplier() * 100) + "%")
                        .withStyle(ChatFormatting.GOLD));
            }
            if (energyStep > 0) {
                textList.add(Component.translatable("gtuf.multiblock.energy_multiplier",
                        (int) Math.round(getEnergyMultiplier() * 100) + "%")
                        .withStyle(ChatFormatting.GOLD));
            }
        }
    }
}
