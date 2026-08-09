package com.liangqu.gtuf.testmod;

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

import com.liangqu.gtuf.api.machine.multiblock.GTUF_PartAbility;
import com.liangqu.gtuf.api.pattern.GTUF_PatternPredicates;
import com.liangqu.gtuf.api.registry.GTUF_CreativeModeTabs;
import com.liangqu.gtuf.common.data.models.GTUFModels;
import com.liangqu.gtuf.common.machine.multiblock.electric.ConfigurableElectricParallelMachine;
import com.liangqu.gtuf.common.machine.multiblock.electric.EnhanceableElectricMachine;
import com.liangqu.gtuf.common.machine.multiblock.electric.MultiThreadElectricMachine;
import com.liangqu.gtuf.common.machine.multiblock.electric.TierElectricParallelMachine;
import com.liangqu.gtuf.common.machine.multiblock.steam.AdjustableSteamParallelMachine;
import com.liangqu.gtuf.common.machine.multiblock.steam.EnhanceableSteamMachine;
import com.liangqu.gtuf.common.machine.multiblock.steam.IndustrialSteamMachine;

import static com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.*;
import static com.gregtechceu.gtceu.api.pattern.Predicates.*;
import static com.gregtechceu.gtceu.common.data.GCYMBlocks.CASING_INDUSTRIAL_STEAM;
import static com.gregtechceu.gtceu.common.data.GTBlocks.*;
import static com.liangqu.gtuf.api.registry.GTUF_Registries.REGISTRATE;
import static net.minecraft.world.level.block.Blocks.GLASS;

/**
 * GTUF 测试专用机器注册（框架定位下不随公开发布打包，仅 dev 环境由
 * {@link GTUF_TestMachines} 加载）。全部为内联多方块结构注册，验证框架机器类；
 * 正式发布 jar 不含本类。
 */
public class GTUF_Machine_Test {

    static {
        REGISTRATE.creativeModeTab(() -> GTUF_CreativeModeTabs.GTUF_TEST);
    }
    public static final MachineDefinition STEAM_MIXER = REGISTRATE
            .multiblock("steam_mixer",
                    holder -> new AdjustableSteamParallelMachine(holder, GTRecipeTypes.MIXER_RECIPES, 64, 64,
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
            .model(GTUFModels.createTieredSteamMachineModel(
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
            .model(GTUFModels.createTieredSteamMachineModel(
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

    /**
     * 可增强电力多方块测试机：外壳等级（并行）×框架等级（能耗）×管道等级（速度）。
     * 构造器 (holder, useFrame=true, usePipe=true)：框架与管道增强均启用。
     * 外壳 = 钢机壳(T1)/铝防霜(T2)/不锈钢洁净(T3)/钛稳定(T4)/钨钢坚固(T5)；
     * 框架 = 钢(T1)/铝(T2)/不锈钢(T3)…；管道 = 青铜(T1)/钢(T2)/钛(T3)/钨钢(T4)。
     */
    public static final MachineDefinition ENHANCEABLE_ELECTRIC_MIXER = REGISTRATE
            .multiblock("enhanceable_electric_mixer", holder -> new EnhanceableElectricMachine(
                    holder, true, true))
            .rotationState(RotationState.ALL)
            .appearanceBlock(CASING_STEEL_SOLID)
            .recipeType(GTRecipeTypes.FORGE_HAMMER_RECIPES)
            .recipeModifier(EnhanceableElectricMachine::recipeModifier, true)
            .addOutputLimit(ItemRecipeCapability.CAP, 1)
            .pattern(definition -> FactoryBlockPattern.start()
                    .aisle("AAAAAA", "ACCCCA", "AAAAAA")
                    .aisle("AAAAAA", "ADDDDA", "AAAAAA")
                    .aisle("AAAAAA", "ACACCA", "AAAAAA")
                    .aisle("AAA###", "AKA###", "AAA###")
                    .where("#", Predicates.any())
                    .where("K", Predicates.controller(blocks(definition.getBlock())))
                    .where("D", GTUF_PatternPredicates.UniversalPipeTier())
                    .where("C", GTUF_PatternPredicates.UniversalFrameTier())
                    .where("A", GTUF_PatternPredicates.UniversalCasingTier().setMinGlobalLimited(40)
                            .or(Predicates.abilities(IMPORT_ITEMS).setPreviewCount(1))
                            .or(Predicates.abilities(EXPORT_ITEMS).setPreviewCount(1))
                            .or(Predicates.abilities(INPUT_ENERGY).setExactLimit(1)))
                    .build())
            .model(GTUFModels.createTieredMachineModel(
                    GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                    GTCEu.id("block/multiblock/large_chemical_reactor")))
            .register();

    /**
     * 多线程多方块测试机：挂 MIXER 与 FORGE_HAMMER 两类配方类型，结构能力位含 THREAD_HATCH。
     * 放入线程仓并调大线程数后，机器可同时处理两类配方（不同配方 ID 各自独立进度，
     * 见 GTUFThreadingLogic）。验证多配方并行 + 线程仓 GUI 可调线程数。
     */
    public static final MachineDefinition THREAD_TEST = REGISTRATE
            .multiblock("thread_test", MultiThreadElectricMachine::new)
            .rotationState(RotationState.ALL)
            .appearanceBlock(CASING_INDUSTRIAL_STEAM)
            .recipeType(GTRecipeTypes.MIXER_RECIPES)
            .recipeType(GTRecipeTypes.FORGE_HAMMER_RECIPES)
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
                            .or(Predicates.abilities(GTUF_PartAbility.THREAD_HATCH).setExactLimit(1)))
                    .build())
            .model(GTMachineModels.createWorkableCasingMachineModel(
                    GTCEu.id("block/casings/gcym/industrial_steam_casing"),
                    GTCEu.id("block/multiblock/large_chemical_reactor")))
            .register();

    /**
     * Mixin 推广验证机：普通电力多方块（用 GTM 原生 {@code WorkableElectricMultiblockMachine}，
     * 不实现 IThreadModifierMachine、不覆盖 createRecipeLogic），结构能力位含 THREAD_HATCH。
     * 验证 {@code GTUFRecipeLogicMixin} 对任意电力多方块生效——装线程仓后同样多线程并行。
     */
    public static final MachineDefinition MIXIN_THREAD_TEST = REGISTRATE
            .multiblock("mixin_thread_test", WorkableElectricMultiblockMachine::new)
            .rotationState(RotationState.ALL)
            .appearanceBlock(CASING_INDUSTRIAL_STEAM)
            .recipeType(GTRecipeTypes.MIXER_RECIPES)
            .recipeType(GTRecipeTypes.FORGE_HAMMER_RECIPES)
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
                            .or(Predicates.abilities(GTUF_PartAbility.THREAD_HATCH).setExactLimit(1)))
                    .build())
            .model(GTMachineModels.createWorkableCasingMachineModel(
                    GTCEu.id("block/casings/gcym/industrial_steam_casing"),
                    GTCEu.id("block/multiblock/large_chemical_reactor")))
            .register();

    public static void init() {}
}
