package com.liangqu.gtuf.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

/**
 * GTUF KubeJS 启动事件组（仿原生 {@code GTCEuStartupEvents}）。
 * 整合包作者在 startup 脚本中调用：
 * <ul>
 *   <li>{@code GTUFStartupEvents.registerHatches(event => { ... })} 注册增强流体仓/并行仓/工业蒸汽仓。</li>
 * </ul>
 * <p>多方块<b>机器</b>不做 GTUF 专用工厂：框架只提供机器类（接口与方法），
 * 结构（pattern）、控制器方向、配方类型、配方调整器、外观一律由作者用
 * <b>原生 GTM KubeJS</b>（{@code GTCEuStartupEvents.registry('gtceu:machine', ...)}）自定义，
 * 通过 {@code .machine(holder => new 类(holder))} 引用 GTUF 机器类。</p>
 */
public interface GTUFStartupEvents {

    EventGroup GROUP = EventGroup.of("GTUFStartupEvents");

    EventHandler REGISTER_HATCHES = GROUP.startup("registerHatches", () -> GTUFHatchesEventJS.class);
}
