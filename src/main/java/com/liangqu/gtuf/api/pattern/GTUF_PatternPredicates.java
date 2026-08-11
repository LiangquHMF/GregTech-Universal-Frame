package com.liangqu.gtuf.api.pattern;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.error.PatternStringError;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GTUF 自定义多方块结构检测谓词，模仿 {@link com.gregtechceu.gtceu.api.pattern.Predicates#heatingCoils()}。
 *
 * <p>
 * 检测外壳/框架方块并把对应等级写入 {@link com.gregtechceu.gtceu.api.pattern.util.PatternMatchContext}，
 * 机器在 {@code onStructureFormed()} 中通过 {@code getMultiblockState().getMatchContext()} 读取。
 * 同一结构内混用不同等级时设置错误信息使结构不匹配。
 * </p>
 *
 * <p>
 * 每个等级体系使用独立的 MatchContext 键，因此同一结构内可同时使用蒸汽外壳
 * ({@link #STEAM_CASING_TIER_KEY}) 与通用外壳 ({@link #UNIVERSAL_CASING_TIER_KEY})、
 * 蒸汽框架 ({@link #STEAM_FRAME_TIER_KEY}) 与通用框架 ({@link #UNIVERSAL_FRAME_TIER_KEY}) 而互不干扰。
 * </p>
 */
public final class GTUF_PatternPredicates {

    public static final String STEAM_CASING_TIER_KEY = "CasingType";
    public static final String STEAM_FRAME_TIER_KEY = "FrameType";
    public static final String UNIVERSAL_CASING_TIER_KEY = "UniversalCasingType";
    public static final String UNIVERSAL_FRAME_TIER_KEY = "UniversalFrameType";
    public static final String UNIVERSAL_PIPE_TIER_KEY = "UniversalPipeType";
    public static final String UNIVERSAL_GEARBOX_TIER_KEY = "UniversalGearboxType";
    public static final String UNIVERSAL_FIREBOX_TIER_KEY = "UniversalFireboxType";

    /** 结构实际使用的外壳方块注册名（由 {@link #Casing} 谓词写入 MatchContext）。 */
    public static final String STRUCTURE_CASING_KEY = "StructureCasing";

    private GTUF_PatternPredicates() {}

    /**
     * 蒸汽外壳等级：青铜机壳 (steam_machine_casing) → Tier 1，脱氧钢机壳 (solid_machine_casing) → Tier 2。
     * 结果写入 {@link #STEAM_CASING_TIER_KEY}。
     */
    public static TraceabilityPredicate SteamCasingTier() {
        return tierPredicate(new LinkedHashMap<>(Map.of(
                GTBlocks.CASING_BRONZE_BRICKS.get(), 1,
                GTBlocks.CASING_STEEL_SOLID.get(), 2)),
                STEAM_CASING_TIER_KEY, "gtuf.multiblock.pattern.error.casing");
    }

    /**
     * 蒸汽框架等级：青铜框架 → Tier 1，钢框架 → Tier 2。结果写入 {@link #STEAM_FRAME_TIER_KEY}。
     */
    public static TraceabilityPredicate SteamFrameTier() {
        Map<Block, Integer> blockTiers = new LinkedHashMap<>();
        putIfNotNull(blockTiers, ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Bronze), 1);
        putIfNotNull(blockTiers, ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Steel), 2);
        return tierPredicate(blockTiers, STEAM_FRAME_TIER_KEY, "gtuf.multiblock.pattern.error.frame");
    }

    /**
     * 通用外壳等级：脱氧钢机壳 → Tier 1，铝防霜机壳 → Tier 2，不锈钢洁净机壳 → Tier 3，
     * 钛稳定机壳 → Tier 4，钨钢坚固机壳 → Tier 5。结果写入 {@link #UNIVERSAL_CASING_TIER_KEY}。
     */
    public static TraceabilityPredicate UniversalCasingTier() {
        return tierPredicate(new LinkedHashMap<>(Map.of(
                GTBlocks.CASING_STEEL_SOLID.get(), 1,
                GTBlocks.CASING_ALUMINIUM_FROSTPROOF.get(), 2,
                GTBlocks.CASING_STAINLESS_CLEAN.get(), 3,
                GTBlocks.CASING_TITANIUM_STABLE.get(), 4,
                GTBlocks.CASING_TUNGSTENSTEEL_ROBUST.get(), 5)),
                UNIVERSAL_CASING_TIER_KEY, "gtuf.multiblock.pattern.error.universal_casing");
    }

    /**
     * 通用框架等级：钢框架 → Tier 1，铝框架 → Tier 2，不锈钢框架 → Tier 3，
     * 钛框架 → Tier 4，钨钢框架 → Tier 5。结果写入 {@link #UNIVERSAL_FRAME_TIER_KEY}。
     */
    public static TraceabilityPredicate UniversalFrameTier() {
        Map<Block, Integer> blockTiers = new LinkedHashMap<>();
        putIfNotNull(blockTiers, ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Steel), 1);
        putIfNotNull(blockTiers, ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Aluminium), 2);
        putIfNotNull(blockTiers, ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.StainlessSteel), 3);
        putIfNotNull(blockTiers, ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Titanium), 4);
        putIfNotNull(blockTiers, ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.TungstenSteel), 5);
        return tierPredicate(blockTiers, UNIVERSAL_FRAME_TIER_KEY, "gtuf.multiblock.pattern.error.universal_frame");
    }

    /**
     * 通用管道等级：青铜管道 → Tier 1，钢管道 → Tier 2，钛管道 → Tier 3，
     * 钨钢管道 → Tier 4。结果写入 {@link #UNIVERSAL_PIPE_TIER_KEY}。
     */
    public static TraceabilityPredicate UniversalPipeTier() {
        return tierPredicate(new LinkedHashMap<>(Map.of(
                GTBlocks.CASING_BRONZE_PIPE.get(), 1,
                GTBlocks.CASING_STEEL_PIPE.get(), 2,
                GTBlocks.CASING_TITANIUM_PIPE.get(), 3,
                GTBlocks.CASING_TUNGSTENSTEEL_PIPE.get(), 4)),
                UNIVERSAL_PIPE_TIER_KEY, "gtuf.multiblock.pattern.error.universal_pipe");
    }

    /**
     * 通用齿轮箱等级：青铜齿轮箱 → Tier 1，钢齿轮箱 → Tier 2，不锈钢齿轮箱 → Tier 3，，钛齿轮箱 → Tier 4，
     * 钨钢管道 → Tier 5。结果写入 {@link #UNIVERSAL_GEARBOX_TIER_KEY}。
     */
    public static TraceabilityPredicate UniversalGearboxTier() {
        return tierPredicate(new LinkedHashMap<>(Map.of(
                GTBlocks.CASING_BRONZE_GEARBOX.get(), 1,
                GTBlocks.CASING_STEEL_GEARBOX.get(), 2,
                GTBlocks.CASING_STAINLESS_STEEL_GEARBOX.get(), 3,
                GTBlocks.CASING_TITANIUM_GEARBOX.get(), 4,
                GTBlocks.CASING_TUNGSTENSTEEL_GEARBOX.get(), 5)),
                UNIVERSAL_GEARBOX_TIER_KEY, "gtuf.multiblock.pattern.error.universal_gearbox");
    }

    /**
     * 通用燃烧室等级：青铜燃烧室 → Tier 1，钢燃烧室 → Tier 2，钛燃烧室 → Tier 3，，钨钢燃烧室 → Tier 4。
     * 结果写入 {@link #UNIVERSAL_FIREBOX_TIER_KEY}。
     */
    public static TraceabilityPredicate UniversalFireboxTier() {
        return tierPredicate(new LinkedHashMap<>(Map.of(
                GTBlocks.FIREBOX_BRONZE.get(), 1,
                GTBlocks.FIREBOX_STEEL.get(), 2,
                GTBlocks.FIREBOX_TITANIUM.get(), 3,
                GTBlocks.FIREBOX_TUNGSTENSTEEL.get(), 4)),
                UNIVERSAL_FIREBOX_TIER_KEY, "gtuf.multiblock.pattern.error.universal_firebox");
    }

    /**
     * 结构外壳方块谓词：匹配指定方块并把<b>实际匹配到的方块注册名</b>写入
     * {@link #STRUCTURE_CASING_KEY}，机器在 {@code onStructureFormed()} 读取后作为
     * 控制器/仓室的渲染外壳——结构铺什么方块，部件就渲染成什么方块（不依赖
     * {@code appearanceBlock} 或等级映射，支持任意方块、任意部件类型）。
     *
     * <p>
     * 与等级谓词不同，本谓词不做混用报错：同一结构允许出现多个候选方块，
     * 以末次匹配为准（结构主体外壳应放在匹配顺序靠后的位置）。适合 KJS 直接把
     * 结构方块注册名传给本谓词的场景，例如
     * {@code GTUFPatternPredicates.Casing('gtceu:bronze_brick_casing')}。
     * </p>
     *
     * <p>
     * <b>只提供 String 变参这一种重载</b>：若再加 {@code Casing(Block...)}，
     * KubeJS/Rhino 对单个字符串参数判定 {@code String[]} 与 {@code Block[]}
     * 歧义（ambiguous），会在机器组装与 JEI 预览注册时抛异常，导致整机失效且
     * GT 全部 JEI 内容消失。Java 侧需要时请先解析成注册名字符串再传入。
     * </p>
     *
     * @param blockIds 允许的结构外壳方块注册名（形如 {@code "gtceu:bronze_brick_casing"}）
     */
    public static TraceabilityPredicate Casing(String... blockIds) {
        return new TraceabilityPredicate(blockWorldState -> {
            var blockState = blockWorldState.getBlockState();
            var blockId = ForgeRegistries.BLOCKS.getKey(blockState.getBlock()).toString();
            for (String id : blockIds) {
                if (id.equals(blockId)) {
                    blockWorldState.getMatchContext().set(STRUCTURE_CASING_KEY, id);
                    return true;
                }
            }
            return false;
        }, () -> Arrays.stream(blockIds)
                .map(id -> {
                    ResourceLocation rl = ResourceLocation.tryParse(id);
                    Block block = rl == null ? null : ForgeRegistries.BLOCKS.getValue(rl);
                    return block == null ? BlockInfo.fromBlockState(Blocks.AIR.defaultBlockState()) :
                            BlockInfo.fromBlockState(block.defaultBlockState());
                })
                .toArray(BlockInfo[]::new))
                .addTooltips(Component.translatable("gtuf.multiblock.pattern.error.casing"));
    }

    /**
     * 非空方块才写入映射（ChemicalHelper 对未知组合可能返回 null）。
     */
    private static void putIfNotNull(Map<Block, Integer> blockTiers, Block block, int tier) {
        if (block != null) blockTiers.put(block, tier);
    }

    /**
     * 等级检测谓词工厂：把"方块 → 等级"映射包装成 {@link TraceabilityPredicate}。
     * 候选方块按传入顺序生成（JEI 预览与等级一一对应），匹配时写入等级并检查混用。
     */
    private static TraceabilityPredicate tierPredicate(Map<Block, Integer> blockTiers, String key, String errorKey) {
        return new TraceabilityPredicate(blockWorldState -> {
            var blockState = blockWorldState.getBlockState();
            for (var entry : blockTiers.entrySet()) {
                if (blockState.is(entry.getKey())) {
                    return checkTier(blockWorldState, key, entry.getValue(), errorKey);
                }
            }
            return false;
        }, () -> blockTiers.keySet().stream()
                .map(block -> BlockInfo.fromBlockState(block.defaultBlockState()))
                .toArray(BlockInfo[]::new))
                .addTooltips(Component.translatable(errorKey));
    }

    /**
     * 写入等级并检查混用。若当前结构已写入不同等级则报错返回 false。
     */
    private static boolean checkTier(MultiblockState blockWorldState, String key, int tier, String errorKey) {
        Object current = blockWorldState.getMatchContext().getOrPut(key, tier);
        if (current instanceof Integer currentTier && currentTier != tier) {
            blockWorldState.setError(new PatternStringError(errorKey));
            return false;
        }
        return true;
    }
}
