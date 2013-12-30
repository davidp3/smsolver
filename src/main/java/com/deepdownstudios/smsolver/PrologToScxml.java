package com.deepdownstudios.smsolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.bind.JAXBElement;

import static com.deepdownstudios.smsolver.ScxmlPrologData.*;
import alice.tuprolog.InvalidTheoryException;
import alice.tuprolog.NoMoreSolutionException;
import alice.tuprolog.NoSolutionException;
import alice.tuprolog.Prolog;
import alice.tuprolog.SolveInfo;
import alice.tuprolog.Struct;
import alice.tuprolog.Term;
import alice.tuprolog.Theory;
import alice.tuprolog.Var;

import com.deepdownstudios.scxml.jaxb.HistoryTypeDatatype;
import com.deepdownstudios.scxml.jaxb.ObjectFactory;
import com.deepdownstudios.scxml.jaxb.ScxmlAssignType;
import com.deepdownstudios.scxml.jaxb.ScxmlElseifType;
import com.deepdownstudios.scxml.jaxb.ScxmlFinalType;
import com.deepdownstudios.scxml.jaxb.ScxmlForeachType;
import com.deepdownstudios.scxml.jaxb.ScxmlHistoryType;
import com.deepdownstudios.scxml.jaxb.ScxmlIfType;
import com.deepdownstudios.scxml.jaxb.ScxmlLogType;
import com.deepdownstudios.scxml.jaxb.ScxmlOnentryType;
import com.deepdownstudios.scxml.jaxb.ScxmlOnexitType;
import com.deepdownstudios.scxml.jaxb.ScxmlParallelType;
import com.deepdownstudios.scxml.jaxb.ScxmlRaiseType;
import com.deepdownstudios.scxml.jaxb.ScxmlScriptType;
import com.deepdownstudios.scxml.jaxb.ScxmlScxmlType;
import com.deepdownstudios.scxml.jaxb.ScxmlStateType;
import com.deepdownstudios.scxml.jaxb.ScxmlTransitionType;

public class PrologToScxml {
	// Map of all states (except TOP_STATE).
	private HashMap<String, Object> stateNameToState = new HashMap<String, Object>();
	private List<Runnable> binders = new ArrayList<Runnable>();
	private ObjectFactory objectFactory = new ObjectFactory();
	private Theory theory;

	public ScxmlScxmlType prologToScxml(String name, List<Term> terms) throws CommandException {
		final Prolog prolog = new Prolog();
		try {
			// I'm pretty certain from the TUProlog docs (Sec 7.2.3.2) that the theory
			// requires facts to be written as clauses (in other words, top-level pred is ':-')
			// with body 'true'.  We convert the 'fact' terms to clauses here.
			List<Term> clauses = new ArrayList<Term>();
			for(Term term : terms)	{
				clauses.add(new Struct(CLAUSE_FUNCTOR_STR, term, TRUE_ATOM));
			}
			theory = new Theory(new Struct(clauses.toArray(new Term[0])));
			System.err.println(theory.toString());
			prolog.setTheory(theory);
		} catch (InvalidTheoryException e) {
			throw new CommandException("BUG: Could not make Prolog theory from the Prolog terms.", e);
		}
		
		final ScxmlScxmlType scxmlType = new ScxmlScxmlType();
		scxmlType.setName(name);
		
		// initial states.  TODO: <initial> child element is more versatile than the attribute.
		processQuerySolutions(prolog, stateInitialQuery(TOP_STATE_ATOM), X_STR, new ValueProcessor()	{
			public void process(final Term stateName) throws CommandException {
				binders.add(new Runnable() {
					public void run() {
						scxmlType.getInitial().add(stateNameToState.get(stateName.toUnquotedString()));
					}
				});
			}
		});
		
		// onEntry property
		processQuerySolutions(prolog, onEntryQuery(TOP_STATE_ATOM), X_STR, new ValueProcessor()	{
			public void process(Term onEntryContent) throws CommandException {
				// Valid onEntryContent for TOP_STATE has to be <script>
				if(!(onEntryContent instanceof Struct))	{
					throw new CommandException("BUG: Invalid format for top-state <script>: " + onEntryContent.toString());
				}
				Struct oec = (Struct) onEntryContent;
				String functor = oec.getName();
				if(!functor.equals(SCRIPT_STR))	{
					throw new CommandException("");
				}
				parseExecutableContent(scxmlType.getScxmlScxmlMix(), prolog, onEntryContent);
			}
		});

		// substates
		processQuerySolutions(prolog, stateChildrenQuery(TOP_STATE_ATOM), X_STR, new ValueProcessor()	{
			public void process(Term stateName) throws CommandException {
				JAXBElement<?> newState = state(prolog, stateName);
				// top-level states have some restrictions that answer set solutions must obey.
				// double check some of them here for redundancy.
				assert !(newState.getValue() instanceof ScxmlHistoryType);
				scxmlType.getScxmlScxmlMix().add(newState);
			}
		});
		
		// Bind cross-references (ie IDREFS)
		for(Runnable binder : binders)
			binder.run();
			
		return scxmlType;
	}

