package com.betterkill.mixin;

import com.betterkill.KillConfirmation;
import com.mojang.brigadier.ParseResults;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 失效检查双保险（两条互补的注入点，覆盖所有原版指令执行路径）：
 *
 * 1) executeWithPrefix（解析前入口）：聊天、服务器控制台、RCON、命令方块的每一条指令
 *    都会经过这里；在解析之前检查，因此“打错、解析失败”的无效指令同样会使确认失效（符合需求）。
 *
 * 2) execute(ParseResults, String)（解析后、分派前入口）：executeWithPrefix 解析成功后
 *    会调用本方法，任何直接调用本方法的路径也被覆盖。
 *
 * 只要指令（可带或不带前导斜杠）归一化后不是“kill yes”，就清除发起者的待确认 kill。
 */
@Mixin(CommandManager.class)
public abstract class CommandManagerMixin {

    @Inject(method = "executeWithPrefix", at = @At("HEAD"))
    private void betterkill$beforeParse(ServerCommandSource source, String command, CallbackInfoReturnable<Integer> cir) {
        betterkill$invalidateIfNotConfirm(source, command);
    }

    @Inject(method = "execute", at = @At("HEAD"))
    private void betterkill$beforeDispatch(ParseResults<ServerCommandSource> parseResults, String command,
                                           CallbackInfoReturnable<Integer> cir) {
        if (parseResults == null) {
            return;
        }
        betterkill$invalidateIfNotConfirm(parseResults.getContext().getSource(), command);
    }

    private void betterkill$invalidateIfNotConfirm(ServerCommandSource source, String command) {
        if (command == null) {
            return;
        }
        String normalized = command.startsWith("/") ? command.substring(1) : command;
        normalized = normalized.trim().replaceAll("\\s+", " ");
        if (!"kill yes".equals(normalized)) {
            KillConfirmation.invalidate(source);
        }
    }
}
