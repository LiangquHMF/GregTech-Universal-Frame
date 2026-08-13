package com.liangqu.gtuf.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.liangqu.gtuf.api.data.material.PressurePipeProperties;
import com.liangqu.gtuf.api.machine.IPressureMachine;
import com.liangqu.gtuf.api.machine.ITieredCasingMachine;
import com.liangqu.gtuf.api.pattern.GTUF_PatternPredicates;
import com.liangqu.gtuf.api.pipelike.pressurepipe.PressurePipeNet;
import com.liangqu.gtuf.api.pressure.GTUF_Pressure;
import com.liangqu.gtuf.common.machine.multiblock.part.PressureHatchPartMachine;
import com.liangqu.gtuf.common.recipe.GTUFPressureRecipeLogic;
import com.liangqu.gtuf.config.GTUF_Config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * 压力多方块机（电力版）：自带压力腔（{@code chamberPressure}，kPa）。
 *
 * <ul>
 * <li><b>外壳等级</b>（{@code UniversalCasingTier}）：决定腔压向大气压（101.325 kPa）回归的
 * 恒定绝对速率 {@code relaxRate = relaxBaseRateKpa / casingTier}（kPa/tick）——等级越高回归越慢。</li>
 * <li><b>玻璃等级</b>（{@code GlassTier}）：决定腔压<b>硬钳制</b>上下限
 * {@code min = max(0.1, glassMinBase - (tier-1)*glassMinStep)}、
 * {@code max = glassMaxBase + (tier-1)*glassMaxStep}——等级越高下限越低、上限越高。
 * 腔压达到上/下限后钳制不再越过（配方仍照常运行、继续产压/接受压力，腔压保持在上/下限值）；
 * 配方侧如需限压用 {@code .RequiredP(min,max)} 条件（{@code PressureCondition} 逐 tick 复查）。</li>
 * <li><b>配方产压/抽压</b>：{@code .Pressure(kpa)} 配方完成后 {@code chamberPressure += 量}
 * （正数=加压、负数=抽压），随后钳制回玻璃上下限。</li>
 * <li><b>创建期可配置项</b>（KubeJS 注册多方块结构时经构造器传入，final 运行时只读）：
 * 最大并行数 + 时长/能耗倍率（作用于配方修改器，不在 GUI 显示）+ 额外压力回归倍率
 * （乘进 {@link #getRelaxAbsRate()}）+ 额外耐压倍率（缩放 {@link #getPressureMin()}
 * 与 {@link #getPressureMax()} 上下限）。各倍率基准值 1.0（不生效，保持原本计算），
 * 可大于 1 或小于 1。并行产压/抽压量经 {@code lastRecipe.parallels} 同步放大
 * （GTRecipe.data 不随 ContentModifier 变化）。</li>
 * <li><b>压力传导</b>：经压力仓接入压力管道网络，每 4 tick 与同网络 peer 机腔压 Jacobi 均衡
 * {@code p += (mean - p) * conductionRate}。</li>
 * <li><b>管道破裂</b>：每 4 tick 取网络组内最大腔压为有效压力，超管道节点 max/min 容限的
 * 节点先收集后破坏（网络自动分裂）。</li>
 * </ul>
 *
 * <p>
 * 腔压只存于机器腔体（恒定绝对速率向大气回归并钳制到大气压；玻璃上下限为硬钳制边界，
 * 配方产压/抽压后被钳回 [min,max] 不再越过），管道与压力仓仅传导不存储。
 * 注册时挂载 {@code .recipeModifier(PressureMultiblockMachine::recipeModifier, true)}。
 * </p>
 */
public class PressureMultiblockMachine extends WorkableElectricMultiblockMachine
                                       implements IPressureMachine, ITieredCasingMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            PressureMultiblockMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** 压力传导/破裂检查周期（tick）。 */
    private static final long CONDUCTION_PERIOD = 4;

    /** 并行数下限（1 = 不并行，原始计算）。 */
    private static final int MIN_PARALLEL = 1;

    /** 默认最大并行数（1 = 不并行）。 */
    public static final int DEFAULT_MAX_PARALLEL = 1;
    /** 默认时长倍率（1.0 = 不改变配方时长）。 */
    public static final double DEFAULT_DURATION_MULTIPLIER = 1.0;
    /** 默认能耗倍率（1.0 = 不改变配方能耗）。 */
    public static final double DEFAULT_ENERGY_MULTIPLIER = 1.0;
    /** 默认压力回归倍率（1.0 = 不改变回归速率）。 */
    public static final double DEFAULT_RELAX_MULTIPLIER = 1.0;
    /** 默认耐压倍率（1.0 = 不改变压力上下限）。 */
    public static final double DEFAULT_TOLERANCE_MULTIPLIER = 1.0;

    /** 腔体压力（kPa），初始为标准大气压。持久化并同步客户端（GUI 显示）。 */
    @Persisted
    @DescSynced
    private double chamberPressure;

    /** 外壳等级（钢=1…钨钢=5），决定回归速率。@RequireRerender：成型换等级时客户端重烘焙外观。 */
    @Persisted
    @DescSynced
    @RequireRerender
    private int casingTier = 1;

    /** 玻璃等级，决定腔压上下限。 */
    @Persisted
    @DescSynced
    private int glassTier = 1;

    /** 创建（KubeJS 注册）时设置的最大并行数，final 运行时只读，不在 GUI 显示。 */
    private final int maxParallel;

    /**
     * 创建时设置的时长倍率（1.0 = 不改变配方时长；>1 变慢、<1 变快；≤0 视为关闭=1.0）。
     * 作用于配方修改器，不在 GUI 显示。
     */
    private final double durationMultiplier;

    /**
     * 创建时设置的能耗倍率（1.0 = 不改变配方能耗；>1 加耗、<1 省耗；≤0 视为关闭=1.0）。
     * 作用于配方修改器，不在 GUI 显示。
     */
    private final double energyMultiplier;

    /** 创建时设置的压力回归倍率（1.0 = 原回归速率；>1 更快、<1 更慢；≤0 视为关闭=1.0）。 */
    private final double relaxMultiplier;

    /** 创建时设置的耐压倍率（1.0 = 原上下限；>1 放宽、<1 收紧；≤0 视为关闭=1.0）。 */
    private final double toleranceMultiplier;

    /**
     * 结构实际使用的外壳方块注册名（{@code Casing(String...)} 谓词写入 MatchContext、成型时读取）。
     * 压力机结构用 {@code UniversalCasingTier} 谓词（无 Casing 谓词）时恒为 null——仓室与控制器
     * 由 {@link #getCasingState()} 按外壳等级映射渲染。
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

    /** 压力 tick 订阅句柄（onLoad 订阅，onUnload 由基类统一退订）。 */
    private TickableSubscription pressureTickSubscription;

    /** 默认构造：并行=1、所有倍率=1.0（全部不生效，保持原始计算）。 */
    public PressureMultiblockMachine(IMachineBlockEntity holder, Object... args) {
        this(holder, DEFAULT_MAX_PARALLEL, DEFAULT_DURATION_MULTIPLIER, DEFAULT_ENERGY_MULTIPLIER,
                DEFAULT_RELAX_MULTIPLIER, DEFAULT_TOLERANCE_MULTIPLIER, args);
    }

    /** 便捷构造：仅指定最大并行数，其余倍率取默认（1.0，不生效）。 */
    public PressureMultiblockMachine(IMachineBlockEntity holder, int maxParallel, Object... args) {
        this(holder, maxParallel, DEFAULT_DURATION_MULTIPLIER, DEFAULT_ENERGY_MULTIPLIER,
                DEFAULT_RELAX_MULTIPLIER, DEFAULT_TOLERANCE_MULTIPLIER, args);
    }

    /**
     * 完整构造：所有可配置项均在创建（KubeJS 注册多方块结构）时设定，final 运行时只读。
     * 各倍率基准值为 1.0（不生效，保持原本计算方法）；>1 与 <1 均可，按各自语义缩放。
     *
     * @param maxParallel         最大并行数（≥1；1 = 不并行）
     * @param durationMultiplier  时长倍率（1.0 = 原时长；0.5 = 提速一倍；2.0 = 变慢一倍；
     *                            ≤0 视为关闭=1.0），不在 GUI 显示
     * @param energyMultiplier    能耗倍率（1.0 = 原能耗；0.5 = 半能耗；2.0 = 双倍能耗；
     *                            ≤0 视为关闭=1.0），不在 GUI 显示
     * @param relaxMultiplier     额外压力回归倍率（1.0 = 原回归速率；2.0 = 回归快一倍；
     *                            0.5 = 回归慢一倍；≤0 视为关闭=1.0）
     * @param toleranceMultiplier 额外耐压倍率（1.0 = 原上下限；2.0 = 上限×2、下限÷2 放宽；
     *                            0.5 = 上限÷2、下限×2 收紧；≤0 视为关闭=1.0）
     * @param args                透传给基类的额外参数（如配方类型）
     */
    public PressureMultiblockMachine(IMachineBlockEntity holder, int maxParallel, double durationMultiplier,
                                     double energyMultiplier, double relaxMultiplier, double toleranceMultiplier,
                                     Object... args) {
        super(holder, args);
        this.chamberPressure = GTUF_Pressure.atmosphericKpa();
        this.maxParallel = Math.max(MIN_PARALLEL, maxParallel);
        this.durationMultiplier = durationMultiplier;
        this.energyMultiplier = energyMultiplier;
        this.relaxMultiplier = relaxMultiplier;
        this.toleranceMultiplier = toleranceMultiplier;
    }

    /**
     * 使用 {@link GTUFPressureRecipeLogic}：标准 RecipeDB 搜索找不到配方时回退扫描
     * "纯电力"压力配方（0 物品/流体输入，仅 EU 即可运行加压/抽压）。带物品/流体输入的
     * 普通压力配方仍走标准搜索。
     */
    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new GTUFPressureRecipeLogic(this);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    //////////////////////////////////////
    // *** 创建期可配置项（KubeJS 注册时设定）***//
    //////////////////////////////////////

    /** 创建时设置的最大并行数（≥1；1 = 不并行）。 */
    public int getMaxParallel() {
        return maxParallel;
    }

    /** 时长倍率（≤0 视为关闭，返回 1.0 不改动时长）。 */
    public double getDurationMultiplier() {
        return durationMultiplier > 0 ? durationMultiplier : 1.0;
    }

    /** 能耗倍率（≤0 视为关闭，返回 1.0 不改动能耗）。 */
    public double getEnergyMultiplier() {
        return energyMultiplier > 0 ? energyMultiplier : 1.0;
    }

    /** 压力回归倍率（≤0 视为关闭，返回 1.0 不改动回归速率）。 */
    public double getRelaxMultiplier() {
        return relaxMultiplier > 0 ? relaxMultiplier : 1.0;
    }

    /** 耐压倍率（≤0 视为关闭，返回 1.0 不改动上下限）。 */
    public double getToleranceMultiplier() {
        return toleranceMultiplier > 0 ? toleranceMultiplier : 1.0;
    }

    /** 时长或能耗倍率是否任一 ≠1（决定并行放大后是否还需叠倍率）。 */
    private boolean hasRecipeMultipliers() {
        return getDurationMultiplier() != 1.0 || getEnergyMultiplier() != 1.0;
    }

    //////////////////////////////////////
    // *** Multiblock LifeCycle ***//
    //////////////////////////////////////

    @Override
    public void onLoad() {
        super.onLoad();
        pressureTickSubscription = subscribeServerTick(this::pressureTick);
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        var ctx = getMultiblockState().getMatchContext();
        if (ctx.get(GTUF_PatternPredicates.UNIVERSAL_CASING_TIER_KEY) instanceof Integer tier) casingTier = tier;
        if (ctx.get(GTUF_PatternPredicates.GLASS_TIER_KEY) instanceof Integer tier) glassTier = tier;
        readStructureCasing();
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        casingTier = 1;
        glassTier = 1;
        resetStructureCasing();
        // 腔压保留：压力存储在腔体，结构失效不丢失。
    }

    //////////////////////////////////////
    // *** 压力公式（IPressureMachine）***//
    //////////////////////////////////////

    @Override
    public double getPressure() {
        return chamberPressure;
    }

    @Override
    public double getPressureMin() {
        double baseMin = Math.max(0.1, GTUF_Config.getPressureGlassMinBaseKpa() - (glassTier - 1) * GTUF_Config
                .getPressureGlassMinStep());
        // 耐压倍率 >1 放宽下限（更耐压）、<1 收紧下限；除以倍率，保持非负。
        return Math.max(0.1, baseMin / getToleranceMultiplier());
    }

    @Override
    public double getPressureMax() {
        double baseMax = GTUF_Config.getPressureGlassMaxBaseKpa() + (glassTier - 1) * GTUF_Config
                .getPressureGlassMaxStep();
        // 耐压倍率 >1 放宽上限、<1 收紧上限；保证上限恒大于下限（退化防御）。
        return Math.max(getPressureMin() + 1.0, baseMax * getToleranceMultiplier());
    }

    @Override
    public int getCasingTier() {
        return casingTier;
    }

    @Override
    public int getGlassTier() {
        return glassTier;
    }

    //////////////////////////////////////
    // *** 等级外观（ITieredCasingMachine）***//
    //////////////////////////////////////

    /**
     * 外壳等级 → 外壳方块状态（供控制器自身与仓室渲染匹配外壳，与
     * {@link GTUF_PatternPredicates#UniversalCasingTier()} 的等级映射一致）：
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
     * 成型后控制器/仓室外观 = 结构外壳等级方块（{@link #getCasingState()}），使控制器与仓室、
     * 仓室与外壳的连接纹理（CTM）/面剔除无缝。压力机是等级外壳机器（无 Casing 谓词，
     * structureCasingId 恒 null），{@code getControllerAppearanceState()} 返回 null 由
     * {@code GTUFTieredPartRender} 回退到本方法；部件外观走 {@link #getPartAppearance} 默认实现
     * （formed 时返回 {@link #getCasingState()}）。
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

    /**
     * 每 tick 向大气压回归的恒定绝对速率（kPa/tick）：
     * {@code relaxBaseRateKpa / max(1, casingTier)}。无论偏离大气压多远，每 tick 固定掉该量
     * （钳制到大气压停止），高压时掉速不再随偏离暴涨——超高压配方可维持。
     */
    public double getRelaxAbsRate() {
        // 压力回归倍率 >1 回归更快、<1 更慢；1.0 = 原本计算。
        return GTUF_Config.getPressureRelaxBaseRateKpa() / Math.max(1, casingTier) * getRelaxMultiplier();
    }

    /**
     * 把腔压钳制到玻璃硬上下限 {@code [getPressureMin(), getPressureMax()]}。
     * 达到上/下限后腔压保持在该边界值不再越过（配方照常运行，继续产压/接受压力）。
     */
    private void clampToBounds() {
        chamberPressure = Math.max(getPressureMin(), Math.min(getPressureMax(), chamberPressure));
    }

    //////////////////////////////////////
    // *** 配方产压/抽压 ***//
    //////////////////////////////////////

    @Override
    public void afterWorking() {
        super.afterWorking();
        GTRecipe lastRecipe = getRecipeLogic().getLastRecipe();
        if (lastRecipe != null && lastRecipe.data != null) {
            double produce = lastRecipe.data.getDouble(GTUF_Pressure.Keys.PRODUCE);
            if (produce != 0) {
                // 有符号：正数=加压机加压，负数=抽压机抽压。随后钳制到玻璃上下限。
                // 并行放大后 GTRecipe.data 不随 ContentModifier 变化，需乘并行数使产压/抽压同步
                // （纯 EU 配方 getParallelAmount 直接返回 maxParallel，并行会放大能耗与产压）。
                chamberPressure += produce * Math.max(1, lastRecipe.parallels);
                clampToBounds();
            }
        }
    }

    //////////////////////////////////////
    // *** 压力 tick：回归 + 传导 + 破裂 ***//
    //////////////////////////////////////

    private void pressureTick() {
        Level level = getLevel();
        if (level == null || level.isClientSide) return;

        // 每 tick：向大气压回归（恒定绝对速率，钳制到大气压不再越过）。
        double atm = GTUF_Pressure.atmosphericKpa();
        if (chamberPressure > atm) {
            chamberPressure = Math.max(atm, chamberPressure - getRelaxAbsRate());
        } else if (chamberPressure < atm) {
            chamberPressure = Math.min(atm, chamberPressure + getRelaxAbsRate());
        }
        // 玻璃硬钳制：腔压保持在上/下限内（即使传导推超也拉回）。
        clampToBounds();

        if (!isFormed()) return;
        if ((level.getGameTime() & (CONDUCTION_PERIOD - 1)) != 0) return;

        // 收集本机压力仓相连的压力网络。
        Set<PressurePipeNet> nets = new HashSet<>();
        for (IMultiPart part : getParts()) {
            if (part instanceof PressureHatchPartMachine hatch) {
                nets.addAll(hatch.getConnectedNets());
            }
        }
        if (nets.isEmpty()) return;

        // 收集网络内 peer 压力机（IdentityHashMap 身份去重，peer != this）。
        IdentityHashMap<IPressureMachine, Boolean> group = new IdentityHashMap<>();
        group.put(this, Boolean.TRUE);
        List<IPressureMachine> peers = new ArrayList<>();
        for (PressurePipeNet net : nets) {
            for (BlockPos nodePos : net.getAllNodes().keySet()) {
                for (Direction facing : GTUtil.DIRECTIONS) {
                    if (MetaMachine.getMachine(level,
                            nodePos.relative(facing)) instanceof PressureHatchPartMachine hatch) {
                        for (IMultiController controller : hatch.getControllers()) {
                            if (controller instanceof IPressureMachine peer && peer != this &&
                                    !group.containsKey(peer)) {
                                group.put(peer, Boolean.TRUE);
                                peers.add(peer);
                            }
                        }
                    }
                }
            }
        }

        // Jacobi 均衡：向组均值逼近。
        if (!peers.isEmpty()) {
            double sum = chamberPressure;
            for (IPressureMachine peer : peers) {
                sum += peer.getPressure();
            }
            double mean = sum / (peers.size() + 1);
            chamberPressure += (mean - chamberPressure) * GTUF_Config.getPressureConductionRate();
        }

        // 破裂检查：每 net 取组内最大腔压为有效压力，超节点 max/min 容限则先收集后破坏。
        // 先收集完再 destroy，绝不在遍历 net 节点图时 mutate（网络分裂安全）。
        List<BlockPos> toBreak = new ArrayList<>();
        for (PressurePipeNet net : nets) {
            double netPressure = chamberPressure;
            for (BlockPos nodePos : net.getAllNodes().keySet()) {
                for (Direction facing : GTUtil.DIRECTIONS) {
                    if (MetaMachine.getMachine(level,
                            nodePos.relative(facing)) instanceof PressureHatchPartMachine hatch) {
                        for (IMultiController controller : hatch.getControllers()) {
                            if (controller instanceof IPressureMachine peer && peer != this) {
                                netPressure = Math.max(netPressure, peer.getPressure());
                            }
                        }
                    }
                }
            }
            for (Map.Entry<BlockPos, com.gregtechceu.gtceu.api.pipenet.Node<PressurePipeProperties>> entry : net
                    .getAllNodes().entrySet()) {
                var props = entry.getValue().data;
                if (netPressure > props.getMaxPressureKpa() || netPressure < props.getMinPressureKpa()) {
                    toBreak.add(entry.getKey());
                }
            }
        }
        for (BlockPos pos : toBreak) {
            if (level.isLoaded(pos)) {
                level.destroyBlock(pos, true);
            }
        }
    }

    //////////////////////////////////////
    // *** 配方修改器 ***//
    //////////////////////////////////////

    /**
     * 配方修改器：先按 {@link #getMaxParallel()} 并行放大配方，乘能耗/时长倍率，再完美过时钟
     * （OC 基于放大后的配方计算）。玻璃上下限已改为硬钳制——腔压被钳在
     * {@code [getPressureMin(), getPressureMax()]} 不再越过，配方不再因超限而失效；加压/抽压
     * 配方达到上/下限后照常运行，腔压保持在上/下限值。配方侧如需限压另用
     * {@code .RequiredP(min,max)}（{@code PressureCondition}）。
     */
    public static ModifierFunction recipeModifier(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof PressureMultiblockMachine pressureMachine)) return ModifierFunction.NULL;

        int parallels = ParallelLogic.getParallelAmount(machine, recipe, pressureMachine.getMaxParallel());
        if (parallels == 0) return ModifierFunction.NULL;

        // 并行数为 1 且时长/能耗倍率均不启用时退化为 IDENTITY（不放大、直接过时钟）。
        ModifierFunction parallelFunc = parallels == 1 && !pressureMachine.hasRecipeMultipliers() ?
                ModifierFunction.IDENTITY :
                ModifierFunction.builder()
                        .modifyAllContents(ContentModifier.multiplier(parallels))
                        // 能耗倍率乘进 eutMultiplier（并行份数 × 倍率）
                        .eutMultiplier(parallels * pressureMachine.getEnergyMultiplier())
                        // 时长倍率单独乘
                        .durationMultiplier(pressureMachine.getDurationMultiplier())
                        .parallels(parallels)
                        .build();

        // 先并行放大配方，再过时钟（OC 基于放大后的配方计算）
        return recipe1 -> {
            GTRecipe paralleled = parallelFunc.apply(recipe1);
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
            textList.add(Component.translatable("gtuf.multiblock.pressure", GTUF_Pressure.format(chamberPressure))
                    .withStyle(ChatFormatting.AQUA));
            textList.add(Component.translatable("gtuf.multiblock.pressure_bounds",
                    GTUF_Pressure.format(getPressureMin()), GTUF_Pressure.format(getPressureMax()))
                    .withStyle(ChatFormatting.GRAY));
            textList.add(Component.translatable("gtuf.multiblock.pressure_rate",
                    GTUF_Pressure.format(getRelaxAbsRate()))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * 右侧配置面板：在基类（并行开关、开关机电源按钮）之后追加"一键释放压力"按钮。
     * 复用 GTM 开关机按钮的 {@link IFancyConfiguratorButton} 机制——图标 tab 点击即触发
     * {@link #releasePressure()}，无需改动主页面布局。
     */
    @Override
    public void attachConfigurators(ConfiguratorPanel panel) {
        super.attachConfigurators(panel);
        panel.attachConfigurators(new IFancyConfiguratorButton() {

            @Override
            public Component getTitle() {
                return Component.translatable("gtuf.multiblock.pressure_release");
            }

            @Override
            public IGuiTexture getIcon() {
                return GuiTextures.TANK_ICON;
            }

            @Override
            public Widget createConfigurator() {
                return new WidgetGroup(0, 0, 10, 10);
            }

            @Override
            public List<Component> getTooltips() {
                return List.of(Component.translatable("gtuf.multiblock.pressure_release"),
                        Component.translatable("gtuf.multiblock.pressure_release.tooltip"));
            }

            @Override
            public void onClick(ClickData clickData) {
                releasePressure();
            }
        });
    }

    /**
     * 一键释放压力：先停掉配方逻辑（否则正在运行的加压/抽压配方会在下次完成时立刻把腔压拉回原值
     * {@link IFancyConfiguratorButton} 的 {@code onClick} 在客户端本地触发且经
     * {@code writeClientAction} 在服务端再触发一次，故仅服务端执行修改（客户端守卫）。
     */
    private void releasePressure() {
        if (isRemote()) {
            return;
        }
        getRecipeLogic().setWorkingEnabled(false);
        chamberPressure = GTUF_Pressure.atmosphericKpa();
        markAsDirty();
    }
}
