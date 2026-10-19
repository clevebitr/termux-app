# PLAN

> 由 `plan` 工具维护，请勿直接编辑。步骤级进度以 `todo` 工具为准；决策与踩坑写入 DECISIONS.md。

<!-- plan-bridge:state -->
current: M7
auto-breakpoint: false
<!-- /plan-bridge:state -->

## Milestones

- [x] M1 — 已交付：Gboard CJK/键盘主题化/设置页/i18n/激活高亮
- [x] M2 — 备份恢复：termux-backup/restore 脚本 + 应用入口
- [x] M3 — 设置页补齐开关（详见 #10-#12）
- [x] M4 — 真斜体字体 + OSC 8 超链接可点
- [x] M5 — 交互三连：键盘收起收行/拖拽滚轮/焦点上报1004（详见 #14）
- [x] M6 — 多面板 extra keys + 会话手势 + 会话列表信息（详见 #15）
- [ ] M7 — 音量键自定义动作（单击/长按，详见 #17）

## Current: M7 — 音量键自定义动作（单击/长按，详见 #17）

goal: 音量键 4 动作 + 2 序列属性、设置页、恢复默认，默认=上游
breakpoint: 待 CI 编译/lint + 真机验证音量键动作；未提交
