package com.liangqu.gtuf.common.data.machines;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderHelper;
import com.gregtechceu.gtceu.common.block.BoilerFireboxType;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.models.GTMachineModels;
import com.liangqu.gtuf.api.pattern.GTUF_PatternPredicates;
import com.liangqu.gtuf.api.registry.GTUF_CreativeModeTabs;
import com.liangqu.gtuf.common.machine.multiblock.electric.ConfigurableElectricParallelMachine;
import com.liangqu.gtuf.common.machine.multiblock.electric.TierElectricParallelMachine;
import com.liangqu.gtuf.common.machine.multiblock.steam.AdjustableSteamParallelMachine;
import com.liangqu.gtuf.common.machine.multiblock.steam.EnhanceableSteamMachine;
import com.liangqu.gtuf.common.machine.multiblock.steam.IndustrialSteamMachine;

import static com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.*;
import static com.gregtechceu.gtceu.api.pattern.Predicates.*;
import static com.gregtechceu.gtceu.common.data.GCYMBlocks.CASING_INDUSTRIAL_STEAM;
import static com.gregtechceu.gtceu.common.data.GTBlocks.*;
import static com.gregtechceu.gtceu.common.data.GTBlocks.CASING_BRONZE_BRICKS;
import static com.gregtechceu.gtceu.common.data.GTBlocks.CASING_BRONZE_PIPE;
import static com.gregtechceu.gtceu.common.data.GTBlocks.FIREBOX_BRONZE;
import static com.liangqu.gtuf.api.registry.GTUF_Registries.REGISTRATE;
import static net.minecraft.world.level.block.Blocks.GLASS;

