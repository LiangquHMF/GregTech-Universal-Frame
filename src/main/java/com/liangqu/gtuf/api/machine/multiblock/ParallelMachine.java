package com.liangqu.gtuf.api.machine.multiblock;

/**
 * @author EasterFG on 2024/12/5
 */
public interface ParallelMachine {

    int getMaxParallel();

    default boolean needConfirmMEStock() {
        return true;
    }
}
