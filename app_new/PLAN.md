# PLAN

> 由 `plan` 工具维护，请勿直接编辑。步骤级进度以 `todo` 工具为准；决策与踩坑写入 DECISIONS.md。

<!-- plan-bridge:state -->
current: M14
auto-breakpoint: false
<!-- /plan-bridge:state -->

## Milestones

- [x] M12 — 终端核心接入（已完成）
- [x] M13 — 标签栏/配置层/命令面板（见 #1-#4）
- [ ] M14 — 会话宿主与终端视图挂载（M12-2…M12-4）
- [x] M13.7 — 技术栈迁移 Kotlin/Compose（见 #7-#8）

## Current: M14 — 会话宿主与终端视图挂载（M12-2…M12-4）

goal: 系统 shell 会话闭环已交付，123 项单测 + 镜像 CI 通过
breakpoint: 等待真机验证会话宿主；M13.5 是发行版/SSH 前置
