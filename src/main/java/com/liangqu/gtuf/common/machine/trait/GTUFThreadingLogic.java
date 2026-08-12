package com.liangqu.gtuf.common.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.ActionResult;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.liangqu.gtuf.api.machine.IThreadModifierMachine;
import com.liangqu.gtuf.common.machine.multiblock.part.ThreadHatchPartMachine;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 线程化多槽核心：让任意带 {@link RecipeLogic} 的电力多方块同时处理多类配方。
 *
 * <p>
 * 本类<b>不是</b> {@link MachineTrait}，也不继承 {@link RecipeLogic}——它是组合式的
 * 普通对象，由 {@code GTUFRecipeLogicMixin}（{@code @Mixin(RecipeLogic.class)}）在
 * 线程模式下挂到 RecipeLogic 实例上（{@code @Unique} 字段）。原因：{@link MachineTrait}
 * 构造器会 {@code machine.attachTraits(this)}，任何 {@code new} 出的 RecipeLogic 子类
 * 都会被重新 attach 进机器 traits（双重冲突）；组合式只操作已有实例，无此问题。
 * </p>
 *
 * <p>
 * 多槽核心机制（来源 GTNA {@code com.raishxn.gtna.common.machine.trait.
 * GTNAMultipleRecipesLogic}）：用 {@link List}<{@link ActiveRecipe}> 替代 GTM 单配方
 * {@code lastRecipe}，每个活跃配方独立进度、独立计时；输入在启动时预扣（
 * {@link RecipeHelper#handleRecipeIO} IO.IN），完成时统一输出（IO.OUT）。空闲线程
 * 对每个 recipeType 调 {@link GTRecipeType#searchRecipe} 收集候选，逐个启动——不同
 * 配方类型各自查找、不同配方 ID 可并行，同 ID 配方去重。
 * </p>
 *
 * <p>
 * 与 GTNA 的差异（本实现修正了 GTNA 的能量缺陷）：GTNA 的 serverTick 只推进进度、
 * 从不调 tick 级 IO，导致配方<b>不消耗 EU/t</b>；本实现每 tick 先
 * {@link RecipeHelper#handleTickRecipeIO} 扣减 tick 级输入（EU/t 等），不足则该线程
 * 本 tick 停住不推进，补足能量后继续。
 * </p>
 *
 * <p>
 * 状态推进复用 {@link RecipeLogic#setStatus}（public），从而复用其全部副作用：
 * notifyStatusChanged / renderState 同步 / updateTickSubscription / DescSynced 同步——
 * 客户端方块渲染与 GUI 工作状态无需额外代码即可跟随。
 * </p>
 */
public class GTUFThreadingLogic {

    /** 每 tick 单类型最多查找的候选配方数（来源 GTNA 的 30）。 */
    private static final int SEARCH_LIMIT = 30;

    /** 空闲节流间隔：无线程活跃且不订阅时每 5 tick 才搜索一次新配方。 */
    private static final long IDLE_SEARCH_INTERVAL = 5;

    /** 一个活跃配方：独立进度 + 独立机会缓存。来源 GTNA {@code GTNARecipeUtils.ActiveRecipe}。 */
    public static class ActiveRecipe {

        public final GTRecipe recipe;
        public int progress;
        public final int maxProgress;
        public final Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches;

        public ActiveRecipe(GTRecipe recipe, int maxProgress,
                            Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches) {
            this.recipe = recipe;
            this.progress = 0;
            this.maxProgress = maxProgress;
            this.chanceCaches = chanceCaches;
        }

        /** @return true 表示该配方已完成，调用方需处理输出并移除 */
        public boolean update() {
            this.progress++;
            return this.progress >= this.maxProgress;
        }
    }

    /** 宿主机器（IRecipeLogicMachine 即能力持有者，构造时由 RecipeLogic.machine 传入）。 */
    private final IRecipeLogicMachine machine;
    /** 被 Mixin 组合的 RecipeLogic 实例，用于 setStatus / isWorkingEnabled 等宿主方法。 */
    private final RecipeLogic outer;
    /** 每个活跃配方独立的机会缓存表（避免并行运行时各线程机会互相覆盖）。 */
    private final Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches;
    private final List<ActiveRecipe> activeRecipes = new ArrayList<>();

    public GTUFThreadingLogic(IRecipeLogicMachine machine, RecipeLogic outer) {
        this.machine = machine;
        this.outer = outer;
        this.chanceCaches = makeChanceCaches();
    }

    /** @return 当前并行运行的配方数（线程数） */
    public int getActiveRecipeCount() {
        return activeRecipes.size();
    }

    /**
     * @return 线程总数 = 基准 1 + 线程仓额外线程，至少 1。
     *         线程仓先查 {@link IThreadModifierMachine} 登记，再回退
     *         {@link GTUFThreadRegistry}（Mixin 推广下普通 GTM 机器不实现该接口）。
     */
    public int getMaxThreads() {
        int threads = 1;
        ThreadHatchPartMachine part = findThreadPart();
        if (part != null) {
            threads += part.getCurrentThread() - 1;
        }
        return Math.max(1, threads);
    }

    /** @return 活跃配方列表（供控制器 GUI / Jade 显示用，只读语义） */
    public List<ActiveRecipe> getActiveRecipesForDisplay() {
        return activeRecipes;
    }

    /** @return 结构中登记的线程仓，无则 null */
    @Nullable
    private ThreadHatchPartMachine findThreadPart() {
        if (machine instanceof IThreadModifierMachine modifierMachine) {
            ThreadHatchPartMachine part = modifierMachine.getThreadPartMachine();
            if (part != null) return part;
        }
        MetaMachine metaMachine = machine.self();
        if (metaMachine instanceof IMultiController controller) {
            return GTUFThreadRegistry.get(controller);
        }
        return null;
    }

    /**
     * 每 tick 推进：先扣 tick 级输入（EU/t 等），不足则本线程停住不推进；
     * 有空闲线程时搜索并启动新配方；最后维护状态。
     */
    public void serverTick() {
        MetaMachine metaMachine = machine.self();
        if (metaMachine.getLevel() == null || metaMachine.getLevel().isClientSide) return;

        // 1) 逐线程推进：先扣 tick 级输入（EU/t 等），不足则该线程本 tick 停住不推进
        Iterator<ActiveRecipe> iterator = activeRecipes.iterator();
        while (iterator.hasNext()) {
            ActiveRecipe active = iterator.next();
            if (active.recipe.hasTick()) {
                ActionResult tickIn = RecipeHelper.handleTickRecipeIO(
                        machine, active.recipe, IO.IN, active.chanceCaches);
                if (!tickIn.isSuccess()) {
                    continue;
                }
                RecipeHelper.handleTickRecipeIO(machine, active.recipe, IO.OUT, active.chanceCaches);
            }
            if (active.update()) {
                completeRecipe(active);
                iterator.remove();
            }
        }

        // 2) 有空闲线程且机器开启时，尝试启动新配方
        boolean isMachineEnabled = outer.isWorkingEnabled();
        if (isMachineEnabled && activeRecipes.size() < getMaxThreads()) {
            // 空闲节流：无活跃配方且非订阅状态下每 IDLE_SEARCH_INTERVAL tick 才搜索一次
            if (!activeRecipes.isEmpty() || machine.keepSubscribing() ||
                    metaMachine.getOffsetTimer() % IDLE_SEARCH_INTERVAL == 0) {
                for (GTRecipe candidate : collectPossibleRecipes(SEARCH_LIMIT)) {
                    if (activeRecipes.size() >= getMaxThreads()) break;
                    if (isRecipeAlreadyActive(candidate)) continue;
                    tryStartRecipe(candidate);
                }
            }
        }

        // 3) 维护状态（驱动机器工作渲染与 GUI 工作状态）
        setStatus(activeRecipes.isEmpty() ? RecipeLogic.Status.IDLE : RecipeLogic.Status.WORKING);
    }

    /**
     * 收集候选配方：对每个 recipeType 公平分配查找额度。
     * 来源 GTNA {@code GTNAMultipleRecipesLogic.collectPossibleRecipes}（去掉其
     * pattern buffer fast-path，GTUF 无 pattern buffer）。
     */
    private List<GTRecipe> collectPossibleRecipes(int searchLimit) {
        List<GTRecipe> possibleRecipes = new ArrayList<>(searchLimit);
        GTRecipeType[] recipeTypes = machine.getRecipeTypes();
        if (recipeTypes == null || recipeTypes.length == 0) {
            recipeTypes = new GTRecipeType[] { machine.getRecipeType() };
        }
        int perTypeLimit = Math.max(4, searchLimit / Math.max(1, recipeTypes.length));
        for (GTRecipeType recipeType : recipeTypes) {
            if (recipeType == null) continue;
            int found = 0;
            // 镜像原生 RecipeLogic.searchRecipe 的 canHandle：先按原始配方过滤可用性，
            // 避免把一堆输入不够的候选填满 searchLimit；并行/超频的最终判定仍在 tryStartRecipe。
            var recipeIterator = recipeType.searchRecipe(
                    machine, r -> RecipeHelper.matchContents(machine, r).isSuccess());
            while (recipeIterator.hasNext() && found < perTypeLimit && possibleRecipes.size() < searchLimit) {
                GTRecipe recipe = recipeIterator.next();
                if (recipe == null || containsRecipe(possibleRecipes, recipe)) continue;
                possibleRecipes.add(recipe);
                found++;
            }
        }
        return possibleRecipes;
    }

    private static boolean containsRecipe(List<GTRecipe> possibleRecipes, GTRecipe candidate) {
        for (GTRecipe existing : possibleRecipes) {
            if (existing == candidate) return true;
            if (existing != null && candidate != null && existing.id != null && existing.id.equals(candidate.id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 启动一个配方：机器配方修改器 → 检查输入足够 → 预扣输入 → 加入活跃列表。
     * 走 GTM 原生 {@link IRecipeLogicMachine#fullModifyRecipe}，与原生 RecipeLogic 的
     * {@code checkMatchedRecipeAvailable} 完全一致——机器的 {@code .recipeModifier(...)}
     * （如 EnhanceableElectricMachine 的并行/能耗/速度倍率，或未配置时的默认
     * {@code OC_NON_PERFECT}）在<b>每个线程</b>上照常生效，线程与并行可叠加
     * （线程数 × 每线程并行份数）。
     * <b>注意：此处不能额外套 {@code ELECTRIC_OVERCLOCK}</b>——自带超频的 modifier
     * （如 {@code OC_PERFECT}）会被二次超频，破坏配方的 EU/duration。
     */
    private boolean tryStartRecipe(GTRecipe recipe) {
        GTRecipe recipeToRun = machine.fullModifyRecipe(recipe);
        if (recipeToRun == null) return false;
        if (recipeToRun.duration < 1) recipeToRun.duration = 1;

        if (!RecipeHelper.matchContents(machine, recipeToRun).isSuccess()) {
            return false;
        }
        ActionResult result = RecipeHelper.handleRecipeIO(machine, recipeToRun, IO.IN, getChanceCaches());
        if (result.isSuccess()) {
            activeRecipes.add(new ActiveRecipe(recipeToRun, recipeToRun.duration, getChanceCaches()));
            return true;
        }
        return false;
    }

    /** 按配方 ID 去重：同一配方只能有一个活跃副本，不同配方（含不同类型）可并行。 */
    private boolean isRecipeAlreadyActive(GTRecipe recipe) {
        if (recipe.id == null) return false;
        for (ActiveRecipe active : activeRecipes) {
            if (active.recipe.id != null && active.recipe.id.equals(recipe.id)) {
                return true;
            }
        }
        return false;
    }

    /** 完成配方：输出所有产物。来源 GTNA {@code GTNAMultipleRecipesLogic.completeRecipe}。 */
    private void completeRecipe(ActiveRecipe active) {
        if (active != null && active.recipe != null) {
            RecipeHelper.handleRecipeIO(machine, active.recipe, IO.OUT, active.chanceCaches);
        }
    }

    /**
     * 逐线程进度显示（控制器 addDisplayText / Jade 用）。
     * 来源 GTNA {@code getRecipeDisplayInfo}，简化版。
     */
    public List<Component> getRecipeDisplayInfo() {
        List<Component> infoList = new ArrayList<>();
        for (int i = 0; i < activeRecipes.size(); i++) {
            ActiveRecipe active = activeRecipes.get(i);
            float currentSec = active.progress / 20.0f;
            float maxSec = active.maxProgress / 20.0f;
            int percentage = active.maxProgress > 0 ? (int) ((active.progress / (float) active.maxProgress) * 100) : 0;
            ChatFormatting percentColor = percentage < 33 ? ChatFormatting.RED :
                    (percentage < 66 ? ChatFormatting.YELLOW : ChatFormatting.GREEN);
            infoList.add(Component.literal("Thread " + (i + 1) + ": ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(String.format(Locale.US, "%.1fs / %.1fs ", currentSec, maxSec))
                            .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(String.format("(%d%%)", percentage))
                            .withStyle(percentColor)));

            infoList.add(Component.literal(" -> ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(outputName(active.recipe))
                            .withStyle(ChatFormatting.LIGHT_PURPLE)));
        }
        return infoList;
    }

    /**
     * 取配方第一个产物的显示名（GUI / Jade 共用）：优先物品产物，无物品产物时兜底
     * 流体产物（如 test_recipetype 的水→岩浆只输出流体）。
     * 来源 GTNA {@code getRecipeDisplayInfo} 的输出名解析，抽取为静态方法复用。
     */
    public static String outputName(GTRecipe recipe) {
        if (recipe == null) return "Unknown";
        String itemName = itemOutputName(recipe);
        if (itemName != null) return itemName;
        String fluidName = fluidOutputName(recipe);
        if (fluidName != null) return fluidName;
        return "Unknown";
    }

    /** 取配方第一个物品产物的显示名；无物品产物返回 null。 */
    private static String itemOutputName(GTRecipe recipe) {
        List<Content> itemOutputs = recipe.outputs.get(ItemRecipeCapability.CAP);
        if (itemOutputs == null || itemOutputs.isEmpty()) return null;
        Object inner = itemOutputs.get(0).getContent();
        if (inner instanceof ItemStack stack) {
            return stack.getHoverName().getString();
        } else if (inner instanceof SizedIngredient sized) {
            ItemStack[] stacks = sized.getItems();
            if (stacks.length > 0) return stacks[0].getHoverName().getString();
        }
        return null;
    }

    /** 取配方第一个流体产物的显示名（Forge {@link FluidStack}）；无流体产物返回 null。 */
    private static String fluidOutputName(GTRecipe recipe) {
        List<Content> fluidOutputs = recipe.outputs.get(FluidRecipeCapability.CAP);
        if (fluidOutputs == null || fluidOutputs.isEmpty()) return null;
        Object inner = fluidOutputs.get(0).getContent();
        if (inner instanceof FluidStack stack) {
            return stack.getDisplayName().getString();
        } else if (inner instanceof FluidIngredient ingredient) {
            FluidStack[] stacks = ingredient.getStacks();
            if (stacks.length > 0) return stacks[0].getDisplayName().getString();
        }
        return null;
    }

    /** 线程模式投影：GUI 进度条显示第一条活跃配方进度。 */
    public int getProgressForDisplay() {
        if (activeRecipes.isEmpty()) return 0;
        return activeRecipes.get(0).progress;
    }

    /** 线程模式投影：GUI 进度条显示第一条活跃配方最大进度。 */
    public int getMaxProgressForDisplay() {
        if (activeRecipes.isEmpty()) return 0;
        return activeRecipes.get(0).maxProgress;
    }

    /** 线程模式投影：GUI 配方预览显示第一条活跃配方。 */
    @Nullable
    public com.gregtechceu.gtceu.api.recipe.GTRecipe getLastRecipeForDisplay() {
        if (activeRecipes.isEmpty()) return null;
        return activeRecipes.get(0).recipe;
    }

    /** 线程模式投影：有活跃配方即视为工作。 */
    public boolean isActiveForDisplay() {
        return !activeRecipes.isEmpty();
    }

    /** 线程模式投影：状态 = 有活跃配方则 WORKING，否则 IDLE。 */
    public RecipeLogic.Status getStatusForDisplay() {
        return activeRecipes.isEmpty() ? RecipeLogic.Status.IDLE : RecipeLogic.Status.WORKING;
    }

    /** 线程模式投影：有无配方等待（保留 GTM 字段的等待语义，多槽模式恒为 false）。 */
    public boolean isWaitingForDisplay() {
        return false;
    }

    /** 通过宿主 RecipeLogic 的 setStatus 维护状态（复用其全部副作用）。 */
    private void setStatus(RecipeLogic.Status status) {
        outer.setStatus(status);
    }

    /** 每线程机会缓存表（来源 GTM {@code RecipeLogic.makeChanceCaches}，此处独立持有）。 */
    private static Map<RecipeCapability<?>, Object2IntMap<?>> makeChanceCaches() {
        Map<RecipeCapability<?>, Object2IntMap<?>> caches = new IdentityHashMap<>();
        for (RecipeCapability<?> cap : GTRegistries.RECIPE_CAPABILITIES.values()) {
            caches.put(cap, cap.makeChanceCache());
        }
        return caches;
    }

    private Map<RecipeCapability<?>, Object2IntMap<?>> getChanceCaches() {
        return chanceCaches;
    }

    /** 重置所有活跃配方（结构失效 / 逻辑重置时由 Mixin 调用）。 */
    public void reset() {
        activeRecipes.clear();
    }

    /** 持久化：存线程数与各线程进度（替代 RecipeLogic 单槽进度）。 */
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        tag.putInt("ActiveRecipeCount", activeRecipes.size());
        for (int i = 0; i < activeRecipes.size(); i++) {
            ActiveRecipe recipe = activeRecipes.get(i);
            tag.putInt("RProg" + i, recipe.progress);
            tag.putInt("RMax" + i, recipe.maxProgress);
        }
    }

    /** 加载：多槽配方不还原（线程数据在未成型时无宿主可挂），清空等待重新启动。 */
    public void loadCustomPersistedData(CompoundTag tag) {
        activeRecipes.clear();
    }
}
