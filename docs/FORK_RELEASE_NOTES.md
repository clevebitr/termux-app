<!--
  This file is appended to the release notes of the `fork-latest` release by
  `.github/workflows/fork_build.yml`, after the per-build information (commit, signing mode, build log).
  Keep it short and stable: the detailed documentation lives in FORK.md, the design decisions in
  DECISIONS.md and the signing/installation notes in FORK_BUILD.md.
-->

## What is in this build

A personal fork of [Termux](https://github.com/termux/termux-app) with quality of life features for
terminal use on a phone. Everything is opt-in: the new properties keep the upstream behaviour unless
they are enabled, and all of them can also be changed in **Settings -> Termux**.

| Area | Highlights | Docs |
| --- | --- | --- |
| Notifications | Notification cards in the corner of the terminal for failed commands (with the command line, the working directory and the duration), for `OSC 9` / `OSC 99` / `OSC 777` requests of programs (buttons, icons, `p=?`/`p=alive` queries, click and close reports, rate limiting), and for app events (session exit, plugin result, title change, bell). Optional system notification when the app is in the background, and a recent notifications list in the long press menu. | FORK.md §20, §23, §24 |
| Long press menu | Styled, configurable context menu (order and selection of the actions) and configurable text selection toolbar actions. | FORK.md §21, §22 |
| Extra keys | Multiple panels, theme colours, tile background, popup hints, `SEARCH` / `SPLIT_V` / `SPLIT_H` / `SPLIT_CLOSE` keys, custom volume key actions. | FORK.md §9, §10, §11, §12, §13 |
| Terminal | In-terminal search, OSC 52 clipboard policy, shell integration with command marks (jump to command, failure marks, exit code and command line), split screen, terminal padding. | FORK.md §14–§18 |
| Status bar | Optional status bar with clock, battery, memory, CPU, network, IP and proxy segments, sparklines, `theme:` colours and click actions. | FORK.md §19 |
| Appearance | Italic font, OSC 8 hyperlinks, `night-mode=color-scheme`, day/night aware status bar icons, simplified chinese translation. | FORK.md §2, §3, §5, §8 |
| Backup | `termux-backup` / `termux-restore` scripts and a settings entry. | FORK.md §6 |

Quick start for the notification cards:

```properties
# ~/.termux/termux.properties, then run `termux-reload-settings`
notification-enabled = true          # master switch, disabled by default
notification-osc-enabled = true      # let programs show cards, e.g. printf '\033]9;hello\007'
notification-events-enabled = true   # cards for session exit / plugin result / title / bell
```

The notification cards need the shell integration to show the command line of a failed command:
**Settings -> Termux -> Terminal View -> Install shell integration** (reinstall it after updating
from an older build, so that the scripts report the command line). See FORK.md §17 and §20.

## Known limitations

* The floating text selection toolbar keeps the system `ActionMode`, so its colours follow the app
  theme and not `colors.properties`, and only its actions can be configured (FORK.md §21.2).
* The split screen is not persisted across activity recreation (planned).
* Search is case sensitive and supports no regular expressions (planned).
* The notification cards are only fully verified on one device; see the "verification" section of
  FORK.md for what was tested on a device and what was not.
* `OSC 99` notifications may be swallowed by `tmux`, depending on its `allow-passthrough` settings.

## Documentation

* [FORK.md](https://github.com/clevebitr/termux-app/blob/master/FORK.md): every feature, its properties and
  its implementation notes.
* [FORK_BUILD.md](https://github.com/clevebitr/termux-app/blob/master/FORK_BUILD.md): how to build, sign and
  install the app with the plugins (`sharedUserId` and signature constraints).
* [DECISIONS.md](https://github.com/clevebitr/termux-app/blob/master/DECISIONS.md): design decisions and the
  pitfalls that were hit, as a development log.
