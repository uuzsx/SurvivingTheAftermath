# 候选版验证记录

记录日期：2026-09-24。以下结果来自提交前已完成的本地构建和服务端 GameTest；GitHub Actions 的运行结果需在相应提交的检查页面查看。

| Minecraft | 加载器 | Java | 最终 GameTest 日志结果 | KubeJS |
| --- | --- | --- | --- | --- |
| 1.20.1 | Forge 47.2.1 | 17 | All 25 required tests passed | 2001.6.5-build.26 |
| 1.21.1 | NeoForge 21.1.251 | 21 | All 26 required tests passed | 2101.7.2-build.377 |
| 1.21.2 | NeoForge 21.2.1-beta | 21 | All 26 required tests passed | 未提供联动 |
| 26.1.1 | NeoForge 26.1.1.15-beta | 25 | All 27 required tests passed | 未提供联动 |
| 26.1.2 | NeoForge 26.1.2.109 | 25 | All 27 required tests passed | 26.1.2-8.0.6 |
| 26.2 | NeoForge 26.2.0.88 | 25 | All 27 required tests passed | 未提供联动 |
| 26.3 | NeoForge 26.3.0.16-beta | 25 | All 27 required tests passed | 未提供联动 |

1.20.1 共 25 项模组测试，NeoForge 工程共 26 项模组测试。未安装 KubeJS 时，其中 1 项联动测试的主体按条件跳过。26.x 的日志总数额外包含 1 项 Minecraft 自带测试。支持联动的三个版本还在安装 KubeJS 的环境执行了全部模组测试；基础环境也已验证。

各目标的数据生成和构建均已完成。NeoForge 候选版 JAR 的环境、文件名和 SHA256 见 [构建清单](rc.3-manifest.json)；这些校验值对应此前交付的本地候选版 JAR。

## 检查内容

- 实体完整碰撞体的安全生成、恶魂越界处理、刷怪数量抽样、独立副本追踪。
- 每波变形、胜利音乐与奖励时序、发奖结束关门和立即重新激活。
- 钻石打火石注册与配方、两种门朝向、失败不扣耐久、普通点火限制。
- 战斗 UUID 保存与恢复、旁观者断线和死亡恢复、条件检查、事件取消、模块重载。
- 结构已有战利品保护、装饰木桶处理、进度条网络包往返。
- NeoForge 附件序列化往返；可选 KubeJS 脚本取消事件。
- 交付 JAR 的版本声明、贴图字节、配方、可选插件入口及开发测试资源排除。

## 复现

Forge 1.20.1：在仓库根目录使用 Java 17，按 [STABILITY.md](../STABILITY.md) 运行。

NeoForge：进入 `ports/mc-<版本>`，使用表中 Java 版本，分两次调用：

```powershell
.\gradlew.bat runData
.\gradlew.bat runGameTestServer build
```

数据生成与测试分开执行，确保新生成资源被复制。应确认日志出现 `All ... required tests passed`，不能仅凭 Gradle 返回成功判断。

在 1.21.1 和 26.1.2 工程可额外执行 `gradlew.bat -PwithKubeJS runGameTestServer`；具体依赖见各工程的 PORTING.md。

## 尚未覆盖

图形客户端血条显示与音乐听感、真人多人长时间游玩、多模组整合包、Lootr 的各版本联机实测，以及旧 Forge 世界跨版本迁移中的进行中副本和阶段数据。本地服务端测试不能替代这些验收。
