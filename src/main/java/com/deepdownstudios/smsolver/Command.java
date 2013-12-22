package com.deepdownstudios.smsolver;

import java.util.ArrayList;
import java.util.List;

import com.google.common.io.Files;

public class Command {
	private enum REPLCommand { LOAD, SAVE, NEW, CHANGE, SET, TEST, DELETE, UNDO, REDO };
	private enum Type { PARENT, SIMPLE, PAR, START, TERMINATE, EDGE, PROP, DEEP, SHALLOW };
	public static final List<String> keywords = getKeywords();

	private static final String SCXML_SUFFIX = "scxml";
	private static final Object LPSCR_SUFFIX = "lpscr";
	
	private List<SingleCommand> subcommands = new ArrayList<SingleCommand>();

	private static List<String> getKeywords() {
		List<String> ret = new ArrayList<String>();
		for(REPLCommand cmd : REPLCommand.values())
			ret.add(cmd.toString().toLowerCase());
		for(Type type : Type.values())
			ret.add(type.toString().toLowerCase());
		return ret;
	}
	
	public static class SingleCommand	{
		private REPLCommand replCommand;
		private List<String> parameters;
		public SingleCommand(REPLCommand replCommand, List<String> parameters)	{
			this.replCommand = replCommand;
			this.parameters = parameters;
		}
		
		public static SingleCommand parseCommand(String command) {
			REPLCommand replCommand = null;
			List<String> parameters = null;
			assert(false);		// TODO:
			return new SingleCommand(replCommand, parameters);
		}
		
		public String toString()	{
			StringBuilder builder = new StringBuilder();
			builder.append(replCommand.toString()).append("(");
			boolean isFirst = true;
			for(String parameter : parameters)	{
				if(!isFirst)
					builder.append(", ");
				builder.append(parameter);
				isFirst = false;
			}
			builder.append(");");
			return builder.toString();
		}

		public List<String> getParameters() {
			return parameters;
		}

		public REPLCommand getREPLCommand() {
			return replCommand;
		}
	}
	
	public Command(List<String> commandList) throws CommandException {
		boolean foundMetaCommand = false;
		for(String subcommandStr : commandList)	{
			SingleCommand subcommand = SingleCommand.parseCommand(subcommandStr);
			subcommands.add(subcommand);
		}
		
		if(foundMetaCommand && subcommands.size() > 1)
			throw new CommandException("Internal error in Command()");
	}

	public CommandResult execute(History history) {
		if(subcommands.size() == 1)	{
			switch(subcommands.get(0).getREPLCommand())	{
			case UNDO:	{
				History newHistory = history.undo();
				return new CommandResult(newHistory, "Undo:\n" + newHistory.getCurrentState().getCommand().toString() + "\n\t" + newHistory.getCurrentState().getCommandMessage());
			}
			case REDO:	{
				History newHistory = history.undo();
				return new CommandResult(newHistory, "Redo:\n" + newHistory.getCurrentState().getCommand().toString() + "\n\t" + newHistory.getCurrentState().getCommandMessage());
			}
			case LOAD:	{
				// TODO: SCXML vs lpscr
				History newHistory = history.pushState(load(subcommands.get(0)));
				return new CommandResult(newHistory);
			}
			case SAVE:	{
				// TODO: SCXML vs lpscr
				String filename = save(subcommands.get(0));
				return new CommandResult(history, "Saved " + filename);
			}
			case NEW:	{
				History newHistory = history.pushState(newState(subcommands.get(0)));
				return new CommandResult(newHistory);
			}
			default:
				break;
			}
		}
		// 1. Build the ASP payload
		// 2. Send to clingo
		// 3. Wait for result
		// 4. Parse the clingo output to build a new state
		// 5. return the CommandResult
		return new CommandResult(history.pushState(newState));
	}

	private State newState(SingleCommand singleCommand) throws CommandException {
		assert singleCommand.getREPLCommand() == REPLCommand.NEW;
		try	{
			if(singleCommand.getParameters().size() != 1)
				throw new Exception();		// caught below
			String filename = singleCommand.getParameters().get(0);
			if(!Files.getFileExtension(filename).toLowerCase().equals(LPSCR_SUFFIX))	{
				// strip .lpscr extension if present
				filename = Files.getNameWithoutExtension(filename);
			}
			if(!Files.getFileExtension(filename).toLowerCase().equals(SCXML_SUFFIX))	{
				// add .scxml extension if not present
				filename = filename + "." + SCXML_SUFFIX;
			}
			return new State(this, filename, "Created " + filename);
		} catch(Exception e)	{
			throw new CommandException("Invalid 'new' command.  Format: new(filename)");
		}
	}

	public String toString() {
		StringBuilder builder = new StringBuilder();
		for(SingleCommand cmd : subcommands)	{
			builder.append("\t").append(cmd.toString()).append("\n");
		}
		return builder.toString();
	}
}