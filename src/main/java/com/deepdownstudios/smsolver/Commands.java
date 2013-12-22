package com.deepdownstudios.smsolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Commands {
	private ArrayList<Command> subcommands = new ArrayList<Command>();

	public Commands(String commandStr) throws CommandException {
		/*
		 * Apparently, this is supposed to be done automatically by Pattern.COMMENTS
		// Remove the comments from the commandStr
		int hashtagIdx = commandStr.indexOf('#');
		while(hashtagIdx != -1)	{
			int newlineIdx = commandStr.indexOf('\n', hashtagIdx);
			String commandCopy = commandStr;
			commandStr = commandCopy.substring(0, hashtagIdx);
			if(newlineIdx != -1)
				commandStr = commandStr + commandCopy.substring(newlineIdx+1); 
			hashtagIdx = commandStr.indexOf('#');
		}
		*/
		
		List<String> curCompoundCommandList = new ArrayList<String>();
		
		// Match commands in possible compound-command-sequence.
		// ([^\";]|(\".*?\"))*; breakdown:
		// [^\";] - match any single character that isn't a quotation mark or a semicolon
		// (\".*?\") - match a quotation mark, followed by anything up to the next quotation mark
		// ([^\";]|(\".*?\"))* - match any combination of zero or more of the first two matchers
		// ([^\";]|(\".*?\"))*\\.- match previous up to a period
		// NOTE: Pattern.COMMENTS skips white-space as well as hash-tag-style line comments
		Pattern p = Pattern.compile("([^\";]|(\".*?\"))*\\.", Pattern.COMMENTS);
		Matcher matcher = p.matcher(commandStr);
		while(matcher.find())	{
			// group 0 is the top-level group (where levels are defined by nested () and 
			// level 0 is the whole thing)
			String singleCommandStr = matcher.group(0);
			
			// We want to put all sequential non-file commands together in one Command structure.
			// File commands are put in their own Command alone.
			if(isMetaCommand(singleCommandStr) && !curCompoundCommandList.isEmpty())	{
				// Make a Command object from all of the commands leading up to this one
				subcommands.add(new Command(curCompoundCommandList));
				curCompoundCommandList.clear();
				subcommands.add(new Command(Arrays.asList(singleCommandStr)));
			}
			else
				curCompoundCommandList.add(singleCommandStr);
		}
		
		if(matcher.end() < commandStr.length()-1)	{
			String theRest = commandStr.substring(matcher.end()+1);
			if(!theRest.trim().isEmpty())	{
				throw new CommandException("Unable to parse command at: " + theRest);
			}
		}
		
		// If there are leftover commands in the curCompoundCommandList then make a command for them too.
		if(!curCompoundCommandList.isEmpty())
			subcommands.add(new Command(curCompoundCommandList));
	}

	/**
	 * True iff cmd describes a meta-interpreter command (ie non-ASP command).  They are load, save, new, undo and redo.
	 */
	private static boolean isMetaCommand(String cmd) {
//		Pattern p = Pattern.compile("^\\s*(load|save|new)\\s*\\(", Pattern.COMMENTS);		// \\s are superfluous since Pattern.COMMENTS
		Pattern p = Pattern.compile("^\\s*(load|save|new|undo|redo)", Pattern.COMMENTS);		// \\s are superfluous since Pattern.COMMENTS
		Matcher matcher = p.matcher(cmd);
		if(matcher.find())
			return true;
		return false;
	}

	public static Commands parse(String commandStr) throws CommandException {
		return new Commands(commandStr);
	}

	public CommandResult execute(History history) throws CommandException {
		if(subcommands.isEmpty())
			return new CommandResult(history);

		CommandResult result = null;
		for(Command subcommand : subcommands) {
			result = subcommand.execute(history);
			history = result.getHistory();
		}
		return result;
	}
}
