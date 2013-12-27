package com.deepdownstudios.smsolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.igormaznitsa.prologparser.PrologParser;
import com.igormaznitsa.prologparser.exceptions.PrologParserException;
import com.igormaznitsa.prologparser.terms.AbstractPrologTerm;
import com.igormaznitsa.prologparser.terms.PrologAtom;
import com.igormaznitsa.prologparser.terms.PrologStructure;
import com.igormaznitsa.prologparser.terms.PrologTermType;

public class Commands {
	private static final Commands EMPTY_COMMANDS = new Commands();
	
	private ArrayList<Command> subcommands = new ArrayList<Command>();

	private Commands() {	}		// noop.  subcommands is empty

	/**
	 * Build subcommands from code.
	 * @param commandStr	A period-separated list of LP/PROLOG-style commands.
	 * @throws CommandException
	 */
	private Commands(String commandStr) throws CommandException {
		assert commandStr.trim().length() > 0;
		
		PrologParser parser = new PrologParser(null);
		AbstractPrologTerm term;
		term = nextTerm(parser, commandStr);
		
		List<PrologStructure> curCompoundCommandList = new ArrayList<PrologStructure>();
		
		while(term != null)	{
			// We want to put all sequential non-file commands together in one Command structure.
			// File commands are put in their own Command alone.
			switch(term.getType())	{
			case STRUCT:	{
				// Most commands will be a prolog structure (ie load("himom"), set(simple(mystate)), etc)
		        PrologStructure commandStruct = (PrologStructure) term;
		        String functor = commandStruct.getFunctor().getText();
		        if(PrologTermType.ATOM != commandStruct.getFunctor().getType())	{
		        	throw new CommandException("Functor '" + functor + "' in command " + term.getText() + " must be a valid LP atom.");
		        }
		        
		        if(isMetaCommand(functor))	{
		        	if(!curCompoundCommandList.isEmpty())	{
						// Make a Command object from all of the commands leading up to this one
						subcommands.add(Command.build(curCompoundCommandList));
						curCompoundCommandList.clear();
		        	}
					subcommands.add(Command.build(Arrays.asList(commandStruct)));
				}
				else
					curCompoundCommandList.add(commandStruct);
				break;
			}
			case ATOM: {
		        // The following are the valid atoms: undo.  redo.  save.  load.
				// They are all meta-commands and should not be lumped in curCompoundCommandList
				subcommands.add(Command.build((PrologAtom)term));
				break;
			}
			default:
				throw new CommandException("Syntax error.  Command is neither ATOM nor STRUCTURE: " + term.getText());
			}

			term = nextTerm(parser, null);
		}
		
		// If there are leftover commands in the curCompoundCommandList then make a command for them too.
		if(!curCompoundCommandList.isEmpty())
			subcommands.add(Command.build(curCompoundCommandList));
	}

	/**
	 * Get the next PROLOG term from the commandStr.  The first call to this function should pass commandStr.
	 * Subsequent calls should pass null for commandStr.
	 * @param parser		The PrologParser
	 * @param commandStr	The string holding the list of PROLOG commands or null if the function has already been called with it.
	 * @return				The Prolog term or NULL if no more exist in the string
	 * @throws CommandException		A syntax error was found (or docs say an I/O error but I believe thats impossible as there is no I/O)
	 */
	private static AbstractPrologTerm nextTerm(PrologParser parser, String commandStr) throws CommandException {
		try {
			if(commandStr != null)
				return parser.nextSentence(commandStr);
			else
				return parser.nextSentence();
		} catch (IOException e) {
			throw new CommandException("Internal error in Commands().\n\t" + e.getMessage());
		} catch (PrologParserException e) {
			throw new CommandException("Syntax error: " + e.getMessage());
		}
	}

	/**
	 * True iff cmd describes a meta-interpreter command (ie non-ASP command).  They are load, save, new, undo and redo.
	 * @throws CommandException 
	 */
	private static boolean isMetaCommand(String cmd) throws CommandException {
		Command.REPLCommand replCommand;
		try	{
			replCommand = Command.REPLCommand.valueOfIgnoreCase(cmd);
		} catch(IllegalArgumentException e)	{
			throw new CommandException("Unknown command: " + cmd);
		}
		assert(replCommand != null);
		return (replCommand == Command.REPLCommand.UNDO || replCommand == Command.REPLCommand.REDO ||
				replCommand == Command.REPLCommand.LOAD || replCommand == Command.REPLCommand.SAVE ||
				replCommand == Command.REPLCommand.NEW);
	}

	/**
	 * Parse the string into a command object.
	 * @param commandStr	The LP/PROLOG-formatted command statement list.  Commands end in periods.  '%' Comments are permitted.
	 * @return				The command object or null if commandStr was 'empty' (ie nothing but whitespace)
	 * @throws CommandException		If the string could not be parsed
	 */
	public static Commands parse(String commandStr) throws CommandException {
		if(commandStr.trim().length() == 0)
			return EMPTY_COMMANDS;
		return new Commands(commandStr);
	}

	/**
	 * Execute this command on the current state in history.  Returns the result, which includes the new history that
	 * results from running the command.
	 * @param history		The history to run the command on.
	 * @return				
	 * @throws CommandException
	 */
	public CommandResult execute(History history) throws CommandException {
		if(subcommands.isEmpty())
			return new CommandResult(history, "");

		CommandResult result = null;
		for(Command subcommand : subcommands) {
			result = subcommand.execute(history);
			history = result.getHistory();
		}
		return result;
	}
}
