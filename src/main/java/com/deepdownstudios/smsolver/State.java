package com.deepdownstudios.smsolver;


public class State {
	private Command command;
	private String commandMessage;
	private ScxmlFile scxmlFile;
	
	/**
	 * Create a State with an empty statechart.
	 * @param command		Command that created the state.  Currently always 'new(filename).'
	 * @param filename		Name of file to create/overwrite.
	 * @param commandMessage	Result message for user from running command.
	 */
	public State(Command command, String commandMessage, String filename)	{
		this(command, commandMessage, new ScxmlFile(filename));
	}
	
	/**
	 * @param command
	 * @param filename		Name of the file that is associated with this scxml document.  Must include either .scxml or .lpscr suffix.
	 * @param commandMessage
	 * @param scxml
	 */
	public State(Command command, String commandMessage, ScxmlFile scxmlFile)	{
		assert command != null && commandMessage != null && scxmlFile != null;
		this.command = command;
		this.commandMessage = commandMessage;
		this.scxmlFile = scxmlFile;
	}
	
	/**
	 * The command that created this state.
	 * @return The command
	 */
	public Command getCommand() {
		return command;
	}

	/**
	 * The message, displayed to user, about the result of executing the State's command.
	 */
	public String getCommandMessage() {
		return commandMessage;
	}

	public ScxmlFile getScxmlFile() {
		return scxmlFile;
	}
}
