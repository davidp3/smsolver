package com.deepdownstudios.smsolver;

import java.util.List;

import com.google.common.collect.ImmutableList;

/**
 * An immutable list of states that represent the evolution of the SCXML document in the current editing session.
 */
public class History {
	private static final int NO_CURRENT_STATE = -1;
	private static final List<State> emptyStateList = ImmutableList.<State>of();
	
	private final List<State> states;
	private final int currentStateIndex;
	
	/**
	 * Create an empty history.
	 */
	public History()	{
		this.states = emptyStateList;
		this.currentStateIndex = NO_CURRENT_STATE;
	}
	
	/**
	 * Create a history with the given list of states and the current state given.
	 * @param states
	 * @param currentStateIndex
	 */
	public History(List<State> states, int currentStateIndex)	{
		assert currentStateIndex >= 0 && currentStateIndex < states.size();
		this.states = ImmutableList.<State>copyOf(states);
		this.currentStateIndex = currentStateIndex;
	}
	
	public class HistoryException extends CommandException	{
		private static final long serialVersionUID = 92391205L;
		public HistoryException(String message) {
			super(message);
		}
		public HistoryException(String message, Exception cause) {
			super(message, cause);
		}
	}
	
	public State getCurrentState() throws HistoryException	{
		if(currentStateIndex == NO_CURRENT_STATE)
			throw new HistoryException("No valid states in history.");
		return states.get(currentStateIndex);
	}

	/**
	 * Get all of the states in the history.
	 */
	public List<State> getStates() {
		return ImmutableList.copyOf(states);		// pretty sure its already immutable so this is really a noop but if not, this keeps us safe.
	}

	public int getCurrentStateIndex() {
		return currentStateIndex;
	}

	/**
	 * Create a new History object with the given state appended after the current state.
	 * Note that any states that exist after the current state will not be included in the
	 * new History.  It is the equivalent of undoing an edit and then doing something else --
	 * the undone edit is lost forever.
	 * @param state	The new state to append.
	 * @return		The new history.
	 */
	public History pushState(State state)	{
		if(currentStateIndex == NO_CURRENT_STATE)	{
			assert states.isEmpty();
			return new History(ImmutableList.of(state), 0);
		}

		List<State> historyStatesSublist = states.subList(0, currentStateIndex);
		List<State> newHistoryStates = ImmutableList.<State>builder()
					.addAll(historyStatesSublist).add(state).build();
		return new History(newHistoryStates, currentStateIndex+1);
	}
	
	/**
	 * Returns a history with the current state's operation undone.  The previous state becomes current.
	 * @return	The history with the last operation undone.
	 * @throws HistoryException		The current state was the first state (so it cant be undone)
	 */
	public History undo() throws HistoryException {
		if(currentStateIndex == 0 || currentStateIndex == NO_CURRENT_STATE)
			throw new HistoryException("Cannot go back from first state.");
		return new History(states, currentStateIndex-1);
	}
	
	/**
	 * Returns a history with the current state's subsequent operation (re-)done.  The next state becomes current.
	 * @return	The history with the last operation undone.
	 * @throws HistoryException		The current state was the most recent state (so there is nothing to redo).
	 */
	public History redo() throws HistoryException {
		if(currentStateIndex == states.size()-1 || currentStateIndex == NO_CURRENT_STATE)
			throw new HistoryException("Cannot go forward from last state.");
		return new History(states, currentStateIndex+1);
	}
}