/**
 * GTUF 测试专用机器注册（框架定位下不随公开发布打包）。
 * 仅含验证用多方块测试机；框架级可复用机器/部件注册见 {@code GTUF_Machines}。
 */
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



    public static final MachineDefinition TEST_MACHINE = REGISTRATE
            .multiblock("test_machine", TierElectricParallelMachine::new)
            .rotationState(RotationState.ALL)
            .appearanceBlock(CASING_INDUSTRIAL_STEAM)
            .recipeType(GTRecipeTypes.FORGE_HAMMER_RECIPES)
            .recipeModifier(TierElectricParallelMachine::recipeModifier, true)
            .addOutputLimit(ItemRecipeCapability.CAP, 1)
            .pattern(definition -> FactoryBlockPattern.start()
                    .aisle("AAAAAA", "ACCCCA", "AAAAAA")
                    .aisle("AAAAAA", "ADDDDA", "AAAAAA")
                    .aisle("AAAAAA", "ACACCA", "AAAAAA")
                    .aisle("AAA###", "AKA###", "AAA###")
                    .where("#", Predicates.any())
                    .where("K", Predicates.controller(blocks(definition.getBlock())))
                    .where("D", blocks(CASING_BRONZE_GEARBOX.get()))
                    .where("C", Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Bronze)))
                    .where("A", blocks(CASING_INDUSTRIAL_STEAM.get()).setMinGlobalLimited(45)
                            .or(Predicates.abilities(IMPORT_ITEMS).setPreviewCount(1))
                            .or(Predicates.abilities(EXPORT_ITEMS).setPreviewCount(1))
                            .or(Predicates.abilities(INPUT_ENERGY).setExactLimit(1)))
                    .build())
            .model(GTMachineModels.createWorkableCasingMachineModel(
                    GTCEu.id("block/casings/gcym/industrial_steam_casing"),
                    GTCEu.id("block/multiblock/large_chemical_reactor")))
            .register();

    /**
     * 可增强蒸汽多方块测试机：验证外壳等级（并行）与框架等级（加速）机制。
     * 外壳 = 蒸汽机械方块(Tier1) / 脱氧钢机械方块(Tier2)；框架 = 青铜框架(Tier1) / 钢框架(Tier2)。
     */
    public static final MachineDefinition ENHANCEABLE_STEAM_MIXER = REGISTRATE
            .multiblock("enhanceable_steam_mixer", holder -> new EnhanceableSteamMachine(holder,
                    GTRecipeTypes.MIXER_RECIPES, 2))
            .rotationState(RotationState.NON_Y_AXIS)
            .appearanceBlock(BRONZE_HULL)
            .recipeType(GTRecipeTypes.MIXER_RECIPES)
            .addOutputLimit(ItemRecipeCapability.CAP, 1)
            .pattern(definition -> FactoryBlockPattern.start()
                    .aisle("AAAAAA", "ACCCCA", "AAAAAA")
                    .aisle("AAAAAA", "ACCCCA", "AAAAAA")
                    .aisle("AAAAAA", "ACCCCA", "AAAAAA")
                    .aisle("AAA###", "AKA###", "AAA###")
                    .where("#", Predicates.any())
                    .where("K", Predicates.controller(blocks(definition.getBlock())))
                    .where("C", GTUF_PatternPredicates.SteamFrameTier())
                    .where("A", GTUF_PatternPredicates.SteamCasingTier().setMinGlobalLimited(40)
                            .or(Predicates.abilities(PartAbility.STEAM_IMPORT_ITEMS).setPreviewCount(1))
                            .or(Predicates.abilities(PartAbility.STEAM_EXPORT_ITEMS).setPreviewCount(1))
                            .or(Predicates.abilities(PartAbility.STEAM).setExactLimit(1))
                            .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS))
                            .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS)))
                    .build())
            .model(GTMachineModels.createWorkableCasingMachineModel(
                    GTCEu.id("block/casings/solid/machine_casing_bronze_plated_bricks"),
                    GTCEu.id("block/multiblock/large_chemical_reactor")))
            .register();

    /**
     * 可配置并行数电力多方块测试机：注册时设置最大并行数为 32，
     * GUI 中可用 [-] / [+] 调整当前并行数（1~32），默认满并行。
     */
    public static final MachineDefinition CONFIGURABLE_ELECTRIC_PARALLEL = REGISTRATE
            .multiblock("configurable_electric_parallel",
                    holder -> new ConfigurableElectricParallelMachine(holder, 32))
            .rotationState(RotationState.ALL)
            .appearanceBlock(CASING_INDUSTRIAL_STEAM)
            .recipeType(GTRecipeTypes.FORGE_HAMMER_RECIPES)
            .recipeModifier(ConfigurableElectricParallelMachine::recipeModifier, true)
            .addOutputLimit(ItemRecipeCapability.CAP, 1)
            .pattern(definition -> FactoryBlockPattern.start()
                    .aisle("AAAAAA", "ACCCCA", "AAAAAA")
                    .aisle("AAAAAA", "ADDDDA", "AAAAAA")
                    .aisle("AAAAAA", "ACACCA", "AAAAAA")
                    .aisle("AAA###", "AKA###", "AAA###")
                    .where("#", Predicates.any())
                    .where("K", Predicates.controller(blocks(definition.getBlock())))
                    .where("D", blocks(CASING_BRONZE_GEARBOX.get()))
                    .where("C", Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Bronze)))
                    .where("A", blocks(CASING_INDUSTRIAL_STEAM.get()).setMinGlobalLimited(45)
                            .or(Predicates.abilities(IMPORT_ITEMS).setPreviewCount(1))
                            .or(Predicates.abilities(EXPORT_ITEMS).setPreviewCount(1))
                            .or(Predicates.abilities(INPUT_ENERGY).setExactLimit(1)))
                    .build())
            .model(GTMachineModels.createWorkableCasingMachineModel(
                    GTCEu.id("block/casings/gcym/industrial_steam_casing"),
                    GTCEu.id("block/multiblock/large_chemical_reactor")))
            .register();

    /**
     * 工业级蒸汽多方块测试机：GUI 可调并行数（最大 32），
     * 配增强蒸汽仓时可处理 MV 配方（转换率 0.25 mB/EU），否则 LV（1.0 mB/EU）。
     */
    public static final MachineDefinition INDUSTRIAL_STEAM_MIXER = REGISTRATE
            .multiblock("industrial_steam_mixer", holder -> new IndustrialSteamMachine(holder,
                    GTRecipeTypes.MIXER_RECIPES, 32))
            .rotationState(RotationState.NON_Y_AXIS)
            .appearanceBlock(BRONZE_HULL)
            .recipeType(GTRecipeTypes.MIXER_RECIPES)
            .addOutputLimit(ItemRecipeCapability.CAP, 1)
            .pattern(definition -> FactoryBlockPattern.start()
                    .aisle("AAAAAA", "ACCCCA", "AAAAAA")
                    .aisle("AAAAAA", "ACCCCA", "AAAAAA")
                    .aisle("AAAAAA", "ACCCCA", "AAAAAA")
                    .aisle("AAA###", "AKA###", "AAA###")
                    .where("#", Predicates.any())
                    .where("K", Predicates.controller(blocks(definition.getBlock())))
                    .where("C", GTUF_PatternPredicates.SteamFrameTier())
                    .where("A", GTUF_PatternPredicates.SteamCasingTier().setMinGlobalLimited(40)
                            .or(Predicates.abilities(PartAbility.STEAM_IMPORT_ITEMS).setPreviewCount(1))
                            .or(Predicates.abilities(PartAbility.STEAM_EXPORT_ITEMS).setPreviewCount(1))
                            .or(Predicates.abilities(PartAbility.STEAM).setExactLimit(1))
                            .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS))
                            .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS)))
                    .build())
            .model(GTMachineModels.createWorkableCasingMachineModel(
                    GTCEu.id("block/casings/solid/machine_casing_bronze_plated_bricks"),
                    GTCEu.id("block/multiblock/large_chemical_reactor")))
            .register();

    /**
     * 增强并行控制仓测试多方块机：挂载原生并行配方修改器（PARALLEL_HATCH + 非完美过时钟），
     * 结构能力位含 PARALLEL_HATCH，放入增强并行仓后其 GUI 设置的并行数即机器实际并行数。
     */
    public static final MachineDefinition PARALLEL_HATCH_TEST = REGISTRATE
            .multiblock("parallel_hatch_test", WorkableElectricMultiblockMachine::new)
            .rotationState(RotationState.ALL)
            .appearanceBlock(CASING_INDUSTRIAL_STEAM)
            .recipeType(GTRecipeTypes.FORGE_HAMMER_RECIPES)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, GTRecipeModifiers.OC_NON_PERFECT_SUBTICK)
            .addOutputLimit(ItemRecipeCapability.CAP, 1)
            .pattern(definition -> FactoryBlockPattern.start()
                    .aisle("AAAAAA", "ACCCCA", "AAAAAA")
                    .aisle("AAAAAA", "ADDDDA", "AAAAAA")
                    .aisle("AAAAAA", "ACACCA", "AAAAAA")
                    .aisle("AAA###", "AKA###", "AAA###")
                    .where("#", Predicates.any())
                    .where("K", Predicates.controller(blocks(definition.getBlock())))
                    .where("D", blocks(CASING_BRONZE_GEARBOX.get()))
                    .where("C", Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Bronze)))
                    .where("A", blocks(CASING_INDUSTRIAL_STEAM.get()).setMinGlobalLimited(45)
                            .or(Predicates.abilities(IMPORT_ITEMS).setPreviewCount(1))
                            .or(Predicates.abilities(EXPORT_ITEMS).setPreviewCount(1))
                            .or(Predicates.abilities(INPUT_ENERGY).setExactLimit(1))
                            .or(Predicates.abilities(PartAbility.PARALLEL_HATCH).setExactLimit(1)))
                    .build())
            .model(GTMachineModels.createWorkableCasingMachineModel(
                    GTCEu.id("block/casings/gcym/industrial_steam_casing"),
                    GTCEu.id("block/multiblock/large_chemical_reactor")))
            .register();

    public static void init() {}
}
