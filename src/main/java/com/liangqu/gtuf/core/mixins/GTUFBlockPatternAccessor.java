package com.liangqu.gtuf.core.mixins;

import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link BlockPattern} 的 protected {@code blockMatches} 字段，供 JEI 预览档位 mixin
 * （{@link GTUFPatternPreviewTierMixin}）扫描结构全部谓词、识别 GTUF 等级谓词。
 *
 * <p>
 * 目标 {@code BlockPattern} 是 GTM mod 类（无 SRG 映射），故 {@code @Accessor} 需
 * {@code remap = false}。仅在客户端加载（{@code client} 列表），无注入行为。
 * </p>
 */
@Mixin(BlockPattern.class)
public interface GTUFBlockPatternAccessor {

    @Accessor(value = "blockMatches", remap = false)
    TraceabilityPredicate[][][] gtuf$getBlockMatches();
}
