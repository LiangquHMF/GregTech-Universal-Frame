package com.liangqu.gtuf.common.data;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.block.MaterialPipeBlock;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.registry.MaterialRegistry;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.item.MaterialPipeBlockItem;
import com.gregtechceu.gtceu.common.data.GTBlocks;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Blocks;

import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.liangqu.gtuf.api.data.material.GTUF_MaterialPropertyKeys;
import com.liangqu.gtuf.api.pipelike.pressurepipe.PressurePipeType;
import com.liangqu.gtuf.common.block.PressurePipeBlock;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;

import static com.liangqu.gtuf.api.registry.GTUF_Registries.REGISTRATE;

/**
 * 压力管道方块生成（仿 GTM {@code GTMaterialBlocks.generateFluidPipeBlocks}）。
 *
 * <p>
 * 为 config {@code [pressure].pressurePipeTolerances} 列出的材质 × {@link PressurePipeType}
 * （唯一规格 NORMAL）生成 {@code gtuf:} 命名空间方块，建表 {@link #PRESSURE_PIPE_BLOCKS} 供
 * {@code GTUF_PressureTagPrefixes} 的 itemTable 与 {@code GTUF_BlockEntities} 的 validBlocks 使用。
 * </p>
 *
 * <p>
 * 必须在 {@code PostMaterialEvent}（材质已挂 {@code PRESSURE_PIPE} 属性）之后调用，且在
 * Forge 方块/物品 RegisterEvent 之前——由 {@code CommonProxy.modifyMaterials} 触发。
 * </p>
 */
public final class GTUF_PressureBlocks {

    private GTUF_PressureBlocks() {}

    static final ImmutableTable.Builder<TagPrefix, Material, BlockEntry<PressurePipeBlock>> PRESSURE_PIPE_BLOCKS_BUILDER = ImmutableTable
            .builder();

    /** 压力管道方块引用表：{@code TagPrefix × Material → BlockEntry}。 */
    public static Table<TagPrefix, Material, BlockEntry<PressurePipeBlock>> PRESSURE_PIPE_BLOCKS;

    public static void generate() {
        GTCEu.LOGGER.debug("Generating GTUF Pressure Pipe Blocks...");
        MaterialRegistry registry = GTCEuAPI.materialManager.getRegistry(GTCEu.MOD_ID);
        // 扫全量材质：凡带 PRESSURE_PIPE 属性者（config 内置材质 + KubeJS 材质脚本
        // .pressurePipe() 自定义材质，均已在 PostMaterialEvent 前挂好属性）生成管道方块。
        for (Material material : registry.getAllMaterials()) {
            if (!material.hasProperty(GTUF_MaterialPropertyKeys.PRESSURE_PIPE)) {
                continue;
            }
            for (PressurePipeType pipeType : PressurePipeType.values()) {
                if (pipeType.tagPrefix.isIgnored(material)) {
                    continue;
                }
                registerPressurePipeBlock(material, pipeType);
            }
        }
        PRESSURE_PIPE_BLOCKS = PRESSURE_PIPE_BLOCKS_BUILDER.build();
        // 方块实体 validBlocks 依赖建好的方块表，须在生成后注册。
        GTUF_BlockEntities.register();
        GTCEu.LOGGER.debug("Generating GTUF Pressure Pipe Blocks... Complete!");
    }

    private static void registerPressurePipeBlock(Material material, PressurePipeType pipeType) {
        var entry = REGISTRATE
                .block("%s_%s_pressure_pipe".formatted(material.getName(), pipeType.name),
                        p -> new PressurePipeBlock(p, pipeType, material))
                .initialProperties(() -> Blocks.IRON_BLOCK)
                .properties(p -> p.dynamicShape().noOcclusion().noLootTable().forceSolidOn())
                .transform(GTBlocks.unificationBlock(pipeType.tagPrefix, material))
                .blockstate(NonNullBiConsumer.noop())
                .setData(ProviderType.LANG, NonNullBiConsumer.noop())
                .setData(ProviderType.LOOT, NonNullBiConsumer.noop())
                .addLayer(() -> RenderType::cutoutMipped)
                .addLayer(() -> RenderType::translucent)
                .color(() -> MaterialPipeBlock::tintedColor)
                .item(MaterialPipeBlockItem::new)
                .model(NonNullBiConsumer.noop())
                .color(() -> MaterialPipeBlockItem::tintColor)
                .build()
                .register();
        PRESSURE_PIPE_BLOCKS_BUILDER.put(pipeType.tagPrefix, material, entry);
    }
}
