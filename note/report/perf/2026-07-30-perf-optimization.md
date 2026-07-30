# 性能优化报告 —— 2026-07-30

> 分支：`perf/optimization`　|　运行环境：JDK 21（`/d/Java/21`）|　构建：`gradle build -x test` 通过

## 1. 目标与红线

在**安全性、稳定性、兼容性**（红线，不可破坏）的前提下优化性能，并以 `benchmark/` 模块量化前后差异。

**红线的具体含义**：
- **兼容性**：不改变任何对外 API 签名与可观察行为（Vault / VaultUnlocked / ServiceIO 三套 API 的契约与优先级语义不变）。
- **稳定性**：不引入新的并发缺陷；不得把"偶发正确"改成"偶发错误"。
- **安全性**：不削弱既有防护（NaN/Infinity 拒收、命令权限门禁、占位符 try/catch 兜底等）。

> 因此本次优化是**内部实现重构**，而非 API 重写。经排查，插件运行期主要可控开销集中在**占位符派发**（PlaceholderAPI 高频轮询）与少量**查表**路径；其余（数据库 I/O、Vault 同步 API 在主线程的阻塞）属固有约束，改动即破坏兼容性，未触碰。

## 2. 方法论

`benchmark/` 是一个独立的 Gradle 子模块，**不依赖真实 Minecraft 服务器**：

- **零额外依赖**：不引入 JMH / Mockito（避免国内网络拉取失败）。Bukkit / Vault 接口用 `java.lang.reflect.Proxy` 合成 stub（[util/Stubs.java](../../../benchmark/src/main/java/net/thenextlvl/service/benchmark/util/Stubs.java)、[util/BukkitStubs.java](../../../benchmark/src/main/java/net/thenextlvl/service/benchmark/util/BukkitStubs.java)），从而能实例化并调用真实的插件类。
- **计时框架**：手写 [util/MicroBench.java](../../../benchmark/src/main/java/net/thenextlvl/service/benchmark/util/MicroBench.java)，预热 + 多轮测量，取**中位数** ns/op（对 GC/调度毛刺鲁棒），并把每次操作返回值折进 sink 以防死码消除。
- **"前"实现即现有代码的逐字拷贝**：[legacy/LegacyPlaceholderStore.java](../../../benchmark/src/main/java/net/thenextlvl/service/benchmark/legacy/LegacyPlaceholderStore.java) 是优化前 `PlaceholderStore` 的逐字冻结副本；`EntityType` / 同步桥的"前"算法也在各自 benchmark 内逐字复刻。前后同进程同 JIT 测量，仅相对加速比有意义。

**复现**：
```bash
JAVA_HOME=/d/Java/21 <gradle>/bin/gradle :benchmark:run --console=plain
# 可选参数：warmupIters rounds itersPerRound（默认 200000 15 200000）
```
输出写入 `benchmark/results.txt`。原始输出见同目录 `raw-benchmark-output.txt`。

**诚实的局限**：手写微基准无独立 JVM fork，JIT 状态前后共享（这对相对 A/B 对比反而合适）；绝对数值受机器/调度影响，**请关注加速比而非绝对纳秒数**。

## 3. 优化项

### 3.1 占位符派发（主优化）—— [PlaceholderStore.java](../../../plugin/src/main/java/net/thenextlvl/service/plugin/placeholder/api/PlaceholderStore.java)

原实现：所有 resolver 放进一个 `HashMap<Pattern, Resolver>`，每次 `resolve()` 线性遍历并对**每个** resolver 跑 `matcher.matches()`，命中即返回。问题：
1. 字面量占位符（`balance`、`prefix`、`group`…，占真实流量绝大多数）也要走正则，且 `HashMap` 迭代序不确定。
2. 多段重叠 pattern（如 `balance_%s` 与 `balance_currency_%s` 都能匹配 `balance_currency_USD`）的命中结果**取决于 HashMap 迭代序，是非确定的潜在 bug**。

新实现：
- **字面量 O(1) 查表**：无 `%s` 且无正则元字符的 resolver 进 `Map<String, Entry>`，`resolve()` 一次 hash 命中，完全不触碰正则引擎。
- **按首段前缀分桶**：pattern resolver 按首段字面量（如 `account_`）分桶；`resolve()` 只遍历输入实际匹配的那个桶。桶按前缀长度**降序**扫描（更长前缀 = 更具体，先匹配即返回），桶内按特异性排序。
- **字面锚点预过滤**：桶内每个 pattern 先检查输入是否包含其全部字面锚点（如 `_currency_`、`_world_`），缺任一则跳过正则——避免在长输入（如 UUID）上对不可能命中的长 pattern 做昂贵回溯。
- **确定的特异性排序**：桶内 pattern 按字面字符数（多优先）、再按捕获组数（多优先）排序，重叠 pattern 始终解析为最具体者（`balance_currency_USD` → `balance_currency_%s`），修复了旧实现的非确定性。