	protected JAXBElement<?> state(Prolog prolog, Term stateName) throws CommandException {
		String stateTypeStr = processQuerySolutions(prolog, stateTypeQuery(stateName), X_STR, new ResultValueProcessor<String>() {
			String type = null;
			public void process(Term stateType) throws CommandException {
				assert type == null;		// only one match for state type should ever be found
				type = stateType.toUnquotedString();
			}
			public String getResult() {
				return type;
			}
		}).getResult();
		
		if(SIMPLE_STR.equals(stateTypeStr))	{
			return objectFactory.createState(simple(prolog, stateName));
		} else if(PARALLEL_STR.equals(stateTypeStr))	{
			return objectFactory.createParallel(parallel(prolog, stateName));
		} else if(DEEP_STR.equals(stateTypeStr))	{
			return objectFactory.createHistory(deep(prolog, stateName));
		} else if(SHALLOW_STR.equals(stateTypeStr))	{
			return objectFactory.createHistory(shallow(prolog, stateName));
		} else if(FINAL_STR.equals(stateTypeStr))	{
			return objectFactory.createFinal(finalState(prolog, stateName));
		}
		throw new CommandException("BUG: Invalid state type given for state: " + stateName + " : " + stateTypeStr);
	}
	
	private ScxmlStateType simple(final Prolog prolog, Term stateName) throws CommandException {
		final ScxmlStateType newState = new ScxmlStateType();
		System.err.println("simple: " + stateName.toUnquotedString());
		newState.setId(stateName.toUnquotedString());
		
		// substates
		processQuerySolutions(prolog, stateChildrenQuery(stateName), X_STR, new ValueProcessor()	{
			public void process(Term substateName) throws CommandException {
				JAXBElement<?> substate = state(prolog, substateName);
				newState.getScxmlStateMix().add(substate);
			}
		});
			
		// transitions
		processMultivariateQuerySolutions(prolog, stateTransitionsQuery(stateName), 
				new String[] { X1_STR /* target state */, X2_STR /* condition */, 
								X3_STR /* events */, X4_STR /* action */ }, 
				new MultiValueProcessor()	{
					public void process(List<Term> edgeParams) throws CommandException {
						ScxmlTransitionType transition = 
								transition(prolog, edgeParams.get(0), edgeParams.get(1), edgeParams.get(2), edgeParams.get(3));
						newState.getScxmlStateMix().add(objectFactory.createTransition(transition));
					}
				});
			
		// initial states.  TODO: <initial> child element is more versatile than the attribute.
		processQuerySolutions(prolog, stateInitialQuery(stateName), X_STR, new ValueProcessor()	{
			public void process(final Term initialStateName) throws CommandException {
				binders.add(new Runnable() {
					public void run() {
						newState.getInitial().add(stateNameToState.get(initialStateName.toUnquotedString()));
					}
				});
			}
		});
		
		// onEntry property
		processQuerySolutions(prolog, onEntryQuery(stateName), X_STR, new ValueProcessor()	{
			public void process(Term onEntryContent) throws CommandException {
				ScxmlOnentryType onEntry = new ScxmlOnentryType();
				newState.getScxmlStateMix().add(onEntry);
				parseExecutableContent(onEntry.getScxmlCoreExecutablecontent(), prolog, onEntryContent);
			}
		});
			
		// onExit property
		processQuerySolutions(prolog, onExitQuery(stateName), X_STR, new ValueProcessor()	{
			public void process(Term onExitContent) throws CommandException {
				ScxmlOnexitType onExit = new ScxmlOnexitType();
				newState.getScxmlStateMix().add(onExit);
				parseExecutableContent(onExit.getScxmlCoreExecutablecontent(), prolog, onExitContent);
			}
		});

		stateNameToState.put(stateName.toUnquotedString(), newState);
		return newState; 
	}

