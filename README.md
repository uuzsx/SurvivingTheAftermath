# 劫后余生 · Surviving the Aftermath

<img src="src/main/resources/surviving_the_aftermath.png" alt="劫后余生 Logo" width="200" />

作者：**sxuuz** · GitHub：[@uuzsx](https://github.com/uuzsx)

This Mod supplements the vanilla raid content and adds related API to expand more adventure content.
Explore ruined structures and cities, face the Nether Raid, and discover powerful enchantments.

劫后余生：扩展原版突袭玩法，加入下界副本、遗迹、城市与附魔。
使用钻石打火石开启 11 波挑战；每波开始时建筑逐渐下界化，胜利后获得奖励并播放音乐。

当前分支 **`main`** 对应 **Minecraft 1.20.1 / Forge 47.2.1 / Java 17**，模组版本为 **0.0.4-rc.12**。
每个版本都在自己的分支根目录独立构建；默认分支 `main` 为 1.20.1。

## 版本与分支

| Minecraft | 加载器 / 验证版本 | Java | 分支 |
| --- | --- | --- | --- |
| 1.20.1 | Forge 47.2.1 | 17 | [main](https://github.com/uuzsx/SurvivingTheAftermath/tree/main) |
| 1.21.1 | NeoForge 21.1.251 | 21 | [1.21.1](https://github.com/uuzsx/SurvivingTheAftermath/tree/1.21.1) |
| 1.21.2 | NeoForge 21.2.1-beta | 21 | [1.21.2](https://github.com/uuzsx/SurvivingTheAftermath/tree/1.21.2) |
| 26.1.1 | NeoForge 26.1.1.15-beta | 25 | [26.1.1](https://github.com/uuzsx/SurvivingTheAftermath/tree/26.1.1) |
| 26.1.2 | NeoForge 26.1.2.109 | 25 | [26.1.2](https://github.com/uuzsx/SurvivingTheAftermath/tree/26.1.2) |
| 26.2 | NeoForge 26.2.0.88 | 25 | [26.2](https://github.com/uuzsx/SurvivingTheAftermath/tree/26.2) |
| 26.3 | NeoForge 26.3.0.16-beta | 25 | [26.3](https://github.com/uuzsx/SurvivingTheAftermath/tree/26.3) |

## 游玩

- 使用 **1 个燧石 + 1 个钻石**无序合成钻石打火石，激活副本；首次与重复挑战都需要它。
- 每波开始时，建筑部分方块变为下界材质，楼梯保留朝向、上下半部、拐角和含水状态。
- 副本怪物不掉落物品和经验；胜利时播放音乐并开始发奖，发奖结束后可再次激活。
- 胜利音乐以建筑中心为声源，在 48 格内随距离衰减。成功重新激活，或最后一名玩家离开范围时停止；回来不会续播。

## 更新记录

rc.12 城市选址避开附近地表结构，包含外围缓坡和 8 格间隔；整地及树叶清理保护已登记的建筑。新选址规则只影响新城市，已损坏房屋不会自动复原。见 [建筑避让与验证](docs/CITY-AVOIDANCE-rc.12.md)。

rc.11 清理城市生成后残留或由相邻区块稍后写入的自然树叶，保留玩家装饰树叶与城外森林。已有城市重新加载也会修复。见 [树叶清理与验证](docs/LEAVES-rc.11.md)。

rc.10 修复表层下面的空心地基，城市改为中位高度落地并增加 24 格外围缓坡；增加城市候选密度及可生成群系，支持森林植被清理。仅影响新生成结构。见 [城市地形与频率验证](docs/TERRAIN-rc.10.md)。

rc.9 修复城市、副本及全部地表结构的悬空与地形掩埋：按占地选址、补齐地基、清理内部空间，并保存跨区块一致的放置规则。仅影响新生成的结构。见 [地形修复与验证](docs/TERRAIN-rc.9.md)。

rc.8 恢复原发布页 Logo 和简介，作者统一为 sxuuz，补全 MIT 许可与新仓库链接，并将七个版本整理成独立分支。保留 rc.7 的全部代码与玩法修复。

- [rc.7 楼梯变形修复](docs/ALL-VERSIONS-rc.7.md)
- [rc.6 猪灵装备、掉落及空间音乐](docs/ALL-VERSIONS-rc.6.md)
- [rc.5 物品、附魔、效果与交易恢复](docs/CONTENT-AUDIT-rc.5.md)
- [稳定性修复历史](STABILITY.md) · [历史验证记录](docs/VALIDATION.md)
- [rc.8 分支与模组信息说明](docs/BRANCHES-rc.8.md) · [本次验证结果](docs/VALIDATION-rc.8.md)

## 构建与验证

安装 Java 17，在当前分支根目录运行：

```powershell
.\gradlew.bat runGameTestServer clientAudioCheck build
```

Linux/macOS 使用 `./gradlew runGameTestServer clientAudioCheck build`。JAR 输出在 `build/libs/`。
GitHub Actions 对每个分支分别执行 GameTest、客户端音频解码检查和构建。

本版本包含 KubeJS 可选联动，可使用 `-PwithKubeJS runGameTestServer` 验证脚本集成。

自动检查不代替图形客户端实玩、真人联机和整合包兼容性验证。各 Minecraft 版本请安装对应 JAR。

## 许可

采用 [MIT License](LICENSE.txt)。发行包包含完整许可及原有版权声明。
