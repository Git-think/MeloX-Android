# 播放回退顺序

MeloX 的播放解析按固定三段执行，前一阶段成功就不会进入下一阶段。

## 1. 当前音源

先使用队列中歌曲所属的平台：

- 本地已下载文件优先于网络解析。
- 其他情况调用该平台自己的 Provider（网易云、QQ、酷狗概念版、汽水、酷我、Bilibili 等）。
- 解析结果必须是完整音频（`Playable`）；当前平台的试听片段仍按试听处理。

登录态缺失（`LoginRequired`）不会进入后续阶段，因为这是账号层面的问题，逐曲回退只会清空整个队列。

## 2. 内置跨平台匹配

仅在设置中开启「不可用资源从其他平台获取」时执行，并且要求：

- 远程兼容性配置没有关闭 `cross_provider_fallback`；
- 当前平台明确返回会员、版权、地区或没有可播放音源。

默认顺序：

```
网易云 → QQ音乐 → 酷狗概念版 → 汽水音乐 → 酷我 → Bilibili
```

- 顺序由云控 `crossProviderFallback.order` 控制；旧配置只列出 QQ／酷狗／Bilibili 时无法通过完整校验，会回退到上面的默认顺序。
- `disabledProviders` 可临时停用某个来源。
- 当前刚失败的音源会被跳过，避免重复请求同一平台。
- 各平台按顺序逐个搜索，不使用并行竞速；匹配必须通过标题、歌手与时长校验。
- 只接受完整音频，试听片段不会被当作完整歌曲。

## 3. 第三方音乐源

仅在「第三方音乐源」开关和协议确认都开启时执行：

1. 配置了 CHKSZ API Key 时先尝试 CHKSZ，可解析网易云、QQ 与酷狗。
2. 再尝试已导入的 LX Music 脚本；LX 保留自身按音源、音质和脚本顺序的选择逻辑。

- 未配置 Key 或没有导入脚本时，对应来源直接跳过。
- 开启「遇到会员歌曲时再调用」后，只有会员／版权／地区受限才会进入此阶段；普通的“暂时没有音源”不会请求第三方。
- 第三方返回的链接仍必须是 HTTP(S) 完整音频。

## 调试日志

```bash
adb logcat -s MeloXThirdParty MeloXNeteaseResolver MeloXPlaybackService
```

关键字段：`Cross-provider fallback: search <source>`、`resolved … via <source>`、`Resolved via chksz`、`Resolved via lx:<scriptId>`。
