package com.deepdownstudios.smsolver;

import com.deepdownstudios.smsolver.History.HistoryException;

public class CommandResult {
	private History history;
	private String message;

	public CommandResult(History history) throws CommandException {
		this(history, getMessageFromHistory(history));
	}
	
	public CommandResult(History history, String message) {
		assert history != null && message != null;
		this.history = history;
		this.message = message;
	}

	public History getHistory() {
		return history;
	}

	public String getMessage() {
		return message;
	}

	private static String getMessageFromHistory(History history) throws CommandException {
		// This function exists because of Java constructor rules.  We want to catch the history exception to change it
		// because it should never happen (so its an internal error).  There is no command whose result should leave the
		// history in an inconsistent state.  That is only possible when no commands have been executed!
		try {
			return history.getCurrentState().getCommandMessage();
		} catch (HistoryException e) {
			throw new CommandException("Internal error in CommandResult()", e);
		}
	}
}
