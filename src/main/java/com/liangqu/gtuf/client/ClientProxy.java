package com.liangqu.gtuf.client;

import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderManager;

import com.liangqu.gtuf.GTUF_Core;
import com.liangqu.gtuf.client.renderer.machine.GTUFTieredPartRender;
import com.liangqu.gtuf.common.CommonProxy;

public class ClientProxy extends CommonProxy {

    public ClientProxy() {
        super();
        init();
    }

    /**
     * 客户端初始化：注册 GTUF 动态渲染类型。
     * 等级蒸汽机的部件渲染器类型必须在客户端模型加载前注册，
     * 否则 {@code gtuf:tiered_steam_parts} 在机器模型 JSON 中反序列化失败。
     */
    public static void init() {
        DynamicRenderManager.register(GTUF_Core.id("tiered_steam_parts"), GTUFTieredPartRender.TYPE);
    }
}
