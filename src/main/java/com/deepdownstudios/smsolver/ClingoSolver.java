package com.deepdownstudios.smsolver;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import alice.tuprolog.InvalidTermException;
import alice.tuprolog.Parser;
import alice.tuprolog.Term;

import com.deepdownstudios.scxml.jaxb.ScxmlScxmlType;
import com.google.common.io.Files;

public class ClingoSolver {
	private static final String CLINGO_UNSATISFIABLE = "UNSATISFIABLE";
	private static final String CLINGO_SATISFIABLE = "SATISFIABLE";
	private static final String CLINGO_UNKNOWN = "UNKNOWN";
	private static final String ENGINE_RESOURCE_NAME = "/engine.lp";
	private static final String CLINGO_ANSWER_TAG = "Answer: ";
	private static String engineCode = getLpscrEngineCode();
	
	/**
	 * Run 'command' on 'state' using clingo.
	 * @param state		The state to use as clingo input
	 * @param command	The commands to execute on the state
	 * @return			The result as a new SCXML model
	 * @throws CommandException
	 */
	public static ScxmlFile run(State state, Command command) throws CommandException {
		// Build the ASP payload
		String aspPayload = buildAspPayload(state, command);
		
		// Send to clingo and get the result.
		String clingoResult = runClingo(aspPayload);

		// Parse the clingo output to build a new state
		String filename = state.getScxmlFile().getFilename();
		String statemachineName = state.getScxmlFile().getScxml().getName();
		assert statemachineName != null;
		ScxmlScxmlType newScxmlDocument = parseClingoResult(statemachineName, clingoResult);
		return new ScxmlFile(filename, newScxmlDocument);
	}

	private static String buildAspPayload(State state, Command command) throws CommandException {
		// First, add the engine and any user functions
		StringBuilder ret = new StringBuilder(engineCode);
		// Then, add the SCXML document from state
		List<Term> terms = ScxmlToProlog.scxmlToProlog(state.getScxmlFile().getScxml());
		for(Term term : terms)
			ret.append(term.toString()).append(".\n");
		// Then add the commands
		ret.append(command.toString());
		return ret.toString();
	}

	/**
	 * Run clingo in a separate process and return (only) the portion of the output that includes the new model.
	 * @param aspPayload	Model given to clingo as String
	 * @return			The resultant model as PROLOG/LP terms on one line, space-separated.  Example: 
	 * 			simple(top_state) start(publisher_start) terminate(publisher_end) deep(publisher_deep_hist) simple(app_splash) simple(publisher_splash)
	 * @throws CommandException		If there was an internal error, I/O error or if the model had no solution.
	 */
	private static String runClingo(String aspPayload) throws CommandException {
		ProcessBuilder procBuilder = new ProcessBuilder("clingo");
		BufferedOutputStream clingoInput = null;
		BufferedReader clingoOutput = null;
		try {
			Process proc = procBuilder.start();
			clingoInput = new BufferedOutputStream(proc.getOutputStream());
			clingoOutput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			clingoOutput.readLine();		// Read the clingo banner
			clingoInput.write(aspPayload.getBytes());
			// The internet was not helpful here.  However, with groovysh I was able to learn that
			// the (only?) way to send the EOF is to close the output stream. 
			clingoInput.close();
			
			// Clingo output is not easily parsed.  However, docs say result is always before the
			// SATISFIED label and after the "Answer: #" line.  Actual result seems to always be 
			// one line (unlike in the docs)
			String line = clingoOutput.readLine();
			boolean lastLineWasAnswerTag=false;
			while(line != null)	{
				if(line.startsWith(CLINGO_ANSWER_TAG))
					lastLineWasAnswerTag = true;
				else if(line.equals(CLINGO_SATISFIABLE))
					throw new CommandException("BUG: Clingo reported SATISFIABLE but provided no answer.");
				else if(line.equals(CLINGO_UNSATISFIABLE))
					throw new CommandException("The state machine commands were not satisfiable.");
				else if(line.equals(CLINGO_UNKNOWN))
					throw new CommandException("BUG: Clingo was interrupted.  I think this happens when the input has a syntax error.");
				else if(lastLineWasAnswerTag && !line.isEmpty())
					return line;
				line = clingoOutput.readLine();
			}
			throw new CommandException("BUG: Clingo output should include either SATISFIABLE, UNSATISFIABLE or UNKNOWN.");
		} catch (IOException e) {
			throw new CommandException("I/O error while communicating with clingo: " + e.getMessage());
		} finally {
			if(clingoInput != null)	{
				try {
					clingoInput.close();
				} catch (IOException e) {
					throw new CommandException("BUG: Could not close clingoInput.");
				}
			}
			if(clingoOutput != null)	{
				try {
					clingoOutput.close();
				} catch (IOException e) {
					throw new CommandException("BUG: Could not close clingoOutput.");
				}
			}
		}
	}
	
