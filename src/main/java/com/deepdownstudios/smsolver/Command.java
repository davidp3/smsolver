package com.deepdownstudios.smsolver;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.igormaznitsa.prologparser.terms.AbstractPrologTerm;
import com.igormaznitsa.prologparser.terms.PrologAtom;
import com.igormaznitsa.prologparser.terms.PrologStructure;

public class Command {
	public enum REPLCommand { LOAD, SAVE, NEW, CHANGE, SET, TEST, DELETE, UNDO, REDO };
	public enum Type { PARENT, SIMPLE, PAR, START, TERMINATE, EDGE, PROP, DEEP, SHALLOW };
	public static final List<String> keywords = getKeywords();

	// parameterless commands
	private static final SingleCommand UNDO_CMD = new SingleCommand(REPLCommand.UNDO, ImmutableList.<AbstractPrologTerm>of());
	private static final SingleCommand REDO_CMD = new SingleCommand(REPLCommand.REDO, ImmutableList.<AbstractPrologTerm>of());
	private static final SingleCommand SAVE_CMD = new SingleCommand(REPLCommand.SAVE, ImmutableList.<AbstractPrologTerm>of());
	private static final SingleCommand LOAD_CMD = new SingleCommand(REPLCommand.LOAD, ImmutableList.<AbstractPrologTerm>of());

	public static final Command NOOP = new Command(ImmutableList.<Command.SingleCommand>of());
	
	private List<SingleCommand> subcommands;

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
		private List<AbstractPrologTerm> parameters;
		public SingleCommand(REPLCommand replCommand, List<AbstractPrologTerm> parameters)	{
			this.replCommand = replCommand;
			this.parameters = parameters;
		}
		
		public static SingleCommand parseCommand(PrologStructure command) throws CommandException {
			REPLCommand replCommand = REPLCommand.valueOf(command.getFunctor().getText());
			if(replCommand == null)
				throw new CommandException("Unrecognized command: " + command.getFunctor().getText());
			List<AbstractPrologTerm> parameters = new ArrayList<AbstractPrologTerm>();
			for(int i=0; i<command.getArity(); ++i)	{
				parameters.add(command.getElement(i));
			}
			return new SingleCommand(replCommand, parameters);
		}
		
		public String toString()	{
			StringBuilder builder = new StringBuilder();
			builder.append(replCommand.toString());
			if(!parameters.isEmpty())	{
				builder.append('(');
				boolean isFirst = true;
				for(AbstractPrologTerm parameter : parameters)	{
					if(!isFirst)
						builder.append(", ");
					builder.append(parameter.getText());
					isFirst = false;
				}
				builder.append(");");
			}
			return builder.append('.').toString();
		}

		public List<AbstractPrologTerm> getParameters() {
			return parameters;
		}

		public REPLCommand getREPLCommand() {
			return replCommand;
		}
	}
	
	private Command(List<SingleCommand> commands)	{
		subcommands = commands;
	}
	
	public static Command build(List<PrologStructure> commandList) throws CommandException {
		List<SingleCommand> subcommands = new ArrayList<SingleCommand>();
		boolean foundMetaCommand = false;
		for(PrologStructure subcommandStruct : commandList)	{
			SingleCommand subcommand = SingleCommand.parseCommand(subcommandStruct);
			subcommands.add(subcommand);
		}
		
		if(foundMetaCommand && subcommands.size() > 1)
			throw new CommandException("BUG: Command.build() called with illegal compound command.");
		return new Command(subcommands);
	}

	public static Command build(PrologAtom atom) throws CommandException {
        // The following are the valid atoms: undo.  redo.  save.  load.
		if(REPLCommand.UNDO.toString().equals(atom))	{
			return new Command(ImmutableList.<Command.SingleCommand>of(UNDO_CMD));
		}
		if(REPLCommand.REDO.toString().equals(atom))	{
			return new Command(ImmutableList.<Command.SingleCommand>of(REDO_CMD));
		}
		if(REPLCommand.SAVE.toString().equals(atom))	{
			return new Command(ImmutableList.<Command.SingleCommand>of(SAVE_CMD));
		}
		if(REPLCommand.LOAD.toString().equals(atom))	{
			return new Command(ImmutableList.<Command.SingleCommand>of(LOAD_CMD));
		}
    	throw new CommandException("Unrecognized command: " + atom.getText());
	}

	public CommandResult execute(History history) throws CommandException {
		if(subcommands.size() == 1)	{
			switch(subcommands.get(0).getREPLCommand())	{
			case UNDO:	{
				History newHistory = history.undo();
				return new CommandResult(newHistory, 
						"Undo:\n" + newHistory.getCurrentState().getCommand().toString() + "\n\t" +
								newHistory.getCurrentState().getCommandMessage());
			}
			case REDO:	{
				History newHistory = history.undo();
				return new CommandResult(newHistory, 
						"Redo:\n" + newHistory.getCurrentState().getCommand().toString() + "\n\t" + 
								newHistory.getCurrentState().getCommandMessage());
			}
			case LOAD:	{
				ScxmlFile scxmlFile = ScxmlFile.load(history, subcommands.get(0));
				State state = new State(this, "Loaded SCXML File '" + scxmlFile.getFilename() + "'", scxmlFile);
				History newHistory = history.pushState(state);
				return new CommandResult(newHistory);
			}
			case SAVE:	{
				String filename = ScxmlFile.save(history, subcommands.get(0));
				return new CommandResult(history, "Saved " + filename);
			}
			case NEW:	{
				ScxmlFile scxmlFile = ScxmlFile.newState(subcommands.get(0));
				State newState = new State(this, "Created " + scxmlFile.getFilename(), scxmlFile);
				History newHistory = history.pushState(newState);
				return new CommandResult(newHistory);
			}
			default:
				break;		// was a single ASP command
			}
		}

		// Run Clingo and parse the result
		ScxmlFile scxmlFile = ClingoSolver.run(history.getCurrentState(), this);
		State newState = new State(this, "Success.", scxmlFile);
		return new CommandResult(history.pushState(newState));
	}

	public String toString() {
		StringBuilder builder = new StringBuilder();
		for(SingleCommand cmd : subcommands)	{
			builder.append("\t").append(cmd.toString()).append("\n");
		}
		return builder.toString();
	}

	public boolean isLoad() {
		return subcommands.size() == 1 && subcommands.get(0).getREPLCommand() == REPLCommand.LOAD; 
	}

	public boolean isNew() {
		return subcommands.size() == 1 && subcommands.get(0).getREPLCommand() == REPLCommand.NEW;
	}

	public boolean isSave() {
		return subcommands.size() == 1 && subcommands.get(0).getREPLCommand() == REPLCommand.SAVE;
	}

	public boolean isUndo() {
		return subcommands.size() == 1 && subcommands.get(0).getREPLCommand() == REPLCommand.UNDO;
	}

	public boolean isRedo() {
		return subcommands.size() == 1 && subcommands.get(0).getREPLCommand() == REPLCommand.REDO;
	}
}