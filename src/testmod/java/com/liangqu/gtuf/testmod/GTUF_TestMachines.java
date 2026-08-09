package com.liangqu.gtuf.testmod;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.liangqu.gtuf.GTUF_Core;
import com.liangqu.gtuf.common.data.GTUF_Machines;

/**
 * testmod 源码集专用注册钩子：仅在 dev/测试运行环境生效，随 testmod 编译进开发运行时，
 * 不打包进公开发布 jar。
 *
 * <p>
 * 通过 {@code @Mod.EventBusSubscriber} 让 Forge 在 mod 加载时自动发现本类并订阅机器注册事件，
 * 在框架注册类 {@code GTUF_Machines} 之后注册测试机器。main 源码集完全不引用本类，
 * 保证公开发布剔除 testmod 后无断链。
 * </p>
 *
 * <p>
 * 同时通过 {@link GTUF_Machines} 的公开工厂注册增强仓（dev-only 验证工厂 API，
 * 让 {@code PARALLEL_HATCH_TEST} / {@code INDUSTRIAL_STEAM_MIXER} 测试机可放入增强仓）。
 * 注意 dev id 前缀 {@code dev_} 与脚本示例 id 不同，避免 dev 环境内冲突；
 * 正式环境这些仓由整合包作者的 KubeJS 脚本注册。
 * </p>
 */
@Mod.EventBusSubscriber(modid = GTUF_Core.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class GTUF_TestMachines {

    @SubscribeEvent
    public static void onRegisterMachines(GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition> event) {
        GTUF_Machine_Test.init();

        // 框架注册工厂的 dev 验证：注册增强流体仓/并行仓/工业蒸汽仓/线程仓（正式发布不含这些预注册）。
        // 档位子集示例：只注册 HV/EV 两级验证 tier 参数（其余测试机需要的档位仍由并行/流体工厂提供）。
        GTUF_Machines.registerEnhancedFluidHatches("dev_enhanced_fluid_input", "Enhanced Input Hatch", IO.IN);
        GTUF_Machines.registerEnhancedFluidHatches("dev_enhanced_fluid_output", "Enhanced Output Hatch", IO.OUT);
        GTUF_Machines.registerEnhancedParallelHatches("dev_enhanced_parallel", "Enhanced Parallel Hatch");
        GTUF_Machines.registerIndustrialSteamHatch("dev_industrial_steam",
                GTUF_Machines.INDUSTRIAL_STEAM_HATCH_CAPACITY);
        GTUF_Machines.registerThreadHatches("dev_thread", "Thread Hatch");
    }
}
