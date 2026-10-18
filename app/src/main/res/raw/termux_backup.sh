#!/data/data/com.termux/files/usr/bin/bash
#
# termux-backup: Back up the Termux $PREFIX and $HOME directories to shared storage, so that they
# can be restored after uninstalling the app, like when switching to a build that is signed with a
# different key (uninstalling the app deletes /data/data/com.termux/files).
# termux-backup：把 Termux 的 $PREFIX 和 $HOME 备份到共享存储，用于卸载应用（比如换成另一个签名
# 的构建）后恢复环境，因为卸载应用会删除 /data/data/com.termux/files。
#
# Usage / 用法:
#   termux-backup [-o <dir>] [-n <name>] [-z <zstd|gzip>] [-h]
#
#   -o <dir>    Directory to save the backup in / 备份输出目录 (default: /sdcard/Download)
#   -n <name>   Backup file basename / 备份文件名 (default: termux-backup-<date>-<time>)
#   -z <tool>   Compressor to use, `zstd` or `gzip` / 压缩工具 (default: zstd if available)
#   -h          Show this help / 显示帮助
#
# The archive contains the full $PREFIX (`./usr`) and $HOME (`./home`) directories, excluding only
# the paths that are regenerated at runtime, and a `metadata` directory with the package selection,
# the Termux properties file and the app/bootstrap versions, so that the restore can report what
# changed. The archive is verified after creation and a `.sha256` file is written next to it.
#
# 归档里包含完整的 $PREFIX（`./usr`）与 $HOME（`./home`），只排除运行时生成的内容，另有 `metadata`
# 目录记录已安装包列表、Termux 配置文件与应用/bootstrap 版本，便于恢复时对比。归档生成后会做完整性
# 校验，并在旁边写一个 `.sha256` 文件。

set -e -u -o pipefail

SCRIPT_NAME="termux-backup"
FILES_DIR="/data/data/com.termux/files"
DEFAULT_OUTPUT_DIR="/sdcard/Download"

OUTPUT_DIR=""
BACKUP_NAME=""
COMPRESSOR=""

usage() {
    # Print the comment block at the top of this script as the help text, until the first line that
    # is not a comment. / 把脚本开头的注释块作为帮助文本输出，遇到第一行非注释即停止。
    awk 'NR >= 3 { if ($0 !~ /^#/) exit; sub(/^# ?/, ""); print }' "$0"
    exit "${1:-0}"
}

log() { printf '[%s] %s\n' "$SCRIPT_NAME" "$*"; }
error() { printf '[%s] ERROR: %s\n' "$SCRIPT_NAME" "$*" >&2; }
die() { error "$*"; exit 1; }

while getopts ":o:n:z:h" opt; do
    case "$opt" in
        o) OUTPUT_DIR="$OPTARG" ;;
        n) BACKUP_NAME="$OPTARG" ;;
        z) COMPRESSOR="$OPTARG" ;;
        h) usage 0 ;;
        \?) die "Invalid option '-$OPTARG', use -h for help. / 无效选项，用 -h 查看帮助。" ;;
        :) die "Option '-$OPTARG' requires an argument. / 选项缺少参数。" ;;
    esac
done

[ -d "$FILES_DIR/home" ] && [ -d "$FILES_DIR/usr" ] || die "This does not look like a Termux app data directory: \"$FILES_DIR\". / 这不是 Termux 的数据目录。"

# Select the compressor, preferring zstd since it is much faster and compresses better than gzip.
# 选择压缩工具，优先 zstd（更快、压缩率更好）。
if [ -z "$COMPRESSOR" ]; then
    if command -v zstd >/dev/null 2>&1; then
        COMPRESSOR="zstd"
    elif command -v gzip >/dev/null 2>&1; then
        COMPRESSOR="gzip"
    else
        die "Neither 'zstd' nor 'gzip' is installed, install one of them, like with 'pkg install zstd'. / 未安装 zstd 或 gzip，请先 'pkg install zstd'。"
    fi
fi

case "$COMPRESSOR" in
    zstd)
        command -v zstd >/dev/null 2>&1 || die "'zstd' is not installed, run 'pkg install zstd'. / 未安装 zstd，请执行 pkg install zstd。"
        ARCHIVE_EXTENSION="tar.zst"
        COMPRESS_ARGS=(-I "zstd -3 -T0")
        VERIFY_ARGS=(-I "zstd -d")
        ;;
    gzip)
        command -v gzip >/dev/null 2>&1 || die "'gzip' is not installed. / 未安装 gzip。"
        ARCHIVE_EXTENSION="tar.gz"
        COMPRESS_ARGS=(-I "gzip -1")
        VERIFY_ARGS=(-I "gzip -d")
        ;;
    *)
        die "Unsupported compressor \"$COMPRESSOR\", use 'zstd' or 'gzip'. / 不支持的压缩工具。"
        ;;
esac

# Select the output directory, falling back to the internal storage if shared storage is not
# available yet, which can happen before `termux-setup-storage` is run.
# 选择输出目录；若共享存储不可用（还没跑 termux-setup-storage），退回到内部存储。
if [ -z "$OUTPUT_DIR" ]; then
    if [ -d "$DEFAULT_OUTPUT_DIR" ] && [ -w "$DEFAULT_OUTPUT_DIR" ]; then
        OUTPUT_DIR="$DEFAULT_OUTPUT_DIR"
    elif [ -d "$HOME/storage/shared" ] && [ -w "$HOME/storage/shared" ]; then
        OUTPUT_DIR="$HOME/storage/shared/Download"
    else
        OUTPUT_DIR="$HOME"
        log "Shared storage is not accessible, saving the backup in \$HOME instead. Run 'termux-setup-storage' and copy it somewhere permanent afterwards. / 共享存储不可访问，备份将保存在 \$HOME，请随后运行 termux-setup-storage 并把备份复制到安全位置。"
    fi
