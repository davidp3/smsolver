#show simple/1.
#show par/1.
#show start/1.
#show terminate/1.
#show shallow/1.
#show deep/1.
#show edge/4.
#show prop/3.
#show parent/2.

%%%%%%%%%%%%%%%%%%%
% INPUT MACHINE
%%%%%%%%%%%%%%%%%%%

%*
input(simple(stages)).							% Later changed to par(stages)
input(simple(app_splash)).
input(simple(publisher_splash)).
input(parent(app_splash, publisher_splash)).	% Later reparented to stages
input(edge(stages, app_splash, no_cond, no_action)).				% Later deleted
input(simple(dumb_state)).						% Later deleted
input(edge(dumb_state, publisher_splash, no_cond, no_action)).		% Deleted because dumb_state is deleted
input(prop(dumb_state, on_enter, noop)).		% Deleted because dumb_state is deleted
input(start(publisher_start)).
input(simple(publisher_init)).
input(simple(publisher_run)).
input(terminate(publisher_end)).
input(deep(publisher_deep_hist)).
input(edge(publisher_run, publisher_end, publisher_stop_evt, publisher_stop_action)).
*%

%%%%%%%%%%%%%%%%%%%
% INPUT COMMANDS
%%%%%%%%%%%%%%%%%%%

%*
set(edge(publisher_start, publisher_init, no_cond, no_action)).
set(edge(publisher_init, publisher_run, publisher_init_evt, publisher_init_action)).
% set(edge(publisher_deep_hist, publisher_init, no_cond, no_action)). % System connects _hist to _start by default

delete(edge(stages, app_splash, X1, X2)) :- input(edge(stages, app_splash, X1, X2)).

change(par(stages)).								% input simple(stages) is changed to par(stages)
change(edge(app_splash, publisher_splash, timer(seconds(1)), start_main_music)).

change(edge(publisher_run, publisher_end, publisher_stop_evt, publisher_stop_action),
	edge(publisher_run, publisher_end, changed_publisher_stop_evt, changed_publisher_stop_action)).

set(prop(app_splash, on_enter, show_splash)).
set(prop(app_splash, on_exit, kill_splash)).
 set(prop(app_splash, on_exit, set_will_fail)).	% Cant set to both kill_splash and set_will_fail

test(prop(app_splash, on_exit, kill_splash)).		% Silly test to prove a point
% test(prop(app_splash, on_exit, test_will_fail)).		% Silly test to prove a point

change(parent(stages, app_splash)).
change(parent(stages, publisher_splash)).
change(parent(publisher_splash,publisher_start)).
change(parent(publisher_splash,publisher_init)).
change(parent(publisher_splash,publisher_run)).
change(parent(publisher_splash,publisher_end)).
change(parent(publisher_splash,publisher_deep_hist)).

delete(simple(dumb_state)).
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
:- change(Orig, simple(Id)),  inputStateById(Orig, Id2), Id2 != Id.
:- change(Orig, par(Id)),  inputStateById(Orig, Id2), Id2 != Id.
:- change(Orig, shallow(Id)),  inputStateById(Orig, Id2), Id2 != Id.
:- change(Orig, deep(Id)),  inputStateById(Orig, Id2), Id2 != Id.
:- change(Orig, start(Id)),  inputStateById(Orig, Id2), Id2 != Id.
:- change(Orig, terminate(Id)),  inputStateById(Orig, Id2), Id2 != Id.

set(parent(X1,X2)) :- change(parent(_,_),parent(X1,X2)).
delete(parent(X1,X2)) :- change(parent(X1,X2),parent(_,_)).
set(prop(X1,X2,X3)) :- change(prop(_,_,_),prop(X1,X2,X3)).
delete(prop(X1,X2,X3)) :- change(prop(X1,X2,X3),prop(_,_,_)).
delete(edge(X1,X2,X3,X4)) :- change(edge(X1,X2,X3,X4),edge(_,_,_,_)). 
set(edge(X1,X2,X3,X4)) :- change(edge(_,_,_,_),edge(X1,X2,X3,X4)).
change(NewState) :- change(OrigState,NewState), inputStateById(OrigState, _).		% inputStateById is just a test to make sure its a state

pseudo_state(X) :- start(X).
pseudo_state(X) :- terminate(X).
pseudo_state(X) :- shallow(X).
pseudo_state(X) :- deep(X).

history(Id) :- shallow(Id).
history(Id) :- deep(Id).

