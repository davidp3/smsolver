package com.deepdownstudios.smsolver;

public class CommandResult {
	private History history;
	private String message;

	public CommandResult(History history) {
		this(history, history.getCurrentState().getCommandMessage());
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
}
