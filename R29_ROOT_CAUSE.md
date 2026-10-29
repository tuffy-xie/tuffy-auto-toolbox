# R29 根因分析

日期：2026-10-26  
失败 RUN_ID：`R1793000564576`  
R29 APK SHA256：`c780e4daa5618e36f5095e351ab6fcb2794d2065e2f099cd831cda30a433203b`

> 标记：**[源码]**、**[日志]**、**[真机]**、**[视频]**、**[历史文档]**、**[推断]**。没有把推断写成已证实事实。

## 1. 失败不是单一故障

**[日志]** R29 的最终结果为：

- WALK-01：`detail_unverified`。
- WALK-02：`card_open_failed`，没有 `open ...` 日志，表示该轮连有效点击都未发生。
- WALK-03/04：`card_open_failed`，有点击日志，但 2400ms 后 `onDetailPage()` 为 false。

因此至少有三类路径：详情已识别但内容校验失败、卡片未定位/未点击、点击后状态判定失败。不能用一个“BottomSheet 独立窗口”概括全部。

## 2. 已证实根因 A：`detailMeters()` 与真实节点结构不兼容

### 旧实现

**[源码]** `AutomationEngine.detailMeters()`：

1. 查找包含 `km` / `m` 的节点。
2. 在**同一个节点文本**中判断「徒歩 / 自転車 / 車」。
3. 再在**同一个节点文本**中调用 `Dist.parse()`。

### 真机结构

**[真机]** `evidence/detail_live_before_fix/ready.xml` 的当前 1km 详情中：

```text
TextView "徒歩"  [112,577][168,617]
TextView "1"     [184,577][200,617]
TextView "km"    [200,577][241,617]
TextView "徒歩で1km移動しよう！" [40,637][680,689]
```

类别、数字和单位是三个节点，没有任何一个节点同时包含「徒歩」和「1km」。`findByText("km")` 找到单位节点后，类别判断会 `continue`，候选集为空，最终返回 null。

**[日志]** 这与 WALK-01 的精确路径一致：

```text
16:45:30 open '徒歩で1km移動しよう！' at (475,1086)
16:45:33 detail unverified -> abort
16:45:34 FAILED detail_unverified
```

**结论：[源码 + 真机 + 日志] WALK-01 是确定性的详情内容解析错误，不是卡片未打开。** 完整标题节点已经同时提供类别和距离，应优先用它建立目标一致性，而不是拼接猜测分裂节点。

## 3. 已证实缺陷 B：全窗口快照与“列表否决”互相冲突

**[源码]** R29 `onDetailPage()` 使用 `ui.snapshot()` 遍历所有窗口，同时包含：

```kotlin
if (labels.contains("参加可能") && labels.contains("スポンサードチャレンジ")) return false
```

**[源码]** `Ui.roots()` 不过滤前台/后台，也不保留窗口 active/focused/layer 信息；`snapshot()` 将所有根拼成同一列表。

**[真机]** 详情 READY 时 ANA 同一场景存在两个 Window：基础 HomeActivity 窗口和带 `DIM_BEHIND` 的弹层窗口。因此全窗口遍历天然可能同时取得“后台列表”和“前台详情”。

当后台列表含有上述两个标签时，即使前台详情标签也存在，列表否决仍先返回 false。**[源码 + 真机] 这是确定存在的跨窗口污染缺陷。**

**[日志]** R29 扫描阶段两次能进入 `discover: stray detail page -> back`，而目标卡点击后又多次判定不在详情，说明识别结果随页面/窗口内容变化，不具备稳定性。

**边界：[推断]** 旧日志没有保存每次否决时的窗口标签，所以不能断言 WALK-03/04 每一次都由这个条件触发；修复时必须保存逐窗口证据。

## 4. 已证实缺陷 C：固定 2400ms 后单点判定混淆 OPENING 与失败

**[源码]** 点击后只执行：

```kotlin
ui.tap(...)
delay(2400)
if (onDetailPage()) return true
// 否则重同步，最终 card_open_failed
```

**[视频]** 原始录像证明打开存在正常过渡：5.3 秒变暗，5.4–5.8 秒弹层移动/空白，5.9 秒淡入，6.0 秒才完整 READY。关闭 × 也早于正文出现。

**[真机]** `uiautomator dump` 会等待 UI idle；本次名为 0.35s/1.0s 的抓取实际都只得到同一 READY 树，不能用它证明应用内节点在每个时刻已经同步。

