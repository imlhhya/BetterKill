package com.betterkill;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 待确认的 kill 状态管理。按“玩家 UUID / 控制台”分别存储，互不影响。
 */
public final class KillConfirmation {

    private static final Map<String, List<Entity>> PENDING = new ConcurrentHashMap<>();

    private KillConfirmation() {
    }

    /**
     * 暂存一次待确认的 kill（由 KillCommandMixin 在拦截 /kill 时调用）。
     */
    public static void offer(ServerCommandSource source, Collection<? extends Entity> targets) {
        PENDING.put(key(source), new ArrayList<>(targets));
    }

    /**
     * 处理 /kill yes：有待确认目标则真正执行 kill，否则提示“没有指定杀死对象！”。
     */
    public static int confirm(ServerCommandSource source) {
        if (!BetterKillMod.shouldIntercept(source) || !source.hasPermissionLevel(2)) {
            return 0;
        }
        List<Entity> entities = PENDING.remove(key(source));
        if (entities == null) {
            source.sendFeedback(BetterKillTexts::noTarget, false);
            return 0;
        }
        int killed = 0;
        for (Entity entity : entities) {
            if (!entity.isRemoved()) {
                entity.kill();
                killed++;
            }
        }
        if (killed == 0) {
            source.sendFeedback(BetterKillTexts::none, false);
        } else {
            final int killedCount = killed;
            source.sendFeedback(() -> BetterKillTexts.success(killedCount), false);
        }
        return killed;
    }

    /**
     * 命令执行者发出了其他指令时使待确认状态失效。
     */
    public static void invalidate(ServerCommandSource source) {
        if (!BetterKillMod.shouldIntercept(source)) {
            return;
        }
        PENDING.remove(key(source));
    }

    /**
     * 玩家断开连接时清理。
     */
    public static void invalidate(ServerPlayerEntity player) {
        PENDING.remove("player:" + player.getUuid());
    }

    private static String key(ServerCommandSource source) {
        if (source.isExecutedByPlayer()) {
            return "player:" + source.getPlayer().getUuid();
        }
        return "console";
    }
}
