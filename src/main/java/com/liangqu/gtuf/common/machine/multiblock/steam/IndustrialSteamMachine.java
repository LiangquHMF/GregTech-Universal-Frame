package com.liangqu.gtuf.common.machine.multiblock.steam;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.liangqu.gtuf.api.machine.multiblock.ParallelMachine;
import com.liangqu.gtuf.api.pattern.GTUF_PatternPredicates;
import com.liangqu.gtuf.common.machine.multiblock.base.SteamMultiBlockBase;
import com.liangqu.gtuf.common.machine.multiblock.part.IndustrialSteamHatchPartMachine;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 工业级蒸汽多方块机器。在原生蒸汽机的基础上扩展三个特征：
 * <ul>
 *   <li><b>GUI 可调并行数</b>：原生蒸汽机并行数固定，本机可用 {@code [-]} / {@code [+]} 在 GUI 中
 *       按 ×2 / ÷2 调整（范围 1 ~ {@link #getMaxParallel()}），类似 {@link AdjustableSteamParallelMachine}；</li>
 *   <li><b>增强蒸汽仓</b>：可搭配 {@link com.liangqu.gtuf.common.machine.multiblock.part.IndustrialSteamHatchPartMachine}
 *       （容量为原生 16 倍），用于高并行 / MV 配方下的蒸汽储备；</li>
 *   <li><b>重写蒸汽→EU 转换公式</b>：配增强蒸汽仓时转换率 {@code 0.25 mB/EU}（1 mB 蒸汽 = 4 EU），
 *       可处理 MV(128 EU/t) 配方；无增强仓时保持原生 {@code 1.0 mB/EU}，仍只处理 LV 配方。</li>
 * </ul>
 * 转换公式封装在可覆盖的 {@link #getConversionRate()} 中，如需修改直接覆盖该方法。
 */
public class IndustrialSteamMachine extends SteamMultiBlockBase implements ParallelMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            IndustrialSteamMachine.class, SteamMultiBlockBase.MANAGED_FIELD_HOLDER);

    private static final int MIN_PARALLEL = 1;
    /** 无增强仓时的蒸汽→EU 转换率（mB/EU），与原生蒸汽机一致。 */
    private static final double BASE_CONVERSION_RATE = 1.0;
    /** 配增强蒸汽仓时的转换率（mB/EU），1 mB 蒸汽 = 4 EU，可支撑 MV 配方。 */
    private static final double ADVANCED_CONVERSION_RATE = 0.25;

    private final GTRecipeType recipeType;
    private final int maxParallel;

    /** 当前生效并行数，GUI 可调，范围 [MIN_PARALLEL, maxParallel]。 */
    @Persisted
    private int targetParallel;

    /** 是否配备增强蒸汽仓（随结构重成形刷新，不持久化）。 */
    private boolean isOC;

    /** 外壳等级（青铜=1，脱氧钢=2）。持久化并同步客户端：成型后部件外观按此匹配外壳。 */
    @Persisted
    @DescSynced
    @RequireRerender
    private int casingTier = 1;

    public IndustrialSteamMachine(IMachineBlockEntity holder, GTRecipeType recipeType, int maxParallel,
                                  Object... args) {
        super(holder, false, args);
        this.recipeType = recipeType;
        this.maxParallel = Math.max(MIN_PARALLEL, maxParallel);
        this.targetParallel = this.maxParallel;
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /** 注册时设置的并行数上限。 */
    @Override
    public int getMaxParallel() {
        return maxParallel;
    }

    /** 当前生效并行数（GUI 调整后）。 */
    public int getTargetParallel() {
        return targetParallel;
    }

    /** 结构外壳等级（青铜=1，脱氧钢=2）。 */
    public int getCasingTier() {
        return casingTier;
    }

    /**
     * 部件外观 = 结构实际使用的外壳：Tier1 青铜机壳（steam_machine_casing），
     * Tier2 脱氧钢机壳（solid_machine_casing）。这样成型后仓/总线材质与被替换外壳一致。
     */
    @Override
    protected BlockState getPartAppearanceState() {
        return getCasingTier() >= 2
                ? GTBlocks.CASING_STEEL_SOLID.get().defaultBlockState()
                : GTBlocks.CASING_BRONZE_BRICKS.get().defaultBlockState();
    }

    //////////////////////////////////////
    // *** Multiblock LifeCycle ***//
    //////////////////////////////////////

    @Override
    public void onStructureFormed() {
        detectOC();
        // 先检测增强仓再调用 super：super 内按 getConversionRate() 动态分派创建蒸汽处理器
        super.onStructureFormed();
        // 从 MatchContext 读取外壳等级（客户端渲染需同步，见 getPartAppearanceState）
        var ctx = getMultiblockState().getMatchContext();
        if (ctx.get(GTUF_PatternPredicates.STEAM_CASING_TIER_KEY) instanceof Integer tier) casingTier = tier;
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        isOC = false;
        casingTier = 1;
    }

    /**
     * 遍历结构部件，若装有增强蒸汽仓（按部件类判断，不依赖预注册 definition）则启用 MV 等级与高效转换。
     * <p>注意必须读取 {@code matchContext} 中的 "parts" 而不是 {@link #getParts()}：
     * 后者要到 super 链最深处（{@code MultiblockControllerMachine.onStructureFormed()}）才被填充，
     * 而 matchContext 在结构匹配（checkPatternWithLock）时就已经写入部件集合。</p>
     */
    private void detectOC() {
        isOC = false;
        Set<IMultiPart> parts = getMultiblockState().getMatchContext()
                .getOrCreate("parts", Collections::emptySet);
        for (var part : parts) {
            if (part.self() instanceof IndustrialSteamHatchPartMachine) {
                isOC = true;
                return;
            }
        }
    }

    /**
     * 蒸汽→EU 转换公式（mB/EU）。配增强蒸汽仓时 0.25（1 mB 蒸汽 = 4 EU），否则 1.0。
     * 需要修改转换公式时直接覆盖此方法。
     */
    @Override
    protected double getConversionRate() {
        return isOC ? ADVANCED_CONVERSION_RATE : BASE_CONVERSION_RATE;
    }

    //////////////////////////////////////
    // *** 配方处理 ***//
    //////////////////////////////////////

    @Nullable
    @Override
    protected GTRecipe getRealRecipe(@Nonnull GTRecipe recipe) {
        if (recipe.getType() != recipeType) return null;
        // 配方电压等级限制：配增强蒸汽仓时放宽到 MV，否则保持 LV。
        int maxTier = isOC ? GTValues.MV : GTValues.LV;
        if (RecipeHelper.getRecipeEUtTier(recipe) > maxTier) return null;

        int parallels = ParallelLogic.getParallelAmount(this, recipe, targetParallel);
        if (parallels == 0) return null;

        // 并行放大配方。蒸汽机不加 eutMultiplier——EUt 由蒸汽处理器按 conversionRate 折算为蒸汽。
        return ModifierFunction.builder()
                .modifyAllContents(ContentModifier.multiplier(parallels))
                .parallels(parallels)
                .build()
                .apply(recipe.copy());
    }

    //////////////////////////////////////
    // *** GUI 显示与交互 ***//
    //////////////////////////////////////

    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
        if (isFormed()) {
            textList.add(Component.translatable("gtuf.multiblock.parallel_amount", targetParallel)
                    .withStyle(ChatFormatting.GOLD));
            textList.add(Component.translatable("gtuf.gui.parallel")
                    .append(ComponentPanelWidget.withButton(
                            Component.translatable("gtuf.gui.decrease"), "parallelSub"))
                    .append(ComponentPanelWidget.withButton(
                            Component.translatable("gtuf.gui.increase"), "parallelAdd")));
        }
    }

    @Override
    public void handleDisplayClick(String componentData, ClickData clickData) {
        if (clickData.isRemote) return;
        this.targetParallel = "parallelSub".equals(componentData)
                ? adjust(targetParallel, false)
                : adjust(targetParallel, true);
    }

    private int adjust(int current, boolean increase) {
        int newValue = increase ? current * 2 : current / 2;
        return Math.max(MIN_PARALLEL, Math.min(newValue, maxParallel));
    }
}
