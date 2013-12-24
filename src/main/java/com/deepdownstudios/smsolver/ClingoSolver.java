package com.deepdownstudios.smsolver;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.deepdownstudios.scxml.jaxb.ScxmlScxmlType;
import com.google.common.io.Files;
import com.igormaznitsa.prologparser.PrologParser;
import com.igormaznitsa.prologparser.exceptions.PrologParserException;
import com.igormaznitsa.prologparser.terms.AbstractPrologTerm;

public class ClingoSolver {
	private static final String CLINGO_UNSATISFIABLE = "UNSATISFIABLE";
	private static final String CLINGO_SATISFIABLE = "SATISFIABLE";
	private static final String ENGINE_RESOURCE_NAME = "engine.lp";
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
		ScxmlScxmlType newScxmlDocument = parseClingoResult(Files.getNameWithoutExtension(filename), clingoResult);
		return new ScxmlFile(filename, newScxmlDocument);
	}

	private static String buildAspPayload(State state, Command command) {
		// First, add the engine and any user functions
		StringBuilder ret = new StringBuilder(engineCode);
		// Then, add the SCXML document from state
		List<AbstractPrologTerm> terms = scxmlToProlog(state.getScxmlFile().getScxml());
		for(AbstractPrologTerm term : terms)
			ret.append(term.getText()).append(".\n");
		// Then add the commands
		ret.append(command.toString());
		return ret.toString();
	}

	private static String getLpscrEngineCode() {
		InputStream stream = ClingoSolver.class.getResourceAsStream(ENGINE_RESOURCE_NAME);
		if(stream == null)
			throw new RuntimeException("Could not find LPSCR engine resource file '" + ENGINE_RESOURCE_NAME + "'.");

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
			
			// Clingo output is not easily parsed.  However,
			// all lines except the actual result line (seems there is only one) start with a Capital Letter.
			String line = clingoOutput.readLine();
			String ret = null;
			// Clingo output should include either SATISFIABLE or UNSATISFIABLE
			boolean gotSatisfiable=false, gotUnsatisfiable=false;
			while(line != null)	{
				if(line.equals(CLINGO_SATISFIABLE))
					gotSatisfiable = true;
				if(line.equals(CLINGO_UNSATISFIABLE))
					gotUnsatisfiable = true;
				if(line.isEmpty() || Character.isUpperCase(line.charAt(0)))
					continue;
				ret = line;
				// Results are all on one line so this is really superfluous but I'm being diligent.
				line = clingoOutput.readLine();
			}
			clingoOutput.close();
			if(gotSatisfiable == gotUnsatisfiable)
				throw new CommandException("BUG: Clingo response was not understood.");
			if(gotUnsatisfiable)
				throw new CommandException("The state machine commands were not satisfiable.");
			assert ret != null;
			return ret;
		} catch (IOException e) {
			throw new CommandException("I/O error while communicating with clingo.");
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
		// The regex matches based on balancing parenthesis while respecting quotation marks.  Clingo
		// output has no comments.
		// Match commands in possible compound-command-sequence.
		// ([^\"\\s]|(\".*?\"))*\\s breakdown:
		// [^\"\\s] - match any single character that isn't a quotation mark or whitespace
		// (\".*?\") - match a quotation mark, followed by anything up to the next quotation mark
		// ([^\";]|(\".*?\"))* - match any combination of zero or more of the first two matchers
		// ([^\"\\s]|(\".*?\"))*\\s - match previous up to whitespace
		PrologParser parser = new PrologParser(null);
		List<AbstractPrologTerm> terms = new ArrayList<AbstractPrologTerm>();
		Pattern p = Pattern.compile("([^\";]|(\".*?\"))*\\.");
		Matcher matcher = p.matcher(clingoResult);
		while(matcher.find())   {
			// group 0 is the top-level group (where levels are defined by nested () and 
			// level 0 is the whole thing)
			String termStr = matcher.group(0);
			try {
				AbstractPrologTerm term = parser.nextSentence(termStr);
				terms.add(term);
				if(parser.nextSentence() != null)
					throw new CommandException("BUG: Leftovers in string while parsing clingo regex output: '" + termStr + "'.");
			} catch (IOException e) {
				throw new CommandException("BUG: I/O error parsing clingo output.");
			} catch (PrologParserException e) {
				throw new CommandException("BUG: Prolog error parsing clingo output: '" + termStr + "'.");
			}
		}
	
		return prologToScxml(name, terms);
	}
	
	private static List<AbstractPrologTerm> scxmlToProlog(ScxmlScxmlType scxml) {
		assert false;		// TODO:
		return null;
	}

	private static ScxmlScxmlType prologToScxml(String name, List<AbstractPrologTerm> terms) {
		ScxmlScxmlType scxmlType = new ScxmlScxmlType();
		scxmlType.setName(name);
		return null;
	}
}
