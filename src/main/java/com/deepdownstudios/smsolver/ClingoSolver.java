package com.deepdownstudios.smsolver;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.deepdownstudios.scxml.jaxb.*;
import com.google.common.base.Joiner;
import com.google.common.io.Files;
import com.igormaznitsa.prologparser.PrologParser;
import com.igormaznitsa.prologparser.exceptions.PrologParserException;
import com.igormaznitsa.prologparser.terms.AbstractPrologTerm;
import com.igormaznitsa.prologparser.terms.PrologAtom;
import com.igormaznitsa.prologparser.terms.PrologStructure;

public class ClingoSolver {
	private static final String CLINGO_UNSATISFIABLE = "UNSATISFIABLE";
	private static final String CLINGO_SATISFIABLE = "SATISFIABLE";
	private static final String ENGINE_RESOURCE_NAME = "/engine.lp";
	private static final String TOP_STATE_STR = "top_state";
	private static final String INITIAL_STR = "initial";
	private static final String PROP_STR = "prop";
	private static final String PARENT_STR = "parent";
	private static final String SIMPLE_STR = "simple";
	private static final String PARALLEL_STR = "par";
	private static final String RUN_STR = "run";
	private static final String ON_ENTRY_STR = "onentry";
	private static final String ON_EXIT_STR = "onexit";
	private static final String RAISE_STR = "raise";
	private static final String NO_CONTENT_STR = "noop";
	private static final String NO_COND_STR = "no_cond";
	private static final String IF_STR = "if";
	private static final String FOREACH_STR = "foreach";
	private static final String NO_INDEX_STR = "no_index";
	private static final String NO_LABEL_STR = "no_label";
	private static final String NO_LOG_EXPR_STR = "no_message";
	private static final String LOG_STR = "log";
	private static final String ASSIGN_STR = "assign";
	private static final String EOL_STR = "\n";
	private static final String SCRIPT_SRC_STR = "src";
	private static final String SCRIPT_STR = "script";
	private static final String DEEP_STR = "deep";
	private static final String SHALLOW_STR = "shallow";
	
	private static final PrologAtom PROP_ATOM = new PrologAtom(PROP_STR);
	private static final PrologAtom INITIAL_ATOM = new PrologAtom(INITIAL_STR);
	private static final PrologAtom TOP_STATE_ATOM = new PrologAtom(TOP_STATE_STR);
	private static final PrologAtom PARENT_ATOM = new PrologAtom(PARENT_STR);
	private static final PrologAtom SIMPLE_ATOM = new PrologAtom(SIMPLE_STR);
	private static final PrologAtom PARALLEL_ATOM = new PrologAtom(PARALLEL_STR);
	private static final PrologAtom RUN_ATOM = new PrologAtom(RUN_STR);
	private static final PrologAtom ON_ENTRY_ATOM = new PrologAtom(ON_ENTRY_STR);
	private static final PrologAtom ON_EXIT_ATOM = new PrologAtom(ON_EXIT_STR);
	private static final PrologAtom RAISE_ATOM = new PrologAtom(RAISE_STR);
	private static final PrologAtom NO_CONTENT_ATOM = new PrologAtom(NO_CONTENT_STR);
	private static final PrologAtom NO_COND_ATOM = new PrologAtom(NO_COND_STR);
	private static final PrologAtom IF_ATOM = new PrologAtom(IF_STR);
	private static final PrologAtom FOREACH_ATOM = new PrologAtom(FOREACH_STR);
	private static final PrologAtom NO_INDEX_ATOM = new PrologAtom(NO_INDEX_STR);
	private static final PrologAtom NO_LABEL_ATOM = new PrologAtom(NO_LABEL_STR);
	private static final PrologAtom NO_LOG_EXPR_ATOM = new PrologAtom(NO_LOG_EXPR_STR);
	private static final PrologAtom LOG_ATOM = new PrologAtom(LOG_STR);
	private static final PrologAtom ASSIGN_ATOM = new PrologAtom(ASSIGN_STR);
	private static final PrologAtom SCRIPT_SRC_ATOM = new PrologAtom(SCRIPT_SRC_STR);
	private static final PrologAtom SCRIPT_ATOM = new PrologAtom(SCRIPT_STR);
	private static final PrologAtom DEEP_ATOM = new PrologAtom(DEEP_STR);
	private static final PrologAtom SHALLOW_ATOM = new PrologAtom(SHALLOW_STR);
	
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

