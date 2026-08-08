package com.liangqu.gtuf.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.gui.widget.LongInputWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredPartMachine;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.liangqu.gtuf.api.machine.IThreadModifierMachine;
import com.liangqu.gtuf.common.machine.trait.GTUFThreadRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * 线程仓（Thread Hatch）：安装该仓室的多方块可同时处理多类配方。
 *
 * <p>线程数由 GUI 配置（来源 GTOcore {@code com.gtocore.common.machine.multiblock.part.
 * ThreadHatchPartMachine}，其 {@code super(holder, tier, 1, 1L << (tier - LuV))} 设
 * 下限 1、上限 {@code 2^(tier-LuV)}；本类内联 {@link LongInputWidget} 实现等价配置）。
 * 默认 1 线程，GUI 中可调 1 ~ 2^(tier-LuV)：UV=4, UHV=8, UEV=16, UIV=32, UXV=64,
 * OpV=128, MAX=256。</p>
 *
 * <p>接入结构时<b>无条件</b>向 {@link GTUFThreadRegistry} 登记自身（Mixin 推广方案下
 * 任何电力多方块——含 GTM 原生机器——装本仓即进入线程模式，通过注册表反查线程数），
 * 并保留 {@link IThreadModifierMachine#setThreadPartMachine} 直连分支（来源 GTNA
 * {@code com.raishxn.gtna.common.machine.multiblock.part.ThreadPartMachine} 的
 * {@code addedToController} 挂钩）。线程数 = 基准 1 + (当前配置 - 1) = 当前配置。</p>
 */
public class ThreadHatchPartMachine extends TieredPartMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            ThreadHatchPartMachine.class, MultiblockPartMachine.MANAGED_FIELD_HOLDER);
    private static final long MIN_THREADS = 1;

    /** 线程数上限：2^(tier-LuV)，由档位决定，构造后不可变。 */
    private final long maxThreads;

    /** 当前配置的线程数，GUI 可调，持久化。 */
    @Persisted
    private int currentThread = 1;

    public ThreadHatchPartMachine(IMachineBlockEntity holder, int tier, Object... args) {
        super(holder, tier);
        this.maxThreads = 1L << (tier - GTValues.LuV);
    }

    public int getCurrentThread() {
        return currentThread;
    }

    public int getMaxThreads() {
        return (int) maxThreads;
    }

    private void setCurrentThread(long thread) {
        this.currentThread = (int) Math.max(MIN_THREADS, Math.min(thread, maxThreads));
        for (var controller : this.getControllers()) {
            if (controller instanceof IThreadModifierMachine threadMachine) {
                threadMachine.setThreadPartMachine(this);
            }
        }
    }

    @Override
    public void addedToController(IMultiController controller) {
        super.addedToController(controller);
        // 无条件登记：Mixin 推广方案下任何多方块（含 GTM 原生）都可经 GTUFThreadRegistry
        // 反查线程仓进入线程模式，不依赖机器实现 IThreadModifierMachine。
        GTUFThreadRegistry.register(controller, this);
        // 兼容分支：实现 IThreadModifierMachine 的 GTUF 控制器保留部件直连（getAdditionalThread）。
        if (controller instanceof IThreadModifierMachine threadMachine) {
            threadMachine.setThreadPartMachine(this);
        }
    }

    @Override
    public void removedFromController(IMultiController controller) {
        super.removedFromController(controller);
        // 无条件注销：结构失效后 Mixin 判定自动退回原生单槽逻辑。
        GTUFThreadRegistry.unregister(controller, this);
        if (controller instanceof IThreadModifierMachine threadMachine) {
            if (threadMachine.getThreadPartMachine() == this) {
                threadMachine.setThreadPartMachine(null);
            }
        }
    }

    @Override
    public Widget createUIWidget() {
        WidgetGroup group = new WidgetGroup(0, 0, 100, 20);
        group.addWidget(new LongInputWidget(() -> (long) this.currentThread, this::setCurrentThread)
                .setMin(MIN_THREADS)
                .setMax(maxThreads));
        return group;
    }

    @Override
    @NotNull
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public boolean canShared() {
        return false;
    }
}
