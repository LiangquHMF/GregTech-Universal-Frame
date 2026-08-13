package com.liangqu.gtuf.client;

import com.gregtechceu.gtceu.client.model.pipe.PipeModel;
import com.gregtechceu.gtceu.data.model.builder.PipeModelBuilder;
import com.gregtechceu.gtceu.data.pack.event.RegisterDynamicResourcesEvent;
import com.gregtechceu.gtceu.utils.data.RuntimeBlockstateProvider;

import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.liangqu.gtuf.GTUF_Core;
import com.liangqu.gtuf.common.block.PressurePipeBlock;
import com.liangqu.gtuf.common.data.GTUF_PressureBlocks;
import com.tterrag.registrate.util.entry.BlockEntry;

/**
 * 压力管道客户端模型接入桥 —— 7.5.3 兼容变体（动态资源注册）。
 *
 * <p>
 * 7.5.3 的 GTM 管道模型走运行时动态生成：{@code RegisterDynamicResourcesEvent} 分发时
 * 把压力管道方块模型加入共享静态集合 {@code PipeModel.DYNAMIC_MODELS}，GTM post 阶段
 * 统一 init 并落 JSON。本类抽出 {@link ClientProxy} 中原有的监听逻辑，客户端初始化时经
 * {@code FMLJavaModLoadingContext.get().getModEventBus().addListener(...)} 挂接，构造入口
 * {@link #init()} 与 7.1.4 变体签名一致，屏蔽版本差异。
 * </p>
 */
public final class PressurePipeModels {

    private PressurePipeModels() {}

    /** 7.5.3 形态：把动态模型监听器挂到 mod 事件总线（每次资源重载都会触发）。 */
    public static void init() {
        FMLJavaModLoadingContext.get().getModEventBus()
                .addListener(PressurePipeModels::registerDynamicPipeModels);
    }

    /**
     * 把每个压力管道方块接入 GTM 的动态管道模型体系（register 阶段）。
     * {@code createPipeModel(RuntimeBlockstateProvider.INSTANCE)} 用 GTM 运行时
     * blockstate provider 建模型，{@code dynamicModel()} 将其登记进共享静态集合
     * {@code PipeModel.DYNAMIC_MODELS}；GTM 的 post 阶段统一 initModels + 落 JSON。
     *
     * <p>
     * GTM 的 preRegisterDynamicAssets 每次事件都先 {@code DYNAMIC_MODELS.clear()}，
     * 而 GTM 的 pre/register/post 三个 handler 都在 gtceu 总线上连续触发。GTUF 总线
     * 位于 gtceu 之后时，GTUF 模型加入集合时 GTM post 已跑完；位于 gtceu 之前时又被
     * pre 的清空抹掉——两种顺序下 GTM post 都不会替 GTUF 生成模型。因此本监听器在
     * 登记后【防御性】补一次 {@code initDynamicModels() + run()}：无论 mod 顺序如何，
     * 本 reload 的压力管道模型都会被 init 并落 JSON（与 GTM post 相同的完整序列，
     * 幂等：缓存实例只重建 blockstate，新实例全量生成）。
     */
    private static int pipeModelFireCount = 0;

    private static void registerDynamicPipeModels(RegisterDynamicResourcesEvent event) {
        GTUF_Core.LOGGER.info("GTUF: RegisterDynamicResourcesEvent fire #{} begin", ++pipeModelFireCount);
        GTUF_Core.LOGGER.info("GTUF: DYNAMIC_MODELS size before = {}", PipeModel.DYNAMIC_MODELS.size());
        int tableSize = GTUF_PressureBlocks.PRESSURE_PIPE_BLOCKS.size();
        GTUF_Core.LOGGER.info("GTUF: PRESSURE_PIPE_BLOCKS size = {}", tableSize);
        int added = 0;
        for (BlockEntry<PressurePipeBlock> entry : GTUF_PressureBlocks.PRESSURE_PIPE_BLOCKS.values()) {
            if (entry == null) {
                continue;
            }
            entry.get().createPipeModel(RuntimeBlockstateProvider.INSTANCE).dynamicModel();
            added++;
            GTUF_Core.LOGGER.info("GTUF: added pipe model for {}", entry.getId());
        }
        GTUF_Core.LOGGER.info("GTUF: DYNAMIC_MODELS size after = {}, added = {}",
                PipeModel.DYNAMIC_MODELS.size(), added);
        PipeModel.initDynamicModels();
        RuntimeBlockstateProvider.INSTANCE.run();
        PipeModelBuilder.clearRestrictorModelCache();
        GTUF_Core.LOGGER.info("GTUF: defensive initDynamicModels + run + clearRestrictorModelCache done");
    }
}
