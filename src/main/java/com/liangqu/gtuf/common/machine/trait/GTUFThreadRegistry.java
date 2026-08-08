package com.liangqu.gtuf.common.machine.trait;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.liangqu.gtuf.common.machine.multiblock.part.ThreadHatchPartMachine;

import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 线程仓登记表：记录"装了线程仓的多方块控制器"与其线程仓部件。
 *
 * <p>Mixin 推广方案下，普通 GTM 电力多方块（不实现 {@code IThreadModifierMachine}）
 * 无法通过部件接口查询线程仓，因此由 {@link ThreadHatchPartMachine} 在
 * {@code addedToController} / {@code removedFromController} 时<b>无条件</b>向本表
 * 登记/注销。Mixin 与 {@link GTUFThreadingLogic} 通过控制器反查线程仓，从而获得
 * 当前配置的线程数。</p>
 *
 * <p>仅服务端逻辑访问（结构成型/失效、机器 tick），单线程模型下无需加锁；
 * 用 {@link IdentityHashMap} 避免控制器对象 equals 语义干扰。</p>
 */
public class GTUFThreadRegistry {

    private static final Map<IMultiController, ThreadHatchPartMachine> REGISTRY = new IdentityHashMap<>();

    /** 线程仓接入结构时登记（重复接入覆盖）。 */
    public static void register(IMultiController controller, ThreadHatchPartMachine part) {
        REGISTRY.put(controller, part);
    }

    /** 线程仓移出结构时注销（仅当登记的就是该仓，避免误清其他仓的登记）。 */
    public static void unregister(IMultiController controller, ThreadHatchPartMachine part) {
        if (REGISTRY.get(controller) == part) {
            REGISTRY.remove(controller);
        }
    }

    /** @return 该控制器登记的线程仓，无则 null */
    @Nullable
    public static ThreadHatchPartMachine get(IMultiController controller) {
        return REGISTRY.get(controller);
    }
}
