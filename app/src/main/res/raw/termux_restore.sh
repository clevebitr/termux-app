#!/data/data/com.termux/files/usr/bin/bash
#
# termux-restore: Restore the Termux $PREFIX and $HOME directories from a backup created by
# `termux-backup`, like after reinstalling the app.
# termux-restore：从 `termux-backup` 生成的备份里恢复 Termux 的 $PREFIX 与 $HOME，例如重装应用之后。
#
# Usage / 用法:
#   termux-restore <archive.tar.zst|archive.tar.gz|backup-dir> [options]
#
#   -y, --yes             Do not ask for confirmation / 不再询问确认
#   --no-pre-backup       Do not back up the current environment first / 不先备份当前环境
#   --target-dir <dir>    Extract into <dir> instead of the app data directory (advanced, for
#                         testing the archive without touching the current environment)
#                         解包到指定目录而不是应用数据目录（高级用法，用于不破坏当前环境地测试归档）
#   --dry-run             Only verify the archive and report what it contains / 只校验归档并报告内容
#   -h, --help            Show this help / 显示帮助
#
# The archive is verified with its `.sha256` file (if present) before anything is extracted. Unless
# `--no-pre-backup` is passed, the current $PREFIX and $HOME are backed up first. Extraction is done
# with tar, so files that are in the archive are overwritten, while files that are not in the
# archive, like packages installed after the backup, are kept.
#
# 解包前会先用 `.sha256` 校验（若存在）。除非传 `--no-pre-backup`，否则会先备份当前环境。解包用 tar
# 覆盖式恢复：归档里的文件会被覆盖，归档里没有的文件（例如备份之后新装的包）会保留。

set -e -u -o pipefail

SCRIPT_NAME="termux-restore"
FILES_DIR="/data/data/com.termux/files"

ARCHIVE_INPUT=""
ASSUME_YES="false"
PRE_BACKUP="true"
TARGET_DIR="$FILES_DIR"
DRY_RUN="false"

usage() {
    # Print the comment block at the top of this script as the help text, until the first line that
    # is not a comment. / 把脚本开头的注释块作为帮助文本输出，遇到第一行非注释即停止。
    awk 'NR >= 3 { if ($0 !~ /^#/) exit; sub(/^# ?/, ""); print }' "$0"
    exit "${1:-0}"
}

log() { printf '[%s] %s\n' "$SCRIPT_NAME" "$*"; }
error() { printf '[%s] ERROR: %s\n' "$SCRIPT_NAME" "$*" >&2; }
die() { error "$*"; exit 1; }

# If no archive was passed, look for backups in the usual locations and ask the user to pick one.
# 若未传入归档参数，则在常用位置查找备份并让用户选择。
pick_archive_interactively() {
    local search_dirs=("$HOME/storage/shared/Download" "/sdcard/Download" "$HOME")
    local candidates=()
    local dir file

    for dir in "${search_dirs[@]}"; do
        if [ ! -d "$dir" ]; then continue; fi
        while IFS= read -r file; do
            if [ -n "$file" ]; then candidates+=("$file"); fi
        done < <(find "$dir" -maxdepth 3 -type f -name 'termux-backup-*.tar.*' 2>/dev/null | sort -r)
    done

    if [ ${#candidates[@]} -eq 0 ]; then
        error "No backup archive was found in \"$HOME/storage/shared/Download\", \"/sdcard/Download\" or \"$HOME\"."
        log "Pass the path to the archive as an argument instead, like: / 请把归档路径作为参数传入，例如："
        log "  termux-restore /sdcard/Download/termux-backup-20260101-120000.tar.zst"
        exit 1
    fi

    # Deduplicate by canonical path, since the same directory can be reached through different
    # paths, like "$HOME/storage/shared/Download" and "/sdcard/Download".
    # 按真实路径去重，因为同一个目录可能有多个路径（如 ~/storage/shared/Download 与 /sdcard/Download）。
    local unique_candidates=()
    local seen="|"
    local resolved
    for file in "${candidates[@]}"; do
        resolved="$(readlink -f "$file" 2>/dev/null || echo "$file")"
        if [ "${seen#*|$resolved|}" = "$seen" ]; then
            unique_candidates+=("$file")
            seen="$seen$resolved|"
        fi
    done
    candidates=("${unique_candidates[@]}")

    log "Backups found / 找到以下备份："
    local index=1
    for file in "${candidates[@]}"; do
        printf '  %d) %s (%s, %s)\n' "$index" "$file" "$(du -h "$file" | cut -f 1)" "$(date -r "$file" '+%Y-%m-%d %H:%M' 2>/dev/null || echo unknown)"
        index=$((index + 1))
    done

    printf '[%s] Enter the number of the backup to restore, or "q" to quit / 输入序号恢复，或输入 q 退出: ' "$SCRIPT_NAME"
    local choice
    read -r choice
    [ "$choice" = "q" ] && exit 0
    case "$choice" in
        ''|*[!0-9]*) die "Invalid selection. / 选择无效。" ;;
    esac
    if [ "$choice" -lt 1 ] || [ "$choice" -gt ${#candidates[@]} ]; then
        die "Invalid selection. / 选择无效。"
    fi

    ARCHIVE_PATH="${candidates[$((choice - 1))]}"
}

while [ $# -gt 0 ]; do
    case "$1" in
        -y|--yes) ASSUME_YES="true"; shift ;;
        --no-pre-backup) PRE_BACKUP="false"; shift ;;
        --target-dir) [ $# -ge 2 ] || die "--target-dir requires an argument. / --target-dir 需要参数。"; TARGET_DIR="$2"; shift 2 ;;
        --dry-run) DRY_RUN="true"; shift ;;
        -h|--help) usage 0 ;;
        -*) die "Invalid option \"$1\", use -h for help. / 无效选项，用 -h 查看帮助。" ;;
        *) [ -z "$ARCHIVE_INPUT" ] || die "Only one archive can be restored at a time. / 一次只能恢复一个归档。"; ARCHIVE_INPUT="$1"; shift ;;
    esac
