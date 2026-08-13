package com.liangqu.gtuf.common.machine.multiblock.steam;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IExplosionMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.Fluids;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 增强大型锅炉（基于 GTM 7.5.3 {@code LargeBoilerMachine} 完整复制并加入可配置并行）。
 *
 * <p>
 * <b>为什么标准并行对锅炉不成立：</b>GTM 的并行机制（{@code ParallelLogic} +
 * {@code modifyAllContents(N)}）只放大配方内容（燃料 item 输入）。但大型锅炉的蒸汽产出
 * <b>完全不来自配方</b>——每 5 tick 由 {@link #updateCurrentTemperature()} 里
 * {@code maxDrain = 温度 × 节流阀 × 5 / (steamPerWater×100)} 驱动的耗水量决定，与配方
 * 的 item/fluid 内容无关。直接套标准并行 = 烧 N 倍燃料、产出相同蒸汽 = 纯浪费。
 * </p>
 *
 * <p>
 * <b>本类的可行化改造：</b>在蒸汽生成处把耗水量（{@code maxDrain}）按当前并行数放大，
 * {@code steamGenerated = drained × steamPerWater} 自动随并行 1:1 翻倍；燃料配方则由
 * {@link #recipeModifier} 经 {@code modifyAllContents(N)} 放大 → N 倍燃料烧出 N 倍蒸汽
 * （并行锅炉：同一配方周期内燃料烧得更快、蒸汽出得更多）。并行数在 KubeJS 注册多方块
 * 结构时经构造器传入（{@code maxParallel}，≥1，1 = 不并行）。
 * </p>
 *
 * <p>
 * <b>时长倍率：</b>同样在注册时经构造器传入（{@code durationMultiplier}，默认 1）。
 * 它只作用于燃料燃烧时长（内类 {@link EnhancedBoilerRecipeLogic} 的 {@code setupRecipe} /
 * {@code modifyFuelBurnTime} 里把 {@code lastRecipe.duration} 乘上该值）：&gt;1 拉长单份
 * 燃料的燃烧时长、&lt;1 缩短，基准值 1 = 维持原有计算公式。蒸汽产出不受其影响（仍由
 * 温度 × 节流阀 × 并行数驱动），故提高时长倍率 = 每份燃料在相同温度下烧更久、总蒸汽量
 * 按比例增多。该值不显示在 GUI（与温度/蒸汽输出/并行数/节流阀不同）。
 * </p>
 *
 * <p>
 * 派生自 GTM {@code LargeBoilerMachine}（GPLv3）。除并行放大蒸汽生成的改动外，其余
 * 温度/节流/爆燃/显示逻辑与 7.5.3 逐字节一致。
 * </p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class EnhancedLargeBoilerMachine extends WorkableMultiblockMachine
                                        implements IExplosionMachine, IDisplayUIMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            EnhancedLargeBoilerMachine.class,
            WorkableMultiblockMachine.MANAGED_FIELD_HOLDER);
    public static final int TICKS_PER_STEAM_GENERATION = 5;

    public final int maxTemperature, heatSpeed;

    /** 最大并行数：KubeJS 注册多方块结构时经构造器传入（≥1，1 = 不并行）。 */
    private final int maxParallel;

    /** 燃料时长倍率：KubeJS 注册多方块结构时经构造器传入（>0，1 = 维持原有计算公式）。 */
    private final double durationMultiplier;

    @Persisted
    private int currentTemperature, throttle;

    @Nullable
    protected TickableSubscription temperatureSubs;
    private int steamGenerated;

    /**
     * 默认构造：不并行（maxParallel = 1）、时长倍率 1（维持原有公式）。与 GTM
     * {@code LargeBoilerMachine} 签名一致，供 testmod/工厂的 {@code ::new} 方法引用使用。
     */
    public EnhancedLargeBoilerMachine(IMachineBlockEntity holder, int maxTemperature, int heatSpeed, Object... args) {
        this(holder, maxTemperature, heatSpeed, 1, 1.0, args);
    }

    /**
     * KubeJS 注册多方块结构时指定最大并行数（时长倍率 = 1）：
     * {@code .machine((holder) => new EnhancedLargeBoilerMachine(holder, 最高温度, 加热速度, 并行数))}。
     *
     * @param maxTemperature 锅炉最高温度（℃）
     * @param heatSpeed      加热速度（每 10 tick 升温量）
     * @param maxParallel    最大并行数（≥1，1 = 不并行；蒸汽产出与燃料消耗按此 1:1 放大）
     * @param args           透传给基类的额外参数（如配方类型）
     */
    public EnhancedLargeBoilerMachine(IMachineBlockEntity holder, int maxTemperature, int heatSpeed, int maxParallel,
                                      Object... args) {
        this(holder, maxTemperature, heatSpeed, maxParallel, 1.0, args);
    }

    /**
     * KubeJS 注册多方块结构时指定最大并行数与时长倍率：
     * {@code .machine((holder) => new EnhancedLargeBoilerMachine(holder, 最高温度, 加热速度, 并行数, 时长倍率))}。
     *
     * @param maxTemperature     锅炉最高温度（℃）
     * @param heatSpeed          加热速度（每 10 tick 升温量）
     * @param maxParallel        最大并行数（≥1，1 = 不并行；蒸汽产出与燃料消耗按此 1:1 放大）
     * @param durationMultiplier 燃料时长倍率（>0，1 = 维持原有计算公式；>1 拉长单份燃料的燃烧
     *                           时长，&lt;1 缩短）。只作用于配方时长，不参与 GUI 显示。
     * @param args               透传给基类的额外参数（如配方类型）
     */
    public EnhancedLargeBoilerMachine(IMachineBlockEntity holder, int maxTemperature, int heatSpeed, int maxParallel,
                                      double durationMultiplier, Object... args) {
        super(holder, args);
        this.maxTemperature = maxTemperature;
        this.heatSpeed = heatSpeed;
        this.maxParallel = Math.max(1, maxParallel);
        this.durationMultiplier = Math.max(0.01, durationMultiplier);
        this.throttle = 100;
    }

    /** 最高温度（℃）。 */
    public int getMaxTemperature() {
        return maxTemperature;
    }

    /** 加热速度（每 10 tick 升温量）。 */
    public int getHeatSpeed() {
        return heatSpeed;
    }

    /** 当前温度（℃）。 */
    public int getCurrentTemperature() {
        return currentTemperature;
    }

    /** 当前节流阀（25~100，百分比）。 */
    public int getThrottle() {
        return throttle;
    }

    /** 最大并行数（≥1）。 */
    public int getMaxParallel() {
        return maxParallel;
    }

    /** 燃料时长倍率（>0，1 = 维持原有计算公式）。 */
    public double getDurationMultiplier() {
        return durationMultiplier;
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    //////////////////////////////////////
    // ****** Recipe Logic ******//
    //////////////////////////////////////

    @Override
    public EnhancedBoilerRecipeLogic createRecipeLogic(Object... args) {
        return new EnhancedBoilerRecipeLogic(this);
    }

    @Override
    public EnhancedBoilerRecipeLogic getRecipeLogic() {
        return (EnhancedBoilerRecipeLogic) super.getRecipeLogic();
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        if (getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new TickTask(0, this::updateSteamSubscription));
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        if (getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new TickTask(0, this::updateSteamSubscription));
        }
    }

    @Override
    public void onUnload() {
        if (temperatureSubs != null) {
            temperatureSubs.unsubscribe();
            temperatureSubs = null;
        }
        super.onUnload();
    }

    protected void updateSteamSubscription() {
        if (currentTemperature > 0) {
            temperatureSubs = subscribeServerTick(temperatureSubs, this::updateCurrentTemperature);
        } else if (temperatureSubs != null) {
            temperatureSubs.unsubscribe();
            temperatureSubs = null;
        }
    }

    /** 当前生效的并行数：取正在运行的配方的 {@code parallels}（未运行或无配方 = 1）。 */
    protected int getCurrentParallels() {
        GTRecipe last = getRecipeLogic().getLastRecipe();
        return last == null ? 1 : Math.max(1, last.parallels);
    }

    protected void updateCurrentTemperature() {
        if (recipeLogic.isWorking()) {
            if (getOffsetTimer() % 10 == 0) {
                if (currentTemperature < getMaxTemperature()) {
                    currentTemperature = Mth.clamp(currentTemperature + heatSpeed * 10, 0, getMaxTemperature());
                }
            }
        } else if (currentTemperature > 0) {
            currentTemperature -= getCoolDownRate();
        }

        if (isFormed() && getOffsetTimer() % TICKS_PER_STEAM_GENERATION == 0) {
            // 并行放大的关键：maxDrain（本 5 tick 的耗水请求量）乘并行数。
            // 燃料被并行配方烧 N 倍快，水与蒸汽产出也按 N 倍走（1:1）。长整型乘法防
            // 高温 + 大并行下 temp*throttle*TICKS*parallels 超 int。
            int parallels = getCurrentParallels();
            long maxDrain = (long) currentTemperature * throttle * TICKS_PER_STEAM_GENERATION * parallels /
                    ((long) ConfigHolder.INSTANCE.machines.largeBoilers.steamPerWater * 100);
            if (currentTemperature < 100) {
                steamGenerated = 0;
            } else if (maxDrain > 0) { // if maxDrain is 0 because throttle is too low, skip trying to make steam
                // drain water
                int maxDrainInt = (int) Math.min(Integer.MAX_VALUE, maxDrain);
                var drainWater = List.of(FluidIngredient.of(Fluids.WATER, maxDrainInt));
                List<IRecipeHandler<?>> inputTanks = new ArrayList<>();
                inputTanks.addAll(getCapabilitiesFlat(IO.IN, FluidRecipeCapability.CAP));
                inputTanks.addAll(getCapabilitiesFlat(IO.BOTH, FluidRecipeCapability.CAP));
                for (IRecipeHandler<?> tank : inputTanks) {
                    drainWater = (List<FluidIngredient>) tank.handleRecipe(IO.IN, null, drainWater, false);
                    if (drainWater == null || drainWater.isEmpty()) {
                        break;
                    }
                }
                int drained = (drainWater == null || drainWater.isEmpty()) ? maxDrainInt :
                        maxDrainInt - drainWater.get(0).getAmount();

                steamGenerated = (int) Math.min(Integer.MAX_VALUE,
                        (long) drained * ConfigHolder.INSTANCE.machines.largeBoilers.steamPerWater);

                if (drained > 0) {
                    // fill steam
                    var fillSteam = List.of(FluidIngredient.of(GTMaterials.Steam.getFluid(steamGenerated)));
                    List<IRecipeHandler<?>> outputTanks = new ArrayList<>();
                    outputTanks.addAll(getCapabilitiesFlat(IO.OUT, FluidRecipeCapability.CAP));
                    outputTanks.addAll(getCapabilitiesFlat(IO.BOTH, FluidRecipeCapability.CAP));
                    for (IRecipeHandler<?> tank : outputTanks) {
                        fillSteam = (List<FluidIngredient>) tank.handleRecipe(IO.OUT, null, fillSteam, false);
                        if (fillSteam == null) break;
                    }
                }

                // check explosion
                if (drained < maxDrain) {
                    doExplosion(2f);
                    var center = getPos().below().relative(getFrontFacing().getOpposite());
                    if (GTValues.RNG.nextInt(100) > 80) {
                        doExplosion(center, 2f);
                    }
                    for (Direction x : Direction.Plane.HORIZONTAL) {
                        for (Direction y : Direction.Plane.HORIZONTAL) {
                            if (GTValues.RNG.nextInt(100) > 80) {
                                doExplosion(center.relative(x).relative(y), 2f);
                            }
                        }
                    }
                }
            }
        }
        updateSteamSubscription();
    }

    protected int getCoolDownRate() {
        return 1;
    }

    @Override
    public boolean onWorking() {
        boolean value = super.onWorking();
        if (currentTemperature < getMaxTemperature()) {
            currentTemperature = Math.max(1, currentTemperature);
            updateSteamSubscription();
        }
        return value;
    }

    /**
     * Recipe Modifier for <b>Enhanced Large Boiler Machines</b> - can be used as a valid {@link RecipeModifier}.
     * <p>
     * 并行数 &gt; 1 时放大燃料配方内容（N 倍燃料），并把 {@code parallels} 写到配方上供
     * {@link #updateCurrentTemperature()} 按 N 倍产出蒸汽。并行数 = 1 时退化为
     * {@link ModifierFunction#IDENTITY}（与原生锅炉一致，节流阀时长由
     * {@link EnhancedBoilerRecipeLogic} 处理）。
     * </p>
     *
     * @param machine a {@link EnhancedLargeBoilerMachine}
     * @param recipe  recipe
     * @return A {@link ModifierFunction} for the given boiler and recipe
     */
    public static ModifierFunction recipeModifier(@NotNull MetaMachine machine, @NotNull GTRecipe recipe) {
        if (!(machine instanceof EnhancedLargeBoilerMachine boiler)) {
            return RecipeModifier.nullWrongType(EnhancedLargeBoilerMachine.class, machine);
        }
        int parallels = ParallelLogic.getParallelAmount(machine, recipe, boiler.getMaxParallel());
        if (parallels <= 1) return ModifierFunction.IDENTITY;
        return ModifierFunction.builder()
                .modifyAllContents(ContentModifier.multiplier(parallels))
                .parallels(parallels)
                .build();
    }

    public void addDisplayText(List<Component> textList) {
        IDisplayUIMachine.super.addDisplayText(textList);
        if (isFormed()) {
            textList.add(Component.translatable("gtuf.multiblock.enhanced_boiler.temperature",
                    currentTemperature + 274, maxTemperature + 274));
            textList.add(Component.translatable("gtuf.multiblock.enhanced_boiler.steam_output",
                    steamGenerated / TICKS_PER_STEAM_GENERATION));
            textList.add(Component.translatable("gtuf.multiblock.parallel_amount", getMaxParallel())
                    .withStyle(ChatFormatting.GOLD));

            var throttleText = Component.translatable("gtuf.multiblock.enhanced_boiler.throttle",
                    ChatFormatting.AQUA.toString() + getThrottle() + "%")
                    .withStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.translatable("gtuf.multiblock.enhanced_boiler.throttle.tooltip"))));
            textList.add(throttleText);

            var buttonText = Component.translatable("gtuf.multiblock.enhanced_boiler.throttle_modify");
            buttonText.append(" ");
            buttonText.append(ComponentPanelWidget.withButton(Component.literal("[-]"), "sub"));
            buttonText.append(" ");
            buttonText.append(ComponentPanelWidget.withButton(Component.literal("[+]"), "add"));
            textList.add(buttonText);
        }
    }

    public void handleDisplayClick(String componentData, ClickData clickData) {
        if (!clickData.isRemote) {
            int result = componentData.equals("add") ? 5 : -5;
            this.throttle = Mth.clamp(throttle + result, 25, 100);
            getRecipeLogic().modifyFuelBurnTime(throttle);
        }
    }

    @Override
    public IGuiTexture getScreenTexture() {
        return GuiTextures.DISPLAY_STEAM.get(maxTemperature > 800);
    }

    /**
     * 内类配方逻辑：原生 {@code LargeBoilerRecipeLogic} 的完整复制，处理节流阀（throttle）
     * 与时长倍率（durationMultiplier）对燃料燃烧时长的缩放。并行由外类的
     * {@code recipeModifier} 与 {@code updateCurrentTemperature} 处理，与本类正交。
     */
    public class EnhancedBoilerRecipeLogic extends RecipeLogic {

        int currentThrottle;

        public EnhancedBoilerRecipeLogic(IRecipeLogicMachine machine) {
            super(machine);
            this.currentThrottle = 100;
        }

        @Override
        public void setupRecipe(GTRecipe recipe) {
            super.setupRecipe(recipe);
            if (lastRecipe != null) {
                currentThrottle = ((EnhancedLargeBoilerMachine) machine).getThrottle();
                duration = (int) Math.round(
                        lastRecipe.duration * ((EnhancedLargeBoilerMachine) machine).getDurationMultiplier() /
                                (currentThrottle / 100.0));
            }
        }

        public void modifyFuelBurnTime(int value) {
            if (lastRecipe != null) {
                double scale = (double) currentThrottle / value;
                duration = (int) Math.round(lastRecipe.duration *
                        ((EnhancedLargeBoilerMachine) machine).getDurationMultiplier() / (value / 100.0));
                progress = (int) Math.round(progress * scale);
            }
            currentThrottle = value;
        }

        public int getCurrentThrottle() {
            return currentThrottle;
        }
    }
}
