package com.liangqu.gtuf.common.machine.multiblock.steam;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.liangqu.gtuf.api.machine.multiblock.ParallelMachine;
import com.liangqu.gtuf.common.machine.multiblock.base.SteamMultiBlockBase;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class AdjustableSteamParallelMachine extends SteamMultiBlockBase implements ParallelMachine{

    private static final int MIN_PARALLEL = 1;

    private final GTRecipeType recipeType;
    private final int maxParallel;
    private final double durationMultiplier;
    private final boolean adjustable;

    @Persisted
    private int targetParallel;

    public record Config(GTRecipeType recipeType, int defaultParallel, int maxParallel,
                         double durationMultiplier, boolean adjustable) {}

    public AdjustableSteamParallelMachine(IMachineBlockEntity holder, GTRecipeType recipeType, int defaultParallel,
                                          int maxParallel, double durationMultiplier, boolean adjustable,
                                          Object... args) {
        super(holder, false, args);
        this.recipeType = recipeType;
        this.targetParallel = defaultParallel;
        this.maxParallel = maxParallel;
        this.durationMultiplier = durationMultiplier;
        this.adjustable = adjustable;
    }

    @Nullable
    @Override
    protected GTRecipe getRealRecipe(@Nonnull GTRecipe recipe) {
        if (recipe.getType() != recipeType) return null;
        int parallels = ParallelLogic.getParallelAmount(this, recipe, targetParallel);
        return parallels == 0 ? null : applyModifier(recipe, parallels);
    }

    private GTRecipe applyModifier(GTRecipe recipe, int parallels) {
        return ModifierFunction.builder()
                .modifyAllContents(ContentModifier.multiplier(parallels))
                .durationMultiplier(durationMultiplier)
                .parallels(parallels)
                .build()
                .apply(recipe.copy());
    }

    @Override
    public int getMaxParallel() {
        return targetParallel;
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
        if (adjustable && isFormed()) {
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
        if (!adjustable || clickData.isRemote) return;
        this.targetParallel = "parallelSub".equals(componentData)
                ? adjust(targetParallel, false)
                : adjust(targetParallel, true);
    }

    private int adjust(int current, boolean increase) {
        int newValue = increase ? current * 2 : current / 2;
        return Math.max(MIN_PARALLEL, Math.min(newValue, maxParallel));
    }
}
