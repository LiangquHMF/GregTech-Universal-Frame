package com.liangqu.gtuf.api.machine;

import com.liangqu.gtuf.common.machine.multiblock.part.ThreadHatchPartMachine;
import org.jetbrains.annotations.Nullable;

/**
 * 线程修改接口：由"支持线程仓的多方块控制器"实现，向多配方逻辑暴露当前线程数。
 *
 * <p>线程数 = 基准 1 线程 + 额外线程。额外线程来自结构中的线程仓：仓室 GUI 配置的
 * 当前线程数为 {@code current}，则额外线程 = {@code current - 1}，总线程 = {@code current}。
 * 仓室接入结构时通过 {@link ThreadHatchPartMachine#addedToController} 调用
 * {@link #setThreadPartMachine} 登记自身。</p>
 *
 * <p>来源：GTNA {@code com.raishxn.gtna.api.machine.IThreadModifierMachine}，移植时
 * 将固定线程数（GTNA 的 {@code getThreadCount()}）改为 GTOcore 的可调线程数
 * （仓室 GUI 配置的当前值），见 GTOcore {@code ThreadHatchPartMachine#getCurrentThread()}。</p>
 */
public interface IThreadModifierMachine {

    /**
     * @return 额外线程数（基准 1 线程之外的线程数），无线程仓时为 0。
     */
    default int getAdditionalThread() {
        ThreadHatchPartMachine part = getThreadPartMachine();
        return part != null ? part.getCurrentThread() - 1 : 0;
    }

    /** @return 结构中登记的线程仓，无则 null */
    @Nullable
    ThreadHatchPartMachine getThreadPartMachine();

    /** 线程仓接入/移出结构时登记或清除。 */
    void setThreadPartMachine(@Nullable ThreadHatchPartMachine threadHatchPart);
}
