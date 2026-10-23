package com.betterkill.mixin;

import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 1.20.1 的 ServerCommandSource 没有公开的静默状态读取方法，通过 Accessor 读取私有字段。
 */
@Mixin(ServerCommandSource.class)
public interface ServerCommandSourceAccessor {

    @Accessor("silent")
    boolean betterkill$isSilent();
}
