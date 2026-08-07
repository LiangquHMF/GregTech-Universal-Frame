package com.liangqu.gtuf.common.machine.multiblock.steam;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.liangqu.gtuf.api.machine.multiblock.ParallelMachine;
import com.liangqu.gtuf.api.pattern.GTUF_PatternPredicates;
import com.liangqu.gtuf.common.machine.multiblock.base.SteamMultiBlockBase;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 可增强蒸汽多方块机器：由结构方块类型驱动两种机制——
 * <ul>
 *   <li><b>外壳等级 (CasingTier)</b>：蒸汽机械方块=1，脱氧钢机械方块=2，
 *       决定最大并行数 = 初始并行数 × 4^(外壳等级-1)</li>
 *   <li><b>框架等级 (FrameTier)</b>：青铜框架=1，钢框架=2，
 *       决定配方速度（Tier1 无加速，加速从 Tier2 开始，公式见 {@link #applyFrameSpeed}）</li>
 * </ul>
 * 等级在结构成形时从 {@link com.gregtechceu.gtceu.api.pattern.util.PatternMatchContext} 读取，
 * 故无需持久化，随结构重新成形即刷新。
 */
public class EnhanceableSteamMachine extends SteamMultiBlockBase implements ParallelMachine {

    private int casingTier = 1;
    private int frameTier = 1;

    private final GTRecipeType recipeType;
    private final int baseParallel;

    public EnhanceableSteamMachine(IMachineBlockEntity holder, GTRecipeType recipeType, int baseParallel,
                                   Object... args) {
        super(holder, false, args);
        this.recipeType = recipeType;
        this.baseParallel = baseParallel;
    }

    //////////////////////////////////////
    // *** Multiblock LifeCycle ***//
    //////////////////////////////////////

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        var ctx = getMultiblockState().getMatchContext();
        if (ctx.get(GTUF_PatternPredicates.CASING_TIER_KEY) instanceof Integer tier) casingTier = tier;
        if (ctx.get(GTUF_PatternPredicates.FRAME_TIER_KEY) instanceof Integer tier) frameTier = tier;
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        casingTier = 1;
        frameTier = 1;
    }

    //////////////////////////////////////
    // *** 等级访问器 ***//
    //////////////////////////////////////

    public int getCasingTier() {
        return casingTier;
    }

    public int getFrameTier() {
        return frameTier;
    }

    //////////////////////////////////////
    // *** 并行与配方处理 ***//
    //////////////////////////////////////

    /**
     * 最大并行数 = 初始并行数 × 4^(外壳等级-1)。
     * Tier1 = 初始并行数，Tier2 = 4×初始并行数。
     */
    @Override
    public int getMaxParallel() {
        return baseParallel * (int) Math.pow(4, casingTier - 1);
    }

    @Nullable
    @Override
    protected GTRecipe getRealRecipe(@Nonnull GTRecipe recipe) {
        if (recipe.getType() != recipeType) return null;
        int parallels = ParallelLogic.getParallelAmount(this, recipe, getMaxParallel());
        if (parallels == 0) return null;
        GTRecipe paralleled = ModifierFunction.builder()
                .modifyAllContents(ContentModifier.multiplier(parallels))
                .parallels(parallels)
                .build()
                .apply(recipe.copy());
        if (paralleled == null) return null;
        return applyFrameSpeed(paralleled);
    }

    /**
     * 框架等级加速：实际时间 = 原始时间^(1-(框架等级-1)×0.01)。
     * Tier1 指数为 1（无加速），Tier2 为 0.99（约 1% 加速）。
     * 如需修改加速公式，直接覆盖此方法。
     */
    protected GTRecipe applyFrameSpeed(GTRecipe recipe) {
        if (frameTier > 1) {
            double exponent = 1.0 - (frameTier - 1) * 0.01;
            recipe.duration = Math.max(1, (int) Math.round(Math.pow(recipe.duration, exponent)));
        }
        return recipe;
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
            textList.add(Component.translatable("gtuf.multiblock.frame_tier", frameTier)
                    .withStyle(ChatFormatting.GRAY));
            textList.add(Component.translatable("gtuf.multiblock.parallel_amount", getMaxParallel())
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
