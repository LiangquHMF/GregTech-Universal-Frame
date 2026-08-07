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
import net.minecraft.world.level.block.Block;

/**
 * GTUF 自定义多方块结构检测谓词，模仿 {@link com.gregtechceu.gtceu.api.pattern.Predicates#heatingCoils()}。
 *
 * <p>检测外壳/框架方块并把对应等级写入 {@link com.gregtechceu.gtceu.api.pattern.util.PatternMatchContext}，
 * 机器在 {@code onStructureFormed()} 中通过 {@code getMultiblockState().getMatchContext()} 读取。
 * 同一结构内混用不同等级时设置错误信息使结构不匹配。</p>
 */
public final class GTUF_PatternPredicates {

    public static final String CASING_TIER_KEY = "CasingType";
    public static final String FRAME_TIER_KEY = "FrameType";

    private GTUF_PatternPredicates() {}

    /**
     * 外壳等级检测。工业蒸汽机械方块 (steam_machine_casing) → Tier 1；
     * 脱氧钢机械方块 (solid_machine_casing) → Tier 2。结果写入 {@link #CASING_TIER_KEY}。
     */
    public static TraceabilityPredicate steamCasingTier() {
        Block bronzeCasing = GTBlocks.CASING_BRONZE_BRICKS.get();
        Block steelCasing = GTBlocks.CASING_STEEL_SOLID.get();
        return new TraceabilityPredicate(blockWorldState -> {
            var blockState = blockWorldState.getBlockState();
            if (blockState.is(steelCasing)) {
                return checkTier(blockWorldState, CASING_TIER_KEY, 2, "gtuf.multiblock.pattern.error.casing");
            }
            if (blockState.is(bronzeCasing)) {
                return checkTier(blockWorldState, CASING_TIER_KEY, 1, "gtuf.multiblock.pattern.error.casing");
            }
            return false;
        }, () -> new BlockInfo[]{
                BlockInfo.fromBlockState(bronzeCasing.defaultBlockState()),
                BlockInfo.fromBlockState(steelCasing.defaultBlockState())})
                .addTooltips(Component.translatable("gtuf.multiblock.pattern.error.casing"));
    }

    /**
     * 框架等级检测。青铜框架 → Tier 1；钢框架 → Tier 2。结果写入 {@link #FRAME_TIER_KEY}。
     */
    public static TraceabilityPredicate frameTier() {
        Block bronzeFrame = ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Bronze);
        Block steelFrame = ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Steel);
        return new TraceabilityPredicate(blockWorldState -> {
            var blockState = blockWorldState.getBlockState();
            if (steelFrame != null && blockState.is(steelFrame)) {
                return checkTier(blockWorldState, FRAME_TIER_KEY, 2, "gtuf.multiblock.pattern.error.frame");
            }
            if (bronzeFrame != null && blockState.is(bronzeFrame)) {
                return checkTier(blockWorldState, FRAME_TIER_KEY, 1, "gtuf.multiblock.pattern.error.frame");
            }
            return false;
        }, () -> new BlockInfo[]{
                BlockInfo.fromBlockState(bronzeFrame.defaultBlockState()),
                BlockInfo.fromBlockState(steelFrame.defaultBlockState())})
                .addTooltips(Component.translatable("gtuf.multiblock.pattern.error.frame"));
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
