package com.liangqu.gtuf.client.renderer.machine;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.client.model.machine.IControllerModelRenderer;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRender;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderType;
import com.gregtechceu.gtceu.client.util.ModelUtils;
import com.liangqu.gtuf.api.machine.ITieredCasingMachine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 可替换外壳多方块机的部件/控制器渲染器：让成型后的<b>控制器与仓室</b>都渲染成
 * {@link ITieredCasingMachine#getCasingState()} 返回的实际外壳方块，材质随结构外壳等级匹配
 * （蒸汽机是青铜/脱氧钢两级，电力机可自行把任意等级映射到对应机壳方块）。
 *
 * <p>GTM 默认的部件外观替换（{@code MachineModel#renderPartOverrides}）把部件 "all" 纹理替换成
 * 控制器模型写死的 {@code baseCasingTexture}，因此外壳换材质时仓室仍显示注册时的纹理；
 * 控制器自身也始终显示该写死纹理。本渲染器仿大锅炉的 {@code BoilerMultiPartRender}：</p>
 * <ul>
 *   <li><b>部件</b>（{@link #renderPartModel}）：直接把部件 quads 替换成当前外壳方块状态；</li>
 *   <li><b>控制器自身</b>（{@link #getRenderQuads}）：成型后在 base 模型之上追加同尺寸
 *       外壳立方体 quads。cutout 渲染用 {@code LEQUAL} 深度测试，共面时后画覆盖，
 *       因此成型后控制器显示外壳材质；base 模型凸出 0.002 的 workable overlay 仍保留。</li>
 * </ul>
 *
 * <p>不限定机器类型：任何实现 {@link ITieredCasingMachine} 的多方块控制器（蒸汽/电力）
 * 都走本渲染器。通过 {@code DynamicRenderManager} 以 {@code gtuf:tiered_steam_parts} 注册类型，
 * 由 {@link com.liangqu.gtuf.common.data.models.GTUFModels#createTieredMachineModel}
 * 附加到控制器模型（{@code addDynamicRenderer}）后生效。</p>
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
     * 控制器自身渲染：成型后追加 {@link ITieredCasingMachine#getCasingState()} 返回的外壳
     * 立方体 quads，盖住 base 模型注册时写死的立方体材质。
     * <p>部件（{@link IMultiPart}）不走此路径——它们的 base 由 {@link #renderPartModel} 替换；
     * 未成型或未实现接口的控制器返回空（显示注册时的 workable base 模型）。</p>
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
