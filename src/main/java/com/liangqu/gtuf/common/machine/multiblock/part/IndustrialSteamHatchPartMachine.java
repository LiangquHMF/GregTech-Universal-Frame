package com.liangqu.gtuf.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.widget.TankWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.SteamHatchPartMachine;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;

import net.minecraft.world.entity.player.Player;

/**
 * 工业级蒸汽输入仓（增强蒸汽仓）：仿原生 {@link SteamHatchPartMachine}，仅接受蒸汽。
 *
 * <p>
 * <b>容量可配置</b>：注册机器时可通过带容量参数的构造指定最大容量（mB），
 * 例如 {@code holder -> new IndustrialSteamHatchPartMachine(holder, 2_048_000)}；
 * 未指定时使用默认容量 {@value #DEFAULT_TANK_CAPACITY} mB（原生蒸汽仓 × 16），
 * 用于支撑高并行 / MV 配方。
 * </p>
 */
public class IndustrialSteamHatchPartMachine extends FluidHatchPartMachine {

    /** 默认容量 = 原生蒸汽仓 × 16 = 1,024,000 mB。 */
    public static final int DEFAULT_TANK_CAPACITY = SteamHatchPartMachine.INITIAL_TANK_CAPACITY * 16;

    /** 实例容量（mB），构造时指定，与 {@link #getTankCapacity()} 一致。 */
    private final int tankCapacity;

    /**
     * 默认容量构造（容量 = {@value #DEFAULT_TANK_CAPACITY} mB）。
     * 注意：GTM 的 machine 工厂是 {@code Function<IMachineBlockEntity, MetaMachine>}，
     * 故自定义容量必须使用 {@link #IndustrialSteamHatchPartMachine(IMachineBlockEntity, int, Object...)}。
     */
    public IndustrialSteamHatchPartMachine(IMachineBlockEntity holder) {
        this(holder, DEFAULT_TANK_CAPACITY);
    }

    /**
     * 指定容量的构造。注册时用 lambda 捕获容量，例如：
     * 
     * <pre>{@code .machine("industrial_steam_input_hatch",
     *          holder -> new IndustrialSteamHatchPartMachine(holder, 2_048_000))}</pre>
     */
    public IndustrialSteamHatchPartMachine(IMachineBlockEntity holder, int capacity, Object... args) {
        super(holder, 0, IO.IN, capacity, 1, args);
        this.tankCapacity = capacity;
    }

    /** 该仓的实例容量（mB）。 */
    public int getTankCapacity() {
        return tankCapacity;
    }

    @Override
    protected NotifiableFluidTank createTank(int initialCapacity, int slots, Object... args) {
        return super.createTank(initialCapacity, slots, args)
                .setFilter(fluidStack -> fluidStack.getFluid().is(GTMaterials.Steam.getFluidTag()));
    }

    // 仅允许蒸汽输入，不允许切换 IO
    @Override
    public boolean swapIO() {
        return false;
    }

    /** 仿原生 {@link SteamHatchPartMachine#createUI}：蒸汽风格界面 + 流体槽与容量显示。 */
    @Override
    public ModularUI createUI(Player entityPlayer) {
        return new ModularUI(176, 166, this, entityPlayer)
                .background(GuiTextures.BACKGROUND_STEAM.get(SteamHatchPartMachine.IS_STEEL))
                .widget(new ImageWidget(7, 16, 81, 55, GuiTextures.DISPLAY_STEAM.get(SteamHatchPartMachine.IS_STEEL)))
                .widget(new LabelWidget(11, 20, "gtceu.gui.fluid_amount"))
                .widget(new LabelWidget(11, 30, () -> tank.getFluidInTank(0).getAmount() + "").setTextColor(-1)
                        .setDropShadow(true))
                .widget(new LabelWidget(6, 6, getBlockState().getBlock().getDescriptionId()))
                .widget(new TankWidget(tank.getStorages()[0], 90, 35, true, true)
                        .setBackground(GuiTextures.FLUID_SLOT))
                .widget(UITemplate.bindPlayerInventory(entityPlayer.getInventory(),
                        GuiTextures.SLOT_STEAM.get(SteamHatchPartMachine.IS_STEEL), 7, 84, true));
    }

    /**
     * 成型后允许控制器模型把本仓替换为结构外壳材质（来源 GTM 7.3.0
     * {@code com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine} 的同名方法）。
     *
     * <p>
     * GTM 7.3.0 起该方法被 {@code GTMachineModelProperties.IS_FORMED} 渲染属性门控——
     * 仅当部件模型注册了该属性且值为 true 时才替换。GTUF 仓室只注册了 RECIPE_LOGIC_STATUS，
     * 未注册 IS_FORMED → {@code hasProperty(IS_FORMED)} 为 false → 成型后整体跳过替换，
     * 保持注册时的仓室材质。这里改为按 {@link #isFormed()}（控制器位置表非空）判定；
     * 7.1.4 无此门控（接口默认 true），{@code isFormed()} 与之行为等价且更精确。
     * </p>
     */
    @Override
    public boolean replacePartModelWhenFormed() {
        return isFormed();
    }
}
