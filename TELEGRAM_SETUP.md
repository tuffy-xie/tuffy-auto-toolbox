# Telegram 通知设置

1. 在 Telegram 的 @BotFather 创建或选择自己的机器人，取得 Token。不要把 Token 发到聊天或放进日志。
2. 在手机 ANA 自动助手的「Telegram 结果通知」填写 Bot 用户名（可选）和 Token。先在 Telegram 打开该机器人并发送 /start。
3. 点「连接并识别接收人」。软件核对机器人身份，并将唯一的私聊接收 Chat ID 自动填入。若机器人已有 Webhook 或多个私聊，手动填入你自己的 Chat ID；群和频道也可手填。Bot 名称用于核对身份，不能代替接收 Chat ID。
4. 开启通知开关并点「保存」，然后点「测试推送」。手机显示「测试推送成功」且 Telegram 收到消息后，才算真实投递通过。

每次每日广告或四类任务结束，推送简洁的本次结果、今日计数和停止原因。Boost额度不表示积分到账。推送使用手机网络，独立于 Windows/USB；Token经AndroidKeyStore加密保存，不导出到任务证据。

官方接口：[getMe / sendMessage](https://core.telegram.org/bots/api#sendmessage)、[getUpdates](https://core.telegram.org/bots/api#getupdates)。
