package com.deepdownstudios.smsolver;

import java.util.List;

import com.google.common.collect.ImmutableList;

public class History {
	private static final int NO_CURRENT_STATE = -1;
	
	private final List<State> historyStates;
	private final int currentStateIndex;
	
	private static List<State> emptyStateList = ImmutableList.<State>of();
	
	public History()	{
		this.historyStates = emptyStateList;
		this.currentStateIndex = NO_CURRENT_STATE;
	}
	public History(List<State> historyStates, int currentStateIndex)	{
		this.historyStates = ImmutableList.<State>copyOf(historyStates);
		this.currentStateIndex = currentStateIndex;
	}
	
	public class HistoryException extends CommandException	{
		private static final long serialVersionUID = 92391205L;
		public HistoryException(String message) {
			super(message);
		}
	}
	
	public State getCurrentState() throws HistoryException	{
		if(currentStateIndex == NO_CURRENT_STATE)
			throw new HistoryException("No valid states in history.");
		return historyStates.get(currentStateIndex);
	}
	
	public History pushState(State state)	{
		if(currentStateIndex == NO_CURRENT_STATE)	{
			assert historyStates.isEmpty();
			return new History(ImmutableList.of(state), 0);
		}

		List<State> historyStatesSublist = historyStates.subList(0, currentStateIndex);
		List<State> newHistoryStates = ImmutableList.<State>builder()
					.addAll(historyStatesSublist).add(state).build();
		return new History(newHistoryStates, currentStateIndex+1);
	}
	
	public History undo() throws HistoryException {
		if(currentStateIndex == 0 || currentStateIndex == NO_CURRENT_STATE)
			throw new HistoryException("Cannot go back from first state.");
		return new History(historyStates, currentStateIndex-1);
	}
	
	public History redo() throws HistoryException {
		if(currentStateIndex == historyStates.size()-1 || currentStateIndex == NO_CURRENT_STATE)
			throw new HistoryException("Cannot go forward from last state.");
		return new History(historyStates, currentStateIndex+1);
	}
}
