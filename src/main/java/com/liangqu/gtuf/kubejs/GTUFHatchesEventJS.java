package com.liangqu.gtuf.kubejs;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.liangqu.gtuf.common.data.GTUF_Machines;
import dev.latvian.mods.kubejs.event.StartupEventJS;

import java.util.Locale;

/**
 * GTUF 增强仓注册事件（KubeJS startup 阶段）。
 * 方法内部直接转发到 {@link GTUF_Machines} 的公开注册工厂，注册时机即脚本执行阶段，
 * 由 REGISTRATE 收集后在 Forge 原生机器注册事件中落盘。
 *
 * <p>各注册方法均支持指定档位：第三个参数可传电压名数组（大小写不敏感，如
 * {@code ["lv", "ev"]}）或 tier 数字数组（如 {@code [1, 4]}），缺省注册全部档位。</p>
 */
public class GTUFHatchesEventJS extends StartupEventJS {

    /**
     * 注册增强流体输入/输出仓各一套（缺省 ULV~UHV 全部档位，容量 = 8000 * 4^tier 无封顶）。
     * 对同一 name 注册 {@code name+"_input"}（IO.IN）与 {@code name+"_output"}（IO.OUT）两组，
     * 螺丝刀 swapIO 可在其间切换。档位子集注册时仅该子集内可 swapIO。
     *
     * @param name 仓名基础名，如 "enhanced_fluid_hatch"
     */
    public void enhancedFluidHatch(String name) {
        enhancedFluidHatch(name, "Enhanced Fluid Hatch");
    }

    /**
     * 同 {@link #enhancedFluidHatch(String)}，自定义显示名。
     *
     * @param displayName 显示名主体，展示为 "{Tier} {displayName} Input/Output"
     */
    public void enhancedFluidHatch(String name, String displayName) {
        enhancedFluidHatch(name, displayName, new Object[0]);
    }

    /** 同 {@link #enhancedFluidHatch(String)}，只注册指定档位。 */
    public void enhancedFluidHatch(String name, Object[] tiers) {
        enhancedFluidHatch(name, "Enhanced Fluid Hatch", tiers);
    }

    /** 同 {@link #enhancedFluidHatch(String)}，自定义显示名 + 只注册指定档位。 */
    public void enhancedFluidHatch(String name, String displayName, Object[] tiers) {
        int[] parsed = parseTiers(tiers);
        GTUF_Machines.registerEnhancedFluidHatches(name + "_input", displayName + " Input", IO.IN, parsed);
        GTUF_Machines.registerEnhancedFluidHatches(name + "_output", displayName + " Output", IO.OUT, parsed);
    }

    /**
     * 注册增强型并行控制仓（缺省 LV~UV 八级，并行上限 = 2^(tier-1 + max(0, tier-IV))）。
     * 并行仓仅支持 LV~UV（1~8），超出会抛错。
     *
     * @param name 注册名，如 "enhanced_parallel_hatch"
     */
    public void enhancedParallelHatch(String name) {
        enhancedParallelHatch(name, "Enhanced Parallel Hatch");
    }

    /** 同 {@link #enhancedParallelHatch(String)}，自定义显示名。 */
    public void enhancedParallelHatch(String name, String displayName) {
        enhancedParallelHatch(name, displayName, new Object[0]);
    }

    /** 同 {@link #enhancedParallelHatch(String)}，只注册指定档位（限 LV~UV）。 */
    public void enhancedParallelHatch(String name, Object[] tiers) {
        enhancedParallelHatch(name, "Enhanced Parallel Hatch", tiers);
    }

    /** 同 {@link #enhancedParallelHatch(String)}，自定义显示名 + 只注册指定档位。 */
    public void enhancedParallelHatch(String name, String displayName, Object[] tiers) {
        GTUF_Machines.registerEnhancedParallelHatches(name, displayName, parseTiers(tiers));
    }

    /**
     * 注册增强蒸汽输入仓（容量默认 4,096,000 mB = 原生 64,000 × 64）。
     * 与工业蒸汽机（GTUF 框架机器）搭配时触发 MV 配方与高效转换（0.25 mB/EU）。
     *
     * @param name 完整机器注册名，如 "industrial_steam_input_hatch"
     */
    public void industrialSteamHatch(String name) {
        industrialSteamHatch(name, GTUF_Machines.INDUSTRIAL_STEAM_HATCH_CAPACITY);
    }

    /** 同 {@link #industrialSteamHatch(String)}，自定义容量（mB）。 */
    public void industrialSteamHatch(String name, int capacity) {
        GTUF_Machines.registerIndustrialSteamHatch(name, capacity);
    }

    /** 解析脚本传入的档位数组：每个元素为电压名（大小写不敏感）或 tier 数字。 */
    private static int[] parseTiers(Object[] tiers) {
        int[] result = new int[tiers.length];
        for (int i = 0; i < tiers.length; i++) {
            result[i] = parseTier(tiers[i]);
        }
        return result;
    }

    private static int parseTier(Object o) {
        if (o instanceof Number num) {
            int tier = num.intValue();
            if (tier < GTValues.ULV || tier >= GTValues.VN.length) {
                throw new IllegalArgumentException("无效电压等级: " + tier);
            }
            return tier;
        }
        String name = o.toString().trim();
        for (int i = 0; i < GTValues.VN.length; i++) {
            if (GTValues.VN[i].equalsIgnoreCase(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException("未知电压等级: " + name + "（可用: "
                + String.join(", ", GTValues.VN) + "，大小写不敏感）");
    }
}