	private ScxmlParallelType parallel(final Prolog prolog, Term stateName) throws CommandException {
		final ScxmlParallelType newState = new ScxmlParallelType();
		System.err.println("parallel: '" + stateName.toUnquotedString() + "'");
		newState.setId(stateName.toUnquotedString());
		
		// substates
		processQuerySolutions(prolog, stateChildrenQuery(stateName), X_STR, new ValueProcessor()	{
			public void process(Term substateName) throws CommandException {
				JAXBElement<?> substate = state(prolog, substateName);
				newState.getScxmlParallelMix().add(substate);
			}
		});
			
		// transitions
		processMultivariateQuerySolutions(prolog, stateTransitionsQuery(stateName), 
				new String[] { X1_STR /* target state */, X2_STR /* condition */, 
								X3_STR /* events */, X4_STR /* action */ }, 
				new MultiValueProcessor()	{
					public void process(List<Term> edgeParams) throws CommandException {
						ScxmlTransitionType transition = 
								transition(prolog, edgeParams.get(0), edgeParams.get(1), edgeParams.get(2), edgeParams.get(3));
						newState.getScxmlParallelMix().add(objectFactory.createTransition(transition));
					}
				});

		// onEntry property
		processQuerySolutions(prolog, onEntryQuery(stateName), X_STR, new ValueProcessor()	{
			public void process(Term onEntryContent) throws CommandException {
				ScxmlOnentryType onEntry = new ScxmlOnentryType();
				newState.getScxmlParallelMix().add(onEntry);
				parseExecutableContent(onEntry.getScxmlCoreExecutablecontent(), prolog, onEntryContent);
			}
		});
			
		// onExit property
		processQuerySolutions(prolog, onExitQuery(stateName), X_STR, new ValueProcessor()	{
			public void process(Term onExitContent) throws CommandException {
				ScxmlOnexitType onExit = new ScxmlOnexitType();
				newState.getScxmlParallelMix().add(onExit);
				parseExecutableContent(onExit.getScxmlCoreExecutablecontent(), prolog, onExitContent);
			}
		});
		
		stateNameToState.put(stateName.toUnquotedString(), newState);
		return newState; 
	}

	private ScxmlHistoryType deep(final Prolog prolog, Term stateName) throws CommandException {
		ScxmlHistoryType newState = historyHelper(prolog, stateName);
		newState.setType(HistoryTypeDatatype.DEEP);
		return newState;
	}

	private ScxmlHistoryType shallow(final Prolog prolog, Term stateName) throws CommandException {
		ScxmlHistoryType newState = historyHelper(prolog, stateName);
		newState.setType(HistoryTypeDatatype.SHALLOW);
		return newState;
	}
	
