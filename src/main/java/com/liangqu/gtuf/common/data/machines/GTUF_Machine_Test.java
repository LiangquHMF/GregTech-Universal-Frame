package com.liangqu.gtuf.common.data.machines;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderHelper;
import com.gregtechceu.gtceu.common.block.BoilerFireboxType;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.models.GTMachineModels;
import com.gregtechceu.gtceu.common.machine.multiblock.steam.SteamParallelMultiblockMachine;
import com.liangqu.gtuf.api.registry.GTUF_CreativeModeTabs;
import com.liangqu.gtuf.common.machine.multiblock.steam.AdjustableSteamParallelMachine;
import net.minecraft.network.chat.Component;

import static com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.*;
import static com.gregtechceu.gtceu.api.pattern.Predicates.*;
import static com.gregtechceu.gtceu.common.data.GTBlocks.*;
import static com.gregtechceu.gtceu.common.data.GTBlocks.CASING_BRONZE_BRICKS;
import static com.gregtechceu.gtceu.common.data.GTBlocks.CASING_BRONZE_PIPE;
import static com.gregtechceu.gtceu.common.data.GTBlocks.FIREBOX_BRONZE;
import static com.liangqu.gtuf.api.registry.GTUF_Registries.REGISTRATE;
import static net.minecraft.world.level.block.Blocks.GLASS;

public class GTUF_Machine_Test {
    static {
        REGISTRATE.creativeModeTab(() -> GTUF_CreativeModeTabs.GTUF_TEST);
    }
    public static final MachineDefinition STEAM_MIXER = REGISTRATE
            .multiblock("steam_mixer", holder -> new AdjustableSteamParallelMachine(holder, GTRecipeTypes.MIXER_RECIPES, 64, 64,
                    0.5, true))
            .rotationState(RotationState.NON_Y_AXIS)
            .appearanceBlock(BRONZE_HULL)
            .recipeType(GTRecipeTypes.MIXER_RECIPES)
            .addOutputLimit(ItemRecipeCapability.CAP, 1)
            .pattern(definition -> FactoryBlockPattern.start()
                    .aisle("#######", "#######", "#######", "#######", "#######", "#######")
                    .aisle("##A#A##", "##A#A##", "##CCC##", "##CCC##", "##CCC##", "#######")
                    .aisle("#######", "#######", "##CCC##", "##CDC##", "##CCC##", "#######")
                    .aisle("##A#A##", "##A#A##", "##CCC##", "##CDC##", "##CCC##", "#######")
                    .aisle("##BBB##", "#######", "#######", "###D###", "##BBB##", "#######")
                    .aisle("#BBBBB#", "##BBB##", "##BBB##", "##BDB##", "#BBBBB#", "##BBB##")
                    .aisle("BBEEEBB", "#BF#FB#", "#B###B#", "#BFDFB#", "BBBBBBB", "#BGGGB#")
                    .aisle("BBEDEBB", "#G#D#G#", "#G#D#G#", "#G#D#G#", "BBBBBBB", "#BGGGB#")
                    .aisle("BBEEEBB", "#BF#FB#", "#B###B#", "#BF#FB#", "BBBBBBB", "#BGGGB#")
                    .aisle("#BBBBB#", "##BKB##", "##BGB##", "##BGB##", "#BBBBB#", "##BBB##")
                    .aisle("##BBB##", "#######", "#######", "#######", "##BBB##", "#######")
                    .where("K", Predicates.controller(blocks(definition.getBlock())))
                    .where("C", blocks(BRONZE_HULL.get()).setMinGlobalLimited(20)
                            .or(Predicates.abilities(PartAbility.STEAM_IMPORT_ITEMS).setPreviewCount(1))
                            .or(Predicates.abilities(PartAbility.STEAM_EXPORT_ITEMS).setPreviewCount(1))
                            .or(Predicates.abilities(PartAbility.STEAM).setExactLimit(1))
                            .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS))
                            .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS)))
                    .where("B", blocks(CASING_BRONZE_BRICKS.get()))
                    .where("A", Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Bronze)))
                    .where("F", Predicates.blocks(CASING_BRONZE_GEARBOX.get()))
                    .where("D", Predicates.blocks(CASING_BRONZE_PIPE.get()))
                    .where("E", Predicates.blocks(FIREBOX_BRONZE.get()))
                    .where("G", Predicates.blocks(GLASS))
                    .where("#", Predicates.any())
                    .build())
            .model(GTMachineModels.createWorkableCasingMachineModel(
                            GTCEu.id("block/casings/solid/machine_casing_bronze_plated_bricks"),
                            GTCEu.id("block/multiblock/large_chemical_reactor"))
                    .andThen(b -> b.addDynamicRenderer(() -> DynamicRenderHelper
                            .makeBoilerPartRender(BoilerFireboxType.BRONZE_FIREBOX, CASING_BRONZE_BRICKS))))
            .register();




    public static void init() {}
}
