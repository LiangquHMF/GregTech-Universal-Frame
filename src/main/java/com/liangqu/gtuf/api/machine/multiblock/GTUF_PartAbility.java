package com.liangqu.gtuf.api.machine.multiblock;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;

/**
 * GTUF 自定义部件能力位（PartAbility）。
 *
 * <p>GTM 原生能力位只有 {@code PARALLEL_HATCH}（并行仓），没有"线程仓"能力。本类仿
 * GTNA 的 {@code com.raishxn.gtna.api.machine.multiblock.GTNAPartAbility} 定义线程仓
 * 能力位，多方块结构通过 {@code Predicates.abilities(GTUF_PartAbility.THREAD_HATCH)}
 * 识别线程仓仓室。</p>
 *
 * <p>来源：GTNA {@code GTNAPartAbility.THREAD_HATCH = new PartAbility("thread_hatch")}。</p>
 */
public final class GTUF_PartAbility {

    private GTUF_PartAbility() {}

    /** 线程仓能力位：安装该仓室的多方块可同时处理多类配方（线程数由仓室 GUI 配置）。 */
    public static final PartAbility THREAD_HATCH = new PartAbility("thread_hatch");
}
