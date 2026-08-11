package com.liangqu.gtuf.client.renderer.machine;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.client.model.machine.IControllerModelRenderer;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRender;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderType;
import com.gregtechceu.gtceu.client.util.GTQuadTransformers;
import com.gregtechceu.gtceu.client.util.ModelUtils;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;

import com.liangqu.gtuf.api.machine.ITieredCasingMachine;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 可替换外壳多方块机的部件/控制器渲染器：成型后<b>仓室</b>渲染成
 * {@link ITieredCasingMachine#getCasingState()} 返回的结构实际外壳方块，材质随结构外壳等级
 * 匹配（蒸汽机是青铜/脱氧钢两级，电力机可自行把任意等级映射到对应机壳方块）；
 * <b>控制器自身</b>渲染成外观方块立方体：Casing 谓词机器取
 * {@link ITieredCasingMachine#getControllerAppearanceState()}（注册外观方块，不被结构外壳覆盖），
 * 等级外壳机器回退 {@link ITieredCasingMachine#getCasingState()}（跟随结构外壳等级）。
 * 外观材质与 base 材质一致时外观立方体整体跳过（base 直接渲染材质 + overlay，避免 z-fight），
 * 不一致时全部面渲染、正面材质与其余面一致。
 *
 * <p>
 * GTM 默认的部件外观替换（{@code MachineModel#renderPartOverrides}）把部件 "all" 纹理替换成
 * 控制器模型写死的 {@code baseCasingTexture}，因此外壳换材质时仓室仍显示注册时的纹理。
 * 本渲染器仿大锅炉的 {@code BoilerMultiPartRender}：
 * </p>
 * <ul>
 * <li><b>部件</b>（{@link #renderPartModel}）：直接把部件 quads 替换成当前外壳方块状态；</li>
 * <li><b>控制器自身</b>（{@link #getRenderQuads}）：成型后在 base 模型之上追加同尺寸
 * 注册外观方块立方体 quads。cutout 渲染用 {@code LEQUAL} 深度测试，共面时后画覆盖，
 * 因此成型后控制器显示注册外观材质；base 模型凸出 0.002 的 workable overlay 仍保留。</li>
 * </ul>
 *
 * <p>
 * 不限定机器类型：任何实现 {@link ITieredCasingMachine} 的多方块控制器（蒸汽/电力）
 * 都走本渲染器。通过 {@code DynamicRenderManager} 以 {@code gtuf:tiered_steam_parts} 注册类型，
 * 由 {@link com.liangqu.gtuf.common.data.models.GTUFModels#createTieredMachineModel}
 * 附加到控制器模型（{@code addDynamicRenderer}）后生效。
 * </p>
 */
public class GTUFTieredPartRender extends DynamicRender<MetaMachine, GTUFTieredPartRender>
                                  implements IControllerModelRenderer {

    // spotless:off
    public static final Codec<GTUFTieredPartRender> CODEC = Codec.unit(GTUFTieredPartRender::new);
    public static final DynamicRenderType<MetaMachine, GTUFTieredPartRender> TYPE =
            new DynamicRenderType<>(CODEC);
    // spotless:on

    /** 外壳方块状态 → 烘焙模型缓存（任意外壳方块，不限于青铜/钢）。 */
    private final Map<BlockState, BakedModel> modelCache = new HashMap<>();

    @Override
    public DynamicRenderType<MetaMachine, GTUFTieredPartRender> getType() {
        return TYPE;
    }

    @Override
    public void render(MetaMachine machine, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {}

    @Override
    public boolean shouldRender(MetaMachine machine, Vec3 cameraPos) {
        return false;
    }

    @Override
    public boolean isBlockEntityRenderer() {
        return false;
    }

    /**
     * 控制器自身渲染：成型后追加外观方块立方体 quads，盖住 base 模型注册时写死的立方体材质。
     * 外观来源优先级：{@link ITieredCasingMachine#getControllerAppearanceState()}
     * （Casing 谓词机器 = 注册外观方块）→ 回退 {@link ITieredCasingMachine#getCasingState()}
     * （等级外壳机器 = 结构外壳等级方块）。
     * <p>
     * 部件（{@link IMultiPart}）不走此路径——它们的 base 由 {@link #renderPartModel} 替换；
     * 未成型、未实现接口的控制器返回空（显示注册时的 workable base 模型）。
     * 控制器朝向面（overlay 所在的正面）无条件由 base 模型直接渲染材质 + overlay：外观立方体
     * 在正面必然与凸出的 overlay（±0.01）深度竞争，远处深度缓冲精度不足会 z-fighting 并被
     * 后画的外观立方体赢走，遮挡 overlay（overlay 消失根因）。非正面外观材质与 base 材质一致
     * （{@link #matchesBaseMaterial}）时外观立方体完全冗余，整体跳过；不一致时渲染以盖住 base。
     * </p>
     */
    @Override
    @OnlyIn(Dist.CLIENT)
    public List<BakedQuad> getRenderQuads(@Nullable MetaMachine machine, @Nullable BlockAndTintGetter level,
                                          @Nullable BlockPos pos, @Nullable BlockState blockState,
                                          @Nullable Direction side, RandomSource rand,
                                          @NotNull ModelData modelData, @Nullable RenderType renderType) {
        if (machine instanceof IMultiPart) return Collections.emptyList();
        if (!(machine instanceof ITieredCasingMachine tiered) || !tiered.isFormed()) return Collections.emptyList();
        if (level == null || pos == null) return Collections.emptyList();
        // 正面（overlay 所在的机器朝向面）：无条件由 base 模型直接渲染材质 + overlay。
        // 外观立方体在正面必然与凸出的 overlay（±0.01）深度竞争，远处（约 4+ blocks）
        // 24 位深度缓冲精度不足 → z-fighting → 后画的外观立方体赢 → 遮挡 overlay
        // （overlay 消失根因）。因此正面一律跳过，保证 overlay 永远可见；正面 base 材质与
        // 其余面外观材质不一致是结构性权衡（正面含 overlay 显示层，GT 机器普遍如此）。
        if (side == machine.getFrontFacing()) return Collections.emptyList();
        BlockState appearance = tiered.getControllerAppearanceState();
        // 等级外壳/可替换外壳机器（structureCasingId 为 null，getControllerAppearanceState 返回 null）
        // 控制器跟随结构外壳（getCasingState()）；Casing 谓词机器（如 Casing=stone）仍显示注册外观方块。
        if (appearance == null) appearance = tiered.getCasingState();
        if (appearance == null) return Collections.emptyList();
        BakedModel model = getModel(appearance);
        if (model == null) return Collections.emptyList();
        modelData = model.getModelData(level, pos, appearance, modelData);
        List<BakedQuad> quads = model.getQuads(appearance, side, rand, modelData, renderType);
        if (quads.isEmpty()) return Collections.emptyList();
        // 外观材质与 base 材质一致时外观立方体完全冗余（base 已渲染同材质 + overlay）：整体跳过，
        // 避免外观立方体（外扩 0.005）与 overlay（凸出 0.01）深度间隙仅 0.005，在远处 z-fighting
        // 遮挡 overlay（控制器 overlay 消失的根因）。不一致时仍需立方体覆盖 base。
        if (matchesBaseMaterial(quads, machine.self().getBlockState(), side, rand, renderType)) {
            return Collections.emptyList();
        }
        // 复制后沿各面法线外扩 0.005：外观立方体落在 base 立方体（[0,16]）与 workable
        // overlay（±0.01）之间，确定性盖住 base 而不会被 overlay 遮挡，避免两者共面
        // z-fight 产生的材质闪烁。直接改原 quad 会污染方块烘焙缓存，因此必须先复制。
        List<BakedQuad> result = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            result.add(GTQuadTransformers.copy(quad));
        }
        GTQuadTransformers.offset(0.005f).processInPlace(result);
        return result;
    }

    /**
     * 外观立方体 quads 与机器 base 模型 quads 是否使用相同材质（一致时外观立方体冗余，应整体跳过）。
     * <p>
     * 仅对非正面调用（正面由 {@link #getRenderQuads} 无条件跳过）。用 {@link ModelData#EMPTY}
     * 取机器 base 模型 quads：modelData 无 LEVEL/POS 时 {@code MachineModel.renderMachine}
     * 走 machine=null 分支，不触发动态渲染器（本渲染器 getRenderQuads 对 null machine 返回空），
     * 不会递归。逐贴图比对。
     * </p>
     */
    @OnlyIn(Dist.CLIENT)
    private static boolean matchesBaseMaterial(List<BakedQuad> appearanceQuads, BlockState machineState,
                                               @Nullable Direction side, RandomSource rand,
                                               @Nullable RenderType renderType) {
        BakedModel baseModel = ModelUtils.getModelForState(machineState);
        if (baseModel == null) return false;
        var baseQuads = baseModel.getQuads(machineState, side, rand, ModelData.EMPTY, renderType);
        for (var aq : appearanceQuads) {
            var as = aq.getSprite();
            if (as == null) continue;
            for (var bq : baseQuads) {
                var bs = bq.getSprite();
                // 1.20.1 的 TextureAtlasSprite 无 getName()；贴图名取 contents().name()（ResourceLocation）。
                if (bs != null && as.contents().name().equals(bs.contents().name())) return true;
            }
        }
        return false;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderPartModel(List<BakedQuad> quads, IMultiController controller, IMultiPart part,
                                Direction frontFacing, @Nullable Direction side, RandomSource rand,
                                @NotNull ModelData modelData, @Nullable RenderType renderType) {
        if (!(controller.self() instanceof ITieredCasingMachine tiered)) return;
        BlockState casing = tiered.getCasingState();
        if (casing == null) return;
        BakedModel model = getModel(casing);
        if (model == null) return;
        MultiblockControllerMachine machine = controller.self();
        modelData = model.getModelData(machine.getLevel(), part.self().getPos(), casing, modelData);
        emitQuads(quads, model, machine.getLevel(), part.self().getPos(), casing, side, rand, modelData, renderType);
    }

    /** 按外壳方块取烘焙模型（懒加载缓存）。 */
    @Nullable
    @OnlyIn(Dist.CLIENT)
    private BakedModel getModel(BlockState casing) {
        BakedModel model = modelCache.get(casing);
        if (model == null) {
            model = ModelUtils.getModelForState(casing);
            if (model != null) modelCache.put(casing, model);
        }
        return model;
    }

    private static void emitQuads(List<BakedQuad> quads, @Nullable BakedModel model,
                                  BlockAndTintGetter level, BlockPos pos, BlockState state,
                                  @Nullable Direction side, RandomSource rand,
                                  ModelData modelData, @Nullable RenderType renderType) {
        if (model == null) return;
        modelData = model.getModelData(level, pos, state, modelData);
        quads.addAll(model.getQuads(state, side, rand, modelData, renderType));
    }
}
