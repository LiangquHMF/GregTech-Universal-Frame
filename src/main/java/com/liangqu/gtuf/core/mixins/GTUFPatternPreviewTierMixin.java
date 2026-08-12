package com.liangqu.gtuf.core.mixins;

import com.gregtechceu.gtceu.api.gui.widget.PatternPreviewWidget;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;

import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.ItemStackKey;

import com.liangqu.gtuf.api.pattern.GTUFTierPreviewState;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * JEI 多方块预览「整体提升」：给 GTM 的 {@link PatternPreviewWidget} 加一个 T:tier 按钮，
 * 点击后把结构里所有 GTUF 等级谓词（通用外壳/框架/管道/齿轮箱/燃烧室、玻璃、蒸汽外壳/框架）
 * 整体换档，并重烘焙当前页预览。
 *
 * <p>
 * <b>整体提升机制</b>：GTM 预览烘焙（{@code BlockPattern.getPreview}）对每个谓词位置恒取
 * {@code SimplePredicate.candidates.get()[0]}。等级谓词的候选 Supplier 经
 * {@link GTUFTierPreviewState#reorder} 把目标档方块重排到数组首位 → 换档后重烘焙，
 * 所有等级谓词同步升级。纯客户端：不写服务端状态、不持久化、真实成型（走 predicate 测试
 * lambda）不受影响。
 * </p>
 *
 * <p>
 * <b>等级谓词识别</b>：{@link GTUFTierPreviewState#TIER_CANDIDATES} 身份注册表 ——
 * 只有 GTUF 等级谓词构造时把自己的候选 Supplier 登记进去；本类扫描
 * {@link BlockPattern#blockMatches}（经 {@link GTUFBlockPatternAccessor}）的所有谓词，
 * 凭 {@code sp.candidates} 身份命中即为等级谓词。能力位/普通方块谓词永不命中 →
 * 无等级谓词的机器 maxTier==1，不显示按钮。
 * </p>
 *
 * <p>
 * 按钮在构造函数 {@code @Inject("<init>") RETURN} 添加：此时 {@code setPage(0)} 已跑完、
 * {@code patterns} 就绪（纯 JEI 下同样成立，不依赖 EMI/REI 的 {@code updateScreen} 分支）。
 * 点击 → 复刻 GTM repetitionDFS 取当前页 reps → {@code getPreview} 重烘焙 →
 * {@code @Invoker initializePattern} → 替换 {@code patterns[index]} → {@code setPage(index)} 重渲染。
 * </p>
 *
 * <p>
 * 目标 {@code PatternPreviewWidget} 是 GTM mod 类（无 SRG 映射），故所有
 * {@code @Shadow/@Inject/@Invoker} 均 {@code remap = false}。
 * </p>
 */
@Mixin(PatternPreviewWidget.class)
public abstract class GTUFPatternPreviewTierMixin {

    @Shadow(remap = false)
    @Final
    public PatternPreviewWidget.MBPattern[] patterns;

    @Shadow(remap = false)
    private int index;

    @Shadow(remap = false)
    @Final
    public MultiblockMachineDefinition controllerDefinition;

    @Shadow(remap = false)
    public abstract void setPage(int index);

    @Invoker(value = "initializePattern", remap = false)
    public abstract PatternPreviewWidget.MBPattern gtuf$invokeInitializePattern(
                                                                                MultiblockShapeInfo shape,
                                                                                HashSet<ItemStackKey> collected);

    /**
     * 拦截构造函数里的 {@code CACHE.computeIfAbsent(...)}：不把结果写入全局共享 CACHE，
     * 而是直接调用原 mapping function 生成本 widget 独立的 {@code patterns} 数组副本。
     *
     * <p>
     * 原实现 {@code patterns = CACHE.computeIfAbsent(def, lambda)} 使 {@code patterns} 字段
     * 直接引用全局静态缓存数组，档位重烘焙写入 {@code patterns[index]} 会永久污染缓存
     * （下次打开该机器预览时 T1 按钮却显示上次档位）。重定向后每个 widget 持有私有数组，
     * 换档重烘焙只影响本 widget。
     * </p>
     *
     * <p>
     * 返回类型必须与目标 {@code computeIfAbsent} 的描述符一致为 {@code Object}（泛型 V
     * 擦除成 Object，mixin 校验 handler 返回值与目标方法精确匹配）；原字节码在调用后的
     * {@code checkcast [LMBPattern;} 负责收窄回 {@code MBPattern[]} 字段。
     * </p>
     */
    @Redirect(
              method = "<init>",
              remap = false,
              at = @At(
                       value = "INVOKE",
                       target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private Object gtuf$freshPatterns(
                                      Map cache, Object key,
                                      Function<Object, Object> function) {
        // 在烘焙（function.apply 求值等级谓词候选）之前重置整体等级为 1：
        // patterns 在构造早期求值候选，若沿用上次预览遗留的静态 currentTier，
        // 初始预览会烘焙成旧档位而按钮却显示 T1（标签与内容错位）。
        // （mixin 0.8.5 不允许 @Inject @At("HEAD") 构造器，故在 @Redirect 处重置。）
        GTUFTierPreviewState.currentTier = 1;
        return function.apply(key);
    }

    /** 当前按钮档位（1..{@link #gtuf$maxTier} 循环）。 */
    @Unique
    private int gtuf$tier = 1;

    /** 本机可切换的最高档 = 结构里所有等级谓词候选数的最大值；无等级谓词时为 1（不显示按钮）。 */
    @Unique
    private int gtuf$maxTier = 1;

    /** 构造完成（{@code controllerDefinition} 已就绪）：默认档位 1，扫描等级谓词决定是否加按钮。 */
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void gtuf$onInit(CallbackInfo ci) {
        gtuf$tier = 1;
        gtuf$maxTier = gtuf$computeMaxTier();
        if (gtuf$maxTier > 1) {
            gtuf$addTierButton();
        }
    }

    /**
     * 扫描机器的 BlockPattern 全部谓词，返回 GTUF 等级谓词候选数的最大值（= 最高可切档位）。
     * 任何异常都返回 1（不向 JEI 传播，按钮不显示即可）。
     */
    @Unique
    private int gtuf$computeMaxTier() {
        try {
            if (controllerDefinition == null || controllerDefinition.getPatternFactory() == null) {
                return 1;
            }
            BlockPattern pattern = controllerDefinition.getPatternFactory().get();
            if (pattern == null) {
                return 1;
            }
            TraceabilityPredicate[][][] matches = ((GTUFBlockPatternAccessor) (Object) pattern).gtuf$getBlockMatches();
            if (matches == null) {
                return 1;
            }
            int max = 1;
            for (TraceabilityPredicate[][] aisle : matches) {
                if (aisle == null) {
                    continue;
                }
                for (TraceabilityPredicate[] row : aisle) {
                    if (row == null) {
                        continue;
                    }
                    for (TraceabilityPredicate tp : row) {
                        if (tp == null) {
                            continue;
                        }
                        max = Math.max(max, gtuf$maxTierOf(tp.common));
                        max = Math.max(max, gtuf$maxTierOf(tp.limited));
                    }
                }
            }
            return max;
        } catch (Exception e) {
            return 1;
        }
    }

    /** 单个谓词列表里 GTUF 等级谓词候选数的最大值（非等级谓词跳过）。 */
    @Unique
    private int gtuf$maxTierOf(List<SimplePredicate> list) {
        if (list == null || list.isEmpty()) {
            return 1;
        }
        int max = 1;
        for (SimplePredicate sp : list) {
            if (sp == null || sp.candidates == null) {
                continue;
            }
            if (!GTUFTierPreviewState.TIER_CANDIDATES.contains(sp.candidates)) {
                continue;
            }
            BlockInfo[] all = sp.candidates.get();
            if (all != null) {
                max = Math.max(max, all.length);
            }
        }
        return max;
    }

    /** 在层切换/成型切换按钮下方（138,70）添加 T 按钮，文字随档位动态刷新。 */
    @Unique
    private void gtuf$addTierButton() {
        ButtonWidget btn = new ButtonWidget(
                138,
                70,
                18,
                18,
                new GuiTextureGroup(
                        ColorPattern.T_GRAY.rectTexture(),
                        new TextTexture().setSupplier(() -> "T" + gtuf$tier)),
                cd -> gtuf$onTierClick());
        btn.setHoverBorderTexture(1, -1);
        // addWidget 继承自 WidgetGroup，不在目标类声明 → 不能 @Shadow，直接转型调用。
        ((WidgetGroup) (Object) this).addWidget(btn);
    }

    @Unique
    private void gtuf$onTierClick() {
        gtuf$tier = gtuf$tier >= gtuf$maxTier ? 1 : gtuf$tier + 1;
        gtuf$rebakeCurrentPage(gtuf$tier);
    }

    /**
     * 换档 → 复刻 GTM repetitionDFS 取当前页 reps → {@code getPreview} 重烘焙 →
     * {@code initializePattern} 替换 {@code patterns[index]} → {@code setPage} 重渲染。
     * 任何失败保持原预览不传播异常。
     */
    @Unique
    private void gtuf$rebakeCurrentPage(int tier) {
        GTUFTierPreviewState.currentTier = tier;
        try {
            if (patterns == null || index < 0 || index >= patterns.length) {
                return;
            }
            if (controllerDefinition == null || controllerDefinition.getPatternFactory() == null) {
                return;
            }
            BlockPattern pattern = controllerDefinition.getPatternFactory().get();
            if (pattern == null) {
                return;
            }
            int[] reps = gtuf$repsForPage(pattern, index);
            if (reps == null) {
                return;
            }
            BlockInfo[][][] blocks = pattern.getPreview(reps);
            if (blocks == null) {
                return;
            }
            patterns[index] = gtuf$invokeInitializePattern(new MultiblockShapeInfo(blocks), new HashSet<>());
            setPage(index);
        } catch (Exception e) {
            // 重烘焙失败保持原预览
        }
    }

    /** 复刻 GTM {@code repetitionDFS} 的叶子顺序，返回第 {@code page} 个重复组合（与 {@link #index} 页一致）。 */
    @Unique
    private int[] gtuf$repsForPage(BlockPattern pattern, int page) {
        return gtuf$dfs(pattern.aisleRepetitions, page, 0, new IntArrayList(), new int[] { 0 });
    }

    @Unique
    private int[] gtuf$dfs(int[][] limits, int target, int depth, IntArrayList stack, int[] count) {
        if (depth == limits.length) {
            if (count[0] == target) {
                return stack.toIntArray();
            }
            count[0]++;
            return null;
        }
        int[] range = limits[depth];
        if (range == null) {
            return null;
        }
        for (int i = range[0]; i <= range[1]; i++) {
            stack.push(i);
            int[] r = gtuf$dfs(limits, target, depth + 1, stack, count);
            stack.pop();
            if (r != null) {
                return r;
            }
        }
        return null;
    }
}
