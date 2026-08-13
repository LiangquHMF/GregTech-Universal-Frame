package com.liangqu.gtuf.client;

/**
 * 压力管道客户端模型接入桥 —— 旧模型兼容变体（空实现，7.1.4~7.4.1 共享，
 * 目录 {@code src/main/java-legacy-pipe}）。
 *
 * <p>
 * 7.1.4~7.4.1 的 GTM {@code MaterialPipeBlock} 构造器直接经抽象
 * {@code createPipeModel()} 建模型并交给 {@code PipeBlockRenderer}（IBlockRendererProvider
 * 机制），无需动态资源注册；压力管道模型由方块本身携带，客户端开盒即用。
 * </p>
 *
 * <p>
 * 该方法保持与 7.5.3 变体相同的入口签名 {@code init()}，由
 * {@code com.liangqu.gtuf.client.ClientProxy} 统一调用，屏蔽版本差异。
 * </p>
 */
public final class PressurePipeModels {

    private PressurePipeModels() {}

    /** 7.1.4 形态：模型随方块构造器创建，无额外注册。 */
    public static void init() {}
}
