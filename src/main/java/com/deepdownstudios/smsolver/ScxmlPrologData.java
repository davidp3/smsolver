package com.deepdownstudios.smsolver;

import alice.tuprolog.Struct;

public class ScxmlPrologData {
	public static final String TOP_STATE_STR = "top_state";
	public static final String INITIAL_STR = "initial";
	public static final String PROP_STR = "prop";
	public static final String PARENT_STR = "parent";
	public static final String SIMPLE_STR = "simple";
	public static final String PARALLEL_STR = "par";
	public static final String RUN_STR = "run";
	public static final String ON_ENTRY_STR = "onentry";
	public static final String ON_EXIT_STR = "onexit";
	public static final String RAISE_STR = "raise";
	public static final String NO_CONTENT_STR = "noop";
	public static final String NO_COND_STR = "no_cond";
	public static final String IF_STR = "if";
	public static final String FOREACH_STR = "foreach";
	public static final String NO_INDEX_STR = "no_index";
	public static final String NO_LABEL_STR = "no_label";
	public static final String NO_LOG_EXPR_STR = "no_message";
	public static final String LOG_STR = "log";
	public static final String ASSIGN_STR = "assign";
	public static final String EOL_STR = "\n";
	public static final String SCRIPT_SRC_STR = "src";
	public static final String SCRIPT_STR = "script";
	public static final String DEEP_STR = "deep";
	public static final String SHALLOW_STR = "shallow";
	public static final String NO_EVENTS_STR = "no_event";
	public static final String NO_TARGET_STR = "no_target";
	public static final String NO_ACTION_STR = "no_action";
	public static final String EDGE_STR = "edge";
	public static final String STATE_STR = "state";
	public static final String FINAL_STR = "final";
	
	public static final Struct PROP_ATOM = new Struct(PROP_STR);
	public static final Struct INITIAL_ATOM = new Struct(INITIAL_STR);
	public static final Struct TOP_STATE_ATOM = new Struct(TOP_STATE_STR);
	public static final Struct PARENT_ATOM = new Struct(PARENT_STR);
	public static final Struct SIMPLE_ATOM = new Struct(SIMPLE_STR);
	public static final Struct PARALLEL_ATOM = new Struct(PARALLEL_STR);
	public static final Struct RUN_ATOM = new Struct(RUN_STR);
	public static final Struct ON_ENTRY_ATOM = new Struct(ON_ENTRY_STR);
	public static final Struct ON_EXIT_ATOM = new Struct(ON_EXIT_STR);
	public static final Struct RAISE_ATOM = new Struct(RAISE_STR);
	public static final Struct NO_CONTENT_ATOM = new Struct(NO_CONTENT_STR);
	public static final Struct NO_COND_ATOM = new Struct(NO_COND_STR);
	public static final Struct IF_ATOM = new Struct(IF_STR);
	public static final Struct FOREACH_ATOM = new Struct(FOREACH_STR);
	public static final Struct NO_INDEX_ATOM = new Struct(NO_INDEX_STR);
	public static final Struct NO_LABEL_ATOM = new Struct(NO_LABEL_STR);
	public static final Struct NO_LOG_EXPR_ATOM = new Struct(NO_LOG_EXPR_STR);
	public static final Struct LOG_ATOM = new Struct(LOG_STR);
	public static final Struct ASSIGN_ATOM = new Struct(ASSIGN_STR);
	public static final Struct SCRIPT_SRC_ATOM = new Struct(SCRIPT_SRC_STR);
	public static final Struct SCRIPT_ATOM = new Struct(SCRIPT_STR);
	public static final Struct DEEP_ATOM = new Struct(DEEP_STR);
	public static final Struct SHALLOW_ATOM = new Struct(SHALLOW_STR);
	public static final Struct NO_EVENTS_ATOM = new Struct(NO_EVENTS_STR);
	public static final Struct NO_TARGET_ATOM = new Struct(NO_TARGET_STR);
	public static final Struct NO_ACTION_ATOM = new Struct(NO_ACTION_STR);
	public static final Struct EDGE_ATOM = new Struct(EDGE_STR);
	public static final Struct STATE_ATOM = new Struct(STATE_STR);
	public static final Struct FINAL_ATOM = new Struct(FINAL_STR);
}
