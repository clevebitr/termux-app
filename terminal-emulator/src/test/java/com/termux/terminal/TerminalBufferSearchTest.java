package com.termux.terminal;

import java.util.List;

/** Tests for {@link TerminalBuffer#findAllMatches(String)}. */
public class TerminalBufferSearchTest extends TerminalTestCase {

	public void testNoMatchesForEmptyQuery() {
		withTerminalSized(10, 3);
		enterString("Hello, world");
		assertEquals(0, mTerminal.getScreen().findAllMatches("").size());
		assertEquals(0, mTerminal.getScreen().findAllMatches(null).size());
	}

	public void testSingleMatch() {
		withTerminalSized(20, 3);
		enterString("Hello, world");
		List<TerminalBuffer.SearchMatch> matches = mTerminal.getScreen().findAllMatches("world");
		assertEquals(1, matches.size());
		TerminalBuffer.SearchMatch match = matches.get(0);
		assertEquals(0, match.row);
		assertEquals(7, match.startColumn);
		assertEquals(12, match.endColumn);
	}

	public void testMatchesIgnoreCase() {
		withTerminalSized(20, 3);
		enterString("Hello HELLO hello");
		List<TerminalBuffer.SearchMatch> matches = mTerminal.getScreen().findAllMatches("hello");
		assertEquals(3, matches.size());
		assertEquals(0, matches.get(0).startColumn);
		assertEquals(6, matches.get(1).startColumn);
		assertEquals(12, matches.get(2).startColumn);
	}

	public void testMatchesAreNonOverlapping() {
		withTerminalSized(10, 3);
		enterString("aaaaa");
		List<TerminalBuffer.SearchMatch> matches = mTerminal.getScreen().findAllMatches("aa");
		assertEquals(2, matches.size());
		assertEquals(0, matches.get(0).startColumn);
		assertEquals(2, matches.get(1).startColumn);
	}

	public void testMatchInTrimmedTrailingSpace() {
		withTerminalSized(20, 3);
		enterString("a b");
		// The trailing cells that were never written must not be matched as spaces.
		assertEquals(1, mTerminal.getScreen().findAllMatches("a").size());
		assertEquals(0, mTerminal.getScreen().findAllMatches("b ").size());
	}

	public void testMatchInTranscript() {
		withTerminalSized(20, 2);
		enterString("line1\r\nline2\r\nline3");
		// With 2 screen rows the screen shows "line2" and "line3", and "line1" is in the transcript.
		List<TerminalBuffer.SearchMatch> matches = mTerminal.getScreen().findAllMatches("line1");
		assertEquals(1, matches.size());
		assertEquals(-1, matches.get(0).row);
		assertEquals(0, matches.get(0).startColumn);
		assertEquals(5, matches.get(0).endColumn);

		matches = mTerminal.getScreen().findAllMatches("line3");
		assertEquals(1, matches.size());
		assertEquals(1, matches.get(0).row);
	}

	public void testMatchSpanningWrappedLine() {
		withTerminalSized(4, 2);
		enterString("abcdef");
		assertLineIs(0, "abcd");
		assertTrue(mTerminal.getScreen().getLineWrap(0));

		// A match inside a single wrapped row.
		List<TerminalBuffer.SearchMatch> matches = mTerminal.getScreen().findAllMatches("bc");
		assertEquals(1, matches.size());
		assertEquals(0, matches.get(0).row);
		assertEquals(1, matches.get(0).startColumn);
		assertEquals(3, matches.get(0).endColumn);

		// A match that spans the wrapping point produces one range per row.
		matches = mTerminal.getScreen().findAllMatches("cde");
		assertEquals(2, matches.size());
		assertEquals(0, matches.get(0).row);
		assertEquals(2, matches.get(0).startColumn);
		assertEquals(4, matches.get(0).endColumn);
		assertEquals(1, matches.get(1).row);
		assertEquals(0, matches.get(1).startColumn);
		assertEquals(1, matches.get(1).endColumn);
	}

	public void testMatchWithWideCharacters() {
		withTerminalSized(10, 3);
		enterString("a你好b");
		List<TerminalBuffer.SearchMatch> matches = mTerminal.getScreen().findAllMatches("你好");
		assertEquals(1, matches.size());
		assertEquals(1, matches.get(0).startColumn);
		assertEquals(5, matches.get(0).endColumn);

		matches = mTerminal.getScreen().findAllMatches("好b");
		assertEquals(1, matches.size());
		assertEquals(3, matches.get(0).startColumn);
		assertEquals(6, matches.get(0).endColumn);
	}

	public void testMatchesAreSortedByRowAndColumn() {
		withTerminalSized(10, 3);
		enterString("x\r\nx x\r\nx");
		List<TerminalBuffer.SearchMatch> matches = mTerminal.getScreen().findAllMatches("x");
		assertEquals(4, matches.size());
		assertEquals(0, matches.get(0).row);
		assertEquals(1, matches.get(1).row);
		assertEquals(0, matches.get(1).startColumn);
		assertEquals(1, matches.get(2).row);
		assertEquals(2, matches.get(2).startColumn);
		assertEquals(2, matches.get(3).row);
	}

}
