package com.liangqu.gtuf.common.data;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.data.recipe.CustomTags;

/**
 * 压力管道的 TagPrefix 注册表（仿 GTM {@code TagPrefix.pipeNormalFluid}）。
 *
 * <p>
 * {@code new TagPrefix(name)} 构造即自动写入 {@code TagPrefix.PREFIXES}。itemTable 指向
 * {@link GTUF_PressureBlocks#PRESSURE_PIPE_BLOCKS} 生成的压力管道方块表（惰性取用，
 * 方块生成完成后再查询）。materialAmount 决定装配/回收折算的材料量。
 * </p>
 */
public final class GTUF_PressureTagPrefixes {

    private GTUF_PressureTagPrefixes() {}

    /** 普通压力管道（NORMAL，唯一规格）。 */
    public static final TagPrefix pipeNormalPressure = new TagPrefix("pipeNormalPressure")
            .itemTable(() -> GTUF_PressureBlocks.PRESSURE_PIPE_BLOCKS)
            .langValue("Normal %s Pressure Pipe")
            .miningToolTag(CustomTags.MINEABLE_WITH_CONFIG_VALID_PICKAXE_WRENCH)
            .materialAmount(GTValues.M * 3)
            .unificationEnabled(true)
            .enableRecycling();
}
