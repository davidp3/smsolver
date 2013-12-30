#show state/2.
#show edge/5.
#show prop/3.
#show parent/2.


%%%%%%%%%%%%%%%%%%%
% INPUT MACHINE
%%%%%%%%%%%%%%%%%%%
%*
input(state(simple, stages)).							% Later changed to par(stages)
input(state(simple, app_splash)).
input(state(simple, publisher_splash)).
input(parent(app_splash, publisher_splash)).	% Later reparented to stages
input(edge(stages, app_splash, no_cond, no_event, no_action)).				% Later deleted
input(state(simple, dumb_state)).						% Later deleted
input(edge(dumb_state, publisher_splash, no_cond, no_event, no_action)).		% Deleted because dumb_state is deleted
input(prop(dumb_state, on_enter, noop)).		% Deleted because dumb_state is deleted
input(state(simple, publisher_init)).
input(state(simple, publisher_run)).
input(state(final, publisher_end)).
input(prop(top_state, initial, stages)).

input(state(simple, publisher_shallow_hist)).
input(state(simple, publisher_start)).
input(prop(publisher_splash, initial, publisher_start)).
input(parent(publisher_splash,publisher_start)).
input(parent(publisher_splash,publisher_shallow_hist)).
input(edge(publisher_run, publisher_end, no_cond, publisher_stop_evt, script(src(publisher_stop_action)))).
*%
%%%%%%%%%%%%%%%%%%%
% INPUT COMMANDS
%%%%%%%%%%%%%%%%%%%
%*
change(state(shallow,publisher_shallow_hist)).

set(edge(publisher_start, publisher_init, no_cond, no_event, no_action)).
set(edge(publisher_init, publisher_run, no_cond, publisher_init_evt, script(publisher_init_action))).
% set(edge(publisher_shallow_hist, publisher_init, no_cond, no_event, no_action)). % System connects _hist to _start by default

delete(edge(stages, app_splash, X1, X2, X3)) :- input(edge(stages, app_splash, X1, X2, X3)).

change(state(par, stages)).								% input simple(stages) is changed to par(stages)
change(edge(app_splash, publisher_splash, timer(seconds(1)), no_event, script(src(start_main_music)))).

change(edge(publisher_run, publisher_end, no_cond, publisher_stop_evt, script(src(publisher_stop_action))),
	edge(publisher_run, publisher_end, no_cond, changed_publisher_stop_evt, script(src(changed_publisher_stop_action)))).

set(prop(app_splash, on_enter, show_splash)).
set(prop(app_splash, on_exit, kill_splash)).
% set(prop(app_splash, on_exit, set_will_fail)).	% Cant set to both kill_splash and set_will_fail

test(prop(app_splash, on_exit, kill_splash)).		% Silly test to prove a point
% test(prop(app_splash, on_exit, test_will_fail)).		% Silly test to prove a point

change(parent(stages, app_splash)).
change(parent(stages, publisher_splash)).
change(parent(publisher_splash,publisher_init)).
change(parent(publisher_splash,publisher_run)).
change(parent(publisher_splash,publisher_end)).

delete(state(simple, dumb_state)).
*%
%%%%%%%%%%%%%%%%%%%
% ENGINE
%%%%%%%%%%%%%%%%%%%

