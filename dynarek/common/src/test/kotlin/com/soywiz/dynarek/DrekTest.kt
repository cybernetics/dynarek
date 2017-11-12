package com.soywiz.dynarek

import org.junit.Test
import kotlin.test.assertEquals

class DrekTest {
	@Test
	fun testInterpreter() {
		val function = function(DClass(State::class), DINT, DVOID) {
			SET(p0[State::a], p1 * 2.lit)
		}

		val state = State()
		val interpreter = DSlowInterpreter(listOf(state, 10))
		interpreter.interpret(function)
		assertEquals(20, state.a)
	}

	//fun testWithState(state: State): State {
	//}

	@Test
	fun testDynarek() {
		val function = function(DClass(State::class), DINT, DVOID) {
			SET(p0[State::a], p0[State::a] + 4.lit * p1)
		}
		val state = State(a = 7)
		val func = function.generateDynarek()
		val ret = func(state, 2)

		assertEquals(15, state.a)
	}

	@Test
	fun testDynarek2() {
		val function = function(DClass(State::class), DINT, DVOID) {
			IF(true) {
				SET(p0[State::a], p0[State::a] + 7.lit * p1)
			}

			IF(true) {
				SET(p0[State::a], p0[State::a] + 4.lit * p1)
			} ELSE {
				SET(p0[State::a], 9.lit * p1)
			}

			IF(false) {
				SET(p0[State::a], p0[State::a] + 4.lit * p1)
			} ELSE {
				SET(p0[State::a], p0[State::a] + 11.lit * p1)
			}
		}
		val state = State(a = 7)
		val func = function.generateDynarek()
		val ret = func(state, 3)

		assertEquals(73, state.a)
	}

	@Test
	fun testDynarekInvokeMethod() {
		val function = function(DClass(State::class), DINT, DVOID) {
			STM(State::mulAB.invoke(p0))
		}
		val state = State(a = 7, b = 3)
		function.generateDynarek()(state, 3)

		assertEquals(7 * 3, state.a)
	}

	@Test
	fun testDynarekInvokeMethod2() {
		val function = function(DClass(State::class), DINT, DVOID) {
			STM(State::mulABArg.invoke(p0, 11.lit))
		}
		val state = State(a = 7, b = 3)
		function.generateDynarek()(state, 3)

		assertEquals(7 * 3 * 11, state.a)
	}

	@Test
	fun testDynarekInvokeMethod3() {
		val function = function(DClass(State::class), DINT, DVOID) {
			STM(State::mulABArg2.invoke(p0, 11.lit, 9.lit))
		}
		val state = State(a = 7, b = 3)
		function.generateDynarek()(state, 3)

		assertEquals(7 * 3 * (11 + 9), state.a)
	}

	@Test
	fun testWhile() {
		val function = function(DClass(State::class), DINT, DVOID) {
			WHILE(p0[State::b] gt 0.lit) {
				SET(p0[State::a], p0[State::a] + 1.lit)
				SET(p0[State::b], p0[State::b] - 1.lit)
			}
		}
		val state = State(a = 7, b = 3)
		function.generateDynarek()(state, 3)

		assertEquals(7 + 3, state.a)
	}

	@Test
	fun testImult() {
		val function = function(DClass(State::class), DINT, DVOID) {
			SET(p0[State::a], (Int.MAX_VALUE - 1).lit * (Int.MAX_VALUE - 2).lit)
		}
		val state = State(a = 7, b = 3)
		function.generateDynarek()(state, 3)

		assertEquals(-2147483642, state.a)
	}
}
