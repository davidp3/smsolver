package com.deepdownstudios.smsolver;

import java.util.List;

import static com.deepdownstudios.smsolver.ScxmlPrologData.*;
import alice.tuprolog.Term;
import com.deepdownstudios.scxml.jaxb.ScxmlScxmlType;

public class PrologToScxml {
	public static ScxmlScxmlType prologToScxml(String name, List<Term> terms) {
		ScxmlScxmlType scxmlType = new ScxmlScxmlType();
		scxmlType.setName(name);
		return scxmlType;
	}

	
}