> 这同时是**性能优化**与**稳定性修复**（消除 HashMap 序依赖的随机命中）。

### 3.2 EntityType 查表缓存 —— [CharacterController.java](../../../src/main/java/net/thenextlvl/service/character/CharacterController.java)、[EntityHologramLine.java](../../../src/main/java/net/thenextlvl/service/hologram/line/EntityHologramLine.java)

原实现每次调用都 `Arrays.stream(EntityType.values())`，而 `values()` **每次返回新数组**且重复过滤。改为启动期一次性构建静态映射（精确匹配）/ 静态过滤列表（可赋值匹配），查表 O(1) / O(小常数)。属冷路径（角色/实体行创建），但增益巨大。

### 3.3 同步经济桥 isDone 快速路径 —— **经测量放弃**

曾尝试在 `VaultEconomyServiceWrapper.resolve()` 对已完成的 future 走 `future.get()` 跳过 `get(5, SECONDS)` 的超时簿记。**实测在 JDK 21 上两者对已完成 future 都是 ~2-3 ns（`get(5,SU)` 已对已完成 future 短路），多出的 `isDone()` 调用反而略增开销（2→3 ns）**。**测量证明这不是优化，已撤销，未改动生产代码**。这一阴性结果保留在此以说明"先测后改"的必要性。

## 4. 结果（中位数 ns/op，before → after）

| 场景 | before | after | 加速 |
|---|---:|---:|---:|
| 占位符 `balance`（字面量，最常见） | 121 | 20 | **6.0x** |
| 占位符 `accounts_count`（字面量） | 176 | 19 | **9.3x** |
| 占位符 `balance_currency_USD`（重叠 pattern） | 258 | 75 | **3.4x**（且命中正确） |
| 占位符 `account_<uuid>`（单段 pattern） | 1253 | 114 | **11.0x** |
| 占位符 `totally_unknown_*`（未命中） | 377 | 35 | **10.8x** |
| EntityType 精确查找（Player） | 224 | 3 | **74.7x** |
| EntityType 精确查找（Zombie） | 301 | 4 | **75.3x** |
| EntityType 可赋值查找（Player） | 3749 | 2781 | **1.35x** |

要点：
- 占位符派发全面加速 **3-11x**；其中字面量与未命中提升最明显。
- **旧实现延迟跨运行剧烈波动**（如 `account_<uuid>` 观测到 85-1253 ns、`accounts_count` 29-176 ns），根因是 `HashMap` 迭代序决定的"命中前要试多少个正则"不确定；**新实现稳定在 19-114 ns**，消除了尖刺。
- `balance_currency_USD` 旧实现命中结果非确定（`balance_%s` 或 `balance_currency_%s` 随机），新实现确定为更具体的 `balance_currency_%s`（benchmark 内有断言验证）。

## 5. 安全/兼容性核查

- `PlaceholderStore` 的对外方法签名（`resolve`、`isEnabled`、`registerResolver`、事件处理）**完全不变**；子类只需在 `registerResolvers()` 内调用 `registerResolver`，行为一致。
- 字面量/pattern 的分类用严格的"无 `%s` 且无正则元字符"判定；含元字符的 pattern 落入 `fallback` 列表照常正则匹配，不会误分类。
- 锚点预过滤是**可靠（sound）**的：缺任一必须字面锚点则正则必不命中，仅作跳过，不改变任何命中结果。
- `EntityType` 缓存用 `putIfAbsent`（首现优先）保持与原 `findAny`（顺序流即首现）等价。
- 全量 `gradle build -x test` 通过；产物字节码仍为 Java 21。

## 6. 不在本次范围（属固有约束，改即破坏红线）

- 后端 I/O（providers 的数据库读写）。
- Vault 同步 API 在主线程的 `.join()` / 5s `tryResolve` 阻塞——这是 Vault 同步契约的固有约束。
- `getOfflinePlayers()` 的 O(n) 遍历（converter / 银行所有者解析）——Vault 1.x 银行 API 的固有限制。

## 7. 文件清单

新增/修改：
- `benchmark/`（新模块）：`build.gradle.kts`、`BenchmarkRunner`、`util/{Stubs,BukkitStubs,MicroBench,BenchResult}`、`legacy/LegacyPlaceholderStore`、`placeholder/PlaceholderDispatchBenchmark`、`entitytype/EntityTypeBenchmark`
- `plugin/.../placeholder/api/PlaceholderStore.java`（重写）
- `src/.../character/CharacterController.java`、`src/.../hologram/line/EntityHologramLine.java`（EntityType 缓存）
- `settings.gradle.kts`（include benchmark）
