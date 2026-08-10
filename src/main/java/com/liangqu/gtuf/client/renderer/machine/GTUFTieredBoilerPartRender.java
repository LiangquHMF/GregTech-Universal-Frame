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
 * <li><b>其余部件</b>：渲染成 {@link ITieredCasingMachine#getCasingState()} 返回的实际外壳方块
 * （青铜/脱氧钢随外壳等级匹配）；</li>
 * <li><b>控制器自身</b>（{@link #getRenderQuads}）：成型后追加外壳立方体 quads。</li>
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
     * 控制器自身渲染：成型后追加 {@link ITieredCasingMachine#getCasingState()} 返回的外壳
     * 立方体 quads，盖住 base 模型注册时写死的立方体材质（overlay 凸出 0.002 仍保留）。
     * <p>
     * 部件（{@link IMultiPart}）不走此路径——它们的 base 由 {@link #renderPartModel} 替换；
     * 未成型或未实现接口的控制器返回空（显示注册时的 workable base 模型）。
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
        BlockState casing = tiered.getCasingState();
        if (casing == null) return Collections.emptyList();
        BakedModel model = getModel(casing);
        if (model == null) return Collections.emptyList();
        modelData = model.getModelData(level, pos, casing, modelData);
        return model.getQuads(casing, side, rand, modelData, renderType);
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