done

# The archive can also be picked interactively when no argument is passed, see below.
# 也可以不传参数，见下面的交互选择。

# Accept either the archive itself or the directory that contains it, so that both
# `termux-restore /sdcard/Download/termux-backup-20260101-120000` and the full archive path work.
# If nothing is passed, ask the user to pick one of the backups found on the device.
# 既接受归档文件本身，也接受包含它的目录；若什么都没传，则列出设备上找到的备份让用户选择。
if [ -z "$ARCHIVE_INPUT" ]; then
    pick_archive_interactively
elif [ -d "$ARCHIVE_INPUT" ]; then
    ARCHIVE_PATH="$(find "$ARCHIVE_INPUT" -maxdepth 1 -name '*.tar.zst' -o -maxdepth 1 -name '*.tar.gz' | sort | head -n 1)"
    [ -n "$ARCHIVE_PATH" ] || die "No backup archive found in \"$ARCHIVE_INPUT\". / 该目录里没有找到备份归档。"
else
    ARCHIVE_PATH="$ARCHIVE_INPUT"
fi

[ -f "$ARCHIVE_PATH" ] || die "The archive \"$ARCHIVE_PATH\" does not exist. / 归档不存在。"

case "$ARCHIVE_PATH" in
    *.tar.zst)
        command -v zstd >/dev/null 2>&1 || die "'zstd' is not installed, run 'pkg install zstd' and try again. / 未安装 zstd，请执行 pkg install zstd 后重试。"
        DECOMPRESS_ARGS=(-I "zstd -d")
        ;;
    *.tar.gz|*.tgz)
        command -v gzip >/dev/null 2>&1 || die "'gzip' is not installed. / 未安装 gzip。"
        DECOMPRESS_ARGS=(-I "gzip -d")
        ;;
    *)
        die "Unsupported archive extension, expected `.tar.zst` or `.tar.gz`. / 不支持的归档扩展名。"
        ;;
esac

log "Verifying the archive ... / 正在校验归档 ..."
CHECKSUM_FILE="${ARCHIVE_PATH%.tar.zst}"
CHECKSUM_FILE="${CHECKSUM_FILE%.tar.gz}"
CHECKSUM_FILE="$(dirname "$ARCHIVE_PATH")/$(basename "$CHECKSUM_FILE").sha256"
if [ -f "$CHECKSUM_FILE" ]; then
    if ! ( cd "$(dirname "$ARCHIVE_PATH")" && sha256sum -c "$(basename "$CHECKSUM_FILE")" ); then
        die "The archive checksum does not match, the backup is corrupt. / 归档校验值不匹配，备份已损坏。"
    fi
else
    log "No checksum file found, skipping it. / 未找到校验文件，跳过。"
fi

if ! tar -t "${DECOMPRESS_ARGS[@]}" -f "$ARCHIVE_PATH" > /dev/null; then
    die "The archive is corrupt. / 归档已损坏。"
fi

ARCHIVE_DIR="$(dirname "$ARCHIVE_PATH")"
ARCHIVE_BASENAME="$(basename "$ARCHIVE_PATH")"
ARCHIVE_BASENAME="${ARCHIVE_BASENAME%.tar.zst}"
ARCHIVE_BASENAME="${ARCHIVE_BASENAME%.tar.gz}"
METADATA_DIR="$ARCHIVE_DIR/$ARCHIVE_BASENAME.metadata"

