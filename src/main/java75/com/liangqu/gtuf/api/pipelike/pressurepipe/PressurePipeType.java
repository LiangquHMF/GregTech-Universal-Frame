package com.liangqu.gtuf.api.pipelike.pressurepipe;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.block.PipeBlock;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.pipenet.IMaterialPipeType;
import com.gregtechceu.gtceu.api.registry.registrate.provider.GTBlockstateProvider;
import com.gregtechceu.gtceu.client.model.pipe.PipeModel;

import net.minecraft.resources.ResourceLocation;

import com.liangqu.gtuf.GTUF_Core;
import com.liangqu.gtuf.api.data.material.PressurePipeProperties;
import com.liangqu.gtuf.common.data.GTUF_PressureTagPrefixes;

/**
 * 压力管道规格枚举（仿 GTM {@code FluidPipeType}，仅保留普通规格 NORMAL）—— 7.5.3 兼容变体。
 *
 * <p>
 * 压力量程 {@link PressurePipeProperties} 由材质决定（config {@code [pressure].pressurePipeTolerances}
 * 或 KubeJS {@code .pressurePipe(max,min)}），{@link #modifyProperties} 原样返回
 * （压力管道不因规格改变承压范围）；规格只决定厚度（渲染/碰撞）与 TagPrefix（标签/合成量）。
 * </p>
 *
 * <p>
 * 模型贴图沿用 GTM 流体管道的通用贴图（{@code GTCEu.id("block/pipe/pipe_side")} 与
 * {@code block/pipe/pipe_%s_in}）——材质资源在 gtceu 命名空间，勿用 gtuf modid。
 * </p>
 *
 * <p>
 * <b>版本差异（7.5.3 形态）：</b>{@code createPipeModel(PipeBlock, Material, GTBlockstateProvider)}
 * 返回新包 {@code client.model.pipe.PipeModel}（构造需方块与 provider，运行时动态模型）。
 * 7.1.4~7.4.1 为无参 {@code createPipeModel(Material)} 返回旧包 {@code client.model.PipeModel}，
 * 见 {@code src/main/java714}。
 * </p>
 */
public enum PressurePipeType implements IMaterialPipeType<PressurePipeProperties> {

    NORMAL("normal", 0.5f, GTUF_PressureTagPrefixes.pipeNormalPressure);

    /** 本管道类型的唯一标识（区分于流体/物品/线缆管道）。 */
    public static final ResourceLocation TYPE_ID = GTUF_Core.id("pressure");

    public final String name;
    public final float thickness;
    public final TagPrefix tagPrefix;

    PressurePipeType(String name, float thickness, TagPrefix tagPrefix) {
        this.name = name;
        this.thickness = thickness;
        this.tagPrefix = tagPrefix;
    }

    @Override
    public float getThickness() {
        return thickness;
    }

    @Override
    public PressurePipeProperties modifyProperties(PressurePipeProperties baseProperties) {
        // 压力管道的承压范围只由材质决定，规格不影响量程。
        return baseProperties;
    }

    @Override
    public boolean isPaintable() {
        return true;
    }

    @Override
    public ResourceLocation type() {
        return TYPE_ID;
    }

    @Override
    public TagPrefix getTagPrefix() {
        return tagPrefix;
    }

    /**
     * 生成方块状态提供器所需的管道模型（7.5.3 API：{@code PipeModel} 构造需方块与 provider）。
     */
    public PipeModel createPipeModel(PipeBlock<?, ?, ?> block, Material material, GTBlockstateProvider provider) {
        return new PipeModel(block, provider, thickness,
                GTCEu.id("block/pipe/pipe_side"),
                GTCEu.id("block/pipe/pipe_%s_in".formatted(name)));
    }
}