% The top_state is the parent of the entire machine and always exists.
% Parenting to top_state is equivalent to being top-level.
simple(top_state).
:- parent(_, top_state).
has_non_top_parent(X) :- state(Y), state(X), Y != top_state, parent(Y, X).
parent(top_state, X) :- state(X), not has_non_top_parent(X), X != top_state.	% parent is top_state unless otherwise specified

:- not parent(_,X), state(X), X != top_state.     				% All states but top_state must have parents

simple(Elt) :- input(simple(Elt)), not -simple(Elt).
start(Elt) :- input(start(Elt)), not -start(Elt).
terminate(Elt) :- input(terminate(Elt)), not -terminate(Elt).
shallow(Elt) :- input(shallow(Elt)), not -shallow(Elt).
deep(Elt) :- input(deep(Elt)), not -deep(Elt).
parent(X1,X2) :- input(parent(X1,X2)), not -parent(X1,X2).
par(X) :- input(par(X)), not -par(X).
edge(X1,X2,X3,X4) :- input(edge(X1,X2,X3,X4)), not -edge(X1,X2,X3,X4).
prop(X1,X2,X3) :- input(prop(X1,X2,X3)), not -prop(X1,X2,X3).

%  delete() command removes State / Edge / Property.  Only works on 'input()' elements.   
-simple(State) :- delete(simple(State)).
-par(State) :- delete(par(State)).
-start(State) :- delete(start(State)).
-terminate(State) :- delete(terminate(State)).
-shallow(State) :- delete(shallow(State)).
-deep(State) :- delete(deep(State)).
-edge(X1,X2,X3,X4) :- delete(edge(X1,X2,X3,X4)). 
-prop(X1,X2,X3) :- delete(prop(X1,X2,X3)). 
-parent(X1,X2) :- delete(parent(X1,X2)). 

% state is anything simple or parallel
state(Id) :- simple(Id).
state(Id) :- par(Id).
state(Id) :- start(Id).
state(Id) :- terminate(Id).
state(Id) :- shallow(Id).
state(Id) :- deep(Id).

% edge(SrcStateId, DestStateId).
:- edge(Id, _, _, _), not state(Id).
:- edge(_, Id, _, _), not state(Id).

% parent(ParentStateId, StateId).
:- parent(Id, A), parent(Y, A), Id != Y.			% constr: only one parent
:- parent(_, Id), not state(Id).					% parent must be a state
:- parent(Id, _), not state(Id).					% only states have parents

% ancestor(AncestorStateId, StateId)
ancestor(GParentId, Id) :- ancestor(GParentId, ParentId), ancestor(ParentId, Id).
ancestor(ParentId, Id) :- parent(ParentId, Id).
:- ancestor(ParentId, Id), ancestor(Id, ParentId).				% no ancestor cycles!

% prop(Id, PropertyName, PropertyValue)
% props may not have two distinct values
:- prop(Id, PName, Val1), prop(Id, PName, Val2), Val1 != Val2.

inputStateById(simple(Id), Id) :- input(simple(Id)).
inputStateById(par(Id), Id) :- input(par(Id)).
inputStateById(start(Id), Id) :- input(start(Id)).
inputStateById(terminate(Id), Id) :- input(terminate(Id)).
inputStateById(shallow(Id), Id) :- input(shallow(Id)).
inputStateById(deep(Id), Id) :- input(deep(Id)).

delete(prop(Id, Prop, Val2)) :- change(prop(Id, Prop, Val)), input(prop(Id, Prop, Val2)), Val2 != Val.
delete(X) :- change(par(Id)), inputStateById(X, Id), X != par(Id).
delete(X) :- change(simple(Id)), inputStateById(X, Id), X != simple(Id).
delete(X) :- change(start(Id)), inputStateById(X, Id), X != start(Id).
delete(X) :- change(terminate(Id)), inputStateById(X, Id), X != terminate(Id).
delete(X) :- change(shallow(Id)), inputStateById(X, Id), X != shallow(Id).
delete(X) :- change(deep(Id)), inputStateById(X, Id), X != deep(Id).

simple(Elt) :- change(simple(Elt)).
par(Elt) :- change(par(Elt)).
start(Elt) :- change(start(Elt)).
terminate(Elt) :- change(terminate(Elt)).
shallow(Elt) :- change(shallow(Elt)).
deep(Elt) :- change(deep(Elt)).
edge(X1,X2,X3,X4) :- change(edge(X1,X2,X3,X4)).
prop(X1,X2,X3) :- change(prop(X1,X2,X3)).
parent(X1,X2) :- change(parent(X1,X2)).

