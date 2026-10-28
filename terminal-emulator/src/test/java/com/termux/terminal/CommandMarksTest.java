package com.termux.terminal;

/** Tests for the `OSC 133` shell integration command marks. */
public class CommandMarksTest extends TerminalTestCase {

	private static final String PROMPT_START = "\033]133;A\007";
	private static final String COMMAND_START = "\033]133;C\007";

	public void testPromptStartMark() {
		withTerminalSized(10, 3);
		enterString(PROMPT_START);
		assertEquals(TerminalBuffer.COMMAND_MARK_PROMPT_START, mTerminal.getScreen().getCommandMark(0));
	}

	public void testCommandStartDoesNotAddASecondMarkOnThePromptRow() {
		withTerminalSized(10, 3);
		enterString(PROMPT_START + "abc" + COMMAND_START);
		// The command is anchored on the prompt row, so the row where the output starts is not marked.
		assertEquals(TerminalBuffer.COMMAND_MARK_PROMPT_START, mTerminal.getScreen().getCommandMark(0));
	}

	public void testCommandStartMarkWithoutPrompt() {
		withTerminalSized(10, 3);
		enterString(COMMAND_START);
		// Without a prompt mark the row where the output starts is used as the command anchor.
		assertEquals(TerminalBuffer.COMMAND_MARK_COMMAND_START, mTerminal.getScreen().getCommandMark(0));
	}

	public void testSuccessfulCommand() {
		withTerminalSized(10, 5);
		enterString(PROMPT_START + "$ ls\r\n" + COMMAND_START + "file\r\n" + "\033]133;D;0\007");
		// The command is anchored on the prompt row, which is row 0, and its output starts on row 1.
		int mark = mTerminal.getScreen().getCommandMark(0);
		assertEquals(TerminalBuffer.COMMAND_MARK_PROMPT_START | TerminalBuffer.COMMAND_MARK_COMMAND_SUCCEEDED, mark);
		assertEquals(0, mTerminal.getScreen().getCommandMark(1));
		assertFalse((mark & TerminalBuffer.COMMAND_MARK_COMMAND_FAILED) != 0);
		assertEquals(1, mOutput.commandExitCodes.size());
		assertEquals(0, mOutput.commandExitCodes.get(0).intValue());
	}

	public void testFailedCommand() {
		withTerminalSized(10, 5);
		enterString(PROMPT_START + "$ fail\r\n" + COMMAND_START + "error\r\n" + "\033]133;D;127\007");
		int mark = mTerminal.getScreen().getCommandMark(0);
		assertTrue((mark & TerminalBuffer.COMMAND_MARK_PROMPT_START) != 0);
		assertTrue((mark & TerminalBuffer.COMMAND_MARK_COMMAND_FAILED) != 0);
		assertFalse((mark & TerminalBuffer.COMMAND_MARK_COMMAND_SUCCEEDED) != 0);
		assertEquals(127, mOutput.commandExitCodes.get(0).intValue());
	}

	public void testExitStatusIsStoredOnThePromptRow() {
		withTerminalSized(10, 5);
		enterString(PROMPT_START + "$ ls\r\n" + COMMAND_START + "file1\r\nfile2\r\n" + "\033]133;D;0\007");
		// The prompt is on row 0 and the output starts on row 1, but the status must be on row 0.
		int mark = mTerminal.getScreen().getCommandMark(0);
		assertTrue((mark & TerminalBuffer.COMMAND_MARK_PROMPT_START) != 0);
		assertTrue((mark & TerminalBuffer.COMMAND_MARK_COMMAND_SUCCEEDED) != 0);
		assertEquals(0, mTerminal.getScreen().getCommandMark(1));
	}

