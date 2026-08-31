package com.liangqu.gtuf.core.mixins;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.liangqu.gtuf.api.machine.multiblock.GTUF_PartAbility;
import com.liangqu.gtuf.config.GTUF_Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 仓室结构注入：让线程仓/节能仓能装入 GTM 用 {@link Predicates#autoAbilities} 定义
 * 能力位的电力多方块（含 GTM 原生机器，如大型组装厂 {@code large_assembler}）。
 *
 * <p>
 * 来源：GTNA {@code com.raishxn.gtna.mixin.gtceu.PredicatesMixin}（LGPL3.0）——GTNA 在
 * {@code autoAbilities} 返回时把其 overclock/accelerate 仓能力 OR 进 predicate，使这些仓可
 * 占据任意外壳/仓室位成型。本类照搬该模式，OR 的是 {@link GTUF_PartAbility#THREAD_HATCH} 与
 * {@link GTUF_PartAbility#ENERGY_SAVING}。
 * </p>
 *
 * <p>
 * 与 GTNA 的关键差异：GTNA 以 {@code checkEnergyIn} 参数门控（其仓只在机器自身把能量
 * 位并入 autoAbilities、传 {@code checkEnergyIn=true} 时生效）；而大型组装厂把能量放进独立
 * 的 {@code INPUT_ENERGY} 能力位、autoAbilities 传 {@code checkEnergyIn=false}。故这里
 * <b>不</b>以 checkEnergyIn 门控，只检查 recipeType 是否消耗 EU（判定电力多方块）——
 * 大型组装厂等能量另走能力位的机器同样能装线程仓/节能仓。
 * </p>
 *
 * <p>
 * <b>按机器 ID 匹配（而非按类匹配）</b>：{@code autoAbilities} 在 pattern 构造期（注册期）
 * 被调用，彼时拿不到任何机器实例，无法用类判断"是哪台机器"。故这里改用 GTM 的
 * {@link Predicates#custom}——谓词在<b>运行期结构检查</b>才求值，彼时经
 * {@link MultiblockState#getController()} → {@link IMultiController#self()} →
 * {@code getDefinition().getId()} 拿到控制器机器注册 ID，再与 config 白名单比对。
 * </p>
 *
 * <p>
 * 门控层次（config {@code [structureCompat]}，均需重启生效——pattern 经 GTMemoizer 缓存）：
 * <ul>
 * <li>{@code threadHatchVanillaInjection} / {@code energySavingHatchVanillaInjection}：
 * 两个 boolean 总开关（默认开启），关掉对应仓完全不再注入。</li>
 * <li>{@code machineWhitelist}：机器 ID 白名单（"namespace:path"）。空 = 不限制（回退到
 * 纯 boolean 门控的旧行为）；非空 = 仅列出的机器获得结构注入。</li>
 * </ul>
 * 关闭后线程仓/节能仓只能装入显式声明其能力位的 GTUF 多方块结构。
 * </p>
 */
@Mixin(Predicates.class)
public abstract class GTUFPredicatesMixin {

    @Inject(method = "autoAbilities([Lcom/gregtechceu/gtceu/api/recipe/GTRecipeType;ZZZZZZ)" +
            "Lcom/gregtechceu/gtceu/api/pattern/TraceabilityPredicate;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false)
    private static void gtuf$addHatchesToElectricMultiblocks(GTRecipeType[] recipeTypes,
                                                             boolean checkEnergyIn,
                                                             boolean checkEnergyOut,
                                                             boolean checkItemIn,
                                                             boolean checkItemOut,
                                                             boolean checkFluidIn,
                                                             boolean checkFluidOut,
                                                             CallbackInfoReturnable<TraceabilityPredicate> cir) {
        // config 开关（[structureCompat]）：两项都关闭时完全不改动原谓词（零行为改变）。
        if (!GTUF_Config.isThreadHatchVanillaInjection() && !GTUF_Config.isEnergySavingHatchVanillaInjection()) {
            return;
        }
        boolean electric = false;
        for (GTRecipeType type : recipeTypes) {
            if (type.getMaxInputs(EURecipeCapability.CAP) > 0) {
                electric = true;
                break;
            }
        }
        if (!electric) {
            return;
        }
        TraceabilityPredicate predicate = cir.getReturnValue();
        if (GTUF_Config.isThreadHatchVanillaInjection()) {
            TraceabilityPredicate injected = buildHatchPredicate(GTUF_PartAbility.THREAD_HATCH);
            if (injected != null) {
                predicate = predicate.or(injected);
            }
        }
        if (GTUF_Config.isEnergySavingHatchVanillaInjection()) {
            TraceabilityPredicate injected = buildHatchPredicate(GTUF_PartAbility.ENERGY_SAVING);
            if (injected != null) {
                predicate = predicate.or(injected);
            }
        }
        cir.setReturnValue(predicate);
    }

    /**
     * 为某能力位构建"白名单感知"的运行期谓词。
     *
     * <p>
     * 方块匹配沿用能力位注册的全部方块（与 {@code Predicates.abilities} 一致），但额外要求：
     * 结构检查时控制器机器 ID 通过 {@link GTUF_Config#isMachineInStructureCompatWhitelist} 白名单
     * 判定。候选（JEI 预览）不变，仍为该能力位的全部方块。
     * </p>
     *
     * @return 注入谓词；能力位尚未注册任何方块时返回 {@code null}（调用方跳过）。
     */
    private static TraceabilityPredicate buildHatchPredicate(PartAbility ability) {
        List<Block> blocks = ability.getAllBlocks().stream().toList();
        if (blocks.isEmpty()) {
            return null;
        }
        Set<Block> blockSet = new HashSet<>(blocks);
        Supplier<BlockInfo[]> candidates = () -> blocks.stream().map(BlockInfo::fromBlock).toArray(BlockInfo[]::new);
        return Predicates.custom(state -> {
            // 先判方块再取控制器：非仓室方块直接短路，避免对每个位都做 block entity 查找。
            if (!isAnyOf(state, blockSet)) {
                return false;
            }
            IMultiController controller = state.getController();
            if (controller == null) {
                return false;
            }
            String machineId = controller.self().getDefinition().getId().toString();
            return GTUF_Config.isMachineInStructureCompatWhitelist(machineId);
        }, candidates)
                .setMaxGlobalLimited(1)
                .setPreviewCount(1);
    }

    private static boolean isAnyOf(MultiblockState state, Set<Block> blocks) {
        BlockState blockState = state.getBlockState();
        for (Block block : blocks) {
            if (blockState.is(block)) {
                return true;
            }
        }
        return false;
    }
}
