package com.liangqu.gtuf.common.machine.multiblock.base;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.steam.SteamEnergyRecipeHandler;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import com.liangqu.gtuf.api.machine.ITieredCasingMachine;
import com.liangqu.gtuf.api.machine.feature.IPatternBufferModeHost;
import com.liangqu.gtuf.api.pattern.GTUF_PatternPredicates;

import java.util.List;

import javax.annotation.Nullable;

public class SteamMultiBlockBase extends WorkableMultiblockMachine
                                 implements IDisplayUIMachine, IPatternBufferModeHost, ITieredCasingMachine {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            SteamMultiBlockBase.class, WorkableMultiblockMachine.MANAGED_FIELD_HOLDER);

    private final boolean isSteel;

    @Nullable
    protected SteamEnergyRecipeHandler steamEnergy = null;

    /**
     * 结构实际使用的外壳方块注册名（由 {@link GTUF_PatternPredicates#Casing} 写入
     * MatchContext、成型时读取）。仓室/总线部件按它渲染外壳——结构铺什么方块，
     * 部件就渲染成什么方块；控制器自身显示注册外观（{@code appearanceBlock}）不受影响；
     * 未设置（null）时仓室回退等级映射/注册外观。
     */
    @Persisted
    @DescSynced
    @RequireRerender
    @Nullable
    private String structureCasingId = null;

    @Nullable
    @Override
    public String getStructureCasingId() {
        return structureCasingId;
    }

    @Override
    public void setStructureCasingId(@Nullable String structureCasingId) {
        this.structureCasingId = structureCasingId;
    }

    public SteamMultiBlockBase(IMachineBlockEntity holder, boolean isSteel, Object... args) {
        super(holder, args);
        this.isSteel = isSteel;
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    protected double getConversionRate() {
        return 1.0;
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        // 读取结构实际使用的外壳方块（Casing 谓词写入），供仓室渲染匹配。
        readStructureCasing();
        for (var part : getParts()) {
            if (!PartAbility.STEAM.isApplicable(part.self().getDefinition().getBlock())) continue;
            var handlers = part.getRecipeHandlers();
            for (var hl : handlers) {
                if (!hl.isValid(IO.IN)) continue;
                for (var fluidHandler : hl.getCapability(FluidRecipeCapability.CAP)) {
                    if (!(fluidHandler instanceof NotifiableFluidTank nft)) continue;
                    if (nft.isFluidValid(0, GTMaterials.Steam.getFluid(1))) {
                        steamEnergy = new SteamEnergyRecipeHandler(nft, getConversionRate());
                        addHandlerList(RecipeHandlerList.of(IO.IN, steamEnergy));
                        return;
                    }
                }
            }
        }
        if (steamEnergy == null) {
            onStructureInvalid();
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        this.steamEnergy = null;
        resetStructureCasing();
    }

    //////////////////////////////////////
    // *** 部件外观（成型后匹配外壳） ***//
    //////////////////////////////////////

    /**
     * 基类默认返回 <b>0（未解析）</b>：结构未使用等级外壳系统的机器（例如 KJS 直接以
     * {@code appearanceBlock} 指定机壳材质、结构铺普通机壳方块）应让仓室回退到注册外观
     * （控制器自身即显示注册外观），从而与结构真实使用的机壳一致。子类若由结构等级驱动
     * （如 {@code EnhanceableSteamMachine} 从 MatchContext 读取等级）须覆盖本方法。
     */
    @Override
    public int getCasingTier() {
        return 0;
    }

    /**
     * 外壳等级 → 外壳方块状态：Tier1 蒸汽机械方块（青铜砖），Tier2 及以上脱氧钢机械方块。
     * <p>
     * 实现 {@link ITieredCasingMachine} 接口，供渲染器（控制器自身 + 仓室/总线）
     * 按结构实际使用的外壳匹配材质。
     * </p>
     * <p>
     * 等级 {@code <= 0}（未解析）时回退注册外观方块（{@code appearanceBlock}），
     * 例如 KJS 机器结构铺 {@code bronze_machine_casing} 且外观同为该方块时，
     * 仓室/控制器材质与结构一致。
     * </p>
     */
    @Nullable
    @Override
    public BlockState getCasingState(int tier) {
        if (tier <= 0) {
            // 未解析等级：回退注册外观方块（appearanceBlock）。
            var appearance = getDefinition().getAppearance();
            return appearance == null ? null : appearance.get();
        }
        return tier >= 2 ? GTBlocks.CASING_STEEL_SOLID.get().defaultBlockState() :
                GTBlocks.CASING_BRONZE_BRICKS.get().defaultBlockState();
    }

    /**
     * 成型后控制器的外观 = 注册时指定的外观方块（{@link #getControllerAppearanceState()}），
     * 与仓室跟随的结构外壳（{@link #getCasingState()}）分离。
     *
     * <p>
     * 控制器方块显示注册的 {@code appearanceBlock}，不被结构 Casing 覆盖；
     * 等级外壳机器（{@code getControllerAppearanceState()} 返回 null）回退
     * {@link #getCasingState()}——相邻方块的面剔除按结构外壳材质处理。
     * 未显式设置外观（默认即机器方块自身）时沿用基类默认行为。
     * </p>
     */
    @Override
    public BlockState getBlockAppearance(BlockState state, BlockAndTintGetter level, BlockPos pos, Direction side,
                                         BlockState sourceState, BlockPos sourcePos) {
        if (isFormed()) {
            BlockState appearance = getControllerAppearanceState();
            if (appearance == null) appearance = getCasingState();
            if (appearance != null) return appearance;
        }
        return super.getBlockAppearance(state, level, pos, side, sourceState, sourcePos);
    }

    public IGuiTexture getScreenTexture() {
        return GuiTextures.DISPLAY_STEAM.get(isSteel);
    }

    @Override
    public ModularUI createUI(Player entityPlayer) {
        var screen = new DraggableScrollableWidgetGroup(7, 4, 162, 121)
                .setBackground(getScreenTexture());

        screen.addWidget(new LabelWidget(4, 5, self().getBlockState().getBlock().getDescriptionId()));
        screen.addWidget(new ComponentPanelWidget(4, 17, this::addDisplayText)
                .setMaxWidthLimit(150)
                .clickHandler(this::handleDisplayClick));

        return new ModularUI(176, 216, this, entityPlayer)
                .background(GuiTextures.BACKGROUND_STEAM.get(isSteel))
                .widget(screen)
                .widget(UITemplate.bindPlayerInventory(entityPlayer.getInventory(),
                        GuiTextures.SLOT_STEAM.get(isSteel), 7, 134, true));
    }

    public void handleDisplayClick(String componentData, ClickData clickData) {}

    @Override
    public @Nullable String gtna$resolvePatternBufferMode(com.gregtechceu.gtceu.api.recipe.GTRecipe recipe) {
        if (getRecipeTypes().length <= 1) {
            return null;
        }
        return recipe.getType().registryName.toString();
    }

    @Override
    public boolean gtna$applyPatternBufferMode(String modeId, com.gregtechceu.gtceu.api.recipe.GTRecipe recipe) {
        if (modeId == null || modeId.isBlank()) {
            return false;
        }
        for (int i = 0; i < getRecipeTypes().length; i++) {
            if (gtna$matchesModeId(modeId, getRecipeTypes()[i])) {
                if (getActiveRecipeType() != i) {
                    setActiveRecipeType(i);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        IDisplayUIMachine.super.addDisplayText(textList);
        if (isFormed()) {
            if (steamEnergy != null && steamEnergy.getCapacity() > 0) {
                long steamStored = steamEnergy.getStored();
                textList.add(Component.translatable("gtceu.multiblock.steam.steam_stored", steamStored,
                        steamEnergy.getCapacity()));
            }

            if (!isWorkingEnabled()) {
                textList.add(Component.translatable("gtceu.multiblock.work_paused"));

            } else if (isActive()) {
                textList.add(Component.translatable("gtceu.multiblock.running"));

                int currentProgress = (int) (recipeLogic.getProgressPercent() * 100);
                double maxInSec = (float) recipeLogic.getDuration() / 20.0f;
                double currentInSec = (float) recipeLogic.getProgress() / 20.0f;

                textList.add(Component.translatable("gtceu.multiblock.progress",
                        String.format("%.2f", (float) currentInSec),
                        String.format("%.2f", (float) maxInSec), currentProgress));

            } else {
                textList.add(Component.translatable("gtceu.multiblock.idling"));
            }

            if (recipeLogic.isWaiting()) {
                textList.add(Component.translatable("gtceu.multiblock.steam.low_steam")
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
            }
        }
    }
}
