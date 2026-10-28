# Termux shell integration for OSC 133 command marks.
#
# This file is sourced from ~/.bashrc by the "Install shell integration" action of the Termux
# settings (Settings -> Termux -> Terminal View -> Shell integration). It reports the start of a
# prompt (OSC 133;A), the start of a command (OSC 133;C) and the end of a command with its exit code
# and the command line that was executed (OSC 133;D;<exit code>;<command>). Termux uses the marks to
# jump between the commands of the transcript, to draw a mark for each command on the right edge of
# the terminal, to tell when a command failed and to show the command in the notification card of a
# failed command. The command line is an additional parameter, which other terminals ignore.
#
# The prompt (PS1) is not modified, and the existing PROMPT_COMMAND entries and PS0 are preserved, so
# other prompt hooks keep working. The command start mark uses PS0, which needs bash 4.4 or later.
# Older versions still get the prompt and exit code marks.
#
# To remove it, use Settings -> Termux -> Terminal View -> Uninstall shell integration.

# Do not do anything if the file has already been sourced, by this or another shell.
if [ -n "${__TERMUX_OSC133_BASH_LOADED:-}" ]; then
    return 0 2>/dev/null || exit 0
fi
__TERMUX_OSC133_BASH_LOADED=1

# The version of these scripts, which is used to detect an outdated install.
__TERMUX_OSC133_VERSION=2

# Print the command line of the last command, so that the terminal can show it in the notification
# card of a command that failed. The history entry starts with the history number, which is dropped
# here. Control characters are replaced by spaces, since they would terminate the escape sequence.
__termux_osc133_last_command() {
    local __termux_osc133_line __termux_osc133_number
    __termux_osc133_line="$(HISTTIMEFORMAT= builtin history 1 2>/dev/null)" || return 0
    IFS=' ' read -r __termux_osc133_number __termux_osc133_line <<< "${__termux_osc133_line}"
    __termux_osc133_line="${__termux_osc133_line//[[:cntrl:]]/ }"
    printf '%s' "${__termux_osc133_line}"
}

__termux_osc133_prompt() {
    local __termux_osc133_exit_code="$?"
    printf '\033]133;D;%d;%s\007' "$__termux_osc133_exit_code" "$(__termux_osc133_last_command)"
    printf '\033]133;A\007'
    # Preserve the exit code for the other PROMPT_COMMAND entries and for the shell.
    return "$__termux_osc133_exit_code"
}

# Add the prompt hook before the existing PROMPT_COMMAND entries, since it needs the exit code of
# the command that just finished. PROMPT_COMMAND may be an array, which is supported since bash 5.1.
__termux_osc133_prompt_command_type="$(declare -p PROMPT_COMMAND 2>/dev/null | cut -d' ' -f2)"
case "$__termux_osc133_prompt_command_type" in
    -a*|*-a*)
        PROMPT_COMMAND=(__termux_osc133_prompt "${PROMPT_COMMAND[@]}")
        ;;
    *)
        PROMPT_COMMAND="__termux_osc133_prompt${PROMPT_COMMAND:+;$PROMPT_COMMAND}"
        ;;
esac
unset __termux_osc133_prompt_command_type

# bash expands PS0 after a command line has been read and before the command is executed, which is
# exactly where the command start mark belongs. It requires bash 4.4 or later.
if [ "${BASH_VERSINFO[0]}" -gt 4 ] || { [ "${BASH_VERSINFO[0]}" -eq 4 ] && [ "${BASH_VERSINFO[1]}" -ge 4 ]; }; then
    case "${PS0:-}" in
        *']133;C'*) ;;
        *) PS0='\[\e]133;C\a\]'"${PS0:-}" ;;
    esac
fi
