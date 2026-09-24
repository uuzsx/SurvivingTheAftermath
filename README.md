# Surviving the Aftermath

Mod by Sxuuz and Vemerion!

劫后余生：副本挑战与波次战斗模组。当前维护仓库为 [uuzsx/SurvivingTheAftermath](https://github.com/uuzsx/SurvivingTheAftermath)。

## 版本与源码

每一行都是独立的 Gradle 工程；构建前进入对应目录。

| Minecraft | 加载器 / 验证版本 | Java | 源码目录 | 模组版本 |
| --- | --- | --- | --- | --- |
| 1.20.1 | Forge 47.2.1 | 17 | 仓库根目录 | 0.0.4-rc.5 |
| 1.21.1 | NeoForge 21.1.251 | 21 | [ports/mc-1.21.1](ports/mc-1.21.1) | 0.0.4-rc.5 |
| 1.21.2 | NeoForge 21.2.1-beta | 21 | [ports/mc-1.21.2](ports/mc-1.21.2) | 0.0.4-rc.5 |
| 26.1.1 | NeoForge 26.1.1.15-beta | 25 | [ports/mc-26.1.1](ports/mc-26.1.1) | 0.0.4-rc.5 |
| 26.1.2 | NeoForge 26.1.2.109 | 25 | [ports/mc-26.1.2](ports/mc-26.1.2) | 0.0.4-rc.5 |
| 26.2 | NeoForge 26.2.0.88 | 25 | [ports/mc-26.2](ports/mc-26.2) | 0.0.4-rc.5 |
| 26.3 | NeoForge 26.3.0.16-beta | 25 | [ports/mc-26.3](ports/mc-26.3) | 0.0.4-rc.5 |

## 本次更新

rc.5 恢复历史版本中缺失的 8 个物品、10 种附魔、遗物商人、食物效果与交易，并修复懦弱的翻译和涨价行为。七个目标同步恢复内容，详情与原代码中的未完成设计见 [内容核对清单](docs/CONTENT-AUDIT-rc.5.md)。

26.1.2 同时保留 rc.4 的修复：猪灵持有武器，副本怪物不掉落物品和经验，胜利音乐以建筑中心随距离衰减；重新激活或所有玩家离开播放范围时停歌。详见 [rc.4 修复及验证说明](docs/26.1.2-rc.4.md)。

- 修复实体卡墙生成、战斗保存与恢复、观战玩家恢复、奖励计时、事件取消及装饰木桶战利品等问题。
- 新增钻石打火石，采用作者提供的贴图；配方为 **1 个燧石 + 1 个钻石**，无序合成。首次和重复挑战都使用该道具。
- 每波开始变形；胜利时播放一次音乐并开始发奖；发奖完成关门，立即允许再次激活，音乐自然结束。

完整说明见 [1.20.1 修复记录](STABILITY.md)、[NeoForge 移植说明](ports/mc-1.21.1/PORTING.md) 和 [验证记录](docs/VALIDATION.md)。

## 构建

安装表中对应 Java 版本，在目标工程目录运行：

```powershell
.\gradlew.bat build
```

Linux/macOS 使用 `./gradlew build`。输出位于该工程的 `build/libs/`。GitHub Actions 配置按七个目标分别构建并上传 JAR。

数据生成与服务端回归测试的运行方式见各版本说明。六个 NeoForge JAR 分别适用于各自的 Minecraft 版本；同一个实例只安装对应的一个 JAR。

## KubeJS 与验证范围

KubeJS 为可选依赖。Forge 1.20.1、NeoForge 1.21.1 和 26.1.2 包含并验证了脚本联动；另外四个移植版保留副本及 JSON 数据包功能，暂未提供 KubeJS 联动。

本地构建与服务端 GameTest 已通过，具体数量及条件跳过项见验证记录。候选版尚未完成图形客户端完整游玩、真实多人联机、整合包兼容性及旧 Forge 存档跨版本迁移测试。
