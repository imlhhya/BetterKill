package com.betterkill;

import com.betterkill.mixin.ServerCommandSourceAccessor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.command.ServerCommandSource;

import static net.minecraft.server.command.CommandManager.literal;

public class BetterKillMod implements ModInitializer {

    public static final String MOD_ID = "betterkill";

    @Override
    public void onInitialize() {
        // 注册独立的 /killa 确认指令：不再改动原版 /kill 指令树，
        // 也避免 "yes" 字面量与实体选择器/玩家名产生混淆。
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("killa")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> KillConfirmation.confirm(context.getSource()))
                )
        );

        // 玩家断开连接时清理其待确认状态，避免残留。
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                KillConfirmation.invalidate(handler.getPlayer())
        );
    }

    /**
     * 只有管理员入口（玩家聊天、服务器控制台、RCON）才需要二次确认；
     * 命令方块、数据包函数等自动执行来源保持原版 /kill 行为，避免破坏自动化。
     */
    public static boolean shouldIntercept(ServerCommandSource source) {
        if (((ServerCommandSourceAccessor) source).betterkill$isSilent()) {
            return false; // 数据包函数等静默执行上下文
        }
        if (source.isExecutedByPlayer()) {
            return true; // 玩家聊天 / 客户端命令
        }
        if (source.getEntity() != null) {
            return false; // /execute as ... 等实体来源
        }
        if (!source.hasPermissionLevel(4)) {
            return false; // 命令方块（等级2、名称"@"）、数据包函数（等级2）
        }
        String name = source.getName();
        return "Server".equals(name) || "Rcon".equals(name); // 服务器 CLI / RCON
    }
}
