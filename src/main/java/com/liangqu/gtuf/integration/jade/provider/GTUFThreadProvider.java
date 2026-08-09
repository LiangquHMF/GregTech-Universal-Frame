package com.liangqu.gtuf.integration.jade.provider;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.liangqu.gtuf.GTUF_Core;
import com.liangqu.gtuf.api.machine.IThreadingRecipeLogic;
import com.liangqu.gtuf.common.machine.trait.GTUFThreadingLogic;
import com.liangqu.gtuf.common.machine.trait.GTUFThreadingLogic.ActiveRecipe;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.List;
import java.util.Locale;

/**
 * 线程仓线程信息 Jade Provider：提示框中显示多线程运行的具体内容（最多 3 条线程）。
 *
 * <p>
 * 模式仿照 GTM {@code com.gregtechceu.gtceu.integration.jade.provider.ParallelProvider}：
 * 服务端 {@link #appendServerData} 把线程信息写入方块 NBT（serverData），客户端
 * {@link #appendTooltip} 读取并渲染，避免客户端直接触碰机器逻辑。线程化核心经
 * {@link IThreadingRecipeLogic}（Mixin 注入到所有 RecipeLogic）获取；无线程仓或
 * 未进入线程模式的机器不写 NBT，Jade 不显示任何线程行。
 * </p>
 */
public class GTUFThreadProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    /** 提示框最多显示的线程条数（用户参考 GTO 行为，至多 3 条）。 */
    private static final int MAX_DISPLAY_THREADS = 3;

    /** 服务端数据键：写入 accessor.serverData，客户端读取。 */
    private static final String DATA_KEY = "gtuf_threads";

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag serverData = accessor.getServerData();
        if (!serverData.contains(DATA_KEY)) return;
        CompoundTag threads = serverData.getCompound(DATA_KEY);
        int maxThreads = threads.getInt("MaxThreads");
        int count = threads.getInt("Count");
        tooltip.add(Component.translatable("gtuf.multiblock.active_threads",
                count + " / " + maxThreads)
                .withStyle(ChatFormatting.AQUA));
        for (int i = 0; i < count; i++) {
            CompoundTag entry = threads.getCompound("Thread" + i);
            int progress = entry.getInt("Progress");
            int maxProgress = entry.getInt("MaxProgress");
            String output = entry.getString("Output");
            int percentage = maxProgress > 0 ? (int) ((progress / (float) maxProgress) * 100) : 0;
            ChatFormatting percentColor = percentage < 33 ? ChatFormatting.RED :
                    (percentage < 66 ? ChatFormatting.YELLOW : ChatFormatting.GREEN);
            tooltip.add(Component.literal("Thread " + (i + 1) + ": ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(String.format(Locale.US, "%.1fs / %.1fs ",
                            progress / 20.0f, maxProgress / 20.0f))
                            .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(String.format("(%d%%)", percentage))
                            .withStyle(percentColor))
                    .append(Component.literal(" -> ")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(output)
                            .withStyle(ChatFormatting.LIGHT_PURPLE)));
        }
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity)) return;
        if (!(blockEntity.getMetaMachine() instanceof IRecipeLogicMachine recipeLogicMachine)) return;
        if (!(recipeLogicMachine.getRecipeLogic() instanceof IThreadingRecipeLogic threading)) return;
        GTUFThreadingLogic logic = threading.getThreadingLogic();
        if (logic == null || logic.getMaxThreads() <= 1) return;

        CompoundTag threads = new CompoundTag();
        threads.putInt("MaxThreads", logic.getMaxThreads());
        List<ActiveRecipe> recipes = logic.getActiveRecipesForDisplay();
        int count = Math.min(recipes.size(), MAX_DISPLAY_THREADS);
        threads.putInt("Count", count);
        for (int i = 0; i < count; i++) {
            ActiveRecipe active = recipes.get(i);
            CompoundTag entry = new CompoundTag();
            entry.putInt("Progress", active.progress);
            entry.putInt("MaxProgress", active.maxProgress);
            entry.putString("Output", GTUFThreadingLogic.outputName(active.recipe));
            threads.put("Thread" + i, entry);
        }
        data.put(DATA_KEY, threads);
    }

    @Override
    public ResourceLocation getUid() {
        return GTUF_Core.id("thread_provider");
    }
}
