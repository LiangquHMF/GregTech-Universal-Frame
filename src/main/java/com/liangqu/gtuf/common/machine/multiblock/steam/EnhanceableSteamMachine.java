package com.liangqu.gtuf.common.machine.multiblock.steam;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import com.liangqu.gtuf.api.machine.multiblock.ParallelMachine;
import com.liangqu.gtuf.api.pattern.GTUF_PatternPredicates;
import com.liangqu.gtuf.common.machine.multiblock.base.SteamMultiBlockBase;
import com.liangqu.gtuf.config.GTUF_Config;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 可增强蒸汽多方块机器：由结构方块类型驱动两种机制——
 * <ul>
 * <li><b>外壳等级 (CasingTier)</b>：蒸汽机械方块=1，脱氧钢机械方块=2，
 * 决定最大并行数 = 初始并行数 × 并行倍率^(外壳等级-1)（并行倍率由 config 控制，默认 4）</li>
 * <li><b>框架等级 (FrameTier)</b>：青铜框架=1，钢框架=2，
 * 决定配方速度（Tier1 无加速，加速从 Tier2 开始，步长由 config 控制，默认 0.1，
 * 公式见 {@link #applyFrameSpeed}）</li>
 * </ul>
 * 等级在结构成形时从 {@link com.gregtechceu.gtceu.api.pattern.util.PatternMatchContext} 读取，
 * 其中外壳等级需要持久化并同步客户端（部件外观渲染按它匹配外壳），随结构重新成形即刷新。
 */
public class EnhanceableSteamMachine extends SteamMultiBlockBase implements ParallelMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            EnhanceableSteamMachine.class, SteamMultiBlockBase.MANAGED_FIELD_HOLDER);

    /** 外壳等级（青铜=1，脱氧钢=2）。持久化并同步客户端：成型后部件外观按此匹配外壳。 */
    @Persisted
    @DescSynced
    @RequireRerender
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
        if (ctx.get(GTUF_PatternPredicates.STEAM_CASING_TIER_KEY) instanceof Integer tier) casingTier = tier;
        if (ctx.get(GTUF_PatternPredicates.STEAM_FRAME_TIER_KEY) instanceof Integer tier) frameTier = tier;
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
     * 最大并行数 = 初始并行数 × 并行倍率^(外壳等级-1)。
     * 并行倍率由 {@link GTUF_Config#getSteamParallelBase()} 控制（默认 4）。
     * Tier1 = 初始并行数，Tier2 = 4×初始并行数。
     */
    @Override
    public int getMaxParallel() {
        return baseParallel * (int) Math.pow(GTUF_Config.getSteamParallelBase(), casingTier - 1);
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
     * 框架等级加速：实际时间 = 原始时间^(1-(框架等级-1)×框架速度步长)。
     * 步长由 {@link GTUF_Config#getSteamFrameSpeedStep()} 控制（默认 0.1）；
     * 指数下限 0（高等级配大步长时时长收敛为 1，不产生小于 1 的时长）。
     * 如需修改加速公式，直接覆盖此方法。
     */
    protected GTRecipe applyFrameSpeed(GTRecipe recipe) {
        if (frameTier > 1) {
            double exponent = Math.max(0.0, 1.0 - (frameTier - 1) * GTUF_Config.getSteamFrameSpeedStep());
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
