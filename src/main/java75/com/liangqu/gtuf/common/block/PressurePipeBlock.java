package com.liangqu.gtuf.common.block;

import com.gregtechceu.gtceu.api.block.MaterialPipeBlock;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.pipenet.IPipeNode;
import com.gregtechceu.gtceu.api.registry.registrate.provider.GTBlockstateProvider;
import com.gregtechceu.gtceu.client.model.pipe.PipeModel;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import com.liangqu.gtuf.api.data.material.GTUF_MaterialPropertyKeys;
import com.liangqu.gtuf.api.data.material.PressurePipeProperties;
import com.liangqu.gtuf.api.pipelike.pressurepipe.LevelPressurePipeNet;
import com.liangqu.gtuf.api.pipelike.pressurepipe.PressurePipeType;
import com.liangqu.gtuf.api.pressure.GTUF_Pressure;
import com.liangqu.gtuf.common.blockentity.PressurePipeBlockEntity;
import com.liangqu.gtuf.common.data.GTUF_BlockEntities;
import com.liangqu.gtuf.common.machine.multiblock.part.PressureHatchPartMachine;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 压力管道方块（仿 GTM {@code FluidPipeBlock}）—— 7.5.3 兼容变体。
 *
 * <p>
 * 四种规格 {@link PressurePipeType} × 配置材质（bronze/steel/titanium/tungstensteel）注册为
 * {@code gtuf:} 命名空间方块。节点数据 = 材质量程 {@link PressurePipeProperties}，由材质
 * {@code PRESSURE_PIPE} 属性提供；量程超出时压力机破裂判定会破坏本节点方块。
 * </p>
 *
 * <p>
 * 连接判定：管道↔管道仅限同类（{@link #canPipesConnect}）；管道↔方块仅限压力仓终端
 * （{@link #canPipeConnectToBlock}）。网络维护走继承路径——放置 {@code onPlace} 计划 tick
 * 后 {@code tick()} 调 {@code getWorldPipeNet(level).addNode(...)}，移除 {@code onRemove} 调
 * {@code removeNode}，网络自动合并不需 GTUF 干预。
 * </p>
 *
 * <p>
 * <b>版本差异（7.5.3 形态）：</b>{@code createPipeModel(GTBlockstateProvider)} 由
 * {@link PressurePipeModels} 在动态资源事件中经 {@code RuntimeBlockstateProvider} 调用。
 * 7.1.4~7.4.1 为无参 {@code createPipeModel()}，见 {@code src/main/java714}。
 * </p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class PressurePipeBlock extends
                               MaterialPipeBlock<PressurePipeType, PressurePipeProperties, LevelPressurePipeNet> {

    public PressurePipeBlock(Properties properties, PressurePipeType pipeType, Material material) {
        super(properties, pipeType, material);
    }

    @Override
    protected PressurePipeProperties createProperties(PressurePipeType pipeType, Material material) {
        return pipeType.modifyProperties(material.getProperty(GTUF_MaterialPropertyKeys.PRESSURE_PIPE));
    }

    @Override
    protected PressurePipeProperties createMaterialData() {
        return material.getProperty(GTUF_MaterialPropertyKeys.PRESSURE_PIPE);
    }

    @Override
    public LevelPressurePipeNet getWorldPipeNet(ServerLevel level) {
        return LevelPressurePipeNet.getOrCreate(level);
    }

    @Override
    public BlockEntityType<? extends PipeBlockEntity<PressurePipeType, PressurePipeProperties>> getBlockEntityType() {
        return GTUF_BlockEntities.PRESSURE_PIPE.get();
    }

    @Override
    public boolean canPipesConnect(IPipeNode<PressurePipeType, PressurePipeProperties> selfTile, Direction side,
                                   IPipeNode<PressurePipeType, PressurePipeProperties> sideTile) {
        // 压力管道只与同类型管道直连（不混接流体/物品管）。
        return selfTile instanceof PressurePipeBlockEntity && sideTile instanceof PressurePipeBlockEntity;
    }

    @Override
    public boolean canPipeConnectToBlock(IPipeNode<PressurePipeType, PressurePipeProperties> selfTile, Direction side,
                                         @Nullable BlockEntity tile) {
        // 终端只有压力仓：压力机经仓接入网络（7.5.3 机器非 BlockEntity，经 MetaMachineBlockEntity 取元机）。
        return tile instanceof MetaMachineBlockEntity machineBlockEntity &&
                machineBlockEntity.getMetaMachine() instanceof PressureHatchPartMachine;
    }

    @Override
    public PipeModel createPipeModel(GTBlockstateProvider provider) {
        return pipeType.createPipeModel(this, material, provider);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        PressurePipeProperties properties = createProperties(defaultBlockState(), stack);
        tooltip.add(Component.translatable("gtuf.pressure_pipe.tolerance",
                GTUF_Pressure.format(properties.getMinPressureKpa()),
                GTUF_Pressure.format(properties.getMaxPressureKpa())));
    }
}
