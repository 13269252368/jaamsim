/*
 * JaamSim Discrete Event Simulation
 * Copyright (C) 2014 Ausenco Engineering Canada Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package com.jaamsim.input;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.junit.Test;

public class TestExpParser {

	private static void testToken(ExpTokenizer.Token tok, int type, String val) {
		assertTrue(tok.type == type);
		assertTrue(tok.value.equals(val));
	}

	@Test
	public void testTokenize() throws ExpTokenizer.Error {
		ArrayList<ExpTokenizer.Token> tokens = ExpTokenizer.tokenize(" a b c 1 2 3 + -");

		assertTrue(tokens.size() == 8);
		testToken(tokens.get(0), ExpTokenizer.VAR_TYPE, "a");
		testToken(tokens.get(1), ExpTokenizer.VAR_TYPE, "b");
		testToken(tokens.get(2), ExpTokenizer.VAR_TYPE, "c");

		testToken(tokens.get(3), ExpTokenizer.NUM_TYPE, "1");
		testToken(tokens.get(4), ExpTokenizer.NUM_TYPE, "2");
		testToken(tokens.get(5), ExpTokenizer.NUM_TYPE, "3");

		testToken(tokens.get(6), ExpTokenizer.SYM_TYPE, "+");
		testToken(tokens.get(7), ExpTokenizer.SYM_TYPE, "-");

		tokens = ExpTokenizer.tokenize("foo bar blarg123");
		assertTrue(tokens.size() == 3);
		testToken(tokens.get(0), ExpTokenizer.VAR_TYPE, "foo");
		testToken(tokens.get(1), ExpTokenizer.VAR_TYPE, "bar");
		testToken(tokens.get(2), ExpTokenizer.VAR_TYPE, "blarg123");


		tokens = ExpTokenizer.tokenize("bar.frump ( -12.3)");
		assertTrue(tokens.size() == 7);
		testToken(tokens.get(0), ExpTokenizer.VAR_TYPE, "bar");
		testToken(tokens.get(1), ExpTokenizer.SYM_TYPE, ".");
		testToken(tokens.get(2), ExpTokenizer.VAR_TYPE, "frump");
		testToken(tokens.get(3), ExpTokenizer.SYM_TYPE, "(");
		testToken(tokens.get(4), ExpTokenizer.SYM_TYPE, "-");
		testToken(tokens.get(5), ExpTokenizer.NUM_TYPE, "12.3");
		testToken(tokens.get(6), ExpTokenizer.SYM_TYPE, ")");

		tokens = ExpTokenizer.tokenize("-12.3e6 ... ---");
		assertTrue(tokens.size() == 8);
		testToken(tokens.get(0), ExpTokenizer.SYM_TYPE, "-");
		testToken(tokens.get(1), ExpTokenizer.NUM_TYPE, "12.3e6");
		testToken(tokens.get(2), ExpTokenizer.SYM_TYPE, ".");
		testToken(tokens.get(3), ExpTokenizer.SYM_TYPE, ".");
		testToken(tokens.get(4), ExpTokenizer.SYM_TYPE, ".");
		testToken(tokens.get(5), ExpTokenizer.SYM_TYPE, "-");
		testToken(tokens.get(6), ExpTokenizer.SYM_TYPE, "-");
		testToken(tokens.get(7), ExpTokenizer.SYM_TYPE, "-");

		tokens = ExpTokenizer.tokenize("-42.3E-6");
		assertTrue(tokens.size() == 2);
		testToken(tokens.get(0), ExpTokenizer.SYM_TYPE, "-");
		testToken(tokens.get(1), ExpTokenizer.NUM_TYPE, "42.3E-6");

		tokens = ExpTokenizer.tokenize("[123][abc]   [+-  2]");
		assertTrue(tokens.size() == 3);
		testToken(tokens.get(0), ExpTokenizer.SQ_TYPE, "123");
		testToken(tokens.get(1), ExpTokenizer.SQ_TYPE, "abc");
		testToken(tokens.get(2), ExpTokenizer.SQ_TYPE, "+-  2");

		// Test long symbol parsing
		tokens = ExpTokenizer.tokenize("&&||==<==&|");
		assertTrue(tokens.size() == 7);
		testToken(tokens.get(0), ExpTokenizer.SYM_TYPE, "&&");
		testToken(tokens.get(1), ExpTokenizer.SYM_TYPE, "||");
		testToken(tokens.get(2), ExpTokenizer.SYM_TYPE, "==");
		testToken(tokens.get(3), ExpTokenizer.SYM_TYPE, "<=");
		testToken(tokens.get(4), ExpTokenizer.SYM_TYPE, "=");
		testToken(tokens.get(5), ExpTokenizer.SYM_TYPE, "&");
		testToken(tokens.get(6), ExpTokenizer.SYM_TYPE, "|");

	}

	@Test
	public void testParser() throws ExpParser.Error {
		class ValLookup implements ExpParser.VarTable {
			@Override
			public ExpResult getVariableValue(String[] name) {
				if (name[0].equals("foo")) return new ExpResult(4);
				if (name[0].equals("bar")) return new ExpResult(3);
				return new ExpResult(1);
			}
		}

		ValLookup vl = new ValLookup();

		ExpParser.Expression exp = ExpParser.parseExpression("2*5 + 3*5*(3-1)+2");
		double val = exp.evaluate(vl).value;
		assertTrue(val == 42);

		exp = ExpParser.parseExpression("max(3, 42)");
		val = exp.evaluate(vl).value;
		assertTrue(val == 42);

		exp = ExpParser.parseExpression("abs(-42)");
		val = exp.evaluate(vl).value;
		assertTrue(val == 42);

		exp = ExpParser.parseExpression("abs(+42)");
		val = exp.evaluate(vl).value;
		assertTrue(val == 42);

		exp = ExpParser.parseExpression("[foo]*[bar]");
		val = exp.evaluate(vl).value;
		assertTrue(val == 12);

		exp = ExpParser.parseExpression("50/2/5"); // left associative
		val = exp.evaluate(vl).value;
		assertTrue(val == 5);

		exp = ExpParser.parseExpression("2^2^3"); // right associative
		val = exp.evaluate(vl).value;
		assertTrue(val == 256);

		exp = ExpParser.parseExpression("1 + 2^2*4 + 2*[foo]");
		val = exp.evaluate(vl).value;
		assertTrue(val == 25);

		exp = ExpParser.parseExpression("1 + 2^(2*4) + 2");
		val = exp.evaluate(vl).value;
		assertTrue(val == 259);

		exp = ExpParser.parseExpression("2----2"); // A quadruple negative
		val = exp.evaluate(vl).value;
		assertTrue(val == 4);

		exp = ExpParser.parseExpression("2---+-2"); // Still a quadruple negative
		val = exp.evaluate(vl).value;
		assertTrue(val == 4);

		exp = ExpParser.parseExpression("(((((1+1)))*5))");
		val = exp.evaluate(vl).value;
		assertTrue(val == 10);

		exp = ExpParser.parseExpression("!42");
		val = exp.evaluate(vl).value;
		assertTrue(val == 0);

		exp = ExpParser.parseExpression("!0");
		val = exp.evaluate(vl).value;
		assertTrue(val == 1);

		exp = ExpParser.parseExpression("42 == 42");
		val = exp.evaluate(vl).value;
		assertTrue(val == 1);

		exp = ExpParser.parseExpression("42 == 41");
		val = exp.evaluate(vl).value;
		assertTrue(val == 0);

		exp = ExpParser.parseExpression("42 != 42");
		val = exp.evaluate(vl).value;
		assertTrue(val == 0);

		exp = ExpParser.parseExpression("42 != 41");
		val = exp.evaluate(vl).value;
		assertTrue(val == 1);

		exp = ExpParser.parseExpression("42 || 0");
		val = exp.evaluate(vl).value;
		assertTrue(val == 1);

		exp = ExpParser.parseExpression("0 || 42");
		val = exp.evaluate(vl).value;
		assertTrue(val == 1);

		exp = ExpParser.parseExpression("0 || 0");
		val = exp.evaluate(vl).value;
		assertTrue(val == 0);

		exp = ExpParser.parseExpression("42 && 0");
		val = exp.evaluate(vl).value;
		assertTrue(val == 0);

		exp = ExpParser.parseExpression("0 && 42");
		val = exp.evaluate(vl).value;
		assertTrue(val == 0);

		exp = ExpParser.parseExpression("1 && 2");
		val = exp.evaluate(vl).value;
		assertTrue(val == 1);

		exp = ExpParser.parseExpression("!(1&&42)");
		val = exp.evaluate(vl).value;
		assertTrue(val == 0);

		exp = ExpParser.parseExpression("!!(1&&42)");
		val = exp.evaluate(vl).value;
		assertTrue(val == 1);

		exp = ExpParser.parseExpression("42<41");
		val = exp.evaluate(vl).value;
		assertTrue(val == 0);
		exp = ExpParser.parseExpression("41<42");
		val = exp.evaluate(vl).value;
		assertTrue(val == 1);
		exp = ExpParser.parseExpression("42>41");
		val = exp.evaluate(vl).value;
		assertTrue(val == 1);
		exp = ExpParser.parseExpression("41>42");
		val = exp.evaluate(vl).value;
		assertTrue(val == 0);

		exp = ExpParser.parseExpression("42<=41");
		val = exp.evaluate(vl).value;
		assertTrue(val == 0);
		exp = ExpParser.parseExpression("41<=42");
		val = exp.evaluate(vl).value;
		assertTrue(val == 1);
		exp = ExpParser.parseExpression("42>=41");
		val = exp.evaluate(vl).value;
		assertTrue(val == 1);
		exp = ExpParser.parseExpression("41>=42");
		val = exp.evaluate(vl).value;
		assertTrue(val == 0);

		exp = ExpParser.parseExpression("42>=42");
		val = exp.evaluate(vl).value;
		assertTrue(val == 1);
		exp = ExpParser.parseExpression("42>=42");
		val = exp.evaluate(vl).value;
		assertTrue(val == 1);
		exp = ExpParser.parseExpression("42>42");
		val = exp.evaluate(vl).value;
		assertTrue(val == 0);
		exp = ExpParser.parseExpression("42>42");
		val = exp.evaluate(vl).value;
		assertTrue(val == 0);

	}

	@Test
	public void testVariables() throws ExpParser.Error {
		class ValLookup implements ExpParser.VarTable {
			@Override
			public ExpResult getVariableValue(String[] name) {
				if (name.length < 1 || !name[0].equals("foo")) return new ExpResult(0);

				if (name.length >= 3 && name[1].equals("bar") && name[2].equals("baz")) return new ExpResult(4);
				if (name.length >= 2 && name[1].equals("bonk")) return new ExpResult(5);

				return new ExpResult(-1);
			}
		}
		ValLookup vl = new ValLookup();

		ExpParser.Expression exp = ExpParser.parseExpression("[foo].bar.baz");
		double val = exp.evaluate(vl).value;
		assertTrue(val == 4);

		exp = ExpParser.parseExpression("[foo].bar.baz*4");
		val = exp.evaluate(vl).value;
		assertTrue(val == 16);

		exp = ExpParser.parseExpression("[foo].bonk");
		val = exp.evaluate(vl).value;
		assertTrue(val == 5);

		exp = ExpParser.parseExpression("[bob].is.your.uncle");
		val = exp.evaluate(vl).value;
		assertTrue(val == 0);

		exp = ExpParser.parseExpression("[foo]");
		val = exp.evaluate(vl).value;
		assertTrue(val == -1);

	}

	@Test
	public void testAssignment() throws ExpParser.Error {

		class ValLookup implements ExpParser.VarTable {
			@Override
			public ExpResult getVariableValue(String[] name) {
				return new ExpResult(-1);
			}
		}
		ValLookup vl = new ValLookup();

		ExpParser.Assignment assign = ExpParser.parseAssignment("[foo].bar = 40 + 2");

		assertTrue(assign.destination.length == 2);
		assertTrue(assign.destination[0].equals("foo"));
		assertTrue(assign.destination[1].equals("bar"));
		assertTrue(assign.value.evaluate(vl).value == 42);
	}
}
