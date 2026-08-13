package com.liangqu.gtuf.api.data.material;

import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;

/**
 * GTUF 自定义材质属性键（PropertyKey）。
 *
 * <p>
 * 仿 GTM {@link PropertyKey#FLUID_PIPE}：压力管道属性 {@link #PRESSURE_PIPE} 挂到材质后，
 * {@code GTUF_PressureBlocks.generate()} 据此为材质生成压力管道方块。
 * </p>
 */
public final class GTUF_MaterialPropertyKeys {

    private GTUF_MaterialPropertyKeys() {}

    /** 压力管道属性：材质能承受的压力量程（kPa）。 */
    public static final PropertyKey<PressurePipeProperties> PRESSURE_PIPE = new PropertyKey<>("gtuf_pressure_pipe",
            PressurePipeProperties.class);
}