	public void testMarksMoveWithScrolling() {
		withTerminalSized(10, 2);
		enterString(PROMPT_START + "$ ls" + COMMAND_START);
		// The marked prompt row is the only row visible.
		assertTrue((mTerminal.getScreen().getCommandMark(0) & TerminalBuffer.COMMAND_MARK_PROMPT_START) != 0);

		// Scroll the marked row into the transcript.
		enterString("\r\none\r\ntwo");
		assertEquals(0, mTerminal.getScreen().getCommandMark(0) & TerminalBuffer.COMMAND_MARK_PROMPT_START);
		assertTrue((mTerminal.getScreen().getCommandMark(-1) & TerminalBuffer.COMMAND_MARK_PROMPT_START) != 0);

		// The exit status is still stored on the row that scrolled into the transcript.
		enterString("\033]133;D;1\007");
		assertTrue((mTerminal.getScreen().getCommandMark(-1) & TerminalBuffer.COMMAND_MARK_COMMAND_FAILED) != 0);
		assertEquals(0, mTerminal.getScreen().getCommandMark(0) & TerminalBuffer.COMMAND_MARK_COMMAND_FAILED);
	}

	public void testExitStatusWithoutCommandStartIsIgnored() {
		withTerminalSized(10, 3);
		enterString("\033]133;D;1\007");
		for (int row = -mTerminal.getScreen().getActiveTranscriptRows(); row < mTerminal.mRows; row++) {
			assertEquals(0, mTerminal.getScreen().getCommandMark(row));
		}
		assertEquals(1, mOutput.commandExitCodes.size());
	}

	public void testMarksAreDroppedWhenRowsAreReused() {
		// Use a terminal with the minimum number of transcript rows, so that writing enough lines
		// recycles the row on which the command started.
		mTerminal = new TerminalEmulator(mOutput, 10, 2, INITIAL_CELL_WIDTH_PIXELS, INITIAL_CELL_HEIGHT_PIXELS,
			TerminalEmulator.TERMINAL_TRANSCRIPT_ROWS_MIN, null);
		enterString(COMMAND_START);
		assertTrue((mTerminal.getScreen().getCommandMark(0) & TerminalBuffer.COMMAND_MARK_COMMAND_START) != 0);

		// Scroll far enough for the marked row to be dropped from the transcript and reused.
		StringBuilder lines = new StringBuilder();
		for (int i = 0; i < TerminalEmulator.TERMINAL_TRANSCRIPT_ROWS_MIN + 20; i++)
			lines.append("line").append(i).append("\r\n");
		enterString(lines.toString());
		enterString("\033]133;D;1\007");
		for (int row = -mTerminal.getScreen().getActiveTranscriptRows(); row < mTerminal.mRows; row++) {
			assertEquals(0, mTerminal.getScreen().getCommandMark(row) & TerminalBuffer.COMMAND_MARK_COMMAND_FAILED);
		}
	}

	public void testMarksSurviveReflow() {
		withTerminalSized(10, 3);
		enterString(PROMPT_START + "abc" + COMMAND_START);
		assertTrue((mTerminal.getScreen().getCommandMark(0) & TerminalBuffer.COMMAND_MARK_PROMPT_START) != 0);

		resize(5, 3);
		assertTrue((mTerminal.getScreen().getCommandMark(0) & TerminalBuffer.COMMAND_MARK_PROMPT_START) != 0);
	}

	public void testResetClearsMarks() {
		withTerminalSized(10, 3);
		enterString(PROMPT_START + COMMAND_START);
		assertTrue(mTerminal.getScreen().getCommandMark(0) != 0);

		mTerminal.reset();
		for (int row = -mTerminal.getScreen().getActiveTranscriptRows(); row < mTerminal.mRows; row++) {
			assertEquals(0, mTerminal.getScreen().getCommandMark(row));
		}
	}

	public void testCommandTextIsReportedWithTheExitCode() {
		withTerminalSized(20, 5);
		enterString(PROMPT_START + "$ make -j8\r\n" + COMMAND_START + "error\r\n" + "\033]133;D;1;make -j8\007");

		assertEquals(1, mOutput.commandResults.size());
		TerminalCommandResult result = mOutput.commandResults.get(0);
		assertEquals(1, result.getExitCode());
		assertEquals("make -j8", result.getCommand());
	}

	public void testCommandTextMayContainSemicolons() {
		withTerminalSized(40, 5);
		enterString(PROMPT_START + "$ ls; pwd\r\n" + COMMAND_START + "\033]133;D;0;ls; pwd; echo a;b\007");

		TerminalCommandResult result = mOutput.commandResults.get(0);
		assertEquals("ls; pwd; echo a;b", result.getCommand());
	}

