package com.liangqu.gtuf.core.mixins;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

import com.liangqu.gtuf.api.machine.multiblock.GTUF_PartAbility;
import com.liangqu.gtuf.config.GTUF_Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 仓室结构注入：让线程仓/节能仓能装入 GTM 所有用 {@link Predicates#autoAbilities} 定义
 * 能力位的电力多方块（含 GTM 原生机器，如大型组装厂 {@code large_assembler}）。
 *
 * <p>
 * 来源：GTNA {@code com.raishxn.gtna.mixin.gtceu.PredicatesMixin}（LGPL3.0）——GTNA 在
 * {@code autoAbilities} 返回时把其 overclock/accelerate 仓能力 OR 进 predicate，使这些仓可
 * 占据任意外壳/仓室位成型。本类照搬该模式，OR 的是 {@link GTUF_PartAbility#THREAD_HATCH} 与
 * {@link GTUF_PartAbility#ENERGY_SAVING}（从 fix3-checkpoint 还原线程仓注入，并配套节能仓）。
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
 * 节能仓对蒸汽机无意义，故与线程仓一样仅在配方类型消耗 EU 时开放结构位。注意 pattern 用
 * {@code GTMemoizer} 缓存，结构位在游戏内首次结构检查时固化，改动需重启对该部分生效。
 * </p>
 *
 * <p>
 * 本注入由配置组 {@code [structureCompat]} 开关（默认均开启）：{@code threadHatchVanillaInjection}
 * 控制线程仓注入、{@code energySavingHatchVanillaInjection} 控制节能仓注入。两项都关闭时
 * 本方法对原谓词零改动；仅开其中一项则只 OR 对应仓的能力位。关闭后线程仓/节能仓只能装入
 * 显式声明其能力位的 GTUF 多方块结构。因结构位经 GTMemoizer 固化，改动需重启对该部分生效。
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
        for (GTRecipeType type : recipeTypes) {
            if (type.getMaxInputs(EURecipeCapability.CAP) > 0) {
                TraceabilityPredicate predicate = cir.getReturnValue();
                if (GTUF_Config.isThreadHatchVanillaInjection()) {
                    predicate = predicate.or(Predicates.abilities(GTUF_PartAbility.THREAD_HATCH)
                            .setMaxGlobalLimited(1)
                            .setPreviewCount(1));
                }
                if (GTUF_Config.isEnergySavingHatchVanillaInjection()) {
                    predicate = predicate.or(Predicates.abilities(GTUF_PartAbility.ENERGY_SAVING)
                            .setMaxGlobalLimited(1)
                            .setPreviewCount(1));
                }
                cir.setReturnValue(predicate);
                return;
            }
        }
    }
}
