package com.soywiz.dynarek

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import java.lang.reflect.Field
import java.lang.reflect.Method

class JvmGenerator(val log: Boolean) {
	var dynarekLastId = 0

	val Method.signature: String
		get() {
			val args = this.parameterTypes.map { it.internalName2 }.joinToString("")
			val ret = this.returnType.internalName2
			return "($args)$ret"
		}

	val Class<*>.internalName: String get() = this.name.replace('.', '/')
	val Class<*>.internalName2: String
		get() = when {
			isPrimitive -> {
				when (this) {
					java.lang.Void.TYPE -> "V"
					java.lang.Integer.TYPE -> "I"
					java.lang.Float.TYPE -> "F"
					else -> TODO("Unknown primitive $this")
				}
			}
			isArray -> "[" + this.componentType.internalName2
			else -> "L${this.internalName};"
		}
//fun Field.getDesc() =

	fun DFieldAccess<*, *>.getField(): Field {
		val leftClazz = this.clazz.java
		val field = leftClazz.getDeclaredField(prop.name)
		field.isAccessible = true
		return field
	}

	inline fun log(msgGen: () -> String) {
		if (log) println(msgGen())
	}

	val jvmOpcodes by lazy { JvmOpcodes.values().map { it.id to it }.toMap() }

	fun MethodVisitor._visitInsn(opcode: Int) {
		log { "visitInsn(${jvmOpcodes[opcode]})" }
		visitInsn(opcode)
	}

	fun MethodVisitor._visitVarInsn(opcode: Int, _var: Int) {
		log { "visitVarInsn(${jvmOpcodes[opcode]}, $_var)" }
		visitVarInsn(opcode, _var)
	}

	fun MethodVisitor._visitFieldInsn(opcode: Int, owner: String, name: String, desc: String) {
		log { "visitFieldInsn(${jvmOpcodes[opcode]}, $owner, $name, $desc)" }
		visitFieldInsn(opcode, owner, name, desc)
	}

	fun MethodVisitor._visitTypeInsn(opcode: Int, type: String): Unit {
		log { "visitTypeInsn(${jvmOpcodes[opcode]}, $type)" }
		visitTypeInsn(opcode, type)
	}

	fun MethodVisitor._visitLdcInsn(cst: Any?): Unit {
		log { "visitLdcInsn($cst)" }
		if (cst is Boolean) {
			visitLdcInsn(if (cst) 1 else 0)
		} else {
			visitLdcInsn(cst)
		}
	}

	fun MethodVisitor._visitMethodInsn(opcode: Int, owner: String, name: String, desc: String, itf: Boolean): Unit {
		log { "visitMethodInsn(${jvmOpcodes[opcode]}, $owner, $name, $desc, $itf)" }
		visitMethodInsn(opcode, owner, name, desc, itf)
	}

	fun MethodVisitor._visitIntInsn(opcode: Int, v: Int): Unit {
		log { "visitIntInsn(${jvmOpcodes[opcode]}, $v)" }
		visitIntInsn(opcode, v)
	}

	fun MethodVisitor._visitFieldInsn(opcode: Int, field: Field): Unit = _visitFieldInsn(opcode, field.declaringClass.internalName, field.name, field.type.internalName2)
	fun MethodVisitor._visitTypeInsn(opcode: Int, type: Class<*>): Unit = _visitTypeInsn(opcode, type.internalName)