	private ScxmlHistoryType historyHelper(final Prolog prolog, Term stateName) throws CommandException {
		final ScxmlHistoryType newState = new ScxmlHistoryType();
		System.err.println("history: " + stateName.toUnquotedString());
		newState.setId(stateName.toUnquotedString());
		
		// transitions
		processMultivariateQuerySolutions(prolog, stateTransitionsQuery(stateName), 
				new String[] { X1_STR /* target state */, X2_STR /* condition */, 
								X3_STR /* events */, X4_STR /* action */ }, 
				new MultiValueProcessor()	{
					public void process(List<Term> edgeParams) throws CommandException {
						// history state transitions cannot have conditions or triggering events
						assert edgeParams.get(1).equals(NO_COND_ATOM);		
						assert edgeParams.get(2).equals(NO_EVENTS_ATOM);
						ScxmlTransitionType transition = 
								transition(prolog, edgeParams.get(0), NO_COND_ATOM, NO_EVENTS_ATOM, edgeParams.get(3));
						// there should be only one transition from history state.  TODO: What about forks?
						assert newState.getTransition() == null;		
						newState.setTransition(transition);
					}
				});
		
		stateNameToState.put(stateName.toUnquotedString(), newState);
		return newState; 
	}

	private ScxmlFinalType finalState(final Prolog prolog, Term stateName) throws CommandException {
		final ScxmlFinalType newState = new ScxmlFinalType();
		System.err.println("final: " + stateName.toUnquotedString());
		newState.setId(stateName.toUnquotedString());
		
		// onEntry property
		processQuerySolutions(prolog, onEntryQuery(stateName), X_STR, new ValueProcessor()	{
			public void process(Term onEntryContent) throws CommandException {
				ScxmlOnentryType onEntry = new ScxmlOnentryType();
				newState.getScxmlFinalMix().add(onEntry);
				parseExecutableContent(onEntry.getScxmlCoreExecutablecontent(), prolog, onEntryContent);
			}
		});
			
		// onExit property
		processQuerySolutions(prolog, onExitQuery(stateName), X_STR, new ValueProcessor()	{
			public void process(Term onExitContent) throws CommandException {
				ScxmlOnexitType onExit = new ScxmlOnexitType();
				newState.getScxmlFinalMix().add(onExit);
				parseExecutableContent(onExit.getScxmlCoreExecutablecontent(), prolog, onExitContent);
			}
		});
		
		stateNameToState.put(stateName.toUnquotedString(), newState);
		return newState; 
	}

	protected ScxmlTransitionType transition(Prolog prolog, final Term targetAtom, Term condAtom, Term eventsAtom,
			Term actionAtom) throws CommandException {
		final ScxmlTransitionType transitionType = new ScxmlTransitionType();
		String eventStr = eventsAtom.toUnquotedString();
		String condStr = condAtom.toUnquotedString();
		if(!NO_EVENTS_STR.equals(eventStr))
			transitionType.setEvent(eventStr);
		if(!NO_COND_STR.equals(condStr))
			transitionType.setCond(condStr);
		binders.add(new Runnable() {		// Run this once all states have been created
			public void run() {
				Object targetState = stateNameToState.get(targetAtom.toUnquotedString());
				transitionType.getTarget().add(targetState);
			}
		});
		
		if(!actionAtom.equals(NO_ACTION_ATOM))
			parseExecutableContent(transitionType.getScxmlCoreExecutablecontent(), prolog, actionAtom);
		return transitionType;
	}


