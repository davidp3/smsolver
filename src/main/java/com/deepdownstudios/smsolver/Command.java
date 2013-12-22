package com.deepdownstudios.smsolver;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.UnmarshalException;
import javax.xml.bind.Unmarshaller;

import com.deepdownstudios.scxml.jaxb.ScxmlScxmlType;
import com.deepdownstudios.smsolver.History.HistoryException;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
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

	private static final String SCXML_TAG = "scxml";
	private static final String LPSCR_BLOCK_DELIMETER = "---";
	
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
			throw new CommandException("Internal error in Command()");
		return new Command(subcommands);
	}

	public static Command build(PrologAtom atom) throws CommandException {
        // The following are the valid atoms: undo.  redo.  save.  load.
		if(REPLCommand.UNDO.toString().equals(atom))	{
			return new Command(Arrays.asList(UNDO_CMD));
		}
		if(REPLCommand.REDO.toString().equals(atom))	{
			return new Command(Arrays.asList(REDO_CMD));
		}
		if(REPLCommand.SAVE.toString().equals(atom))	{
			return new Command(Arrays.asList(SAVE_CMD));
		}
		if(REPLCommand.LOAD.toString().equals(atom))	{
			return new Command(Arrays.asList(LOAD_CMD));
		}
    	throw new CommandException("Unrecognized command: " + atom.getText());
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
				History newHistory = history.pushState(load(history, subcommands.get(0)));
				return new CommandResult(newHistory);
			}
			case SAVE:	{
				String filename = save(history, subcommands.get(0));
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
		assert false;		// TODO:
		// 2. Send to clingo
		// 3. Wait for result
		// 4. Parse the clingo output to build a new state
		// 5. return the CommandResult
		return new CommandResult(history.pushState(newState));
	}

	private State load(History history, SingleCommand singleCommand) throws CommandException {
		String filename, 
			requestedName;		// For error messages
		File file;
		List<AbstractPrologTerm> parameters = singleCommand.getParameters();
		boolean asScxml;		// either scxml or lpscr
		if(parameters.isEmpty())	{
			filename = history.getCurrentState().getLPSCRName();
			requestedName = Files.getNameWithoutExtension(filename);
			asScxml = false;
			file = new File(filename);
			if(!file.isFile())	{
				// Try scxml
				filename = history.getCurrentState().getSCXMLName();
				file = new File(filename);
				asScxml = true;
			}
		}
		else if(parameters.size() == 1)	{
			filename = parameters.get(0).getText();
			String suffix = Files.getFileExtension(filename);
			if(!State.LPSCR_SUFFIX.equals(suffix) && !State.SCXML_SUFFIX.equals(suffix))	{
				filename = filename + '.' + State.LPSCR_SUFFIX;
				asScxml = false;
			}
			else
				asScxml = State.SCXML_SUFFIX.equals(suffix);
			file = new File(filename);
			requestedName = filename;
		}
		else
			throw new CommandException("Syntax error: '" + singleCommand.toString() + "'.  Format is load. or load(filename).");
		
		if(!file.isFile())
			throw new CommandException("File '" + requestedName + "' not found.");

		if(asScxml)
			return loadScxml(file);
		
		return loadLpscr(file);
	}

	private State loadLpscr(File file) throws CommandException {
		// Open file
		Scanner scanner;
		try {
			scanner = new Scanner(file);
		} catch (FileNotFoundException e) {
			throw new CommandException("File '" + file.getAbsolutePath() + "' was not found.");
		}
		
		try	{
			// First line is: 
			// scxml "filename"
			// (the quotation marks are present)
			String scxmlTag = scanner.next();
			if(!SCXML_TAG.equals(scxmlTag))
				throw new CommandException("scxml tag missing from file '" + file.getPath() + "'.");
			String scxmlFilename = scanner.nextLine();		// presumably, this skips the word we already read
			// Filename is everything between single-quotes
			scxmlFilename = scxmlFilename.substring(scxmlFilename.indexOf('\''), scxmlFilename.lastIndexOf('\''));
			State newState = loadScxml(new File(scxmlFilename));
			// The rest of the lines are lpscr command blocks, broken into groups, each 
			// followed by lines that are just three dashes (ie '---'), including the final block of commands.
			// Issue each block as a Command to build a history
			History fakeHistory = new History(ImmutableList.<State>of(newState), 0);
			assert newState != null;
			while(scanner.hasNext())	{
				StringBuffer commandStrBuf = new StringBuffer();
				boolean done = false;
				while(!done)	{
					String nextLine = scanner.nextLine();
					if(nextLine.equals(LPSCR_BLOCK_DELIMETER))
						break;		// read entire block of commands
					commandStrBuf.append(nextLine);
				}
				String commandStr = commandStrBuf.toString();
				Commands commands = Commands.parse(commandStr);
				CommandResult result = commands.execute(fakeHistory);
				fakeHistory = result.getHistory();
			}
			return fakeHistory.getCurrentState();
		} finally {
			scanner.close();
		}
	}

	private State loadScxml(File file) throws CommandException {
		JAXBContext context;
		try {
			context = JAXBContext.newInstance("com.deepdownstudios.scxml.jaxb");
		} catch (JAXBException e) {
			throw new CommandException("Internal error: JAXB was unable to initialize namespace 'com.deepdownstudios.scxml.jaxb'", e);
		}
		
		Unmarshaller unmarshaller;
		try {
			unmarshaller = context.createUnmarshaller();
		} catch (JAXBException e) {
			throw new CommandException("Internal error: Could not create SCXML JAXB unmarshaller.", e);
		}
		
		ScxmlScxmlType scxml;
		try {
			scxml = (ScxmlScxmlType)unmarshaller.unmarshal(file);
		} catch (UnmarshalException e) {
			throw new CommandException("'" + file.getAbsolutePath() + "' is not a valid SCXML file.", e);
		} catch (JAXBException e) {
			throw new CommandException("Internal error: Could not create SCXML JAXB unmarshaller.", e);
		}
		return new State(this, file.getPath(), "Loaded SCXML File '" + file.getAbsolutePath() + "'", scxml);
	}

	private String save(History history, SingleCommand singleCommand) throws CommandException {
		String filename;
		List<AbstractPrologTerm> parameters = singleCommand.getParameters();
		boolean asScxml;		// either scxml or lpscr
		if(parameters.isEmpty())	{
			filename = history.getCurrentState().getLPSCRName();
			asScxml = false;
		}
		else if(parameters.size() == 1)	{
			filename = parameters.get(0).getText();
			String suffix = Files.getFileExtension(filename);
			if(!State.LPSCR_SUFFIX.equals(suffix) && !State.SCXML_SUFFIX.equals(suffix))	{
				filename = filename + '.' + State.LPSCR_SUFFIX;
				asScxml = false;
			}
			else
				asScxml = State.SCXML_SUFFIX.equals(suffix);
		}
		else
			throw new CommandException("Syntax error: '" + singleCommand.toString() + "'.  Format is save. or save(filename).");
		
		assert false;		// TODO:
		return filename;
	}

	private State newState(SingleCommand singleCommand) throws CommandException {
		assert singleCommand.getREPLCommand() == REPLCommand.NEW;
		if(singleCommand.getParameters().size() != 1)
			throw new CommandException("Syntax error in '" + singleCommand.toString() + "'.  Expected format: new(filename)");

		String filename = singleCommand.getParameters().get(0).getText();
		String ext = Files.getFileExtension(filename).toLowerCase(); 
		if(!ext.equals(State.LPSCR_SUFFIX))	{
			// strip .lpscr extension if present
			filename = Files.getNameWithoutExtension(filename);
		}
		if(!ext.equals(State.SCXML_SUFFIX))	{
			// add .scxml extension if not present
			filename = filename + "." + State.SCXML_SUFFIX;
		}
		return new State(this, filename, "Created " + filename);
	}

	public String toString() {
		StringBuilder builder = new StringBuilder();
		for(SingleCommand cmd : subcommands)	{
			builder.append("\t").append(cmd.toString()).append("\n");
		}
		return builder.toString();
	}
}