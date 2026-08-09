package com.liangqu.gtuf.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import com.liangqu.gtuf.api.machine.IThreadModifierMachine;
import com.liangqu.gtuf.api.machine.IThreadingRecipeLogic;
import com.liangqu.gtuf.common.machine.multiblock.part.ThreadHatchPartMachine;
import com.liangqu.gtuf.common.machine.trait.GTUFThreadingLogic;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 多线程电力多方块控制器：配线程仓后同时处理多类配方。
 *
 * <p>
 * 自线程仓推广起不再覆盖 {@code createRecipeLogic}——多槽能力由
 * {@code GTUFRecipeLogicMixin} 注入到 GTM 所有电力多方块共用的 {@code RecipeLogic}。
 * 本控制器仅保留 {@link IThreadModifierMachine} 直连登记（供 {@link #getAdditionalThread}
 * 使用）与线程信息显示。其余（能量仓、配方查找、结构）继承
 * {@link WorkableElectricMultiblockMachine}。
 * </p>
 *
 * <p>
 * 控制器基类参考 GTNA
 * {@code com.raishxn.gtna.common.machine.multiblock.electric.WorkableElectricMultipleRecipesMachine}，
 * 精简掉 GTNA 特有的加速/超频/输出增强仓（GTUF 暂无对应仓室）。线程数由结构中的线程仓
 * GUI 配置决定，见 {@link IThreadModifierMachine} 与 {@link ThreadHatchPartMachine}。
 * </p>
 */
public class MultiThreadElectricMachine extends WorkableElectricMultiblockMachine implements IThreadModifierMachine {

    @Nullable
    private ThreadHatchPartMachine threadModifierPart;

    public MultiThreadElectricMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public @Nullable ThreadHatchPartMachine getThreadPartMachine() {
        return threadModifierPart;
    }

    @Override
    public void setThreadPartMachine(@Nullable ThreadHatchPartMachine threadHatchPart) {
        this.threadModifierPart = threadHatchPart;
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        MultiblockDisplayText.builder(textList, isFormed())
                .setWorkingStatus(recipeLogic.isWorkingEnabled(), recipeLogic.isActive())
                .addCustom(text -> {
                    // 线程化核心由 GTUFRecipeLogicMixin 注入到 RecipeLogic；未成型/无线程仓时为 null
                    GTUFThreadingLogic logic = recipeLogic instanceof IThreadingRecipeLogic threading ?
                            threading.getThreadingLogic() : null;
                    if (logic != null) {
                        text.add(Component.translatable("gtuf.multiblock.active_threads",
                                logic.getActiveRecipeCount() + " / " + logic.getMaxThreads())
                                .withStyle(ChatFormatting.AQUA));
                        List<Component> threadInfo = logic.getRecipeDisplayInfo();
                        if (!threadInfo.isEmpty()) {
                            text.addAll(threadInfo);
                        } else {
                            text.add(Component.literal("Idle - Waiting for inputs...")
                                    .withStyle(ChatFormatting.DARK_GRAY));
                        }
                    }
                });
    }
}
