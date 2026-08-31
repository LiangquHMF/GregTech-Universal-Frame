package com.liangqu.gtuf.core.mixins;

import com.gregtechceu.gtceu.api.gui.widget.PatternPreviewWidget;
import com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoWrapper;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;

import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

/**
 * JEI 多方块信息页悬停越界防护：修复 GTM 7.5.3 的
 * {@code MultiblockInfoCategory$1ProxyRecipeWidget.getSlotUnderMouse} 在鼠标悬停时抛
 * {@code IndexOutOfBoundsException} 的 bug（如 {@code Index 15 out of bounds for length 15}）。
 * 移植自 HyperMatrix-Core 的 {@code HMC_MultiblockInfoSlotGuardMixin}。
 *
 * <p>
 * <b>bug 机制</b>：LDLib {@code ModularUIRecipeCategory.setRecipe} 给每个 JEI 配方槽命名
 * {@code "slot_<扁平控件下标>"}，下标取自 {@code modularUI.getFlatWidgetCollection()} 构建页面那一刻。
 * GTM 的 ProxyRecipeWidget 悬停时<b>重新展开</b>扁平控件列表，再用槽名里的旧下标直接
 * {@code list.get(index)}。一旦预览控件集在两次之间发生变化（如 {@link GTUFPatternPreviewTierMixin}
 * 的 T:tier 换档重烘焙 {@code setPage} 重建 slotWidgets 数组），旧下标即越界。本 bug 由
 * 等级谓词机器的结构预览触发。
 * </p>
 *
 * <p>
 * <b>修法</b>：在 {@code getSlotUnderMouse} 入口整体拦截并安全重实现（原逻辑 + 越界防护）：
 * 槽名下标越界或解析失败时视为该槽不在鼠标下（跳过），正常槽照常高亮。对任意机器生效，
 * 不改变正常悬停行为。任何意外异常都按"不在鼠标下"降级，绝不让 JEI 页崩溃。
 * </p>
 *
 * <p>
 * 目标是 GTM mod 类的匿名内部类（Java 无法直接引用），用 {@code @Mixin(targets=...)} 字符串
 * 定位；GTM/LDLib 均非 MC 映射类，注入点 {@code remap = false}。
 * </p>
 *
 * <p>
 * <b>为何不 {@code @Shadow}</b>：mixin 注解处理器对 {@code @Shadow} 字段校验时会遍历目标类
 * 所有字段并对字段描述符调用 {@code Type.getReturnType}（该函数只接受方法描述符），匿名类
 * 的字段（如 {@code position}）是纯字段描述符 → 处理器自身抛
 * {@code StringIndexOutOfBoundsException} 编译崩溃。故捕获变量 {@code val$recipe}/{@code val$slots}
 * 改在运行时用反射读取（见 {@link #gtuf$captured}）。
 * </p>
 */
@Mixin(targets = "com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoCategory$1ProxyRecipeWidget")
public abstract class GTUF_MultiblockInfoSlotGuardMixin {

    /** 反射缓存：悬停期间 {@code getSlotUnderMouse} 每帧调用，避免每帧 {@code getDeclaredField}。 */
    @Unique
    private static volatile Field gtuf$recipeField;

    /** 反射缓存：同上。 */
    @Unique
    private static volatile Field gtuf$slotsField;

    /**
     * 整体拦截 {@code getSlotUnderMouse}：以安全重实现替换原逻辑，悬停永不越界。
     */
    @Inject(method = "getSlotUnderMouse", at = @At("HEAD"), remap = false, cancellable = true)
    private void gtuf$guardSlotUnderMouse(double mouseX, double mouseY,
                                          CallbackInfoReturnable<Optional<RecipeSlotUnderMouse>> cir) {
        cir.setReturnValue(gtuf$safeGetSlotUnderMouse(mouseX, mouseY));
    }

    /**
     * 复刻原 {@code getSlotUnderMouse} 逻辑，仅在下标解析/越界处加防护；整个流程再包一层
     * try/catch，任何意外（含反射失败）都降级为"不在鼠标下"。
     */
    @Unique
    private Optional<RecipeSlotUnderMouse> gtuf$safeGetSlotUnderMouse(double mouseX, double mouseY) {
        try {
            MultiblockInfoWrapper recipe = (MultiblockInfoWrapper) gtuf$captured("val$recipe", gtuf$recipeField);
            List<?> slots = (List<?>) gtuf$captured("val$slots", gtuf$slotsField);

            Widget previewWidget = recipe.getWidget();
            if (!(previewWidget instanceof PatternPreviewWidget preview)) {
                return Optional.empty();
            }
            Position pos = preview.getSelfPosition();
            Size size = preview.getSize();
            if (!Widget.isMouseOver(pos.x, pos.y, size.width, size.height, mouseX, mouseY)) {
                return Optional.empty();
            }
            List<Widget> flatWidgets = recipe.modularUI.getFlatWidgetCollection();
            for (Object item : slots) {
                if (item instanceof IRecipeSlotDrawable drawable &&
                        gtuf$isSlotUnderMouse(drawable, flatWidgets, mouseX, mouseY)) {
                    return Optional.of(new RecipeSlotUnderMouse(drawable, 0, 0));
                }
            }
        } catch (Exception e) {
            // 悬停只是 tooltip 显示：任何异常都按"不在鼠标下"降级，绝不让 JEI 页崩溃。
        }
        return Optional.empty();
    }

    /**
     * 单个槽是否在鼠标下：解析槽名 {@code "slot_<下标>"}，下标越界或解析失败一律视为不在
     * （跳过该槽）。正常时把槽移到对应控件位置再判悬停，与原逻辑一致。
     */
    @Unique
    private boolean gtuf$isSlotUnderMouse(IRecipeSlotDrawable drawable, List<Widget> flatWidgets,
                                          double mouseX, double mouseY) {
        Optional<String> slotName = drawable.getSlotName();
        if (slotName.isEmpty()) {
            return false;
        }
        int index;
        try {
            index = Integer.parseInt(slotName.get().substring(5));
        } catch (RuntimeException e) {
            return false;
        }
        if (index < 0 || index >= flatWidgets.size()) {
            return false;
        }
        Widget widget = flatWidgets.get(index);
        if (widget == null) {
            return false;
        }
        drawable.setPosition(widget.getPositionX(), widget.getPositionY());
        return drawable.isMouseOver(mouseX, mouseY);
    }

    /**
     * 运行时反射读取匿名类捕获的 {@code val$xxx} 字段。mixin 注入后 {@code this} 即目标匿名类
     * 实例，字段名按 javap 实际声明（{@code val$recipe}/{@code val$slots}）解析。
     * 首次解析后缓存 {@link Field}（悬停每帧调用，避免重复 {@code getDeclaredField}/{@code setAccessible}）。
     */
    @Unique
    private Object gtuf$captured(String name, Field cache) throws Exception {
        Field field = cache;
        if (field == null) {
            field = getClass().getDeclaredField(name);
            field.setAccessible(true);
            if (name.equals("val$recipe")) {
                gtuf$recipeField = field;
            } else if (name.equals("val$slots")) {
                gtuf$slotsField = field;
            }
        }
        return field.get(this);
    }
}