ENTRY_COUNT="$(tar -t "${DECOMPRESS_ARGS[@]}" -f "$ARCHIVE_PATH" | wc -l)"
ARCHIVE_SIZE="$(du -h "$ARCHIVE_PATH" | cut -f 1)"
log "Archive: / 归档：$ARCHIVE_PATH ($ARCHIVE_SIZE, $ENTRY_COUNT entries / 条目)"

if [ -d "$METADATA_DIR" ] && [ -f "$METADATA_DIR/metadata.txt" ]; then
    log "Backup metadata: / 备份元数据："
    sed 's/^/  /' "$METADATA_DIR/metadata.txt"
    if [ -f "$METADATA_DIR/dpkg-selections.txt" ]; then
        log "Installed package count in backup: / 备份中的已安装包数量：$(wc -l < "$METADATA_DIR/dpkg-selections.txt")"
    fi
fi

if [ "$DRY_RUN" = "true" ]; then
    log "Dry run, nothing was extracted. / 试运行，未解包任何文件。"
    log "The archive contains, among others: / 归档包含（示例）："
    tar -t "${DECOMPRESS_ARGS[@]}" -f "$ARCHIVE_PATH" | grep -E '^\./(home/\.termux/termux\.properties|usr/bin/bash|usr/bin/node)$' | sed 's/^/  /' || true
    exit 0
fi

REAL_TARGET_DIR="$(cd "$(dirname "$TARGET_DIR")" && pwd)/$(basename "$TARGET_DIR")"
mkdir -p "$TARGET_DIR" || die "Failed to create \"$TARGET_DIR\". / 无法创建目标目录。"

if [ "$TARGET_DIR" = "$FILES_DIR" ]; then
    log "WARNING: This will overwrite \"$FILES_DIR/home\" and \"$FILES_DIR/usr\" with the contents of the backup. / 警告：将用备份内容覆盖 \$HOME 与 \$PREFIX。"
    if [ "$PRE_BACKUP" = "true" ]; then
        log "The current environment will be backed up first to \"$ARCHIVE_DIR/termux-backup-before-restore-$(date +%Y%m%d-%H%M%S).tar.${ARCHIVE_PATH##*.}\". / 会先自动备份当前环境。"
    fi
    if [ "$ASSUME_YES" != "true" ]; then
        printf '[%s] Continue? Type "yes" to proceed / 输入 yes 继续: ' "$SCRIPT_NAME"
        read -r answer
        [ "$answer" = "yes" ] || die "Aborted. / 已取消。"
    fi

    if [ "$PRE_BACKUP" = "true" ]; then
        PRE_BACKUP_NAME="termux-backup-before-restore-$(date +%Y%m%d-%H%M%S)"
        log "Backing up the current environment to \"$PRE_BACKUP_NAME\" ... / 正在备份当前环境 ..."
        if [ -x "$PREFIX/bin/termux-backup" ]; then
            "$PREFIX/bin/termux-backup" -o "$ARCHIVE_DIR" -n "$PRE_BACKUP_NAME" || die "Failed to back up the current environment, use --no-pre-backup to skip it. / 备份当前环境失败，可用 --no-pre-backup 跳过。"
        else
            tar -c -I "zstd -3 -T0" --exclude=./usr/tmp/\* --exclude=./usr/var/run/\* \
                --exclude=./home/storage --exclude=./home/.cache/\* --warning=no-file-changed \
                -f "$ARCHIVE_DIR/$PRE_BACKUP_NAME.tar.zst" -C "$FILES_DIR" ./home ./usr \
                || die "Failed to back up the current environment, use --no-pre-backup to skip it. / 备份当前环境失败。"
        fi
    fi
fi

log "Extracting into \"$REAL_TARGET_DIR\" ... / 正在解包 ..."
if ! tar -x "${DECOMPRESS_ARGS[@]}" -f "$ARCHIVE_PATH" -C "$TARGET_DIR" --no-same-owner; then
    die "Failed to extract the archive. / 解包失败。"
fi

log "Restore finished. / 恢复完成。"
if [ "$TARGET_DIR" = "$FILES_DIR" ]; then
    log ""
    log "Run the following to finish: / 建议执行以下收尾步骤："
    log "  termux-reload-settings   # reload ~/.termux/termux.properties, colors and fonts / 重新加载配置、配色与字体"
    log "  pkg upgrade              # update the restored packages / 升级恢复回来的包"
    log "  termux-setup-storage     # recreate ~/storage, which is not part of the backup / 重建 ~/storage"
    log "  termux-fix-shebang ~/bin/*   # only if you have scripts in ~/bin with absolute shebangs / 仅当 ~/bin 里有绝对路径 shebang 的脚本时需要"
    log ""
    log "If the app was reinstalled as a different version, some binaries may need to be reinstalled with 'pkg reinstall <package>'. / 若应用版本变化导致某些二进制不兼容，可用 pkg reinstall 重装对应包。"
fi
