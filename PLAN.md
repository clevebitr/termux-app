# PLAN

> 由 `plan` 工具维护，请勿直接编辑。步骤级进度以 `todo` 工具为准；决策与踩坑写入 DECISIONS.md。

<!-- plan-bridge:state -->
current: M2
auto-breakpoint: false
<!-- /plan-bridge:state -->

## Milestones

- [x] M1 — 已交付：Gboard CJK/键盘主题化/设置页/i18n/激活高亮
- [ ] M2 — 备份恢复：termux-backup/restore 脚本 + 应用入口
- [ ] M3 — 设置页补齐开关（主题/全屏/工具栏/按键行为/鼠标点按唤键盘）
- [ ] M4 — 真斜体字体 + OSC 8 超链接可点
- [ ] M5 — 交互三连：键盘收起收行/拖拽滚轮/焦点上报1004
- [ ] M6 — 多面板 extra keys + 会话手势 + 会话列表信息

## Current: M2 — 备份恢复：termux-backup/restore 脚本 + 应用入口

goal: M2 完成标准：termux-backup/termux-restore 可在真机跑通（备份→校验→恢复演练），设置页有一键备份/恢复入口
breakpoint: M2 起点：需实现 app/src/main/assets 脚本 + 设置页入口 + CI 把脚本放进 Release
