package com.liangqu.gtuf.common.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

import com.liangqu.gtuf.api.pressure.GTUF_Pressure;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * 压力机的自定义 {@link RecipeLogic}：支持"纯电力"加压/抽压配方（0 物品/流体输入）。
 *
 * <p>
 * GTM 的 {@link RecipeDB} 三叉树<b>只按 item/fluid 输入内容</b>（{@code isRecipeSearchFilter()}
 * 的能力）索引配方与发起搜索——能量不是搜索过滤器。因此纯 EU 配方入库时不建 Trie 节点，
 * 机器搜索时 `fromHolder` 又因无 item/fluid 输入内容而返回空 → 标准搜索永远命中不了。
 * 这是 GTM 的硬约束，任何纯 EU 配方都必须走非 DB 搜索路径。
 * </p>
 *
 * <p>
 * 本类在标准搜索无结果时回退：扫描本机配方类型里第一个带
 * {@code gtuf_pressure_produce} data 且物品/流体输入均为空的配方。找到后交回
 * {@code handleSearchingRecipes}，EU 是否足够由 {@code matchRecipe}
 * （{@code RecipeHelper.matchContents}）门控。带物品/流体输入的普通压力配方
 * （如 sand→glass）仍优先走标准 RecipeDB 搜索。
 * </p>
 */
public class GTUFPressureRecipeLogic extends RecipeLogic {

    public GTUFPressureRecipeLogic(IRecipeLogicMachine machine) {
        super(machine);
    }

    @Override
    public Iterator<GTRecipe> searchRecipe() {
        // 标准搜索优先：带物品/流体输入的配方（如 sand→glass）仍走 RecipeDB。
        Iterator<GTRecipe> normal = super.searchRecipe();
        if (normal.hasNext()) {
            return normal;
        }
        // 回退：纯电力加压/抽压配方（可多个，EU 不足的一个会被 matchRecipe 拒掉、试下一个）。
        List<GTRecipe> euOnly = findEuOnlyPressureRecipes();
        return euOnly.iterator();
    }

    /**
     * 扫描本机配方类型里所有"纯电力"压力配方：data 含 {@code gtuf_pressure_produce}
     * 且物品/流体输入均为空。返回空列表表示没有可用配方（机器保持待机）。
     */
    private List<GTRecipe> findEuOnlyPressureRecipes() {
        List<GTRecipe> result = new ArrayList<>();
        GTRecipeType type = machine.getRecipeType();
        if (type == null) {
            return result;
        }
        for (Set<GTRecipe> recipes : type.getCategoryMap().values()) {
            for (GTRecipe recipe : recipes) {
                if (recipe.data != null && recipe.data.contains(GTUF_Pressure.Keys.PRODUCE) &&
                        recipe.getInputContents(ItemRecipeCapability.CAP).isEmpty() &&
                        recipe.getInputContents(FluidRecipeCapability.CAP).isEmpty()) {
                    result.add(recipe);
                }
            }
        }
        return result;
    }
}