simple(Elt) :- set(simple(Elt)).
par(Elt) :- set(par(Elt)).
start(Elt) :- set(start(Elt)).
terminate(Elt) :- set(terminate(Elt)).
shallow(Elt) :- set(shallow(Elt)).
deep(Elt) :- set(deep(Elt)).
edge(X1,X2,X3,X4) :- set(edge(X1,X2,X3,X4)).
prop(X1,X2,X3) :- set(prop(X1,X2,X3)).
parent(X1,X2) :- set(parent(X1,X2)).

:- not simple(Elt), test(simple(Elt)).
:- not par(Elt), test(par(Elt)).
:- not start(Elt), test(start(Elt)).
:- not terminate(Elt), test(terminate(Elt)).
:- not shallow(Elt), test(shallow(Elt)).
:- not deep(Elt), test(deep(Elt)).
:- not edge(X1,X2,X3,X4), test(edge(X1,X2,X3,X4)).
:- not prop(X1,X2,X3), test(prop(X1,X2,X3)).
:- not parent(X1,X2), test(parent(X1,X2)).

%  Move State Into State
% set(parent(ParentState, State)).
% change(parent(ParentState, State)).
parent(ParentState, State) :- change(parent(ParentState, State)).
delete(parent(OldParentState, State)) :- change(parent(ParentState, State)), 
					input(parent(OldParentState, State)), OldParentState != ParentState.

% deleted states delete adjacent edges and properties as well.  Note that changed states are NOT included
% since the state still exists (so not state() will still be false).
delete(edge(DeletedState,X2,X3,X4)) :- input(edge(DeletedState,X2,X3,X4)), not state(DeletedState).
delete(edge(X1,DeletedState,X3,X4)) :- input(edge(X1,DeletedState,X3,X4)), not state(DeletedState).
delete(prop(DeletedState,X2,X3)) :- input(prop(DeletedState,X2,X3)), not state(DeletedState).

% At most one start state in any (simple) state
:- parent(X, Y), parent(X, Z), start(Y), start(Z), Y != Z.

% No pseudo_state as tracks for a parallel state (parallel forks must be simple or parallel states)
:- parent(X,Y), par(X), pseudo_state(Y).

% start/terminate/shallow/deep states (ie pseudo_state) cannot be compound states
:- parent(X, _), pseudo_state(X).

% No onEntry/onExit for start states
:- prop(Id, on_entry, _), start(Id).
:- prop(Id, on_exit, _), start(Id).

% No transitions from a terminate state.
:- edge(Id,_,_,_), terminate(Id).

% Transitions from start states must not have conditions.  It may have actions.
:- edge(Id,_,Cond,_), Cond != no_cond, start(Id).

% history states must have exactly one unconditioned exit transition.  May have actions.
:- history(Id), not edge(Id,_,_,_).
:- history(Id), edge(Id,X,_,_), edge(Id,Y,_,_), X != Y.
:- history(Id), edge(Id,_,_,X), edge(Id,_,_,Y), X != Y.
:- history(Id), edge(Id,_,Cond,_), Cond != no_cond.

% NON-STANDARD: I am imposing a rule that a history state by default has its default transition
% as a condition-less, action-less transition targetting the adjacent start state.
% TODO: This is crap but it works correctly.  -edge2 plays the role of strong-negation of edge, ignoring
% conds and actions as we dont care about them when using the strong neg term below.  There is nothing
% technically wrong with this but it is confusing.
-edge2(X1,X2) :- state(X1), state(X2), not edge(X1,X2,_,_).
edge(HistState, StartState, no_cond, no_action) :- not -edge2(HistState, StartState), history(HistState), parent(Parent, HistState),
				parent(Parent, StartState), start(StartState). 

% I'm rejecting from the standard:
% * The standard allows specifying any state as a start ("initial") state thru the "state"s "initial" attribute.
%   I require initial states to be start() states.  This does not restrict or seriously complicate any functionality.
%   In other words, if you had:
%   <state id="Test5PSub1" initial="Test5PSub1Final">
%           <final id="Test5PSub1Final"/>
%   </state>
%   Then you need
%   simple(test5PSub1).   terminate(test5PSub1Final).   start(dummy_start).
%   parent(test5PSub1, test5PSub1Final).   parent(test5PSub1, dummy_start).
%   edge(dummy_start, test5PSub1Final, no_cond, no_action).



