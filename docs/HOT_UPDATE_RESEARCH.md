# MeloX Android 热更新研究

调研时间：2026-10。目标：在不重新安装 APK 的前提下，尽可能快、尽可能安全地修正线上问题。

## 1. 结论先行

- 现有能力已经覆盖了两头：**配置热更新**（签名云控）与**整包更新**（应用内检查 + GitHub Releases 下载安装）。
- 缺的是中间层：**文案/资源覆盖**、**受限脚本逻辑**、**代码/DEX 热修复**。
- 建议按「低风险 → 高风险」推进：
  1. 资源/文案覆盖（最低风险，收益直接）。
  2. quickjs 受限脚本（复用现有运行时，做非 UI 的动态行为）。
  3. 关键缺陷的 DEX 补丁通道（可选，仅正式版；用 Tinker）。
- **不建议**引入插件化/动态组件框架（VirtualAPK 已归档，Shadow/RePlugin 复杂度与维护成本高，收益不匹配本项目体量）。

## 2. 现有基础（本仓库）

| 能力 | 位置 | 说明 |
| --- | --- | --- |
| 签名云控 | `core/remoteconfig/` | 远程 JSON，签名校验（`MeloXRemoteConfigVerifier`），能力开关 `disabledCapabilities`、回退顺序 `fallback`、公告；按 `versionCode` 区间生效（`appliesTo`）。已能当 kill switch。 |
| 整包更新 | `core/update/MeloXUpdateClient.kt` | GitHub Releases 检查，`isNewer(versionName)` 语义比较；仅允许 `github.com/lladlam/MeloX-Android/releases/download/` 下的 APK。 |
| 脚本运行时 | `quickjs-android`（`LxUserRuntime.kt`） | 目前只执行 lx-music 用户脚本。已有沙箱执行的基础。 |
| 分发渠道 | GitHub Releases | 自签名自分发，不受 Google Play 的动态代码加载政策约束（但见 §5）。 |

## 3. 热更新的“热度”分级与选型

| 方案 | 可热更内容 | 风险 | 生态现状（2026-10） |
| --- | --- | --- | --- |
| 配置热更新 | 开关、顺序、阈值、公告 | 极低 | 已有 |
| 资源/文案覆盖 | 字符串、图片、颜色 | 低 | 自行实现 |
| JS/解释逻辑 | 非 UI 的业务逻辑、解析器 | 中 | quickjs 已内置 |
| DEX/代码热修复 | 任意 Kotlin/Java 方法 | 高 | Tinker 活跃（2026-09 更新）；Robust 停滞（2022）；Sophix 商业闭源 |
| 插件化/动态组件 | 整个模块 | 很高 | Shadow 活跃；RePlugin 一般；VirtualAPK 已归档 |
| 整包/增量更新 | 全部 | 最低 | 已有 |

### 关键外部资料

- Tinker（`Tencent/tinker`，BSD-3，17.6k star，2026-09 仍在更新）：支持 dex、so、资源热修复；需要基线 APK、`TinkerApplication` 改造与补丁包生成插件。
- Robust（`Meituan-Dianping/Robust`）：方法级热修复，最后更新 2022，基本停更。
- Sophix（阿里）：商业方案，需采购。
- Shadow（`Tencent/Shadow`）：插件框架，活跃但对本项目过重。
- Google Play「Device and Network Abuse」政策：禁止下载并执行动态代码（dex/so），Play 内应用需遵守；MeloX 走 GitHub 自分发，不直接受此约束，但仍是安全与合规的参考底线。

## 4. 推荐落地设计

### 4.1 统一的「热更新清单」

复用云控的签名校验与镜像路由，定义一个带签名的清单（可并入现有 `android-models.json` 同款模式）：

```json
{
  "hotUpdateVersion": 3,
  "minVersionCode": 22,
  "maxVersionCode": 40,
  "resources": [
    { "name": "strings-zh.json", "sha256": "...", "url": "..." }
  ],
  "scripts": [
    { "name": "lx-default.js", "sha256": "...", "url": "..." }
  ],
  "patches": [
    { "abi": "arm64-v8a", "name": "patch-0.6.4-1.dex", "sha256": "...", "url": "..." }
  ]
}
```

- 所有条目强制 SHA-256 校验，且下载源限定在固定 GitHub release 前缀（与 `AutoSyncModelRepository.requireTrusted` 同思路）。
- 清单本身用与云控相同的签名公钥验证，防篡改。
- 失败一律回退到内置默认，绝不因热更新内容崩溃。

### 4.2 资源/文案覆盖（优先）

- UI 层已经大量使用 `stringResource`，但 0.6.3 起部分文案内联。建议先把内联文案收回资源，再在 `stringResource` 外层加一层 `MeloXHotStrings.overlay(key)`，命中下发覆盖则用覆盖值。
- 只允许覆盖「展示文案」，不允许覆盖安全/协议相关字符串。
- 覆盖表按语言分文件，缺失即回落。

### 4.3 quickjs 受限脚本（次优先）

- 复用 `LxUserRuntime` 的 quickjs 环境，新增一个受控入口（白名单 API、执行超时、内存上限、无网络/文件系统权限）。
- 适合：歌词清洗规则、搜索标题归一化、通知格式等纯函数逻辑。
- 不适合：UI 结构、播放器状态机（放进去会变成两套状态源）。

### 4.4 DEX 热修复（可选，谨慎）

只有确认要走这条路才引入 Tinker：

1. 依赖：`com.tencent.tinker:tinker-android-lib` + `tinker-patch-gradle-plugin`。
2. 入口改造：`MeloXApplication` 改为 `TinkerApplication` + `ApplicationLike`（改动大，需回归启动链路）。
3. 补丁生成：CI 用「旧基线 APK + 新代码」产出 patch 包，签名后上传到固定 release。
4. 客户端：启动时拉清单 → 校验 hash/签名 → `TinkerInstaller` 安装 → 下次启动生效；崩溃率异常自动 `Tinker.with(context).cleanPatch()` 回滚。
5. 约束：补丁只对**同 versionCode 的基线**有效；ROM/加固/多进程场景兼容性要真机验证。

> 注意：Tinker 的基线绑定意味着每个正式版发布后都要归档对应基线 APK；本项目已有 `releases/` 目录与 GitHub Release，可作为归档点。

## 5. 风险与合规

- 安全：DEX/JS 下发是任意代码/逻辑执行面；必须签名校验 + 固定下载源 + 失败回退。密钥泄露等同于可被投毒。
- 平台：若未来上架 Google Play，DEX/SO 热更新会被判定违规；资源与 JS（解释执行）的边界也要谨慎。
- 完整性：可结合现有签名证书（CN=MeloX）与 `apksigner` 校验，防止中间人替换补丁。
- 可观测：热更新必须带版本与命中率埋点，且能被云控一键关闭。

## 6. 里程碑建议

| 阶段 | 内容 | 预估 |
| --- | --- | --- |
| M1 | 统一签名热更新清单 + 资源覆盖（文案） | 小 |
| M2 | quickjs 白名单脚本通道（先接 1~2 个纯函数场景） | 中 |
| M3 | 决策是否引入 Tinker；如是，做基线归档 + 补丁 CI + 回滚 | 大 |
| — | 插件化 | 不做 |

## 7. 与 AutoSync 模型下发的复用

模型包（`android-models-v0.1.0-beta.2`）已经是「签名索引 + 固定 release + 逐文件 SHA-256 + 原子安装 + 失败回退」的成熟模式，热更新清单应直接复用同一套下载/校验/镜像代码，避免再造一套下发通道。
