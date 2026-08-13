package com.liangqu.gtuf.core.mixins;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.MaterialProperties;

import com.liangqu.gtuf.api.data.material.GTUF_MaterialPropertyKeys;
import com.liangqu.gtuf.api.data.material.PressurePipeProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 给 GTM {@link Material.Builder} 注入压力管道属性方法（KubeJS 材质脚本可链式调用）。
 *
 * <p>
 * KubeJS：{@code GTCEuStartupEvents.registry('gtceu:material', event =>
 * event.create('my_material').ingot().pressurePipe(12000, 5))}。材质脚本在
 * {@code MaterialEvent} 运行（早于 {@code PostMaterialEvent}），属性写进 builder 的
 * {@link MaterialProperties}；随后 {@code GTUF_PressureBlocks.generate()} 扫全量材质，
 * 凡带 {@code PRESSURE_PIPE} 属性者生成压力管道方块。
 * </p>
 *
 * <p>
 * 命名直接用 {@code pressurePipe}（非 {@code kjs$} 前缀、非 {@code @Unique}）：
 * <ol>
 * <li>不依赖 {@code @RemapPrefixForJS} 的类加载重映射时序——该注解的处理器在类加载时
 * 扫描字节码，mixin 注入的方法此时未必已合入目标类，时序不可靠；</li>
 * <li>避免引入 KubeJS 注解类导致非 KubeJS 运行期 {@code NoClassDefFoundError}；</li>
 * <li>{@code @Unique} 会被 Mixin 0.8.2+ 改名成 {@code $} 前缀，Rhino 按原名找不到
 * （见 GTUFRecipeBuilderMixin 同款教训）。</li>
 * </ol>
 * 公开方法随类合并后 Rhino 按原名暴露，与 GTM 原生 {@code fluidPipeProperties} 同机制。
 * 目标类是 GTM mod 类（不混淆），无需 remap 标志。
 * </p>
 */
@Mixin(Material.Builder.class)
public abstract class GTUFMaterialBuilderMixin {

    /** 目标类私有字段 {@code Material.Builder.properties}，经 @Shadow 供合并方法访问。 */
    @Shadow
    private MaterialProperties properties;

    /**
     * 给材质挂压力管道属性（量程 kPa）：{@code [minPressureKpa, maxPressureKpa]} 之外腔压
     * 会导致该材质管道节点破裂。挂属性后生成 TINY/NORMAL/LARGE/HUGE 四种规格压力管道方块。
     */
    public Material.Builder pressurePipe(double maxKpa, double minKpa) {
        properties.setProperty(GTUF_MaterialPropertyKeys.PRESSURE_PIPE, new PressurePipeProperties(maxKpa, minKpa));
        return (Material.Builder) (Object) this;
    }
}
