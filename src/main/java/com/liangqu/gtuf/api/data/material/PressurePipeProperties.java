package com.liangqu.gtuf.api.data.material;

import com.gregtechceu.gtceu.api.data.chemical.material.properties.IMaterialProperty;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.MaterialProperties;

/**
 * 压力管道材质属性：该材质能承受的压力量程（kPa）。
 *
 * <p>
 * 挂到材质上后，{@code GTUF_PressureBlocks.generate()} 会为该材质生成压力管道方块
 * （TINY/NORMAL/LARGE/HUGE 四种规格）。管道节点在压力传导时若腔压超出本量程
 * {@code [minPressureKpa, maxPressureKpa]} 会破裂损坏。
 * </p>
 *
 * <p>
 * <b>必须实现 equals/hashCode</b>：{@code PipeNet.serializeAllNodeList} 用
 * {@code Object2IntOpenHashMap<NodeDataType>} 按 data 对象去重序列化节点属性，
 * 两个量程相同的管道节点应视为同一属性。
 * </p>
 */
public class PressurePipeProperties implements IMaterialProperty {

    /** 最大可承受压力（kPa）。 */
    private final double maxPressureKpa;
    /** 最小可承受压力（kPa）。 */
    private final double minPressureKpa;

    public PressurePipeProperties(double maxPressureKpa, double minPressureKpa) {
        this.maxPressureKpa = maxPressureKpa;
        this.minPressureKpa = minPressureKpa;
    }

    public double getMaxPressureKpa() {
        return maxPressureKpa;
    }

    public double getMinPressureKpa() {
        return minPressureKpa;
    }

    @Override
    public void verifyProperty(MaterialProperties properties) {
        // 压力管道不要求材质额外属性（金属/聚合物均可承载）。
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PressurePipeProperties that)) return false;
        return Double.doubleToLongBits(maxPressureKpa) == Double.doubleToLongBits(that.maxPressureKpa) &&
                Double.doubleToLongBits(minPressureKpa) == Double.doubleToLongBits(that.minPressureKpa);
    }

    @Override
    public int hashCode() {
        long maxBits = Double.doubleToLongBits(maxPressureKpa);
        long minBits = Double.doubleToLongBits(minPressureKpa);
        int result = (int) (maxBits ^ (maxBits >>> 32));
        result = 31 * result + (int) (minBits ^ (minBits >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "PressurePipeProperties{max=" + maxPressureKpa + " kPa, min=" + minPressureKpa + " kPa}";
    }
}
