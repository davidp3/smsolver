package com.deepdownstudios.smsolver;

import java.util.List;

import com.google.common.io.Files;

public class State {
	private Command command;
	private String filenameBase;		///< Suffix-less filename
	private String commandMessage;
	public static final Object LPSCR_SUFFIX = "lpscr";
	public static final String SCXML_SUFFIX = "scxml";
	
	public State(Command command, String filename, String commandMessage)	{
		this.filenameBase = Files.getNameWithoutExtension(filename);
		this.command = command;
		this.commandMessage = commandMessage;
	}
	
	/**
	 * The command that created this state.
	 * @return The command
	 */
	public Command getCommand() {
		return command;
	}

	public String getSCXMLName() {
		return filenameBase + '.' + SCXML_SUFFIX;
	}

	/**
	 * Return message, displayed to user, about the result of executing the State's command.
	 * @return The text message for the user
	 */
	public String getCommandMessage() {
		return commandMessage;
	}

	public String getLPSCRName() {
		return filenameBase + '.' + LPSCR_SUFFIX;
	}
}
