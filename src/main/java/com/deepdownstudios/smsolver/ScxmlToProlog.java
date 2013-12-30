package com.deepdownstudios.smsolver;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.xml.bind.JAXBElement;

import static com.deepdownstudios.smsolver.ScxmlPrologData.*;
import alice.tuprolog.Struct;
import alice.tuprolog.Term;

import com.deepdownstudios.scxml.jaxb.HistoryTypeDatatype;
import com.deepdownstudios.scxml.jaxb.ScxmlAssignType;
import com.deepdownstudios.scxml.jaxb.ScxmlCancelType;
import com.deepdownstudios.scxml.jaxb.ScxmlDatamodelType;
import com.deepdownstudios.scxml.jaxb.ScxmlElseifType;
import com.deepdownstudios.scxml.jaxb.ScxmlFinalType;
import com.deepdownstudios.scxml.jaxb.ScxmlForeachType;
import com.deepdownstudios.scxml.jaxb.ScxmlHistoryType;
import com.deepdownstudios.scxml.jaxb.ScxmlIfType;
import com.deepdownstudios.scxml.jaxb.ScxmlInitialType;
import com.deepdownstudios.scxml.jaxb.ScxmlInvokeType;
import com.deepdownstudios.scxml.jaxb.ScxmlLogType;
import com.deepdownstudios.scxml.jaxb.ScxmlOnentryType;
import com.deepdownstudios.scxml.jaxb.ScxmlOnexitType;
import com.deepdownstudios.scxml.jaxb.ScxmlParallelType;
import com.deepdownstudios.scxml.jaxb.ScxmlRaiseType;
import com.deepdownstudios.scxml.jaxb.ScxmlScriptType;
import com.deepdownstudios.scxml.jaxb.ScxmlScxmlType;
import com.deepdownstudios.scxml.jaxb.ScxmlSendType;
import com.deepdownstudios.scxml.jaxb.ScxmlStateType;
import com.deepdownstudios.scxml.jaxb.ScxmlTransitionType;
import com.google.common.base.Joiner;

public class ScxmlToProlog {
	public static List<Term> scxmlToProlog(ScxmlScxmlType scxml) throws CommandException {
		List<Term> ret = new ArrayList<Term>();

		addInitialStates(ret, TOP_STATE_ATOM, scxml.getInitial());
		
		List<Term> onEntryHandlers = new ArrayList<Term>();
		for(Object iChildObj : scxml.getScxmlScxmlMix())	{
			Object iChild = resolveJAXBObject(iChildObj);
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
				Term script = executableContentHelper(iChild);
				onEntryHandlers.add(script);
			} else if(iChild instanceof ScxmlDatamodelType)	{
				throw new CommandException("SCXML child element " + iChild + " is not yet supported.");
			} else {
				throw new CommandException("SCXML child element " + iChild + " could not be interpreted.");
			}
		}
		
