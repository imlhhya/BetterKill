# BetterKill

一个 Minecraft **1.20.1** 的 **Fabric** 模组：给 `/kill` 指令加上二次确认，防止管理员误用指令误杀不该杀的实体。

## 功能

- 管理员（玩家聊天、服务器 CLI、RCON）输入 `/kill <目标>`（例如 `/kill @e[type=item]`，也包括不带参数的 `/kill`）时，**不会立即执行**，而是只向指令发出者返回：

  > 本次指令将杀死 [n] 个实体，确认杀死？（输入 /kill yes 确认）

  其中 `[n]` 为将要被杀死的实体数量，并以**黄色字体**高亮显示。

- 此时输入 `/kill yes` 才会真正完成 kill 指令，并返回 `已杀死 [n] 个实体。`
- 在确认之前，只要执行了**任何其他以 `/` 开头的指令**（无论指令是否存在、是否执行成功、是否打错），此次待确认的 kill 即告失效。
- 若没有待确认的 kill，直接输入 `/kill yes`，聊天窗返回：

  > 没有指定杀死对象！

- **所有提示均为服务端生成的中文字面量，客户端无需安装本模组**（纯服务端模组）。
- 不同玩家之间、玩家与控制台之间的确认状态相互独立（以"指令发起者"为单位）；玩家退出服务器时其待确认状态自动清理。
- **命令方块和数据包函数中的 `/kill` 保持原版行为**（不拦截），避免破坏自动化。

## 构建

要求：JDK 17+。

```bash
gradlew build
```

构建产物位于 `build/libs/betterkill-0.0.1.jar`。

## 测试运行

```bash
gradlew build                      # 构建
gradlew runServer                  # 专用服务器开发环境（需要 run/eula.txt: eula=true）
```

自动化功能测试（需要服务器运行且开启 RCON，端口 25575 / 密码见 run/server.properties）：

```powershell
# 在 run/server.properties 中确认: enable-rcon=true, rcon.port=25575, rcon.password=betterkill
Set-ExecutionPolicy -Scope Process Bypass -Force
& .\tools\rcon-test.ps1           # 14 项检查全部 PASS 即通过
```

测试覆盖：无目标 `/kill yes` 提示、`/kill @e[type=item]` 提示数量且不执行、其他指令使确认失效、`/kill yes` 恰好杀死统计数量、零匹配保留原版报错等。

## 安装

将 `betterkill-0.0.1.jar` 放入服务端的 `mods/` 目录（纯服务端模组，客户端无需安装），需要同时安装：
- Fabric Loader（对应 1.20.1）
- Fabric API（任意匹配 1.20.1 的版本）

## 实现原理

- `KillCommandMixin`：Mixin 注入原版 `KillCommand.execute`（`/kill` 字面量与 `targets` 参数两条路径的汇合点），拦截后暂存目标实体并提示数量（中文字面量），取消立即杀死。
- `CommandManagerMixin`：在两条互补的注入点上做失效检查——`executeWithPrefix`（解析前，聊天/控制台/RCON/命令方块所有指令必经，打错的指令也能触发失效）与 `execute(ParseResults, String)`（解析后分派前，覆盖直接调用路径）。凡不是 `kill yes` 的指令一律清除发起者的待确认状态。
- 模组初始化时通过 Fabric 的 `CommandRegistrationCallback` 给原版 `kill` 指令树注册 `yes` 字面量子节点用于确认。
- `BetterKillTexts`：所有消息由服务端直接生成中文字面量（`Text.literal`），与客户端语言文件无关，保证客户端不装模组也能看到正确中文提示。

## 说明

该模组由AI制作，可能会出现预期之外的问题，欢迎提出

## 许可

MIT