	fun MethodVisitor._visitCastTo(type: Class<*>): Unit {
		if (type.isPrimitive) {
			when { // unbox
				type.isPrimitiveIntClass() -> {
					_visitTypeInsn(CHECKCAST, "java/lang/Integer")
					_visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false)
				}
			}
		} else {
			_visitTypeInsn(CHECKCAST, type)
		}
	}

	fun MethodVisitor.visit(expr: DExpr<*>): Unit = when (expr) {
		is DArg<*> -> {
			val aindex = expr.index + 1
			val clazz = expr.clazz
			when (clazz) {
				java.lang.Integer.TYPE.kotlin -> _visitVarInsn(ILOAD, aindex)
				else -> _visitVarInsn(ALOAD, aindex)
			}
		}
		is DBinopInt -> {
			visit(expr.left)
			visit(expr.right)
			when (expr.op) {
				"+" -> _visitInsn(IADD)
				"-" -> _visitInsn(ISUB)
				"*" -> _visitInsn(IMUL)
				"/" -> _visitInsn(IDIV)
				"%" -> _visitInsn(IREM)
				"|" -> _visitInsn(IOR)
				"&" -> _visitInsn(IAND)
				"^" -> _visitInsn(IXOR)
				"<<" -> _visitInsn(ISHL)
				">>" -> _visitInsn(ISHR)
				">>>" -> _visitInsn(IUSHR)
				else -> TODO("Unsupported operator ${expr.op}")
			}
		}
		is DBinopIntBool -> {
			visit(expr.left)
			visit(expr.right)
			val opcode = when (expr.op) {
				"==" -> IF_ICMPEQ
				"!=" -> IF_ICMPNE
				">=" -> IF_ICMPGE
				">" -> IF_ICMPGT
				"<=" -> IF_ICMPLE
				"<" -> IF_ICMPLT
				else -> TODO("Unsupported operator ${expr.op}")
			}
			val label1 = Label()
			val label2 = Label()
			visitJumpInsn(opcode, label1)
			_visitLdcInsn(true)
			visitJumpInsn(GOTO, label2)
			visitLabel(label1)
			_visitLdcInsn(false)
			visitLabel(label2)
		}
		is DFieldAccess<*, *> -> {
			visit(expr.obj)
			_visitFieldInsn(GETFIELD, expr.getField())
		}
		is DExprInvoke<*, *> -> {
			val clazz = expr.clazz.java
			val name = expr.name
			for (arg in expr.args) visit(arg)
			val method = clazz.declaredMethods.firstOrNull { it.name == name } ?: throw IllegalArgumentException("Can't find method $clazz.$name")

			_visitMethodInsn(INVOKEVIRTUAL, clazz.internalName, method.name, method.signature, false)
			if (method.returnType == Void.TYPE) {
				visitInsn(ACONST_NULL)
			}
			Unit
		}
		is DLiteral<*> -> {
			val value = expr.value
			when (value) {
				is Int -> _visitLdcInsn(value)
				is Boolean -> _visitLdcInsn(value)
				else -> TODO("MethodVisitor.visit: $expr")
			}
		}
		is DLocal<*> -> {
			visitLocal(expr)
		}
		else -> TODO("MethodVisitor.visit: $expr")
	}

	fun MethodVisitor.visit(stm: DStm): Unit = when (stm) {
		is DAssign<*> -> {
			val left = stm.left
			val right = stm.value
			when (left) {
				is DFieldAccess<*, *> -> {
					visit(left.obj)
					visit(right)
					_visitFieldInsn(PUTFIELD, left.getField())
				}
				is DLocal<*> -> {
					visit(right)
					visitAssignLocal(left)
				}
				else -> TODO("MethodVisitor.visit.DAssign: $left, $right")
			}
		}
		is DStms -> {
			for (s in stm.stms) visit(s)
		}
		is DStmExpr -> {
			visit(stm.expr)
			visitInsn(POP)
		}
		is DIfElse -> {
			val cond = stm.cond
			val strue = stm.strue
			val sfalse = stm.sfalse

			if (sfalse == null) {
				// IF
				val endLabel = Label()
				visit(cond)
				visitJumpInsn(IFEQ, endLabel)
				visit(strue)
				visitLabel(endLabel)
			} else {
				// IF+ELSE
				val elseLabel = Label()
				val endLabel = Label()

				visit(cond)
				visitJumpInsn(IFEQ, elseLabel)

				visit(strue)
				visitJumpInsn(GOTO, endLabel)

				visitLabel(elseLabel)
				visit(sfalse)

				visitLabel(endLabel)
			}
			Unit
		}
		is DWhile -> {
			val startLabel = Label()
			val endLabel = Label()
			visitLabel(startLabel)
			visit(stm.cond)
			visitJumpInsn(IFNE, endLabel)
			visit(stm.block)
			visitJumpInsn(GOTO, startLabel)
			visitLabel(endLabel)
		}
		else -> TODO("MethodVisitor.visit: $stm")
	}

	fun MethodVisitor.visit(func: DFunction) {
		visit(func.body)
	}

	fun Class<*>.isPrimitiveIntClass() = this == java.lang.Integer.TYPE

	var localsToIndex: Map<DLocal<*>, Int> = HashMap<DLocal<*>, Int>()

	val DLocal<*>.index: Int get() = localsToIndex[this] ?: -1

	fun MethodVisitor.visitAssignLocal(local: DLocal<*>) {
		val clazz = local.clazz.java
		when {
			clazz.isPrimitive -> when {
				clazz.isPrimitiveIntClass() -> _visitIntInsn(ISTORE, local.index)
				else -> TODO("Unsupported $clazz")
			}
			else -> _visitIntInsn(ASTORE, local.index)
		}
	}

	fun MethodVisitor.visitLocal(local: DLocal<*>) {
		val clazz = local.clazz.java
		when {
			clazz.isPrimitive -> when {
				clazz.isPrimitiveIntClass() -> _visitIntInsn(ILOAD, local.index)
				else -> TODO("Unsupported $clazz")
			}
			else -> _visitIntInsn(ALOAD, local.index)
		}
	}

	fun <T> _generateDynarek(func: DFunction, interfaceClass: Class<T>): T {
		val nargs = func.args.size
		val classId = dynarekLastId++
		val cw = ClassWriter(0)
		val className = "com/soywiz/dynarek/Generated$classId"
		val refObj = "Ljava/lang/Object;"
		val refObjArgs = (0 until nargs).map { refObj }.joinToString("")
		cw.visit(V1_5, ACC_PUBLIC or ACC_FINAL, className, null, "java/lang/Object", arrayOf(interfaceClass.canonicalName.replace('.', '/')))

		val typedArgSignature = func.args.map { it.clazz.java.internalName2 }.joinToString("")
		val typedRetSignature = func.ret.clazz.java.internalName2
		//println(typedArgSignature)
		//println(typedRetSignature)

		val startIndex = 1 + func.args.size
		val locals = func.getLocals()
		localsToIndex = locals.withIndex().map { it.value to (startIndex + it.index) }.toMap()
		val maxLocal = localsToIndex.map { it.value }.max() ?: startIndex

		cw.apply {
			// constructor
			visitMethod(ACC_PUBLIC, "<init>", "()V", null, null).apply {
				visitMaxs(2, 1)
				visitVarInsn(ALOAD, 0) // push `this` to the operand stack
				visitMethodInsn(INVOKESPECIAL, Type.getInternalName(Any::class.java), "<init>", "()V", false) // call the constructor of super class
				visitInsn(RETURN)
				visitEnd()
			}
			// invoke method
			visitMethod(ACC_PUBLIC, "invoke", "($typedArgSignature)$typedRetSignature", null, null).apply {
				log { "-------- invoke($typedArgSignature)$typedRetSignature" }
				//visitMaxs(32, maxLocal)
				visitMaxs(32, maxLocal + 1)

				for (local in locals) {
					visit(local.initialValue)
					visitAssignLocal(local)
				}

				visit(func)
				visitInsn(ACONST_NULL)
				visitInsn(ARETURN)
				visitEnd()
			}
			visitMethod(ACC_PUBLIC, "invoke", "($refObjArgs)$refObj", null, null).apply {
				log { "-------- invoke($refObjArgs)$refObj\", null, null)" }
				visitMaxs(16, 16)
				_visitVarInsn(ALOAD, 0) // this
				for ((index, arg) in func.args.withIndex()) {
					val jclazz = arg.clazz.java
					_visitVarInsn(ALOAD, index + 1) // this
					_visitCastTo(jclazz)
				}
				_visitMethodInsn(INVOKEVIRTUAL, className, "invoke", "($typedArgSignature)$typedRetSignature", false)
				//visitInsn(POP)
				//visitInsn(ACONST_NULL)
				visitInsn(ARETURN)
				visitEnd()
			}
		}
		cw.visitEnd()
		val classBytes = cw.toByteArray()

		val gclazz = createDynamicClass(ClassLoader.getSystemClassLoader(), className.replace('/', '.'), classBytes)
		return gclazz.declaredConstructors.first().newInstance() as T
	}

	fun createDynamicClass(parent: ClassLoader, clazzName: String, b: ByteArray): Class<*> = OwnClassLoader(parent).defineClass(clazzName, b)

	private class OwnClassLoader(parent: ClassLoader) : ClassLoader(parent) {
		fun defineClass(name: String, b: ByteArray): Class<*> = defineClass(name, b, 0, b.size)
	}
}

//fun <T> _generateDynarek(func: DFunction, interfaceClass: Class<T>): T = JvmGenerator(log = true)._generateDynarek(func, interfaceClass)
fun <T> _generateDynarek(func: DFunction, interfaceClass: Class<T>): T = JvmGenerator(log = false)._generateDynarek(func, interfaceClass)