		// THIS IS A PROBLEM because I cant easily pull apart the parameters when the arity is random as it is here.
		if(!onEntryHandlers.isEmpty())
			ret.add(onEntryProp(TOP_STATE_ATOM, toSeqList(onEntryHandlers)));
		return ret;
	}

	private static void addInitialStates(List<Term> ret, Struct fromState, List<Object> initialStates) throws CommandException {
		for(Object iStateObj : initialStates)	{
			Object iState = resolveJAXBObject(iStateObj);
			if(iState instanceof ScxmlStateType)	{
				ScxmlStateType state = (ScxmlStateType) iState;
				assert state.getId() != null;		// must be in order to be a target.  See common sense and Sec 3.14 "IDs".
				ret.add(initialProp(fromState, new Struct(state.getId())));
			} else if(iState instanceof ScxmlParallelType)	{
				ScxmlParallelType state = (ScxmlParallelType) iState; 
				assert state.getId() != null;		// must be in order to be a target.  See common sense and Sec 3.14 "IDs".
				ret.add(initialProp(fromState, new Struct(state.getId())));
			} else if(iState instanceof ScxmlHistoryType)	{
				ScxmlHistoryType state = (ScxmlHistoryType) iState; 
				assert state.getId() != null;		// must be in order to be a target.  See common sense and Sec 3.14 "IDs".
				ret.add(initialProp(fromState, new Struct(state.getId())));
			} else {
				throw new CommandException("Initial state " + iState + " could not be interpreted.");
			}
		}
	}

	private static void addSimpleState(List<Term> ret, String parentStr, ScxmlStateType state) throws CommandException {
		String idStr = state.getId();
		if(idStr == null)
			idStr = genId();
		Struct id = new Struct(idStr);
		ret.add(simple(id));
		if(!TOP_STATE_STR.equals(parentStr))		// parent(top_state,xxx) is not supposed to be given to clingo
			ret.add(parent(new Struct(parentStr), id));
		addInitialStates(ret, id, state.getInitial());
		
		List<Term> onEntryHandlers = new ArrayList<Term>();
		List<Term> onExitHandlers = new ArrayList<Term>();
		for(Object childObj : state.getScxmlStateMix())	{
			Object child = resolveJAXBObject(childObj);
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
			ret.add(onEntryProp(id, toSeqList(onEntryHandlers)));
		}
		if(!onExitHandlers.isEmpty())	{
			ret.add(onExitProp(id, toSeqList(onExitHandlers)));
		}
	}

	private static void addTransition(List<Term> ret, Struct srcState, ScxmlTransitionType transition) throws CommandException {
		Term events = events(transition.getEvent());
		Term cond = condition(transition.getCond());
		Term target;
		List<Object> targets = transition.getTarget();
		if(targets != null && targets.size() > 1)	{
			// TODO:
			throw new CommandException("Fork-transitions are not yet supported: " + transition.toString());
		}
		if(targets != null)	{
			String targetId = getId(targets.get(0));
			target = new Struct(targetId);
		} else {
			target = NO_TARGET_ATOM;
		}
		
		// Executable content defines actions
		Term action;
		if(!transition.getScxmlCoreExecutablecontent().isEmpty())	{
			action = executableContent(transition.getScxmlCoreExecutablecontent());
		} else {
			action = NO_ACTION_ATOM;
		}
		ret.add(new Struct(EDGE_STR, new Term[] { srcState, target, cond, events, action } ));
	}

	private static Object resolveJAXBObject(Object potentialJAXBObject)	{
		if(potentialJAXBObject instanceof JAXBElement<?>)
			return ((JAXBElement<?>)potentialJAXBObject).getValue();
		return potentialJAXBObject;
	}
	
	private static String getId(Object eltObj) throws CommandException {
		Object elt = resolveJAXBObject(eltObj);
		if(elt instanceof ScxmlStateType)
			return ((ScxmlStateType)elt).getId();
		if(elt instanceof ScxmlParallelType)
			return ((ScxmlParallelType)elt).getId();
		if(elt instanceof ScxmlHistoryType)
			return ((ScxmlHistoryType)elt).getId();
		if(elt instanceof ScxmlFinalType)
			return ((ScxmlFinalType)elt).getId();
		throw new CommandException("ID of element type could not be interpreted: " + elt.toString());
	}

	private static void addParallelState(List<Term> ret, String parentStr, ScxmlParallelType state) throws CommandException {
		String idStr = state.getId();
		if(idStr == null)
			idStr = genId();
		Struct id = new Struct(idStr);
		ret.add(parallel(id));
		ret.add(parent(new Struct(parentStr), id));
		
		List<Term> onEntryHandlers = new ArrayList<Term>();
		List<Term> onExitHandlers = new ArrayList<Term>();
		for(Object childObj : state.getScxmlParallelMix())	{
			Object child = resolveJAXBObject(childObj);
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
			ret.add(onEntryProp(id, toSeqList(onEntryHandlers)));
		}
		if(!onExitHandlers.isEmpty())	{
			ret.add(onExitProp(id, toSeqList(onExitHandlers)));
		}
	}

	private static void addHistoryState(List<Term> ret, String parentStr, ScxmlHistoryType state) throws CommandException {
		String idStr = state.getId();
		if(idStr == null)
			idStr = genId();
		Struct id = new Struct(idStr);
		ret.add(simple(id));
		ret.add(parent(new Struct(parentStr), id));
		HistoryTypeDatatype historyDatatype = state.getType();
		Struct historyType;
		if(historyDatatype == HistoryTypeDatatype.DEEP)
			historyType = DEEP_ATOM;
		else
			historyType = SHALLOW_ATOM;
		ScxmlTransitionType transition = state.getTransition();
		ret.add(new Struct(STATE_STR, new Term[] { historyType, id }));
		if(transition != null)
			addTransition(ret, id, transition);
	}

	private static void addFinalState(List<Term> ret, String parentStr, ScxmlFinalType state) throws CommandException {
		String idStr = state.getId();
		if(idStr == null)
			idStr = genId();
		Struct id = new Struct(idStr);
		ret.add(finalState(id));
		ret.add(parent(new Struct(parentStr), id));
		
		List<Term> onEntryHandlers = new ArrayList<Term>();
		List<Term> onExitHandlers = new ArrayList<Term>();
		for(Object childObj : state.getScxmlFinalMix())	{
			Object child = resolveJAXBObject(childObj);
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
			ret.add(onEntryProp(id, toSeqList(onEntryHandlers)));
		}
		if(!onExitHandlers.isEmpty())	{
			ret.add(onExitProp(id, toSeqList(onExitHandlers)));
		}
	}

	private static Term onentry(ScxmlOnentryType xml) throws CommandException {
		return executableContent(xml.getScxmlCoreExecutablecontent());
	}

	private static Term onexit(ScxmlOnexitType xml) throws CommandException {
		return executableContent(xml.getScxmlCoreExecutablecontent());
	}

	public static Term executableContentHelper(Object execContentNodeObj) throws CommandException {
		Object execContentNode = resolveJAXBObject(execContentNodeObj);
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
			Term ifContent = executableContent(executablecontentIf);
			Term elseifContent = executableContent(executablecontentIfElseIf);
			Term elseContent = executableContent(executablecontentIfElse);
			Term ifCond = new Struct(ifCondStr);
			Term elseIfCond;
			if(elseifType != null)	{
				String elseIfCondStr = elseifType.getCond();
				elseIfCond = new Struct(elseIfCondStr);
			} else {
				elseIfCond = NO_COND_ATOM;
			}
			return new Struct(IF_STR, new Term[] { ifCond, ifContent, elseIfCond, elseifContent, elseContent });
		} else if(execContentNode instanceof ScxmlForeachType)	{
			ScxmlForeachType foreachType = (ScxmlForeachType) execContentNode;
			String indexStr = foreachType.getIndex();
			Struct index;
			if(indexStr == null)
				index = NO_INDEX_ATOM;
			else
				index = new Struct(indexStr);
			return new Struct(FOREACH_STR, new Term[] { 
					new Struct(foreachType.getArray()), new Struct(foreachType.getItem()), index });
		} else if(execContentNode instanceof ScxmlLogType)	{
			ScxmlLogType logType = (ScxmlLogType) execContentNode;
			Struct label, expr;
			if(logType.getLabel() == null)
				label = NO_LABEL_ATOM;
			else
				label = new Struct(logType.getLabel());
			if(logType.getLabel() == null)
				expr = NO_LOG_EXPR_ATOM;
			else
				expr = new Struct(logType.getExpr());
			return new Struct(LOG_STR, new Term[] { label, expr });
		} else if(execContentNode instanceof ScxmlAssignType) {
			ScxmlAssignType assignType = (ScxmlAssignType)execContentNode;
			Struct expr;
			if(assignType.getExpr() == null)	{
				// assignment value is in children of XML element.  Not sure what to expect so just making a String separated by EOLs. 
				StringBuilder contentStr = new StringBuilder();
				for(Object o : assignType.getContent())	{
					contentStr.append(o.toString()).append(EOL_STR);
				}
				expr = new Struct(contentStr.toString());
			} else {
				expr = new Struct(assignType.getExpr());
			}
			assert !(expr.isEmptyList());		// assign value cannot be empty!
			return new Struct(ASSIGN_STR, new Term[] { new Struct(assignType.getLocation()), expr });
		} else if(execContentNode instanceof ScxmlScriptType) {
			ScxmlScriptType scriptType = (ScxmlScriptType)execContentNode;
			Term script;
			if(scriptType.getSrc() != null) {
				script = new Struct(SCRIPT_SRC_STR, new Term[] { new Struct(scriptType.getSrc()) });
			} else {
				// assignment value is in children of XML element.  Not sure what to expect so just making a String separated by EOLs. 
				StringBuilder contentStr = new StringBuilder();
				for(Object o : scriptType.getContent())	{
					if(contentStr.length() != 0)
						contentStr.append(EOL_STR);
					contentStr.append(o.toString());
				}
				script = new Struct(contentStr.toString());
			}
			return new Struct(SCRIPT_STR, new Term[] { script });
		} else if(execContentNode instanceof ScxmlSendType || execContentNode instanceof ScxmlCancelType)	{
			// TODO:
			throw new CommandException("SCXML Element not yet implemented: " + execContentNode.toString());
		} else {
			throw new CommandException("Executable content could not be interpreted: " + execContentNode.toString());
		}
	}

	private static Term toSeqList(List<Term> terms) throws CommandException	{
		if(terms.isEmpty())
			throw new CommandException("BUG: Empty sequential operations list.");
		Term ret = terms.get(0);
		for(int i=1; i<terms.size(); i++)
			ret = new Struct(SEQ_STR, ret, terms.get(i));
		return ret;
	}
	
	private static Term executableContent(List<Object> executablecontent) throws CommandException {
		if(executablecontent == null)	{
			return NO_CONTENT_ATOM;
		}
		List<Term> terms = new ArrayList<Term>();
		for(Object iChild : executablecontent)	{
			terms.add(executableContentHelper(iChild));
		}
		return toSeqList(terms);
	}

	private static Term raise(String event) {
		return new Struct(RAISE_STR, new Term[] { new Struct(event) });
	}

	private static Struct onEntryProp(Struct id, Term onEntryHandlers) {
		return prop(id, ON_ENTRY_ATOM, onEntryHandlers);
	}

	private static Struct onExitProp(Struct id, Term onExitHandlers) {
		return prop(id, ON_EXIT_ATOM, onExitHandlers);
	}

	private static Struct simple(Struct id) {
		return new Struct(STATE_STR, new Term[] { SIMPLE_ATOM, id });
	}

	private static Struct parallel(Struct id) {
		return new Struct(STATE_STR, new Term[] { PARALLEL_ATOM, id });
	}

	private static Struct finalState(Struct id) {	// NOTE: 'final' is a Java keyword so name is 'finalState'
		return new Struct(STATE_STR, new Term[] { FINAL_ATOM, id });
	}

	private static String genId() {
		return String.valueOf(UUID.randomUUID().toString());
	}

	private static Struct parent(Struct parent, Struct child) {
		return new Struct(PARENT_STR, new Term[] { parent, child });
	}

	private static Struct prop(Term param0, Term param1, 
			Term param2) {
		return new Struct(PROP_STR, new Term[] {param0, param1, param2});
	}
	
	private static Struct initialProp(Struct parent, Struct target)	{
		return prop(parent, INITIAL_ATOM, target);
	}

	private static Term events(String event) {
		if(event == null)
			return NO_EVENTS_ATOM;
		return new Struct(event);
	}

	private static Term condition(String cond) {
		if(cond == null)
			return NO_COND_ATOM;
		return new Struct(cond);
	}
}
