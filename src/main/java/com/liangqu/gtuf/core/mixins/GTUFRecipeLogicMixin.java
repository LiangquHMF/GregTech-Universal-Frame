package com.liangqu.gtuf.core.mixins;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.liangqu.gtuf.api.machine.IThreadingRecipeLogic;
import com.liangqu.gtuf.common.machine.trait.GTUFThreadRegistry;
import com.liangqu.gtuf.common.machine.trait.GTUFThreadingLogic;

import net.minecraft.nbt.CompoundTag;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 线程仓推广核心：把多线程能力注入到 GTM 所有电力多方块共用的 {@link RecipeLogic}。
 *
 * <p>采用<b>组合式</b>（而非 new 子类）：{@code MachineTrait} 构造器会
 * {@code machine.attachTraits(this)}，任何 new 出的 RecipeLogic 子类都会被重新
 * attach 进机器 traits（双重冲突）；本 Mixin 在 RecipeLogic 实例上直接挂
 * {@code @Unique} 字段 {@link GTUFThreadingLogic}，与机器 traits 解耦。</p>
 *
 * <p>无线程仓时<b>零行为改变</b>：所有注入仅多一次
 * {@code instanceof IMultiController + GTUFThreadRegistry 查表}（O(1) HashMap），
 * 未命中即放行原逻辑。线程模式判定基于 {@link GTUFThreadRegistry} 动态登记，
 * 结构失效后登记移除 → 自动退回原生单槽逻辑。</p>
 *
 * <p>状态/进度投影：线程模式下 {@code getProgress/getMaxProgress/getLastRecipe/
 * isActive/getStatus} 均投影到第一条活跃配方（默认投影，不改 GTM GUI 代码）；客户端
 * 方块工作渲染经 {@code status} 字段（@DescSynced）由 {@code RecipeLogic#setStatus}
 * 正常同步，无需额外处理。</p>
 *
 * <p>目标 {@code RecipeLogic} 是 GTM mod 类（非原版、无 SRG 映射），故所有
 * {@code @Inject/@Shadow} 均 {@code remap = false}（dev 与正式包类名一致）。</p>
 */
@Mixin(RecipeLogic.class)
public abstract class GTUFRecipeLogicMixin implements IThreadingRecipeLogic {

    @Shadow(remap = false)
    @Final
    public IRecipeLogicMachine machine;

    /** 线程化多槽核心：进入线程模式后懒创建；结构失效经 resetRecipeLogic 清空置 null。 */
    @Unique
    private GTUFThreadingLogic gtuf$threading;

    /**
     * @return 当前是否处于线程模式（机器是多方块控制器且结构登记了线程仓）
     */
    @Unique
    private boolean gtuf$isThreaded() {
        if (!(machine.self() instanceof IMultiController controller)) return false;
        return GTUFThreadRegistry.get(controller) != null;
    }

    /** 懒创建线程化核心（组合式，不 attach 进机器 traits）。 */
    @Unique
    private void gtuf$ensureThreading() {
        if (gtuf$threading == null) {
            gtuf$threading = new GTUFThreadingLogic(machine, (RecipeLogic) (Object) this);
        }
    }

    @Override
    public GTUFThreadingLogic getThreadingLogic() {
        return gtuf$threading;
    }

    /**
     * 线程模式下拦截原生单槽 serverTick，全部走多槽核心。
     */
    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtuf$serverTick(CallbackInfo ci) {
        if (gtuf$isThreaded()) {
            gtuf$ensureThreading();
            gtuf$threading.serverTick();
            ci.cancel();
        }
    }

    /** 线程模式投影：进度条显示第一条活跃配方进度。 */
    @Inject(method = "getProgress", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtuf$getProgress(CallbackInfoReturnable<Integer> cir) {
        if (gtuf$threading != null) {
            cir.setReturnValue(gtuf$threading.getProgressForDisplay());
        }
    }

    /** 线程模式投影：进度条显示第一条活跃配方最大进度。 */
    @Inject(method = "getMaxProgress", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtuf$getMaxProgress(CallbackInfoReturnable<Integer> cir) {
        if (gtuf$threading != null) {
            cir.setReturnValue(gtuf$threading.getMaxProgressForDisplay());
        }
    }

    /** 线程模式投影：配方预览显示第一条活跃配方。 */
    @Inject(method = "getLastRecipe", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtuf$getLastRecipe(CallbackInfoReturnable<GTRecipe> cir) {
        if (gtuf$threading != null) {
            cir.setReturnValue(gtuf$threading.getLastRecipeForDisplay());
        }
    }

    /** 线程模式投影：有活跃配方即视为工作。 */
    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtuf$isActive(CallbackInfoReturnable<Boolean> cir) {
        if (gtuf$threading != null) {
            cir.setReturnValue(gtuf$threading.isActiveForDisplay());
        }
    }

    /** 线程模式投影：状态 = 有活跃配方则 WORKING，否则 IDLE。 */
    @Inject(method = "getStatus", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtuf$getStatus(CallbackInfoReturnable<RecipeLogic.Status> cir) {
        if (gtuf$threading != null) {
            cir.setReturnValue(gtuf$threading.getStatusForDisplay());
        }
    }

    /**
     * 结构失效（GTM {@code WorkableMultiblockMachine.onStructureInvalid} 会调
     * resetRecipeLogic）时清理线程化核心，自动退回原生单槽逻辑。
     * 不 cancel：原生 reset 同时清空单槽字段。
     */
    @Inject(method = "resetRecipeLogic", at = @At("HEAD"), remap = false)
    private void gtuf$resetRecipeLogic(CallbackInfo ci) {
        if (gtuf$threading != null) {
            gtuf$threading.reset();
            gtuf$threading = null;
        }
    }

    /** 线程模式持久化：保存各线程进度，替代原生单槽进度。 */
    @Inject(method = "saveCustomPersistedData", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtuf$saveCustomPersistedData(CompoundTag tag, boolean forDrop, CallbackInfo ci) {
        if (gtuf$threading != null) {
            gtuf$threading.saveCustomPersistedData(tag, forDrop);
            ci.cancel();
        }
    }

    /** 线程模式加载：多槽数据不还原（未成型时无宿主），清空等待重启。 */
    @Inject(method = "loadCustomPersistedData", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtuf$loadCustomPersistedData(CompoundTag tag, CallbackInfo ci) {
        if (gtuf$threading != null) {
            gtuf$threading.loadCustomPersistedData(tag);
            ci.cancel();
        }
    }
}
