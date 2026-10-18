# PLAN

> 由 `plan` 工具维护，请勿直接编辑。步骤级进度以 `todo` 工具为准；决策与踩坑写入 DECISIONS.md。

<!-- plan-bridge:state -->
current: M4
auto-breakpoint: false
<!-- /plan-bridge:state -->

## Milestones

- [x] M1 — 已交付：Gboard CJK/键盘主题化/设置页/i18n/激活高亮
- [x] M2 — 备份恢复：termux-backup/restore 脚本 + 应用入口
- [x] M3 — 设置页补齐开关（详见 #10-#12）
- [ ] M4 — 真斜体字体 + OSC 8 超链接可点
- [ ] M5 — 交互三连：键盘收起收行/拖拽滚轮/焦点上报1004
- [ ] M6 — 多面板 extra keys + 会话手势 + 会话列表信息

## Current: M4 — 真斜体字体 + OSC 8 超链接可点

goal: M4 完成标准：font-italic.ttf 生效且无则回退；OSC 8 链接有下划线且点击打开
breakpoint: M4 断点：设计已定（questionnaire），从真斜体开始实现
