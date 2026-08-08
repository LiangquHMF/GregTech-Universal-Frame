package com.liangqu.gtuf.integration.jade;

import com.liangqu.gtuf.integration.jade.provider.GTUFThreadProvider;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * GTUF 的 Jade 集成入口：注册线程仓线程信息 Provider。
 *
 * <p>结构仿照 GTM {@code com.gregtechceu.gtceu.integration.jade.GTJadePlugin}（
 * {@code @WailaPlugin} + {@code register/registerClient}）。Jade 为可选依赖：
 * 未装 Jade 时本类不会被扫描加载（Jade API 仅 compileOnly），不影响核心功能。</p>
 */
@WailaPlugin
public class GTUFJadePlugin implements IWailaPlugin {

    private static final GTUFThreadProvider THREAD_PROVIDER = new GTUFThreadProvider();

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(THREAD_PROVIDER, BlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(THREAD_PROVIDER, Block.class);
    }
}