	private static String buildAspPayload(State state, Command command) throws CommandException {
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
	
	private static PrologStructure prop(AbstractPrologTerm param0, AbstractPrologTerm param1, 
			AbstractPrologTerm param2) {
		return new PrologStructure(PROP_ATOM, new AbstractPrologTerm[] {param0, param1, param2});
	}
	
	private static PrologStructure initialProp(PrologAtom parent, PrologAtom target)	{
		return prop(parent, INITIAL_ATOM, target);
	}
	
	private static List<AbstractPrologTerm> scxmlToProlog(ScxmlScxmlType scxml) throws CommandException {
		List<AbstractPrologTerm> ret = new ArrayList<AbstractPrologTerm>();

		addInitialStates(ret, TOP_STATE_ATOM, scxml.getInitial());
		
		for(Object iChild : scxml.getScxmlScxmlMix())	{
			if(iChild instanceof ScxmlStateType)	{
				ScxmlStateType state = (ScxmlStateType) iChild;
				addSimpleState(ret, TOP_STATE_STR, state);
			} else if(iChild instanceof ScxmlParallelType)	{
				ScxmlParallelType state = (ScxmlParallelType) iChild; 
				addParallelState(ret, TOP_STATE_STR, state);
			} else if(iChild instanceof ScxmlFinalType)	{
				ScxmlFinalType state = (ScxmlFinalType) iChild; 
				addFinalState(ret, TOP_STATE_STR, state);
			} else if(iChild instanceof ScxmlScriptType)	{
				// Model top-level scripts as onEntry into TOP_STATE_ATOM.
				AbstractPrologTerm script = executableContentHelper(iChild);
				ret.add(onEntryProp(TOP_STATE_ATOM, new PrologStructure(RUN_ATOM, new AbstractPrologTerm[] { script } )));
			} else if(iChild instanceof ScxmlDatamodelType)	{
				throw new CommandException("SCXML child element " + iChild + " is not yet supported.");
			} else {
				throw new CommandException("SCXML child element " + iChild + " could not be interpreted.");
			}
		}
		return ret;
	}

	private static void addInitialStates(List<AbstractPrologTerm> ret, PrologAtom fromState, List<Object> initialStates) throws CommandException {
		if(initialStates == null)
			return;		// no initial states
		for(Object iState : initialStates)	{
			if(iState instanceof ScxmlStateType)	{
				ScxmlStateType state = (ScxmlStateType) iState;
				assert state.getId() != null;		// must be in order to be a target.  See common sense and Sec 3.14 "IDs".
				ret.add(initialProp(fromState, new PrologAtom(state.getId())));
			} else if(iState instanceof ScxmlParallelType)	{
				ScxmlParallelType state = (ScxmlParallelType) iState; 
				assert state.getId() != null;		// must be in order to be a target.  See common sense and Sec 3.14 "IDs".
				ret.add(initialProp(fromState, new PrologAtom(state.getId())));
			} else if(iState instanceof ScxmlHistoryType)	{
				ScxmlHistoryType state = (ScxmlHistoryType) iState; 
				assert state.getId() != null;		// must be in order to be a target.  See common sense and Sec 3.14 "IDs".
				ret.add(initialProp(fromState, new PrologAtom(state.getId())));
			} else {
				throw new CommandException("Initial state " + iState + " could not be interpreted.");
			}
		}
	}

	private static void addSimpleState(List<AbstractPrologTerm> ret, String parentStr, ScxmlStateType state) throws CommandException {
		String idStr = state.getId();
		if(idStr == null)
			idStr = genId();
		PrologAtom id = new PrologAtom(idStr);
		ret.add(simple(id));
		ret.add(parent(new PrologAtom(parentStr), id));
		addInitialStates(ret, id, state.getInitial());
		
		List<AbstractPrologTerm> onEntryHandlers = new ArrayList<AbstractPrologTerm>();
		List<AbstractPrologTerm> onExitHandlers = new ArrayList<AbstractPrologTerm>();
		for(Object child : state.getScxmlStateMix())	{
			if(child instanceof ScxmlOnentryType)	{
				ScxmlOnentryType executable = (ScxmlOnentryType) child;
				onEntryHandlers.add(onentry(executable));
			} else if(child instanceof ScxmlOnexitType)	{
				ScxmlOnexitType executable = (ScxmlOnexitType) child;
				onExitHandlers.add(onexit(executable));
			} else if(child instanceof ScxmlStateType)	{
				ScxmlStateType substate = (ScxmlStateType) child;
				addSimpleState(ret, idStr, substate);
			} else if(child instanceof ScxmlParallelType)	{
				ScxmlParallelType substate = (ScxmlParallelType) child; 
				addParallelState(ret, idStr, substate);
			} else if(child instanceof ScxmlFinalType)	{
				ScxmlFinalType substate = (ScxmlFinalType) child; 
				addFinalState(ret, idStr, substate);
			} else if(child instanceof ScxmlHistoryType)	{
				ScxmlHistoryType substate = (ScxmlHistoryType) child; 
				addHistoryState(ret, idStr, substate);
			} else if(child instanceof ScxmlInvokeType)	{
				ScxmlInvokeType invokeElt = (ScxmlInvokeType) child; 
				throw new CommandException("The invoke element is not yet implemented: " + invokeElt);
			} else if(child instanceof ScxmlTransitionType)	{
				ScxmlTransitionType transitionElt = (ScxmlTransitionType) child; 
				addTransition(ret, id, transitionElt);
			} else if(child instanceof ScxmlInitialType)	{
				ScxmlInitialType initialElt = (ScxmlInitialType) child;
				assert initialElt.getTransition().getCond() == null;		// by the spec
				assert initialElt.getTransition().getEvent() == null;		// by the spec
				assert state.getInitial() == null;		// by the spec
				if(initialElt.getTransition().getScxmlCoreExecutablecontent() != null)	{
					throw new CommandException("Initial transition actions are not yet supported: " +
							Joiner.on("\n").join(initialElt.getTransition().getScxmlCoreExecutablecontent()));
				}
				addInitialStates(ret, id, initialElt.getTransition().getTarget());
			} else if(child instanceof ScxmlDatamodelType)	{
				throw new CommandException("Simple state child element " + child + " is not yet supported.");
			} else {
				throw new CommandException("Simple state child element " + child + " could not be interpreted.");
			}
		}
		if(!onEntryHandlers.isEmpty())	{
			ret.add(onEntryProp(id, new PrologStructure(RUN_ATOM, onEntryHandlers.toArray(new AbstractPrologTerm[0]))));
		}
		if(!onExitHandlers.isEmpty())	{
			ret.add(onExitProp(id, new PrologStructure(RUN_ATOM, onExitHandlers.toArray(new AbstractPrologTerm[0]))));
		}
	}

	private static void addTransition(List<AbstractPrologTerm> ret, PrologAtom srcState, ScxmlTransitionType transition) {
		AbstractPrologTerm cond, events;
		// probably lump events in with cond (say, as 'match(event)' condition).
		// maybe not tho because it would be hard to pull back out and there is mild reason to believe things
		// will be better with them separate
		
		// Executable content defines actions
		if(transition.getScxmlCoreExecutablecontent() != null)
			
	}

	private static void addParallelState(List<AbstractPrologTerm> ret, String parentStr, ScxmlParallelType state) throws CommandException {
		String idStr = state.getId();
		if(idStr == null)
			idStr = genId();
		PrologAtom id = new PrologAtom(idStr);
		ret.add(parallel(id));
		ret.add(parent(new PrologAtom(parentStr), id));
		
		List<AbstractPrologTerm> onEntryHandlers = new ArrayList<AbstractPrologTerm>();
		List<AbstractPrologTerm> onExitHandlers = new ArrayList<AbstractPrologTerm>();
		for(Object child : state.getScxmlParallelMix())	{
			if(child instanceof ScxmlOnentryType)	{
				ScxmlOnentryType executable = (ScxmlOnentryType) child;
				onEntryHandlers.add(onentry(executable));
			} else if(child instanceof ScxmlOnexitType)	{
				ScxmlOnexitType executable = (ScxmlOnexitType) child;
				onExitHandlers.add(onexit(executable));
			} else if(child instanceof ScxmlStateType)	{
				ScxmlStateType substate = (ScxmlStateType) child;
				addSimpleState(ret, idStr, substate);
			} else if(child instanceof ScxmlParallelType)	{
				ScxmlParallelType substate = (ScxmlParallelType) child; 
				addParallelState(ret, idStr, substate);
			} else if(child instanceof ScxmlHistoryType)	{
				ScxmlHistoryType substate = (ScxmlHistoryType) child; 
				addHistoryState(ret, idStr, substate);
			} else if(child instanceof ScxmlInvokeType)	{
				ScxmlInvokeType invokeElt = (ScxmlInvokeType) child; 
				throw new CommandException("The invoke element is not yet implemented: " + invokeElt);
			} else if(child instanceof ScxmlTransitionType)	{
				ScxmlTransitionType transitionElt = (ScxmlTransitionType) child; 
				addTransition(ret, id, transitionElt);
			} else if(child instanceof ScxmlDatamodelType)	{
				throw new CommandException("Simple state child element " + child + " is not yet supported.");
			} else {
				throw new CommandException("Simple state child element " + child + " could not be interpreted.");
			}
		}
		if(!onEntryHandlers.isEmpty())	{
			ret.add(onEntryProp(id, new PrologStructure(RUN_ATOM, onEntryHandlers.toArray(new AbstractPrologTerm[0]))));
		}
		if(!onExitHandlers.isEmpty())	{
			ret.add(onExitProp(id, new PrologStructure(RUN_ATOM, onExitHandlers.toArray(new AbstractPrologTerm[0]))));
		}
	}

	private static void addHistoryState(List<AbstractPrologTerm> ret, String parentStr, ScxmlHistoryType state) {
		String idStr = state.getId();
		if(idStr == null)
			idStr = genId();
		PrologAtom id = new PrologAtom(idStr);
		ret.add(simple(id));
		ret.add(parent(new PrologAtom(parentStr), id));
		HistoryTypeDatatype historyDatatype = state.getType();
		PrologAtom historyType;
		if(historyDatatype == HistoryTypeDatatype.DEEP)
			historyType = DEEP_ATOM;
		else
			historyType = SHALLOW_ATOM;
		ScxmlTransitionType transition = state.getTransition();
		ret.add(new PrologStructure(historyType, new AbstractPrologTerm[] { id }));
		if(transition != null)
			addTransition(ret, id, transition);
	}

	private static void addFinalState(List<AbstractPrologTerm> ret, String parentStr, ScxmlFinalType state) throws CommandException {
		String idStr = state.getId();
		if(idStr == null)
			idStr = genId();
		PrologAtom id = new PrologAtom(idStr);
		ret.add(simple(id));
		ret.add(parent(new PrologAtom(parentStr), id));
		
		List<AbstractPrologTerm> onEntryHandlers = new ArrayList<AbstractPrologTerm>();
		List<AbstractPrologTerm> onExitHandlers = new ArrayList<AbstractPrologTerm>();
		for(Object child : state.getScxmlFinalMix())	{
			if(child instanceof ScxmlOnentryType)	{
				ScxmlOnentryType executable = (ScxmlOnentryType) child;
				onEntryHandlers.add(onentry(executable));
			} else if(child instanceof ScxmlOnexitType)	{
				ScxmlOnexitType executable = (ScxmlOnexitType) child;
				onExitHandlers.add(onexit(executable));
			} else {
				throw new CommandException("Final state child element " + child + " could not be interpreted.");
			}
		}
		if(!onEntryHandlers.isEmpty())	{
			ret.add(onEntryProp(id, new PrologStructure(RUN_ATOM, onEntryHandlers.toArray(new AbstractPrologTerm[0]))));
		}
		if(!onExitHandlers.isEmpty())	{
			ret.add(onExitProp(id, new PrologStructure(RUN_ATOM, onExitHandlers.toArray(new AbstractPrologTerm[0]))));
		}
	}

	private static AbstractPrologTerm onentry(ScxmlOnentryType xml) throws CommandException {
		return executableContent(xml.getScxmlCoreExecutablecontent());
	}

	private static AbstractPrologTerm onexit(ScxmlOnexitType xml) throws CommandException {
		return executableContent(xml.getScxmlCoreExecutablecontent());
	}

	public static AbstractPrologTerm executableContentHelper(Object execContentNode) throws CommandException {
		if(execContentNode instanceof ScxmlRaiseType)	{
			ScxmlRaiseType raiseType = (ScxmlRaiseType) execContentNode;
			return raise(raiseType.getEvent());
		} else if(execContentNode instanceof ScxmlIfType)	{
			ScxmlIfType ifType = (ScxmlIfType) execContentNode;
			String ifCondStr = ifType.getCond();
			// DLP: The spec makes conditionals pretty worthless because 
			// multiple elseifs lead to conflicts so the spec doesn't include them.
			ScxmlElseifType elseifType = ifType.getElseif();
			// ScxmlElseType elseType = ifType.getElse();		// has no actual content
			List<Object> executablecontentIf = ifType.getScxmlCoreExecutablecontentIf();
			List<Object> executablecontentIfElseIf = ifType.getScxmlCoreExecutablecontentIfElseif();
			List<Object> executablecontentIfElse = ifType.getScxmlCoreExecutablecontentIfElse();
			AbstractPrologTerm ifContent = executableContent(executablecontentIf);
			AbstractPrologTerm elseifContent = executableContent(executablecontentIfElseIf);
			AbstractPrologTerm elseContent = executableContent(executablecontentIfElse);
			AbstractPrologTerm ifCond = new PrologAtom(ifCondStr);
			AbstractPrologTerm elseIfCond;
			if(elseifType != null)	{
				String elseIfCondStr = elseifType.getCond();
				elseIfCond = new PrologAtom(elseIfCondStr);
			} else {
				elseIfCond = NO_COND_ATOM;
			}
			return new PrologStructure(IF_ATOM, new AbstractPrologTerm[] { ifCond, ifContent, elseIfCond, elseifContent, elseContent });
		} else if(execContentNode instanceof ScxmlForeachType)	{
			ScxmlForeachType foreachType = (ScxmlForeachType) execContentNode;
			String indexStr = foreachType.getIndex();
			PrologAtom index;
			if(indexStr == null)
				index = NO_INDEX_ATOM;
			else
				index = new PrologAtom(indexStr);
			return new PrologStructure(FOREACH_ATOM, new AbstractPrologTerm[] { 
					new PrologAtom(foreachType.getArray()), new PrologAtom(foreachType.getItem()), index });
		} else if(execContentNode instanceof ScxmlLogType)	{
			ScxmlLogType logType = (ScxmlLogType) execContentNode;
			PrologAtom label, expr;
			if(logType.getLabel() == null)
				label = NO_LABEL_ATOM;
			else
				label = new PrologAtom(logType.getLabel());
			if(logType.getLabel() == null)
				expr = NO_LOG_EXPR_ATOM;
			else
				expr = new PrologAtom(logType.getLabel());
			return new PrologStructure(LOG_ATOM, new AbstractPrologTerm[] { label, expr });
		} else if(execContentNode instanceof ScxmlAssignType) {
			ScxmlAssignType assignType = (ScxmlAssignType)execContentNode;
			PrologAtom expr;
			if(assignType.getExpr() == null)	{
				// assignment value is in children of XML element.  Not sure what to expect so just making a String separated by EOLs. 
				StringBuilder contentStr = new StringBuilder();
				for(Object o : assignType.getContent())	{
					contentStr.append(o.toString()).append(EOL_STR);
				}
				expr = new PrologAtom(contentStr.toString());
			} else {
				expr = new PrologAtom(assignType.getExpr());
			}
			return new PrologStructure(ASSIGN_ATOM, new AbstractPrologTerm[] { new PrologAtom(assignType.getLocation()), expr });
		} else if(execContentNode instanceof ScxmlScriptType) {
			ScxmlScriptType scriptType = (ScxmlScriptType)execContentNode;
			AbstractPrologTerm script;
			if(scriptType.getSrc() != null) {
				script = new PrologStructure(SCRIPT_SRC_ATOM, new AbstractPrologTerm[] { new PrologAtom(scriptType.getSrc()) });
			} else {
				// assignment value is in children of XML element.  Not sure what to expect so just making a String separated by EOLs. 
				StringBuilder contentStr = new StringBuilder();
				for(Object o : scriptType.getContent())	{
					contentStr.append(o.toString()).append(EOL_STR);
				}
				script = new PrologAtom(contentStr.toString());
			}
			return new PrologStructure(SCRIPT_ATOM, new AbstractPrologTerm[] { script });
		} else if(execContentNode instanceof ScxmlSendType || execContentNode instanceof ScxmlCancelType)	{
			// TODO:
			throw new CommandException("SCXML Element not yet implemented: " + execContentNode.toString());
		} else {
			throw new CommandException("Executable content could not be interpreted: " + execContentNode.toString());
		}
	}

	private static AbstractPrologTerm executableContent(List<Object> executablecontent) throws CommandException {
		if(executablecontent == null)	{
			return NO_CONTENT_ATOM;
		}
		List<AbstractPrologTerm> terms = new ArrayList<AbstractPrologTerm>();
		for(Object iChild : executablecontent)	{
			terms.add(executableContentHelper(iChild));
		}
		return new PrologStructure(RUN_ATOM, terms.toArray(new AbstractPrologTerm[0]));
	}

	private static AbstractPrologTerm raise(String event) {
		return new PrologStructure(RAISE_ATOM, new AbstractPrologTerm[] { new PrologAtom(event) });
	}

	private static PrologStructure onEntryProp(PrologAtom id, PrologStructure onEntryHandlers) {
		return prop(id, ON_ENTRY_ATOM, onEntryHandlers);

	}

	private static PrologStructure onExitProp(PrologAtom id, PrologStructure onExitHandlers) {
		return prop(id, ON_EXIT_ATOM, onExitHandlers);

	}

	private static PrologStructure simple(PrologAtom id) {
		return new PrologStructure(SIMPLE_ATOM, new AbstractPrologTerm[] { id });
	}

	private static PrologStructure parallel(PrologAtom id) {
		return new PrologStructure(PARALLEL_ATOM, new AbstractPrologTerm[] { id });
	}

	private static String genId() {
		return String.valueOf(UUID.randomUUID().toString());
	}

	private static PrologStructure parent(PrologAtom parent, PrologAtom child) {
		return new PrologStructure(PARENT_ATOM, new AbstractPrologTerm[] { parent, child });
	}

	private static ScxmlScxmlType prologToScxml(String name, List<AbstractPrologTerm> terms) {
		ScxmlScxmlType scxmlType = new ScxmlScxmlType();
		scxmlType.setName(name);
		return null;
	}
}