% change/2 is different than change/1 in that it works for edges and properties.  For edges/props, it works by
% delete()ing the original (parameter #1) and set()ing the new one (parameter #2).  For states, it works
% the same as change/1.  Note that you cannot change from a state to an edge or any such weirdness (currently silently skips such commands).
:- change(parent(_,Child), parent(_,Child2)), Child != Child2.		% change/2 only 'changes' parent of same state (otherwise its really gibberish)
:- change(X,X).		% no-op change is disallowed for no particular reason.
% Change/2 for states requires both have the same ID (cant change state ID yet)
:- change(state(_, Id2), state(_, Id)), Id2 != Id.

set(parent(X1,X2)) :- change(parent(_,_),parent(X1,X2)).
delete(parent(X1,X2)) :- change(parent(X1,X2),parent(_,_)).
set(prop(X1,X2,X3)) :- change(prop(_,_,_),prop(X1,X2,X3)).
delete(prop(X1,X2,X3)) :- change(prop(X1,X2,X3),prop(_,_,_)).
delete(edge(X1,X2,X3,X4,X5)) :- change(edge(X1,X2,X3,X4,X5),edge(_,_,_,_,_)). 
set(edge(X1,X2,X3,X4,X5)) :- change(edge(_,_,_,_,_),edge(X1,X2,X3,X4,X5)).
change(state(Type, Id)) :- change(state(_, Id), state(Type, Id)).

pseudo_state(X) :- state(final, X).
pseudo_state(X) :- state(shallow, X).
pseudo_state(X) :- state(deep, X).

history(Id) :- state(shallow, Id).
history(Id) :- state(deep, Id).

% The top_state is the parent of the entire machine and always exists.
% Parenting to top_state is equivalent to being top-level.
state(simple, top_state).
:- parent(_, top_state).
has_non_top_parent(X) :- Y != top_state, parent(Y, X).
parent(top_state, X) :- state(_, X), not has_non_top_parent(X), X != top_state.		% parent is top_state unless otherwise specified

:- not parent(_,X), state(_,X), X != top_state.     				% All states but top_state must have parents

state(Type, Elt) :- input(state(Type, Elt)), not -state(Type, Elt).
parent(X1,X2) :- input(parent(X1,X2)), not -parent(X1,X2).
edge(X1,X2,X3,X4,X5) :- input(edge(X1,X2,X3,X4,X5)), not -edge(X1,X2,X3,X4,X5).
prop(X1,X2,X3) :- input(prop(X1,X2,X3)), not -prop(X1,X2,X3).

%  delete() command removes State / Edge / Property.  Only works on 'input()' elements.   
-state(Type, State) :- delete(state(Type, State)).
-edge(X1,X2,X3,X4,X5) :- delete(edge(X1,X2,X3,X4,X5)). 
-prop(X1,X2,X3) :- delete(prop(X1,X2,X3)). 
-parent(X1,X2) :- delete(parent(X1,X2)). 

% edge(SrcStateId, DestStateId).
:- edge(Id, _, _, _, _), not state(_,Id).
:- edge(_, Id, _, _, _), not state(_,Id), Id != no_target.

% parent(ParentStateId, StateId).
:- parent(Id, A), parent(Y, A), Id != Y.			% constr: only one parent
:- parent(_, Id), not state(_,Id).					% parent must be a state
:- parent(Id, _), not state(_,Id).					% only states have parents

% ancestor(AncestorStateId, StateId)
ancestor(GParentId, Id) :- ancestor(GParentId, ParentId), ancestor(ParentId, Id).
ancestor(ParentId, Id) :- parent(ParentId, Id).
:- ancestor(ParentId, Id), ancestor(Id, ParentId).				% no ancestor cycles!

% common_ancestor(Ancestor, child0, child1)
common_ancestor(Anc, S0, S1) :- ancestor(Anc, S0), ancestor(Anc, S1).

% max_common_ancestor(Ancestor, child0, child1)
max_common_ancestor(Anc, S0, S1) :- common_ancestor(Anc, S0, S1), not -max_common_ancestor(Anc, S0, S1).
-max_common_ancestor(Anc0, S0, S1) :- common_ancestor(Anc0, S0, S1), common_ancestor(Anc1, S0, S1), ancestor(Anc0, Anc1). 

% prop(Id, PropertyName, PropertyValue)
% props may not have two distinct values
:- prop(Id, PName, Val1), prop(Id, PName, Val2), Val1 != Val2.

% change
delete(prop(Id, Prop, Val2)) :- change(prop(Id, Prop, Val)), input(prop(Id, Prop, Val2)), Val2 != Val.
delete(state(OldType, Id)) :- change(state(NewType, Id)), input(state(OldType, Id)), OldType != NewType.

state(Type, Elt) :- change(state(Type, Elt)).
edge(X1,X2,X3,X4,X5) :- change(edge(X1,X2,X3,X4,X5)).
prop(X1,X2,X3) :- change(prop(X1,X2,X3)).
parent(X1,X2) :- change(parent(X1,X2)).

% set
state(Type, Elt) :- set(state(Type, Elt)).
edge(X1,X2,X3,X4,X5) :- set(edge(X1,X2,X3,X4,X5)).
prop(X1,X2,X3) :- set(prop(X1,X2,X3)).
parent(X1,X2) :- set(parent(X1,X2)).

% test
:- not state(Type, Elt), test(state(Type, Elt)).
:- not edge(X1,X2,X3,X4,X5), test(edge(X1,X2,X3,X4,X5)).
:- not prop(X1,X2,X3), test(prop(X1,X2,X3)).
:- not parent(X1,X2), test(parent(X1,X2)).

% valid state types are simple, par, shallow, deep, final
:- state(Type,_), Type != simple, Type != par, Type != shallow, Type != deep, Type != final.

% no_target is not a valid state ID
:- state(_,no_target).

%  Move State Into State
% set(parent(ParentState, State)).
% change(parent(ParentState, State)).
parent(ParentState, State) :- change(parent(ParentState, State)).
delete(parent(OldParentState, State)) :- change(parent(ParentState, State)), 
					input(parent(OldParentState, State)), OldParentState != ParentState.

% deleted states delete adjacent edges and properties as well.  Note that changed states are NOT included
% since the state still exists (so not state() will still be false).
delete(edge(DeletedState,X2,X3,X4,X5)) :- input(edge(DeletedState,X2,X3,X4,X5)), not state(_,DeletedState).
delete(edge(X1,DeletedState,X3,X4,X5)) :- input(edge(X1,DeletedState,X3,X4,X5)), not state(_,DeletedState), DeletedState != no_target.
delete(prop(DeletedState,X2,X3)) :- input(prop(DeletedState,X2,X3)), not state(_,DeletedState).

% These properties hold for initial states and deep history states (call them s#_) where # is a number.
% Note: we must use different numbered functors because otherwise initial and deep history state lists would mix!
s0_(State, Declarer) :- prop(Declarer, initial, State).
s1_(State, Declarer) :- state(deep, State), parent(Declarer, State).

% s0_s/s1_s must only target descendants of their declarer.
:- not ancestor(Decl,Child), s0_(Child,Decl).
:- not ancestor(Decl,Child), s1_(Child,Decl).


% Explanation of below: anc0 is NOT the max ancestor if there are 3 initial states and anc0 is the max ancestor
% of two while anc1 is the max ancestor of two others and anc1 is an ancestor of anc0.  The max ancestor
% is then the max ancestor that is 'not not' the max ancestor.
% initial
-s0_max_common_ancestor(Anc0, Declarer) :- s0_(State0, Declarer), s0_(State1, Declarer), s0_(State2, Declarer),
	State0 != State1, State0 != State2, max_common_ancestor(Anc0, State0, State1), max_common_ancestor(Anc1, State0, State2),
	ancestor(Anc1, Anc0). 
s0_max_common_ancestor(Anc, Declarer) :- s0_(State0, Declarer), s0_(State1, Declarer),
	max_common_ancestor(Anc, State0, State1), not -s0_max_common_ancestor(Anc, Declarer).
% deep history
-s1_max_common_ancestor(Anc0, Declarer) :- s1_(State0, Declarer), s1_(State1, Declarer), s1_(State2, Declarer),
	State0 != State1, State0 != State2, max_common_ancestor(Anc0, State0, State1), max_common_ancestor(Anc1, State0, State2),
	ancestor(Anc1, Anc0). 
s1_max_common_ancestor(Anc, Declarer) :- s1_(State0, Declarer), s1_(State1, Declarer),
	max_common_ancestor(Anc, State0, State1), not -s1_max_common_ancestor(Anc, Declarer).
% edges.
s2_(Tgt,Src) :- edge(Src,Tgt,_,_,_).
-s2_max_common_ancestor(Anc0, Declarer) :- s2_(State0, Declarer), s2_(State1, Declarer), s2_(State2, Declarer),
	State0 != State1, State0 != State2, max_common_ancestor(Anc0, State0, State1), max_common_ancestor(Anc1, State0, State2),
	ancestor(Anc1, Anc0). 
s2_max_common_ancestor(Anc, Declarer) :- s2_(State0, Declarer), s2_(State1, Declarer),
	max_common_ancestor(Anc, State0, State1), not -s2_max_common_ancestor(Anc, Declarer).
	
	
% If there are multiple s0_s/s1_s then their max_common_ancestor must be parallel.
% (Any missing parallel tracks will take their default transition).
:- s0_max_common_ancestor(Anc, Decl), s0_(S0, Decl), s0_(S1, Decl), S0 != S1, not state(par, Anc).
:- s1_max_common_ancestor(Anc, Decl), s1_(S0, Decl), s1_(S1, Decl), S0 != S1, not state(par, Anc).

% TODO: DOES NOT WORK!  This should be for compound edges (edges with multiple targets) which I dont
% model properly.  At present, multiple edges are just multiple edges.
% Update: ignore this.  The only difference compound edges provide is that what would be dummy-states
% in diagrams are drawn as fork 'bars' but they behave exactly the same way.  So create dummy states
% on XML-to-Prolog ingest.
% :- s2_max_common_ancestor(Anc, Decl), s2_(S0, Decl), s2_(S1, Decl), S0 != S1, not state(par, Anc).

% No final states as direct children of a parallel state.
:- parent(X,Y), state(par, X), state(final, Y).

% final/shallow/deep states (ie pseudo_state) cannot be compound states
:- parent(X, _), pseudo_state(X).

% No transitions from a final state.
:- edge(Id,_,_,_,_), state(final, Id).

% History states must have a transition.  If they dont then we add a transition to the 
% default initial state of their parent.
% TODO: This is crap but it works correctly.  -edge2 plays the role of strong-negation of edge, ignoring
% conds and actions as we dont care about them when using the strong neg term below.  There is nothing
% technically wrong with this but it is confusing.
-edge2(X1,X2) :- state(_,X1), state(_,X2), not edge(X1,X2,_,_,_).
edge(HistState, StartState, no_cond, no_event, no_action) :- not -edge2(HistState, StartState), history(HistState), 
				parent(Parent, HistState), prop(Parent, initial, StartState).

% History-state transitions must have a target.
:- history(HistState), edge(HistState,no_target,_,_,_).

% Shallow history states must target exactly one immediate child of their parent.
:- state(shallow, S), edge(S,X,_,_,_), edge(S,Y,_,_,_), X != Y.
:- state(shallow, Shallow), parent(Parent, Shallow), edge(Shallow,Target,_,_,_), not parent(Parent, Target).

% history states outgoing transitions must not have conditions.  They may have actions.  They must have an outgoing
% transition.  (They may still have multiple outgoing transitions if targeting states inside of parallel tracks). 
:- history(Id), not edge(Id,_,_,_,_).
:- history(Id), edge(Id,_,Cond,_,_), Cond != no_cond.

% History states may not be top-level
:- parent(top_state, History), history(History).

