# PLAN

> 由 `plan` 工具维护，请勿直接编辑。步骤级进度以 `todo` 工具为准；决策与踩坑写入 DECISIONS.md。

<!-- plan-bridge:state -->
current: M5
auto-breakpoint: false
<!-- /plan-bridge:state -->

## Milestones

- [x] M1 — 已交付：Gboard CJK/键盘主题化/设置页/i18n/激活高亮
- [x] M2 — 备份恢复：termux-backup/restore 脚本 + 应用入口
- [x] M3 — 设置页补齐开关（详见 #10-#12）
- [x] M4 — 真斜体字体 + OSC 8 超链接可点
- [ ] M5 — 交互三连：键盘收起收行/拖拽滚轮/焦点上报1004
- [ ] M6 — 多面板 extra keys + 会话手势 + 会话列表信息

## Current: M5 — 交互三连：键盘收起收行/拖拽滚轮/焦点上报1004

goal: M5 完成标准：IME 隐藏收 extra keys 行、拖拽按鼠标拖拽上报、聚焦发 CSI I/O
breakpoint: M5 断点：三项行为已确认（#140/#3263、#1384、1004），开始实现