	protected void parseExecutableContent(List<Object> executableContent, Prolog prolog, Term executableContentTerm) throws CommandException {
		assert executableContentTerm != null;
		if(!(executableContentTerm instanceof Struct))	{
			throw new CommandException("BUG: Invalid format for executable content: " + executableContentTerm.toString());
		}
		Struct oec = (Struct) executableContentTerm;
		String functor = oec.getName();
		if(functor.equals(NO_CONTENT_STR))	{
			return;
		}
		if(functor.equals(SEQ_STR))	{
			assert oec.getArity() == 2;
			parseExecutableContent(executableContent, prolog, oec.getArg(0));
			parseExecutableContent(executableContent, prolog, oec.getArg(1));
			return;
		}
		if(functor.equals(RAISE_STR))	{
			assert oec.getArity() == 1;
			ScxmlRaiseType elt = new ScxmlRaiseType();
			elt.setEvent(oec.getArg(0).toUnquotedString());
			executableContent.add(objectFactory.createRaise(elt));
			return;
		}
		if(functor.equals(IF_STR))	{
			assert oec.getArity() == 5;
			ScxmlIfType elt = new ScxmlIfType();
			elt.setCond(oec.getArg(0).toUnquotedString());		// if-cond
			parseExecutableContent(elt.getScxmlCoreExecutablecontentIf(), prolog, oec.getArg(1));	// if-branch
			ScxmlElseifType elseifType = new ScxmlElseifType();
			elseifType.setCond(oec.getArg(2).toUnquotedString());		// elseif-cond
			elt.setElseif(elseifType);
			parseExecutableContent(elt.getScxmlCoreExecutablecontentIfElseif(), prolog, oec.getArg(3));		// elseif-branch
			parseExecutableContent(elt.getScxmlCoreExecutablecontentIfElse(), prolog, oec.getArg(4));		// else-branch
			executableContent.add(objectFactory.createIf(elt));
			return;
		}
		if(functor.equals(FOREACH_STR))	{
			assert oec.getArity() == 3;
			ScxmlForeachType foreachType = new ScxmlForeachType();
			foreachType.setArray(oec.getArg(0).toUnquotedString());
			foreachType.setItem(oec.getArg(1).toUnquotedString());
			String index = oec.getArg(2).toUnquotedString();
			if(!index.equals(NO_INDEX_STR))
				foreachType.setIndex(index);
			executableContent.add(objectFactory.createForeach(foreachType));
			return;
		}
		if(functor.equals(LOG_STR))	{
			assert oec.getArity() == 2;
			ScxmlLogType logType = new ScxmlLogType();
			String label = oec.getArg(0).toUnquotedString();
			String expr = oec.getArg(1).toUnquotedString();
			if(!label.equals(NO_LABEL_STR))
				logType.setLabel(label);
			if(!expr.equals(NO_LOG_EXPR_STR))
				logType.setExpr(expr);
			executableContent.add(objectFactory.createLog(logType));
			return;
		}
		if(functor.equals(ASSIGN_STR))	{
			assert oec.getArity() == 2;
			ScxmlAssignType assignType = new ScxmlAssignType();
			String location = oec.getArg(0).toUnquotedString();
			String expr = oec.getArg(1).toUnquotedString();
			assignType.setExpr(expr);
			assignType.setLocation(location);
			executableContent.add(objectFactory.createAssign(assignType));
			return;
		}
		if(functor.equals(SCRIPT_STR))	{
			assert oec.getArity() == 1;
			ScxmlScriptType scxmlScriptType = new ScxmlScriptType();
			if(!(oec.getArg(0) instanceof Struct))	{
				throw new CommandException("BUG: Invalid format for <script> executable content: " + oec.toString());
			}
			Struct src = (Struct) oec.getArg(0);
			String srcFunctor = src.getName();
			if(srcFunctor.equals(SCRIPT_SRC_STR))	{
				assert src.getArity() == 1;
				String scriptSrc = src.getArg(0).toUnquotedString();
				scxmlScriptType.setSrc(scriptSrc);
			} else {
				scxmlScriptType.getContent().add(oec.getArg(0).toUnquotedString());
			}
			executableContent.add(objectFactory.createScript(scxmlScriptType));
			return;
		}
		throw new CommandException("BUG: Unable to interpret executable content: " + oec.toString());
	}

