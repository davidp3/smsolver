package com.deepdownstudios.smsolver;

import java.util.List;

import com.deepdownstudios.scxml.jaxb.ScxmlScxmlType;
import com.google.common.io.Files;

public class State {
	private Command command;
	private String filenameBase;		///< Suffix-less filename
	private String commandMessage;
	public static final Object LPSCR_SUFFIX = "lpscr";
	public static final String SCXML_SUFFIX = "scxml";
	private ScxmlScxmlType scxml;
	
	/**
	 * Create a State with an empty SCXML file.
	 * @param command		Command that created the state.  Currently always 'new(filename).'
	 * @param filename		Name of SCXML file to create/overwrite.
	 * @param commandMessage	Result message for user from running command.
	 */
	public State(Command command, String filename, String commandMessage)	{
		this(command, filename, commandMessage, getNewScxmlDocument(Files.getNameWithoutExtension(filename)));
	}
	
	public State(Command command, String filename, String commandMessage, ScxmlScxmlType scxml)	{
		assert filename != null && command != null && commandMessage != null && scxml != null;
		this.filenameBase = Files.getNameWithoutExtension(filename);
		this.command = command;
		this.commandMessage = commandMessage;
		this.scxml = scxml;
	}
	
	private static ScxmlScxmlType getNewScxmlDocument(String name)	{
		ScxmlScxmlType ret = new ScxmlScxmlType();
		ret.setName(name);
		// TODO: Technically should have at least one state, parallel or final child but I wont
		// tell if JAXB2 wont.
		return ret;
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
