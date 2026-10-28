# Termux shell integration for OSC 133 command marks.
#
# This file is sourced from ~/.zshrc by the "Install shell integration" action of the Termux settings
# (Settings -> Termux -> Terminal View -> Shell integration). It reports the start of a prompt
# (OSC 133;A), the start of a command (OSC 133;C) and the end of a command with its exit code and the
# command line that was executed (OSC 133;D;<exit code>;<command>). Termux uses the marks to jump
# between the commands of the transcript, to draw a mark for each command on the right edge of the
# terminal, to tell when a command failed and to show the command in the notification card of a failed
# command. The command line is an additional parameter, which other terminals ignore.
#
# The hooks are added with add-zsh-hook, so the existing precmd and preexec hooks keep working.
#
# To remove it, use Settings -> Termux -> Terminal View -> Uninstall shell integration.

# Do not do anything if the file has already been sourced, by this or another shell.
if [ -n "${__TERMUX_OSC133_ZSH_LOADED:-}" ]; then
    return 0 2>/dev/null || exit 0
fi
__TERMUX_OSC133_ZSH_LOADED=1

# The version of these scripts, which is used to detect an outdated install.
__TERMUX_OSC133_VERSION=2

# The command line of the command that is running, which is stored by the preexec hook, so that the
# precmd hook does not have to read the history with a subshell on every prompt.
__TERMUX_OSC133_LAST_COMMAND=""

__termux_osc133_precmd() {
    local __termux_osc133_exit_code=$?
    local __termux_osc133_command="${__TERMUX_OSC133_LAST_COMMAND}"
    # Control characters would terminate the escape sequence or break the notification card.
    __termux_osc133_command="${__termux_osc133_command//[[:cntrl:]]/ }"
    printf '\033]133;D;%d;%s\007' "$__termux_osc133_exit_code" "$__termux_osc133_command"
    printf '\033]133;A\007'
    __TERMUX_OSC133_LAST_COMMAND=""
    # Preserve the exit code for the other precmd hooks and for the shell.
    return $__termux_osc133_exit_code
}

# The first argument of preexec is the command line as it was typed, which is what the user wants to
# see in the notification card, unlike the expanded command in the third argument.
__termux_osc133_preexec() {
    __TERMUX_OSC133_LAST_COMMAND="${1:-}"
    printf '\033]133;C\007'
}

autoload -Uz add-zsh-hook
add-zsh-hook precmd __termux_osc133_precmd
add-zsh-hook preexec __termux_osc133_preexec
