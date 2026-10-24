package com.betterkill;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 所有提示消息均由服务端直接生成中文字面量文本（Text.literal），
 * 客户端无需安装本模组即可正常显示，不会出现未翻译的原始键。
 */
public final class BetterKillTexts {

    private BetterKillTexts() {
    }

    public static Text prompt(int count) {
        return Text.literal("本次指令将杀死 ")
                .append(Text.literal(String.valueOf(count)).formatted(Formatting.YELLOW))
                .append(" 个实体，确认杀死？（输入 /killa 确认）");
    }

    public static Text noTarget() {
        return Text.literal("没有指定杀死对象！");
    }

    public static Text success(int count) {
        return Text.literal(String.format("已杀死 %d 个实体。", count));
    }

    public static Text none() {
        return Text.literal("目标实体已不存在，未杀死任何实体。");
    }
}
