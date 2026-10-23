package com.betterkill.mixin;

import java.util.Collection;

import com.betterkill.BetterKillMod;
import com.betterkill.BetterKillTexts;
import com.betterkill.KillConfirmation;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.KillCommand;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拦截原版 /kill 的实际执行（字面量“kill”与“targets”参数两条路径都汇合到 execute 方法）。
 * 对管理员来源：取消立即杀死，改为暂存目标并提示实体数量，等待 /kill yes 确认。
 */
@Mixin(KillCommand.class)
public abstract class KillCommandMixin {

    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    private static void betterkill$interceptExecute(ServerCommandSource source,
                                                    Collection<? extends Entity> targets,
                                                    CallbackInfoReturnable<Integer> cir) {
        if (!BetterKillMod.shouldIntercept(source)) {
            return; // 命令方块 / 数据包函数等来源保持原版行为
        }
        int count = targets.size();
        KillConfirmation.offer(source, targets);
        source.sendFeedback(() -> BetterKillTexts.prompt(count), false);
        cir.setReturnValue(count);
    }
}
