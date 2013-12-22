package com.deepdownstudios.smsolver;

import java.util.List;

import com.google.common.io.Files;

public class State {
	private Command command;
	private String filenameBase;		///< Suffix-less filename
	private String commandMessage;
	
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
		return filenameBase + ".scxml";
	}

	/**
	 * Return message, displayed to user, about the result of executing the State's command.
	 * @return The text message for the user
	 */
	public String getCommandMessage() {
		return commandMessage;
	}
}
