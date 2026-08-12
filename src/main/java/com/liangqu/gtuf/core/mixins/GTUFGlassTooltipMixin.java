package com.liangqu.gtuf.core.mixins;

import com.gregtechceu.gtceu.api.GTValues;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import com.liangqu.gtuf.api.pattern.GTUF_PatternPredicates;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

import javax.annotation.Nullable;

/**
 * 玻璃方块 tooltip 注入：对纳入玻璃等级（{@link GTUF_PatternPredicates#GLASS_TIERS}）的方块
 * 物品，在 hover 文本末尾追加一行"玻璃等级: %s"，%s 为电压等级名（九压标准，
 * 玻璃等级与电压等级一一对应，如钢化玻璃=3 → MV）。
 *
 * <p>
 * 注入点选 {@link BlockItem#appendHoverText}（方块物品 tooltip 生成的公共路径，原版玻璃、
 * GTCEu 玻璃都走这里），在 RETURN 时若方块注册名命中玻璃表则追加一行。等级取电压名
 * {@link GTValues#VN}[tier-1]（VN 数组从 ULV=0 起，九压标准从 ULV=1 起，故减一）。
 * </p>
 *
 * <p>
 * 打 Minecraft 类（{@link BlockItem}）的 mixin 必须走默认 {@code remap=true}
 * （refmap 映射到 SRG {@code BlockItem;m_7373_}），与打 GTCEu 类时 {@code remap=false}
 * 的做法不同；本类未显式设置 remap，沿用默认。
 * </p>
 */
@Mixin(BlockItem.class)
public abstract class GTUFGlassTooltipMixin {

    @Inject(method = "appendHoverText",
            at = @At("RETURN"))
    private void gtuf$injectGlassTierTooltip(ItemStack stack, @Nullable Level level,
                                             List<Component> tooltip, TooltipFlag flag,
                                             CallbackInfo ci) {
        Block block = ((BlockItem) (Object) this).getBlock();
        String id = ForgeRegistries.BLOCKS.getKey(block).toString();
        Integer tier = GTUF_PatternPredicates.GLASS_TIERS.get(id);
        // 玻璃映射由 config 驱动，等级可能被用户配出范围（>9）——越界时降级为不显示等级行，而非崩溃。
        if (tier != null && tier >= 1 && tier <= GTValues.VN.length) {
            tooltip.add(Component.translatable("gtuf.multiblock.glass_tier", GTValues.VN[tier - 1])
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
