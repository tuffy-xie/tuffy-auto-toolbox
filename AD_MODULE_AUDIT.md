# 广告模块审查（R36）

| 模块 | 可复用部分 | 本轮处理 |
|---|---|---|
| AutomationEngine | 历史UI文案和挑战识别成果 | 旧runAds使用混合队列、Dates本地日期、旧adDone和宽松回执；不用于每日广告。保留源码，挑战入口暂停 |
| AutoAccessibilityService | 常驻服务、日志、前台运行、停止请求 | 独立广告入口加同步，保留单实例，断开通知，任务结果始终汇总 |
| Ui | click/tap/launch/capture/dump | 新增精确active root的foregroundNodes，广告不回退到后台同包窗口；原详情方法保留 |
| DailyAdTaskController | R33–R35已有控制器与历史记录数据模型 | 整理计数持久化、状态迁移、日期隔离、导航和停止；分离AdInteraction可验证核心 |
| AdFlowController | 未发现独立文件 | 以现有DailyAdTaskController整理，不新建工程 |
| Scheduler/Alarm/Worker/Boot | Android本机闹钟、后台兜底、开机恢复 | JST选时、到时检查、当日派发门闩，连接重试最多3次，自检不消费广告 |
| RunStore | 执行历史、旧配置/挑战队列 | 保留，不把旧adDone当可靠广告启动次数；旧计数不明确则阻止启动 |
| Evidence | 设备文件日志、截图、UI树 | 每次状态和结果保留RUN_ID；测试fixture在文件名与报告单独标识 |
| MainActivity | 现有权限设置、时间、日志、记录 | 每日广告置前，无次数选择器；暂停详情诊断按钮；开始/停止/查看记录及统计 |

真实历史证据：AD1793030469265_1.json记录elapsed=0、returned_too_early，但PNG仍是第三方Ad 1 of 3。说明旧「返回」判定不可靠。另一次AD1793026526368也没有完成或奖励证据。

因此只保留广告入口文案、节点手势、日志、截图和本机调度基础。删除“播放够30秒即完成”、等待50秒即视为关闭可用、盲点右上角和裸数字积分增长等判定。未知SDK出现无可读关闭按钮时，有界等待后UNCONFIRMED停止，不能编造成功。

旧记录的启动次数保留，完成/奖励结论降级需核对；测试数据不能进入正式日计数。受控fixture只证明测试交互，不证明ANA广告SDK或积分到账。

本轮只做入口导航的真实只读核对：ANA「広告視聴」页面显示今日1/25，50pt/回；没有点击观看。uiautomator XML未读出正文，因此后续须以APK自身无障碍前台快照验证，不可把截图或历史50pt文案当作本次积分到账。
