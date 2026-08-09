package com.liangqu.gtuf.api.machine.feature;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import org.jetbrains.annotations.Nullable;

/**
 * @author Raishxn
 */
public interface IPatternBufferModeProvider {

    @Nullable
    String gtna$getPreferredModeForRecipe(GTRecipe recipe);

    void gtna$onRecipeStarted(GTRecipe recipe);
}