**结论：[源码 + 视频] 当前算法没有 OPENING 状态，也没有 UNKNOWN/TREE_MISSING 出口；单次文本缺失被错误当成终局失败。** 是否恰好在某次 R29 点击的 2400ms 点仍处于空白，旧日志无法回溯，需要新分阶段日志验证。

## 5. 窗口/Activity/WebView 的真实结论

**[真机]** `dumpsys_activity_ready.txt`：仅一个 `HomeActivity` 处于 resumed；其 `windows=[Window{32bfc39...}, Window{cb29ac4...}]`。

**[真机]** `dumpsys_window_ready.txt`：

- 基础窗口：`ty=BASE_APPLICATION`。
- 弹层窗口：`ty=APPLICATION`，`fl=DIM_BEHIND ...`，与视频的背景变暗一致。
- 当前焦点在弹层窗口。

**[真机]** READY Accessibility 树共 122 个节点；正文是原生 `TextView`，有 2 个 WebView 节点（画面中还存在广告区域）。所以不能笼统声称“整个详情都是 WebView”，也不能说“没有独立窗口”。准确说法是：**同一 Activity 内的独立弹层 Window，正文原生节点与 WebView 内容共存。**

## 6. `currentPackage()` 守卫是风险点，但不是已证实的本轮根因

**[源码]** R29 新增 `if (ui.currentPackage() != pkg) return false`；而 `currentPackage()` 仍读取 `rootInActiveWindow`。项目自己的 `pickRoot()` 注释已经记录该 API 在 ANA 页面会返回不可靠的小窗口。

**[真机]** 本次手动 READY 时活动焦点确实属于 ANA 弹层，因此这一守卫在本次现场可以通过。旧 R29 没有逐项打印它是否否决。

**结论：[源码] 应移除对单一 active-root 包名的硬依赖，改为显式窗口角色判断；但不能把它伪装成已证实的 WALK-03/04 原因。**

## 7. 历史“成功版”对比：R15 不是可复现的可靠实现

**[历史文档]** 找到目标记录：

- 轮次：R15，2026-10-26 15:15。
- RUN_ID：`R1792995304188`。
- APK SHA256：`0141e111f66d69157c1e51fd061831db0fd7d07208d0ace3391b17a4dca155c0`。
- 成功日志：`open '徒歩で2km移動しよう！' at (475,1091)` → `detail verified WALK 2km`。
- 同一轮 T54 另有 3 次卡片打开 FAIL，T55 仅 1 次详情 PASS。

**[历史文档]** R15 使用前台窗口策略；R16 放宽卡片查找并在卡片过低时先上滑；R29 改为全窗口快照。R15 的点击 y=1091 与 R29 WALK-01 的 y=1086 几乎相同，说明 WALK-01 不能用“坐标完全点错”解释。

**[源码/工程检查]** 当前目录不是 Git 仓库，未找到 R15 APK 或对应源码快照；现有 `dist` 只有当前 v9/v10 等少量 APK，无法诚实还原 R15 的精确函数体或重打同一个 APK。

**结论：R15 是一次成功样本，不是“连续可靠的成功版本”。** 在源码/APK 未保留、同轮仍有 3 次失败的情况下，不能优先照搬其旧实现。可复用的是“前台窗口优先”的方向；详情解析必须按当前真机节点证据重做。

## 8. 旧证据缺失与本轮新增验证要求

R29 在 `tap did not open detail` 时没有落盘：逐窗口 active/focused/layer、每窗节点数、目标匹配、视觉状态。因此 WALK-02/03/04 不能再被简单归结为同一种根因。

新诊断必须：

1. 每个 ANA 窗口单独遍历，前台窗口用于判定，后台窗口只用于解释来源。
2. 点击后有限时轮询 LIST → OPENING → READY，不在首次空文本时失败。
3. 用完整详情标题 + 类别 + 距离 + 期限/报酬结构验证目标。
4. 视觉明确有弹层而树长期无正文时输出 `DETAIL_VISIBLE_TREE_MISSING`，不是 `CARD_OPEN_FAILED`。
5. 保存每次检测的时间、前台窗口、节点数、视觉/无障碍状态、目标匹配和最终理由。

只有新诊断日志才能把“未点击成功”“仍在打开”“树缺失”“打开了错误任务”分别闭环。