fi

mkdir -p "$OUTPUT_DIR" || die "Failed to create the output directory \"$OUTPUT_DIR\". / 无法创建输出目录。"
[ -w "$OUTPUT_DIR" ] || die "The output directory \"$OUTPUT_DIR\" is not writable. / 输出目录不可写。"

if [ -z "$BACKUP_NAME" ]; then
    BACKUP_NAME="termux-backup-$(date +%Y%m%d-%H%M%S)"
fi

ARCHIVE_PATH="$OUTPUT_DIR/$BACKUP_NAME.$ARCHIVE_EXTENSION"
METADATA_DIR="$OUTPUT_DIR/$BACKUP_NAME.metadata"

[ -e "$ARCHIVE_PATH" ] && die "The file \"$ARCHIVE_PATH\" already exists, pass a different name with -n. / 文件已存在，请用 -n 指定别的名字。"

log "Backing up \"$FILES_DIR\" to \"$ARCHIVE_PATH\" with $COMPRESSOR ... / 正在备份到该文件 ..."

# Collect metadata that helps the restore report differences, like the Termux app and bootstrap
# versions and the installed packages. / 收集用于恢复时对比的元数据。
mkdir -p "$METADATA_DIR" || die "Failed to create \"$METADATA_DIR\". / 无法创建元数据目录。"
{
    echo "backup_date=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "backup_script_version=1"
    echo "compressor=$COMPRESSOR"
    echo "prefix_dir=$PREFIX"
    echo "home_dir=$HOME"
    echo "termux_app_version=${TERMUX_VERSION:-unknown}"
    for version_file in "$PREFIX/etc/termux-bootstrap-version" "$PREFIX/etc/bootstrap-version" "$PREFIX/etc/os-release"; do
        if [ -f "$version_file" ]; then
            echo "$(basename "$version_file")=$(head -n 1 "$version_file")"
        fi
    done
    echo "dpkg_architecture=$(dpkg --print-architecture 2>/dev/null || echo unknown)"
} > "$METADATA_DIR/metadata.txt"
dpkg --get-selections > "$METADATA_DIR/dpkg-selections.txt" 2>/dev/null || true
if [ -f "$HOME/.termux/termux.properties" ]; then
    cp -a "$HOME/.termux/termux.properties" "$METADATA_DIR/termux.properties"
fi
if [ -f "$HOME/.termux/colors.properties" ]; then
    cp -a "$HOME/.termux/colors.properties" "$METADATA_DIR/colors.properties"
fi

# Paths that are excluded since they contain sockets, temporary files or are regenerated:
#   usr/tmp, usr/var/run -> sockets and temporary files of running processes
#   home/storage         -> symlinks to the device storage, recreated by `termux-setup-storage`
#   home/.cache          -> caches that are regenerated
# 排除项：socket/临时文件、指向设备存储的符号链接、可再生成的缓存。
EXCLUDE_ARGS=(
    "--exclude=./usr/tmp/*"
    "--exclude=./usr/var/run/*"
    "--exclude=./home/storage"
    "--exclude=./home/.cache/*"
)

START_TIME=$(date +%s)
if ! tar -c "${COMPRESS_ARGS[@]}" "${EXCLUDE_ARGS[@]}" --warning=no-file-changed \
        -f "$ARCHIVE_PATH" -C "$FILES_DIR" ./home ./usr; then
    error "Failed to create the backup archive, deleting the incomplete file. / 创建归档失败，删除不完整的文件。"
    rm -f "$ARCHIVE_PATH"
    exit 1
fi
ELAPSED=$(( $(date +%s) - START_TIME ))

log "Verifying the archive ... / 正在校验归档 ..."
if ! tar -t "${VERIFY_ARGS[@]}" -f "$ARCHIVE_PATH" > /dev/null; then
    error "The backup archive is corrupt. / 备份归档已损坏。"
    exit 1
fi

tar -t "${VERIFY_ARGS[@]}" -f "$ARCHIVE_PATH" | wc -l > "$METADATA_DIR/file-count.txt"

log "Writing the SHA-256 checksum ... / 正在写入 SHA-256 校验值 ..."
( cd "$OUTPUT_DIR" && sha256sum "$BACKUP_NAME.$ARCHIVE_EXTENSION" > "$BACKUP_NAME.sha256" ) \
    || die "Failed to write the checksum file. / 无法写入校验文件。"

ARCHIVE_SIZE="$(du -h "$ARCHIVE_PATH" | cut -f 1)"
log "Backup finished in ${ELAPSED}s, size: $ARCHIVE_SIZE. / 备份完成。"
log "Archive: / 归档：$ARCHIVE_PATH"
log "Checksum: / 校验值：$OUTPUT_DIR/$BACKUP_NAME.sha256"
log "Entries: / 条目数：$(cat "$METADATA_DIR/file-count.txt")"
log "Metadata: / 元数据：$METADATA_DIR"
log ""
log "To restore it after reinstalling the app, run: / 重装应用后可用以下命令恢复："
log "  termux-restore \"$ARCHIVE_PATH\""
