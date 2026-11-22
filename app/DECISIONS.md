# DECISIONS

> 决策与踩坑日志，只追加，不参与日常上下文。可用 grep 检索。条目按文件顺序编号（第一个 `##` 是 #1）。

## 2026-11-22 — 交付基线纠正：目标为 app/，app_new 降级为原型

需求方于 2026-11-23 书面确认：交付目标是真实应用 app/（com.termux），而非新外壳原型 app_new/。前期 27 个提交全部落在 app_new/，属于基线误判。

决定：
1. 授予 app/ 修改豁免：app/ 可直接修改并逐项单独提交；terminal-view/、terminal-emulator/、termux-shared/ 仍只读，如需改动须另行书面豁免。AGENTS.md 第 3 节已同步更新。
2. app_new/ 保留为原型，不再新增功能；docs/UI_REVIEW.md、docs/ARCHITECTURE_REVIEW.md、docs/UPGRADE.md 中与 app_new 相关的内容仅作参考，验收与交付计划以 app/ 为准。
3. 后续改造在 app/ 内进行：命令面板、菜单收敛、鼠标右键转发、虚拟按键入口等，遵循「包装层优先」，不修改只读模块。

基线事实：app/ 为 83 个 Java 文件、18719 行；已有 TerminalMenuDialog 终端上下文菜单、会话抽屉 TermuxSessionsListViewController、extra-keys（termux-shared 的 ExtraKeysView）、TerminalView 由布局 XML 实例化；无命令面板实现。右键当前固定走 TerminalView.onTouchEvent → onShowContextMenu → TermuxActivity.showTerminalMenu()，不区分鼠标跟踪状态。

影响：app_new 的提交与两份报告不再作为交付物；后续在 app/ 重新实施，并复用其中的设计结论（导航真源、设置描述层、命令面板分组、原子写入、会话状态真实化）。
