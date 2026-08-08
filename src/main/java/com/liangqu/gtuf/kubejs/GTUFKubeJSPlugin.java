package com.liangqu.gtuf.kubejs;

import dev.latvian.mods.kubejs.KubeJSPlugin;

/**
 * GTUF 的 KubeJS 插件入口，通过 {@code kubejs.plugins.txt} 被 KubeJS 自动发现。
 * 重写 {@code registerEvents()} 注册 GTUF 事件组（仿原生
 * {@code com.gregtechceu.gtceu.integration.kjs.GregTechKubeJSPlugin}）。
 */
public class GTUFKubeJSPlugin extends KubeJSPlugin {

    @Override
    public void registerEvents() {
        super.registerEvents();
        GTUFStartupEvents.GROUP.register();
    }
}
