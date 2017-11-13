package com.soywiz.dynarek

import kotlin.reflect.*

interface DNode

interface DType<T : Any> : DNode {
	val clazz: KClass<T>
}

data class DClass<T : Any>(override val clazz: KClass<T>) : DType<T>
data class DPrimType<T : Any>(override val clazz: KClass<T>, val id: Int) : DType<T>

val DVOID = DPrimType<Unit>(Unit::class, 0)
val DINT = DPrimType<Int>(Int::class, 1)
val DFLOAT = DPrimType<Float>(Float::class, 2)
val DBOOL = DPrimType<Boolean>(Boolean::class, 3)

interface DExpr<T> : DNode
data class DLiteral<T>(val value: T) : DExpr<T>
data class DArg<T : Any>(val clazz: KClass<T>, val index: Int) : DExpr<T>
data class DBinopInt(val left: DExpr<Int>, val op: String, val right: DExpr<Int>) : DExpr<Int>
data class DBinopIntBool(val left: DExpr<Int>, val op: String, val right: DExpr<Int>) : DExpr<Boolean>
class DLocal<T : Any>(val clazz: KClass<T>, val initialValue: DExpr<T>) : DRef<T>, DExpr<T>

interface DExprInvoke<TThis : Any, TR : Any> : DExpr<TR> {
	val clazz: KClass<TThis>
	val name: String
	val args: List<DExpr<*>>
}

data class DExprInvoke1<TThis : Any, TR : Any>(override val clazz: KClass<TThis>, val func: KFunction1<TThis, TR>, val p0: DExpr<TThis>) : DExprInvoke<TThis, TR> {
	override val name = func.name
	override val args = listOf(p0)
}

data class DExprInvoke2<TThis : Any, T1 : Any, TR : Any>(override val clazz: KClass<TThis>, val func: KFunction2<TThis, T1, TR>, val p0: DExpr<TThis>, val p1: DExpr<T1>) : DExprInvoke<TThis, TR> {
	override val name = func.name
	override val args = listOf(p0, p1)
}

data class DExprInvoke3<TThis : Any, T1 : Any, T2 : Any, TR : Any>(override val clazz: KClass<TThis>, val func: KFunction3<TThis, T1, T2, TR>, val p0: DExpr<TThis>, val p1: DExpr<T1>, val p2: DExpr<T2>) : DExprInvoke<TThis, TR> {
	override val name = func.name
	override val args = listOf(p0, p1, p2)
}

interface DRef<T> : DNode
data class DFieldAccess<T : Any, TR>(val clazz: KClass<T>, val obj: DExpr<T>, val prop: KMutableProperty1<T, TR>) : DExpr<TR>, DRef<TR>
//data class DInstanceMethod1<T : Any, TR>(val clazz: KClass<T>, val obj: DExpr<T>, val prop: KFunction1<T, TR>)

interface DStm : DNode
data class DStms(val stms: List<DStm>) : DStm
data class DReturnExpr<T>(val expr: DExpr<T>) : DStm
data class DReturnVoid(val dummy: Boolean) : DStm
data class DAssign<T>(val left: DRef<T>, val value: DExpr<T>) : DStm
data class DStmExpr(val expr: DExpr<*>) : DStm

data class DIfElse(val cond: DExpr<Boolean>, val strue: DStm, var sfalse: DStm? = null) : DStm
data class DWhile(val cond: DExpr<Boolean>, val block: DStm) : DStm
