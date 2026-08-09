package com.liangqu.gtuf.api.machine.feature;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * @author Raishxn
 */
public interface IPatternBufferModeHost {

    default @Nullable String gtna$resolvePatternBufferMode(GTRecipe recipe) {
        return null;
    }

    default boolean gtna$applyPatternBufferMode(String modeId, GTRecipe recipe) {
        return false;
    }

    default boolean gtna$matchesModeId(String requestedModeId, GTRecipeType recipeType) {
        if (requestedModeId == null || requestedModeId.isBlank() || recipeType == null ||
                recipeType.registryName == null) {
            return false;
        }
        String requested = requestedModeId.trim().toLowerCase(Locale.ROOT);
        String fullId = recipeType.registryName.toString().toLowerCase(Locale.ROOT);
        String path = recipeType.registryName.getPath().toLowerCase(Locale.ROOT);
        String requestedNormalized = requested.replace('_', '/');
        String pathNormalized = path.replace('_', '/');
        if (requested.equals(fullId) || requested.equals(path)) {
            return true;
        }
        if (requestedNormalized.equals(fullId) || requestedNormalized.equals(pathNormalized)) {
            return true;
        }
        if (path.endsWith("_" + requested) || path.endsWith("/" + requested)) {
            return true;
        }
        if (pathNormalized.endsWith("/" + requestedNormalized)) {
            return true;
        }
        return ("saw".equals(requested) || "cutting_saw".equals(requested)) &&
                (path.contains("cutter") || path.contains("saw"));
    }
}
