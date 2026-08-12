package com.liangqu.gtuf.api.pattern;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * JEI 多方块预览「整体提升」等级状态的客户端共享状态。
 *
 * <p>
 * GTM 预览烘焙（{@code BlockPattern.getPreview}）对每个谓词位置恒取
 * {@code SimplePredicate.candidates.get()[0]} 作为预览方块。本类持有的
 * {@link #currentTier} 让 {@link GTUF_PatternPredicates} 的等级谓词在返回候选时
 * 把目标档方块重排到数组首位 —— 所有等级谓词同步换档，整个结构预览随之整体升级。
 * </p>
 *
 * <p>
 * <b>纯客户端语义</b>：字段只在客户端 JEI 预览交互时被写入（由
 * {@code GTUFPatternPreviewTierMixin} 维护），服务端恒为 1（默认值），因此真实结构
 * 成型匹配（走 {@code predicate} 测试 lambda，不读 candidates）行为完全不变。
 * </p>
 */
public final class GTUFTierPreviewState {

    /** 当前预览整体等级（1 = 默认，与未启用时一致；不越界 clamp 到候选长度内）。 */
    public static volatile int currentTier = 1;

    /**
     * 等级谓词候选 Supplier 登记表（身份比较）。
     *
     * <p>
     * {@link GTUF_PatternPredicates} 的等级谓词（{@code tierPredicate}/{@code glassTierPredicate}）
     * 构造时把自己的 preview 候选 Supplier 登记进本集合；客户端 JEI 预览 mixin
     * （{@code GTUFPatternPreviewTierMixin}）扫描机器结构谓词时，凭 {@code sp.candidates}
     * 的身份在集合中命中来识别"这是等级谓词"，从而计算可切换档位（候选数 = 最高档）。
     * 只有 GTUF 自己的候选会登记，能力位/普通方块谓词永远不命中 → 无等级谓词的机器不显示按钮。
     * </p>
     */
    public static final Set<Supplier<BlockInfo[]>> TIER_CANDIDATES = ConcurrentHashMap.newKeySet();

    private GTUFTierPreviewState() {}

    /**
     * 把候选数组重排为目标等级方块在前：返回的 {@code [0]} 即预览渲染的方块，
     * 其余方块保持原相对顺序。{@code currentTier <= 1} 或数组长度 <= 1 时原样返回
     * （与未启用时行为完全一致）。
     *
     * @param all 按等级升序排列的全部候选方块（调用方保证顺序）
     */
    public static BlockInfo[] reorder(BlockInfo[] all) {
        int tier = Math.max(1, Math.min(currentTier, all.length));
        if (tier <= 1 || all.length <= 1) return all;
        BlockInfo[] reordered = new BlockInfo[all.length];
        reordered[0] = all[tier - 1];
        for (int i = 0, j = 1; i < all.length; i++) {
            if (i == tier - 1) continue;
            reordered[j++] = all[i];
        }
        return reordered;
    }
}
