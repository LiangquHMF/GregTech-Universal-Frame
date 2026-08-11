package com.liangqu.gtuf.core.mixins;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.client.model.machine.MachineModel;
import com.gregtechceu.gtceu.client.model.machine.MachineRenderState;
import com.gregtechceu.gtceu.client.util.GTQuadTransformers;
import com.gregtechceu.gtceu.client.util.ModelUtils;
import com.gregtechceu.gtceu.core.IGTBakedQuad;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

import com.liangqu.gtuf.api.machine.ITieredCasingMachine;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * 泛化部件替换：强制 GTUF 等级外壳机（{@link ITieredCasingMachine} 控制器）的部件
 * 在成型后把 base 模型替换为外壳模型。
 *
 * <p>
 * GTM 7.3.0+ 在 {@link MachineModel#renderMachine} 中用
 * {@code IMultiPart.replacePartModelWhenFormed()} 门控部件替换，而该方法被
 * {@code MultiblockPartMachine} 覆写为"IS_FORMED 渲染属性"判定；GTM 蒸汽总线
 * （{@code colorOverlaySteamHullModel} 只注册 IS_PAINTED，不注册 IS_FORMED）等部件
 * 在该门控下返回 false → 成型后仍显示自身材质、不随外壳替换。本 {@link Redirect}
 * 把门控调用重定向：对挂在 GTUF 等级外壳机上的部件直接返回 {@code isFormed()}
 * （绕过 IS_FORMED 渲染属性门控），其余部件走原逻辑，零行为改变。
 * </p>
 *
 * <p>
 * <b>控制器正面 base 材质跟随外壳（task #81）</b>：{@code createTieredMachineModel}
 * 的 base 立方体贴图写死为注册时的 {@code baseCasingTexture}，tier-2+ 结构外壳换成
 * 高等级机壳后，控制器正面（overlay 所在面）仍显示 base 写死材质、与其余面（外观
 * 立方体外扩 0.005 显示外壳材质）不一致。本类 {@link #gtuf$renderBaseModelWithFrontMaterial}
 * 在 {@code renderBaseModel} 返回后、动态渲染器与 covers 加入前，把正面 base 立方体
 * quads 的贴图<b>原地替换</b>为外壳/外观材质（{@link GTQuadTransformers#setSprite}，
 * 深度不变仍为 [0,16]，不引入第三层 → overlay 凸出 ±0.01 保持可见、无 z-fight）；
 * overlay/emissive quads 按 textureKey 的 {@code #overlay} 前缀排除，不受影响。
 * </p>
 */
@Mixin(MachineModel.class)
public abstract class GTUFMachineModelMixin {

    @Redirect(method = "renderMachine",
              remap = false,
              at = @At(value = "INVOKE",
                       target = "Lcom/gregtechceu/gtceu/api/machine/feature/multiblock/IMultiPart;replacePartModelWhenFormed()Z"))
    private boolean gtuf$forceReplacePartModelWhenFormed(IMultiPart part) {
        for (IMultiController controller : part.getControllers()) {
            if (controller.self() instanceof ITieredCasingMachine) {
                // 挂在 GTUF 等级外壳机上：直接以"是否成型"为准，绕过 IS_FORMED 渲染属性门控
                return part.isFormed();
            }
        }
        return part.replacePartModelWhenFormed();
    }

    /**
     * 控制器正面 base 材质跟随外壳（task #81）。目标 {@code renderBaseModel} 是实例方法，
     * 处理器必须是实例方法（static 修饰符不匹配会在 apply 阶段崩溃，见 task #68）。
     */
    @Redirect(method = "renderMachine",
              remap = false,
              at = @At(value = "INVOKE",
                       target = "Lcom/gregtechceu/gtceu/client/model/machine/MachineModel;renderBaseModel(Ljava/util/List;Lcom/gregtechceu/gtceu/client/model/machine/MachineRenderState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/util/RandomSource;Lnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V"))
    private void gtuf$renderBaseModelWithFrontMaterial(MachineModel instance, List<BakedQuad> quads,
                                                       MachineRenderState renderState,
                                                       BlockState baseState, Direction side, RandomSource rand,
                                                       ModelData modelData, RenderType renderType,
                                                       MetaMachine machine, BlockAndTintGetter level, BlockPos pos,
                                                       BlockState callerState, Direction callerSide,
                                                       RandomSource callerRand,
                                                       ModelData callerData, RenderType callerType) {
        instance.renderBaseModel(quads, renderState, baseState, side, rand, modelData, renderType);
        // 仅控制器、成型、且当前渲染面是机器朝向面（正面 overlay 所在面）。此时 quads 只有
        // base 立方体 + workable overlay（covers 在 getMachineQuads 里稍后添加，不在此处）。
        if (machine instanceof ITieredCasingMachine tiered && tiered.isFormed() && !(machine instanceof IMultiPart) &&
                side == machine.getFrontFacing()) {
            BlockState casing = tiered.getControllerAppearanceState();
            if (casing == null) casing = tiered.getCasingState();
            TextureAtlasSprite sprite = casing == null ? null : gtuf$getFrontSprite(casing, rand, renderType);
            if (sprite != null) {
                for (int i = 0; i < quads.size(); i++) {
                    BakedQuad q = quads.get(i);
                    if (q.getDirection() != side) continue;
                    String key = ((IGTBakedQuad) q).gtceu$getTextureKey();
                    // 只换 base 立方体面（key 如 #side/#bottom/#top/#all/#front）；overlay/emissive
                    // 以 #overlay 前缀排除，保持 overlay 可见。
                    if (key == null || key.startsWith("#overlay")) continue;
                    quads.set(i, GTQuadTransformers.setSprite(q, sprite));
                }
            }
        }
        // 部件挂在 GTUF 等级外壳机上且成型：清掉 base 立方体 quads（保留 overlay），避免与
        // renderPartModel 追加的外壳 quads 共面 [0,16] z-fight（材质跳变闪烁）。GTM 的 blank
        // 机制依赖部件模型注册的 replaceableTextures——线程仓用 GTM 原生 workableTieredHullModel
        // 未注册（createWorkableTieredHullMachineModel 没有 addReplaceableTextures），blank 表
        // 为空、base 未清空 → 与外壳共面闪烁。这里按 textureKey 直接清，不再依赖该前提。
        if (machine instanceof IMultiPart part && part.isFormed() && gtuf$isOnTieredCasingController(part)) {
            quads.removeIf(q -> {
                String key = ((IGTBakedQuad) q).gtceu$getTextureKey();
                return key != null && !key.startsWith("#overlay");
            });
        }
    }

    @Unique
    private static boolean gtuf$isOnTieredCasingController(IMultiPart part) {
        for (IMultiController controller : part.getControllers()) {
            if (controller.self() instanceof ITieredCasingMachine) return true;
        }
        return false;
    }

    /** 取外壳方块模型正面（NORTH，各向同性）第一个 quads 的贴图。 */
    @Nullable
    @Unique
    private static TextureAtlasSprite gtuf$getFrontSprite(BlockState casing, RandomSource rand, RenderType renderType) {
        BakedModel model = ModelUtils.getModelForState(casing);
        if (model == null) return null;
        List<BakedQuad> quads = model.getQuads(casing, Direction.NORTH, rand, ModelData.EMPTY, renderType);
        for (BakedQuad q : quads) {
            if (q.getSprite() != null) return q.getSprite();
        }
        return null;
    }
}