	private static ScxmlScxmlType parseClingoResult(String name, String clingoResult) throws CommandException {
		// UPDATE: I wonder if changing from PrologParser to TUProlog means that I dont need this
		// anymore.  TUProlog should be able to parse clingo output, which is separated by spaces,
		// on its own (see parser.nextTerm(boolean)'s parameter below).
		// However, I dont see how to get a list of results.
		// The regex matches based on balancing parenthesis while respecting quotation marks.  Clingo
		// output has no comments.
		// Match commands in possible compound-command-sequence.
		final String REGEX = "([^\"'\\s]|\"\"|''|(\".*?[^\\\\]\")|('.*?[^\\\\]'))+(\\s|$)";
		// ([^\"'\\s]|\"\"|''|(\".*?[^\\\\]\")|('.*?[^\\\\]'))*\\s breakdown:
		// [^\"'\\s] - match any single character that isn't a quotation mark (single or double) or whitespace
		// \"\"|'' - match empty single or double quotes
		// \".*?[^\\\\]\" - match double quotes as long as the second quote isn't preceeded by backslash
		// '.*?[^\\\\]' - same thing but for single quotes
		// ([^\"'\\s]|\"\"|''|(\".*?[^\\\\]\")|('.*?[^\\\\]'))+ - match any non-empty sequence of the above four conditions
		// ([^\"'\\s]|\"\"|''|(\".*?[^\\\\]\")|('.*?[^\\\\]'))+(\\s|$) - match previous up to whitespace or EOL
		List<Term> terms = new ArrayList<Term>();
		Pattern p = Pattern.compile(REGEX);
		Matcher matcher = p.matcher(clingoResult);
		while(matcher.find())   {
			// group 0 is the top-level group (where levels are defined by nested () and 
			// level 0 is the whole thing).  Prolog sentences must end in a period.
			String termStr = matcher.group(0)+'.';
			try {
				Parser parser = new Parser(termStr);
				Term term = parser.nextTerm(true);
				terms.add(term);
				if(parser.nextTerm(true) != null)
					throw new CommandException("BUG: Leftovers in string while parsing clingo regex output: '" + termStr + "'.");
			} catch (InvalidTermException e) {
				throw new CommandException("BUG: Prolog error parsing clingo output: '" + termStr + "'.");
			}
		}
	
		return PrologToScxml.prologToScxml(name, terms);
	}
	
	private static String getLpscrEngineCode() {
		InputStream stream = ClingoSolver.class.getResourceAsStream(ENGINE_RESOURCE_NAME);
		if(stream == null)
			throw new RuntimeException("BUG: Could not find LPSCR engine resource file '" + ENGINE_RESOURCE_NAME + "'.");

		BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
		StringBuilder ret = new StringBuilder();
		String line;
		try {
			line = reader.readLine();
			while(line != null)	{
				ret.append(line).append('\n');
				line = reader.readLine();
			}
		} catch (IOException e) {
			throw new RuntimeException("I/O error while reading LPSCR engine code from '"+ENGINE_RESOURCE_NAME+"'.");
		}
		return ret.toString();
	}
}
