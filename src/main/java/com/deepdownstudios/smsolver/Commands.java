package com.deepdownstudios.smsolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import alice.tuprolog.InvalidTermException;
import alice.tuprolog.Parser;
import alice.tuprolog.Struct;
import alice.tuprolog.Term;

public class Commands {
	private static final Commands EMPTY_COMMANDS = new Commands();

	private ArrayList<Command> subcommands = new ArrayList<Command>();

	private Commands() {
	} // noop. subcommands is empty

	/**
	 * Build subcommands from code.
	 * 
	 * @param commandStr
	 *            A period-separated list of LP/PROLOG-style commands.
	 * @throws CommandException
	 */
	private Commands(String commandStr) throws CommandException {
		assert commandStr.trim().length() > 0;

		Parser parser = new Parser(commandStr);
		List<Struct> curCompoundCommandList = new ArrayList<Struct>();
		try {
			Term term = parser.nextTerm(true); // true means 'input must end in period'
			while (term != null) {
				// We want to put all sequential non-file commands together in one Command structure.
				// File commands are put in their own Command alone.
				if (term.isCompound()) {
					// Most commands will be a prolog structure (ie
					// load("himom"), set(simple(mystate)), etc)
					Struct commandStruct = (Struct) term;
					String functor = commandStruct.getName().toString();
					if (isMetaCommand(functor)) {
						if (!curCompoundCommandList.isEmpty()) {
							// Make a Command object from all of the commands leading up to this one
							subcommands.add(Command.build(curCompoundCommandList));
							curCompoundCommandList.clear();
						}
						subcommands.add(Command.build(Arrays.asList(commandStruct)));
					} else	{
						curCompoundCommandList.add(commandStruct);
					}
				} else if (term.isAtom()) {
					// The following are the valid atoms: undo. redo. save. load.
					// They are all meta-commands and should not be lumped in
					// curCompoundCommandList
					if (!curCompoundCommandList.isEmpty()) {
						// Make a Command object from all of the commands leading up to this one
						subcommands.add(Command.build(curCompoundCommandList));
						curCompoundCommandList.clear();
					}
					subcommands.add(Command.build((Struct) term));
				} else {
					throw new CommandException("Syntax error.  Command is neither ATOM nor STRUCTURE: "
							+ term.toString());
				}

				term = parser.nextTerm(true);
			}
		} catch (InvalidTermException e) {
			throw new CommandException("Syntax error: " + e.getMessage(), e);
		}

		// If there are leftover commands in the curCompoundCommandList then
		// make a command for them too.
		if (!curCompoundCommandList.isEmpty())
			subcommands.add(Command.build(curCompoundCommandList));
	}

	/**
	 * True iff cmd describes a meta-interpreter command (ie non-ASP command).
	 * They are load, save, new, undo and redo.
	 * 
	 * @throws CommandException
	 */
	private static boolean isMetaCommand(String cmd) throws CommandException {
		Command.REPLCommand replCommand;
		try {
			replCommand = Command.REPLCommand.valueOfIgnoreCase(cmd);
		} catch (IllegalArgumentException e) {
			throw new CommandException("Unknown command: " + cmd);
		}
		assert (replCommand != null);
		return (replCommand == Command.REPLCommand.UNDO || replCommand == Command.REPLCommand.REDO
				|| replCommand == Command.REPLCommand.LOAD || replCommand == Command.REPLCommand.SAVE || replCommand == Command.REPLCommand.NEW);
	}

	/**
	 * Parse the string into a command object.
	 * 
	 * @param commandStr
	 *            The LP/PROLOG-formatted command statement list. Commands end
	 *            in periods. '%' Comments are permitted.
	 * @return The command object or null if commandStr was 'empty' (ie nothing
	 *         but whitespace)
	 * @throws CommandException
	 *             If the string could not be parsed
	 */
	public static Commands parse(String commandStr) throws CommandException {
		if (commandStr.trim().length() == 0)
			return EMPTY_COMMANDS;
		return new Commands(commandStr);
	}

	/**
	 * Execute this command on the current state in history. Returns the result,
	 * which includes the new history that results from running the command.
	 * 
	 * @param history
	 *            The history to run the command on.
	 * @return
	 * @throws CommandException
	 */
	public CommandResult execute(History history) throws CommandException {
		if (subcommands.isEmpty())
			return new CommandResult(history, "");

		CommandResult result = null;
		for (Command subcommand : subcommands) {
			result = subcommand.execute(history);
			history = result.getHistory();
		}
		return result;
	}
}