	public void testEmptyCommandTextIsReportedAsNull() {
		withTerminalSized(20, 5);
		enterString(PROMPT_START + "$ ls\r\n" + COMMAND_START + "\033]133;D;1;\007");

		assertNull(mOutput.commandResults.get(0).getCommand());
	}

	public void testExitCodeWithoutCommandTextIsParsed() {
		withTerminalSized(20, 5);
		enterString(PROMPT_START + "$ ls\r\n" + COMMAND_START + "\033]133;D;127\007");

		TerminalCommandResult result = mOutput.commandResults.get(0);
		assertEquals(127, result.getExitCode());
		assertNull(result.getCommand());
	}

	public void testInvalidExitCodeKeepsTheCommandText() {
		withTerminalSized(20, 5);
		enterString(PROMPT_START + "$ ls\r\n" + COMMAND_START + "\033]133;D;abc;ls -la\007");

		TerminalCommandResult result = mOutput.commandResults.get(0);
		assertEquals(0, result.getExitCode());
		assertEquals("ls -la", result.getCommand());
	}

	public void testCommandDurationAndSequenceAreReported() {
		withTerminalSized(20, 5);
		enterString(PROMPT_START + "$ sleep 1\r\n" + COMMAND_START + "\033]133;D;1;sleep 1\007");

		TerminalCommandResult result = mOutput.commandResults.get(0);
		assertTrue("The duration must be known when the command start mark was reported", result.getDurationMs() >= 0);
		assertTrue("The command must have a sequence number", result.getCommandSequence() != 0);
		assertTrue((mTerminal.getScreen().getCommandMark(0) & TerminalBuffer.COMMAND_MARK_PROMPT_START) != 0);
		assertEquals(mTerminal.getScreen().getCommandMarkSequence(0), result.getCommandSequence());
	}

	public void testDurationIsUnknownWithoutCommandStartMark() {
		withTerminalSized(20, 5);
		enterString(PROMPT_START + "$ ls\r\n" + "\033]133;D;0;ls\007");

		TerminalCommandResult result = mOutput.commandResults.get(0);
		assertEquals(-1, result.getDurationMs());
		assertEquals(0, result.getCommandSequence());
	}

	public void testCommandTextCanBeReadFromTheBuffer() {
		withTerminalSized(20, 5);
		enterString(PROMPT_START + "$ ls -la\r\n" + COMMAND_START + "\033]133;D;1\007");

		int sequence = mOutput.commandResults.get(0).getCommandSequence();
		assertTrue(sequence != 0);
		// The row is anchored on the prompt, so the text of the row contains the prompt and the command.
		assertEquals("$ ls -la", mTerminal.getScreen().getCommandTextForSequence(sequence));
		assertNull("An unknown sequence number must not return text of another row", mTerminal.getScreen().getCommandTextForSequence(sequence + 1000));
	}

	public void testCommandTextOfTheBufferFollowsTheCommandWhenScrolling() {
		withTerminalSized(20, 10);
		enterString(PROMPT_START + "$ ls\r\n" + COMMAND_START + "\033]133;D;1\007");
		int sequence = mOutput.commandResults.get(0).getCommandSequence();

		// Push the command into the transcript, the row of the command must still be found by sequence.
		enterString("line1\r\nline2\r\nline3\r\nline4\r\nline5\r\nline6\r\nline7\r\nline8\r\nline9\r\n");
		assertEquals("$ ls", mTerminal.getScreen().getCommandTextForSequence(sequence));
	}

	public void testResetClearsTheRunningCommandDuration() {
		withTerminalSized(20, 5);
		enterString(PROMPT_START + COMMAND_START);
		assertTrue(mTerminal.getScreen().getRunningCommandDurationMs() >= 0);

		mTerminal.reset();
		assertEquals(-1, mTerminal.getScreen().getRunningCommandDurationMs());
		assertEquals(0, mTerminal.getScreen().getRunningCommandSequence());
	}

}
