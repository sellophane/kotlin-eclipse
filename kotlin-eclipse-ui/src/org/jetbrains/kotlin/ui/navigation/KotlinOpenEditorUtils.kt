package org.jetbrains.kotlin.ui.navigation

import org.jetbrains.kotlin.psi.JetVisitorVoid
import com.intellij.psi.PsiElement
import org.eclipse.jdt.core.IJavaElement
import org.jetbrains.kotlin.psi.JetElement
import org.eclipse.jdt.core.IType
import org.jetbrains.kotlin.psi.JetVisitor
import org.jetbrains.kotlin.psi.JetClass
import org.eclipse.jdt.core.IMethod
import org.jetbrains.kotlin.psi.JetNamedFunction
import org.jetbrains.kotlin.psi.JetFile
import kotlin.MutableList
import java.util.ArrayList
import org.jetbrains.kotlin.load.kotlin.PackageClassUtils
import org.jetbrains.kotlin.psi.JetClassBody
import org.jetbrains.kotlin.psi.JetObjectDeclaration
import org.jetbrains.kotlin.name.FqName
import org.eclipse.jdt.core.IField

fun findKotlinDeclarations(element: IJavaElement, jetFile: JetFile): List<JetElement> {
	val result = ArrayList<JetElement>()
	val visitor = makeVisitor(element, result)
	if (visitor != null) {
		jetFile.acceptChildren(visitor)
	}

	return result
}

fun makeVisitor(element: IJavaElement, result: MutableList<JetElement>): JetVisitorVoid? {
	return when (element) {
		is IType -> object : JetAllVisitor() {
			override fun visitClass(jetClass: JetClass) {
				val fqName = jetClass.getFqName()
				if (fqName != null) {
					val javaFqName = FqName(element.getFullyQualifiedName('.'))
					if (fqName == javaFqName) {
						result.add(jetClass)
						return
					}
				}
				jetClass.acceptChildren(this)
			}
		}
		is IField -> object: JetAllVisitor() {
			override fun visitObjectDeclaration(declaration: JetObjectDeclaration) {
				val fqName = declaration.getName()
				if (fqName != null && fqName == element.getElementName()) {
					if (equalsDeclaringTypes(declaration, element)) {
						result.add(declaration)
						return
					}
				}

				declaration.acceptChildren(this)
			}
		}
		is IMethod -> 
			if (element.isConstructor()) {
				return makeVisitor(element.getDeclaringType(), result)
			} else {
				object : JetAllVisitor() {
					override fun visitNamedFunction(function: JetNamedFunction) {
						if (function.getName() == element.getElementName()) {
							if (equalsDeclaringTypes(function, element)) {
								result.add(function)
								return
							}
						}
						
						function.acceptChildren(this)
					}
				}
			}
		else -> null
	}
}

fun equalsDeclaringTypes(declaration: JetObjectDeclaration, javaField: IField): Boolean {
	val parent = declaration.getParent()
	val classFqName = (parent.getParent() as JetClass).getFqName()
	if (classFqName != null) {
		val javaFqName = FqName(javaField.getDeclaringType().getFullyQualifiedName('.'))
		if (classFqName == javaFqName) {
			return true
		}
	}
	
	return false
}

fun equalsDeclaringTypes(function: JetNamedFunction, javaElement: IMethod): Boolean {
	val parent = function.getParent()
	val fqName: FqName? = when (parent) {
			is JetFile -> PackageClassUtils.getPackageClassFqName(parent.getPackageFqName())
			is JetClassBody -> {
				val p = parent.getParent()
				when (p) {
					is JetClass -> p.getFqName()
					is JetObjectDeclaration -> p.getFqName()
					else -> null
				}
			}
			else -> null
		}

	if (fqName != null) {
		val javaFqName = FqName(javaElement.getDeclaringType().getFullyQualifiedName('.'))
		if (fqName == javaFqName) {
			return true
		}
	}
	
	return false
}

open class JetAllVisitor() : JetVisitorVoid() {
	override fun visitElement(element: PsiElement) {
		element.acceptChildren(this)
	}
}