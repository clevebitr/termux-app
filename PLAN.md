# PLAN

> 由 `plan` 工具维护，请勿直接编辑。步骤级进度以 `todo` 工具为准；决策与踩坑写入 DECISIONS.md。
> （2026-10-30 手工同步一次：M8.x 已全部交付并通过 CI，见 FORK.md 的「里程碑时间线」。）

<!-- plan-bridge:state -->
current: M9
auto-breakpoint: false
<!-- /plan-bridge:state -->

## Milestones

- [x] M1 — 已交付：Gboard CJK/键盘主题化/设置页/i18n/激活高亮
- [x] M2 — 备份恢复：termux-backup/restore 脚本 + 应用入口
- [x] M3 — 设置页补齐开关（详见 #10-#12）
- [x] M4 — 真斜体字体 + OSC 8 超链接可点
- [x] M5 — 交互三连：键盘收起收行/拖拽滚轮/焦点上报1004（详见 #14）
- [x] M6 — 多面板 extra keys + 会话手势 + 会话列表信息（详见 #15）
- [x] M7 — 音量键自定义动作（单击/长按，详见 #17）；代码已交付，真机未验证
- [x] M8 — 终端内通知体系（详见 #20-#24）：
  - M8.1 渲染层（卡片栈/配色/自动消失）
  - M8.2 失败命令通道 + shell 集成 v2（上报命令行/耗时/序列号）
  - M8.3 长按菜单与浮条可配 + `SEARCH`/`SPLIT_*` 虚拟键 + 设置页 GUI
  - M8.4 OSC 9/99/777 完整协议（分块/按钮/图标/查询/关闭/回报/限流）
  - M8.5 App 事件卡片 + 后台系统通知 + 最近通知列表
- [ ] M9 — 以下三项，按顺序做（上下文有限，一次只做一个）
  1. 分屏状态持久化：Activity 重建（换主题/深色切换/旋转）后保持上下或左右分屏与两个窗格的会话
  2. 搜索增强：大小写敏感、正则开关、最近搜索词历史
  3. 质量收尾：编排层单测（`OscNotificationHandler` 限流/occasion、`TerminalNotificationManager`
     通道判定/历史/后台分支、`AppEventNotifier`）+ 三个 P3 小问题（`getTitle(null)` 的 `%1$d`
     陷阱、只有图标没有标题的 OSC 通知、`notification-osc-icons=false` 时仍解码图标）

## Current: M9 — 未开始

goal: 先做「分屏状态持久化」（最小、独立、易验证），再做「搜索增强」
breakpoint: M8.5 与复核重构已推送并通过 CI（`b85e6cc`），真机验收项见 FORK.md「验证情况」
