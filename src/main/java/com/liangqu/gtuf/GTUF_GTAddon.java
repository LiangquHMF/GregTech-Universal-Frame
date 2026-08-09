package com.liangqu.gtuf;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.data.recipes.FinishedRecipe;

import com.liangqu.gtuf.api.registry.GTUF_Registries;

import java.util.function.Consumer;

@GTAddon
public class GTUF_GTAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return GTUF_Registries.REGISTRATE;
    }

    @Override
    public void initializeAddon() {}

    @Override
    public String addonModId() {
        return GTUF_Core.MOD_ID;
    }

    public void registerElements() { /* GTUF_Elements.init(); */}

    @Override
    public void addRecipes(Consumer<FinishedRecipe> provider) {
        /* GTUF_Recipes.init(provider); */
    }
}