	private <T extends ValueProcessor> T processQuerySolutions(Prolog _junk_prolog, Term query, String varName,
			T valueProcessor) throws CommandException {
		final Prolog prolog = new Prolog();
		try {
			prolog.setTheory(theory);
		} catch (InvalidTheoryException e1) {
			throw new CommandException("BUG: Could not make new Prolog instance with same theory as previous instance.");
		}
		SolveInfo solution = prolog.solve(query);
		assert solution != null;
		if(solution.isSuccess())	{
			try {
				while(true)	{
					Term varValue = solution.getVarValue(varName);
					valueProcessor.process(varValue);
					solution = prolog.solveNext();
					assert solution != null;
				}
			} catch (NoMoreSolutionException e) {
				// Due to SolveInfo API design, this is how we end the loop.
			} catch (NoSolutionException e) {
				// getVarValue failed.
				throw new CommandException("BUG: Unable to get value of variable: " + e.getMessage(), e);
			}
		}
		return valueProcessor;
	}
	
	private <T extends MultiValueProcessor> T processMultivariateQuerySolutions(Prolog __junk_prolog, Term query, String[] varNames,
			T multiValueProcessor) throws CommandException {
		final Prolog prolog = new Prolog();
		try {
			prolog.setTheory(theory);
		} catch (InvalidTheoryException e1) {
			throw new CommandException("BUG: Could not make new Prolog instance with same theory as previous instance.");
		}
		SolveInfo solution = prolog.solve(query);
		assert solution != null;
		if(solution.isSuccess())	{
			try {
				while(true)	{
					List<Term> varValues = new ArrayList<Term>();
					for(String varName : varNames)	{
						Term varValue = solution.getVarValue(varName);
						varValues.add(varValue);
					}
					multiValueProcessor.process(varValues);
					solution = prolog.solveNext();
					assert solution != null;
				}
			} catch (NoMoreSolutionException e) {
				// Due to SolveInfo API design, this is how we end the loop.
			} catch (NoSolutionException e) {
				// getVarValue failed.
				throw new CommandException("BUG: Unable to get value of variable: " + e.getMessage(), e);
			}
		}
		return multiValueProcessor;
	}
	

	private static interface ValueProcessor {
		void process(Term value) throws CommandException;
	}

	private static interface MultiValueProcessor {
		void process(List<Term> value) throws CommandException;
	}

	private static interface ResultValueProcessor<T> extends ValueProcessor {
		T getResult();
	}

	@SuppressWarnings("unused")
	private static interface ResultMultiValueProcessor<T> extends MultiValueProcessor {
		T getResult();
	}


	/******************************** QUERIES ***********************************/
	// Because TUProlog does some variable matching based on Java Var-object
	// identity, I dont use a single static instance.

	private static Term stateInitialQuery(Term stateAtom) {
		Term xVar = new Var("X");
		return new Struct(PROP_STR, stateAtom, INITIAL_ATOM, xVar);
	}

	private static final Term stateChildrenQuery(Term parentStateAtom) {
		Term xVar = new Var("X");
		return new Struct(PARENT_STR, parentStateAtom, xVar);
	}

	private static Term onEntryQuery(Term stateAtom) {
		Term xVar = new Var("X");
		return new Struct(PROP_STR, stateAtom, ON_ENTRY_ATOM, xVar);
	}

	private static Term onExitQuery(Term stateAtom) {
		Term xVar = new Var("X");
		return new Struct(PROP_STR, stateAtom, ON_EXIT_ATOM, xVar);
	}

	private static Term stateTypeQuery(Term stateNameAtom) {
		Term xVar = new Var("X");
		return new Struct(STATE_STR, xVar, stateNameAtom);
	}

	private static Term stateTransitionsQuery(Term stateAtom) {
		Term x1Var = new Var(X1_STR);
		Term x2Var = new Var(X2_STR);
		Term x3Var = new Var(X3_STR);
		Term x4Var = new Var(X4_STR);
		return new Struct(EDGE_STR, stateAtom, x1Var, x2Var, x3Var, x4Var);
	}
}
