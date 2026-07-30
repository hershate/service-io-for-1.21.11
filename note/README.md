# 构建与维护笔记（service-io-for-1.21.11）

本目录记录把上游 [TheNextLvl-net/service-io](https://github.com/TheNextLvl-net/service-io) 适配到 **Minecraft 1.21.11 / JDK 21** 的维护要点。

## 一句话概览

本项目是上游 ServiceIO（Vault 替代品）的第三方 fork，**目标是仅支持 1.21.11，且全程使用 JDK 21 编译运行**（不使用 Java 25）。当前所有模块均可在 JDK 21 下编译通过。

## 快速编译

```bash
# 前提：本机已安装 JDK 21（如 D:\Java\21）
#       已下载 Gradle 9.7.0-rc-1（见 编译指南.md，国内需用镜像）
JAVA_HOME="/d/Java/21" <gradle>/bin/gradle build -x test --no-daemon --console=plain
```

产物：`plugin/build/libs/service-io-3.0.0-pre11-all.jar`（Java 21 字节码，可直接放进 1.21.11 服务器的 `plugins/`）。

## 文件索引

| 文件 | 内容 |
|---|---|
| [编译指南.md](编译指南.md) | 环境要求、Gradle 获取（国内镜像）、完整编译命令、产物、测试说明 |
| [改动记录.md](改动记录.md) | 为 JDK 21 / 1.21.11 做的全部改动及原因（文件、提交哈希、依赖降级表）；含性能优化小节 |
| [国内网络与镜像.md](国内网络与镜像.md) | Gradle 发行版镜像、各 Maven 仓库可达性、踩过的网络坑 |
| [report/perf/](report/perf/) | 性能优化对比报告（`2026-07-30-perf-optimization.md` + 原始 benchmark 输出） |

## benchmark 模块

根目录 `benchmark/` 是离线性能基准（不依赖真实服务器）：

```bash
JAVA_HOME=/d/Java/21 <gradle>/bin/gradle :benchmark:run --console=plain
```

用 Proxy stub 替代 Mockito、手写计时替代 JMH，"前"实现为现有代码的逐字拷贝，输出 `benchmark/results.txt`。详见 [report/perf/2026-07-30-perf-optimization.md](report/perf/2026-07-30-perf-optimization.md)。

## 相关提交

```
551f5a0 build: compile and run on JDK 21 for MC 1.21.11
7c1cab1 fix: restore Minecraft 1.21.11 compatibility   (paper-api 回退 + world.key 修复)
1085715 docs: add third-party maintenance notice to README
```
