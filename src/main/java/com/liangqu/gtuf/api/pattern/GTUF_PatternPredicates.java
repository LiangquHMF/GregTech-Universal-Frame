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

import com.liangqu.gtuf.config.GTUF_Config;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

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
    /** 通用玻璃等级。 */
    public static final String GLASS_TIER_KEY = "GlassType";

    /** 结构实际使用的外壳方块注册名（由 {@link #Casing} 谓词写入 MatchContext）。 */
    public static final String STRUCTURE_CASING_KEY = "StructureCasing";

    /** 玻璃混用等级错误键。 */
    public static final String GLASS_ERROR_KEY = "gtuf.multiblock.pattern.error.glass";
    /** 玻璃等级不足错误键（低于 {@link #GlassTier(int)} 要求的最低等级）。 */
    public static final String GLASS_MIN_ERROR_KEY = "gtuf.multiblock.pattern.error.glass.min";

    /**
     * 通用玻璃方块注册名 → 玻璃等级（= 电压等级，九压标准 ULV=1/LV=2/…/UV=9）。
     *
     * <p>
     * 与 {@link #GlassTier(int)} 谓词共享，tooltip mixin
     * {@code GTUFGlassTooltipMixin} 也按此表给方块注入"玻璃等级"提示。
     * 键用 {@link ResourceLocation#toString()} 的注册名（如 {@code "minecraft:glass"}、
     * {@code "gtceu:tempered_glass"}），不依赖 {@link GTBlocks#get()} 的运行时注册顺序。
     * </p>
     *
     * <p>
     * 映射由 {@link GTUF_Config} 的 {@code [glass].glassTiers} 配置驱动（完整列表，默认值
     * 即下方内置六个玻璃），整合包可新增其他模组的玻璃、改等级或删除条目；本字段引用
     * config 的运行期共享 map，config 重载（游戏内 reload 或重启）后原地刷新可见最新值。
     * </p>
     */
    public static final Map<String, Integer> GLASS_TIERS = GTUF_Config.getGlassTiers();

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
     * 通用玻璃等级（无最低等级门槛）：匹配 {@link #GLASS_TIERS} 中任一方块并按等级
     * 写入 {@link #GLASS_TIER_KEY}；同结构混用等级由 {@code checkTier} 拦截不成型。
     */
    public static TraceabilityPredicate GlassTier() {
        return glassTierPredicate(1);
    }

    /**
     * 通用玻璃等级（带最低等级门槛）：同 {@link #GlassTier()}，但低于 {@code minTier} 的玻璃
     * 直接报错不成型（不写入等级）。用于需要强制高等级玻璃的机器。
     *
     * @param minTier 允许的最低玻璃等级（1=原版，2=遮光，3=钢化，4=超净间，5=夹层，7=聚变）
     */
    public static TraceabilityPredicate GlassTier(int minTier) {
        return glassTierPredicate(minTier);
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
     *
     * <p>
     * 预览候选经 {@link GTUFTierPreviewState#reorder} 感知客户端 JEI「整体提升」档位：
     * 档位 &gt; 1 时目标档方块重排到数组首位，GTM 预览烘焙恒取 {@code candidates[0]}，
     * 使整个结构的所有等级谓词同步升级。服务端档位恒 1，候选顺序不变、真实成型不受影响。
     * 候选 Supplier 同时登记进 {@link GTUFTierPreviewState#TIER_CANDIDATES}，
     * 供 JEI 预览 mixin 按身份识别本谓词属于等级谓词。
     * </p>
     */
    private static TraceabilityPredicate tierPredicate(Map<Block, Integer> blockTiers, String key, String errorKey) {
        // 按等级值升序生成候选：candidates[0] 恒为 T1、candidates[tier-1] 恒为第 tier 档。
        // 不能直接用 keySet 顺序——调用方传入的 LinkedHashMap 由 Map.of(...) 拷贝而来，
        // 而 Map.of 的迭代顺序未定义（与插入序无关），会导致预览档位与方块错位。
        Supplier<BlockInfo[]> candidates = () -> GTUFTierPreviewState.reorder(blockTiers.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(entry -> BlockInfo.fromBlockState(entry.getKey().defaultBlockState()))
                .toArray(BlockInfo[]::new));
        GTUFTierPreviewState.TIER_CANDIDATES.add(candidates);
        return new TraceabilityPredicate(blockWorldState -> {
            var blockState = blockWorldState.getBlockState();
            for (var entry : blockTiers.entrySet()) {
                if (blockState.is(entry.getKey())) {
                    return checkTier(blockWorldState, key, entry.getValue(), errorKey);
                }
            }
            return false;
        }, candidates)
                .addTooltips(Component.translatable(errorKey));
    }

    /**
     * 玻璃等级检测谓词：{@link #tierPredicate} 的玻璃专用版，支持最低等级门槛。
     * 低于 {@code minTier} 的玻璃直接报 {@link #GLASS_MIN_ERROR_KEY} 返回 false（不成型、
     * 不写入等级）；达到门槛后复用 {@code checkTier} 做混用检查。
     */
    private static TraceabilityPredicate glassTierPredicate(int minTier) {
        // 与 tierPredicate 相同：按玻璃等级值升序生成候选，保证 candidates[0] 恒为最低档。
        Supplier<BlockInfo[]> candidates = () -> GTUFTierPreviewState.reorder(GLASS_TIERS.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(entry -> {
                    ResourceLocation rl = ResourceLocation.tryParse(entry.getKey());
                    Block block = rl == null ? null : ForgeRegistries.BLOCKS.getValue(rl);
                    return block == null ? BlockInfo.fromBlockState(Blocks.AIR.defaultBlockState()) :
                            BlockInfo.fromBlockState(block.defaultBlockState());
                })
                .toArray(BlockInfo[]::new));
        GTUFTierPreviewState.TIER_CANDIDATES.add(candidates);
        return new TraceabilityPredicate(blockWorldState -> {
            var blockState = blockWorldState.getBlockState();
            var blockId = ForgeRegistries.BLOCKS.getKey(blockState.getBlock()).toString();
            Integer tier = GLASS_TIERS.get(blockId);
            if (tier != null) {
                if (tier < minTier) {
                    blockWorldState.setError(new PatternStringError(GLASS_MIN_ERROR_KEY));
                    return false;
                }
                return checkTier(blockWorldState, GLASS_TIER_KEY, tier, GLASS_ERROR_KEY);
            }
            return false;
        }, candidates)
                .addTooltips(Component.translatable(GLASS_ERROR_KEY));
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
