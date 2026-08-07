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
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.liangqu.gtuf.api.machine.feature.IPatternBufferModeHost;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.List;

public class SteamMultiBlockBase extends WorkableMultiblockMachine implements IDisplayUIMachine, IPatternBufferModeHost {
    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            SteamMultiBlockBase.class, WorkableMultiblockMachine.MANAGED_FIELD_HOLDER);

    private final boolean isSteel;

    @Nullable
    protected SteamEnergyRecipeHandler steamEnergy = null;

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
