package com.liangqu.gtuf.client.renderer.machine;

import com.gregtechceu.gtceu.api.block.property.GTBlockStateProperties;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.client.model.machine.IControllerModelRenderer;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRender;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderType;
import com.gregtechceu.gtceu.client.util.GTQuadTransformers;
import com.gregtechceu.gtceu.client.util.ModelUtils;
import com.gregtechceu.gtceu.common.block.BoilerFireboxType;
import com.gregtechceu.gtceu.common.data.GTBlocks;

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
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 带燃烧室的等级外壳多方块机渲染器：{@link GTUFTieredPartRender}（控制器/仓室按外壳等级渲染）
 * 与 GTM 大锅炉 {@code BoilerMultiPartRender}（燃烧室行部件渲染成燃烧室方块）的合并版。
 * 移植自 hypermatrix_core 的 {@code HMCTieredBoilerPartRender}。
 *
 * <p>
 * 用途：合金炉这类<b>底部燃烧室 + 蒸汽仓</b>的结构。若像最初那样同时挂两个渲染器，
 * GTM 的 {@code MachineModel#renderPartOverrides} 会把<b>所有</b>实现了
 * {@link IControllerModelRenderer} 的渲染器 quads 累积进同一个列表，部件被重复渲染
 * （连接缝/z-fighting），且模型 JSON 里 type 冲突。本类用一个渲染器完成两件事：
 * </p>
 * <ul>
 * <li><b>燃烧室行部件</b>（位于控制器正下方那一行，仿大锅炉判定）：渲染成燃烧室方块
 * （{@code firebox_active} / {@code firebox_idle} 两态，工作/闲置切换），方块自身的连接纹理
 * 会让蒸汽仓与相邻燃烧室无缝融合；</li>
 * <li><b>其余部件</b>：渲染成 {@link ITieredCasingMachine#getCasingState()} 返回的结构实际
 * 外壳方块（青铜/脱氧钢随外壳等级匹配）；</li>
 * <li><b>控制器自身</b>（{@link #getRenderQuads}）：成型后追加注册外观方块
 * （{@code appearanceBlock}，{@link ITieredCasingMachine#getControllerAppearanceState()}）
 * 立方体 quads，不被结构外壳覆盖；外观材质与 base 一致时整体跳过（避免 z-fight 遮 overlay）。</li>
 * </ul>
 *
 * <p>
 * 燃烧室行判定复用 {@code BoilerMultiPartRender} 的 {@code belowControllerY} 方案：
 * 非竖直翻转的多方块（如 {@code RotationState.NON_Y_AXIS}）下方即世界 DOWN，
 * 控制器正下方一行正是 pattern 中放燃烧室的那一行。
 * </p>
 *
 * <p>
 * 通过 {@code DynamicRenderManager} 以 {@code gtuf:tiered_boiler_parts} 注册类型，
 * 由 {@link com.liangqu.gtuf.common.data.models.GTUFModels#createTieredBoilerMachineModel}
 * 附加到控制器模型（{@code addDynamicRenderer}）后生效。切勿在此之上再叠加其他
 * {@code IControllerModelRenderer}（叠加即重复渲染）。
 * </p>
 */
public class GTUFTieredBoilerPartRender extends DynamicRender<MetaMachine, GTUFTieredBoilerPartRender>
                                        implements IControllerModelRenderer {

    // spotless:off
    public static final Codec<GTUFTieredBoilerPartRender> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(BlockState.CODEC.fieldOf("firebox_idle").forGetter(GTUFTieredBoilerPartRender::getFireboxIdle),
                   BlockState.CODEC.fieldOf("firebox_active").forGetter(GTUFTieredBoilerPartRender::getFireboxActive))
            .apply(instance, GTUFTieredBoilerPartRender::new));
    public static final DynamicRenderType<MetaMachine, GTUFTieredBoilerPartRender> TYPE =
            new DynamicRenderType<>(CODEC);
    // spotless:on

    /** 燃烧室闲置态方块状态。 */
    private final BlockState fireboxIdle;
    /** 燃烧室工作态方块状态（{@code active=true}）。 */
    private final BlockState fireboxActive;

    /** 外壳方块状态 → 烘焙模型缓存（任意外壳方块，不限于青铜/钢）。 */
    private final Map<BlockState, BakedModel> modelCache = new HashMap<>();
    private BakedModel fireboxIdleModel;
    private BakedModel fireboxActiveModel;

    public GTUFTieredBoilerPartRender(BoilerFireboxType fireboxType) {
        this(GTBlocks.ALL_FIREBOXES.get(fireboxType).getDefaultState(),
                GTBlocks.ALL_FIREBOXES.get(fireboxType).getDefaultState()
                        .setValue(GTBlockStateProperties.ACTIVE, true));
    }

    public GTUFTieredBoilerPartRender(BlockState fireboxIdle, BlockState fireboxActive) {
        this.fireboxIdle = fireboxIdle;
        this.fireboxActive = fireboxActive;
    }

    @Override
    public DynamicRenderType<MetaMachine, GTUFTieredBoilerPartRender> getType() {
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
        // 控制器跟随结构外壳（getCasingState()）；Casing 谓词机器仍显示注册外观方块。
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
        // 同 GTUFTieredPartRender：复制后沿各面法线外扩 0.005，盖住 base 立方体（[0,16]）
        // 而留在 workable overlay（±0.01）之后，避免共面 z-fight 闪烁。原 quad 不可直接改。
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

    /**
     * 部件渲染：位于燃烧室行的部件（蒸汽仓）渲染成燃烧室方块，其余部件渲染成 tiered 外壳。
     */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderPartModel(List<BakedQuad> quads, IMultiController controller, IMultiPart part,
                                Direction frontFacing, @Nullable Direction side, RandomSource rand,
                                @NotNull ModelData modelData, @Nullable RenderType renderType) {
        MultiblockControllerMachine machine = controller.self();
        Direction relativeDown = RelativeDirection.DOWN
                .getRelative(machine.getFrontFacing(), machine.getUpwardsFacing(), machine.isFlipped());
        int fireboxRow = machine.getPos().relative(relativeDown).get(relativeDown.getAxis());
        if (fireboxRow == part.self().getPos().get(relativeDown.getAxis())) {
            // 燃烧室行：渲染成燃烧室方块（工作/闲置两态），连接纹理与相邻燃烧室融合
            ensureFireboxModels();
            boolean working = controller instanceof IRecipeLogicMachine recipeLogic &&
                    recipeLogic.getRecipeLogic().isWorking();
            BlockState state = working ? fireboxActive : fireboxIdle;
            BakedModel model = working ? fireboxActiveModel : fireboxIdleModel;
            BlockAndTintGetter level = machine.getLevel();
            BlockPos partPos = part.self().getPos();
            if (model != null) {
                modelData = model.getModelData(level, partPos, state, modelData);
                quads.addAll(model.getQuads(state, side, rand, modelData, renderType));
            }
        } else {
            // 其余位置：渲染成实际外壳方块
            if (!(machine instanceof ITieredCasingMachine tiered)) return;
            BlockState casing = tiered.getCasingState();
            if (casing == null) return;
            BakedModel model = getModel(casing);
            if (model == null) return;
            BlockAndTintGetter level = machine.getLevel();
            BlockPos partPos = part.self().getPos();
            modelData = model.getModelData(level, partPos, casing, modelData);
            emitQuads(quads, model, level, partPos, casing, side, rand, modelData, renderType);
        }
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

    /** 懒加载燃烧室两态烘焙模型。 */
    @OnlyIn(Dist.CLIENT)
    private void ensureFireboxModels() {
        if (fireboxIdleModel == null) fireboxIdleModel = ModelUtils.getModelForState(fireboxIdle);
        if (fireboxActiveModel == null) fireboxActiveModel = ModelUtils.getModelForState(fireboxActive);
    }

    private static void emitQuads(List<BakedQuad> quads, @Nullable BakedModel model,
                                  BlockAndTintGetter level, BlockPos pos, BlockState state,
                                  @Nullable Direction side, RandomSource rand,
                                  ModelData modelData, @Nullable RenderType renderType) {
        if (model == null) return;
        modelData = model.getModelData(level, pos, state, modelData);
        quads.addAll(model.getQuads(state, side, rand, modelData, renderType));
    }

    @NotNull
    public BlockState getFireboxIdle() {
        return fireboxIdle;
    }

    @NotNull
    public BlockState getFireboxActive() {
        return fireboxActive;
    }
}
