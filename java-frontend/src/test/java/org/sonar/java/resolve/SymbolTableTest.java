/*
 * SonarQube Java
 * Copyright (C) 2012-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java.resolve;

import com.google.common.collect.Iterables;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.semantic.Type;
import org.sonar.plugins.java.api.tree.AssignmentExpressionTree;
import org.sonar.plugins.java.api.tree.BlockTree;
import org.sonar.plugins.java.api.tree.ConditionalExpressionTree;
import org.sonar.plugins.java.api.tree.ExpressionStatementTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.LambdaExpressionTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.NewClassTree;
import org.sonar.plugins.java.api.tree.ParenthesizedTree;
import org.sonar.plugins.java.api.tree.ReturnStatementTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.VariableTree;

import java.util.List;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;

public class SymbolTableTest {

  @Rule
  public ExpectedException expectedEx = ExpectedException.none();

  @Test
  public void Generics() {
    Result result = Result.createFor("Generics");
    JavaSymbol.TypeJavaSymbol typeSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("A");
    JavaSymbol symbolA1 = result.symbol("a1");
    assertThat(symbolA1.type.symbol).isSameAs(typeSymbol);
    JavaSymbol symbolA2 = result.symbol("a1");
    assertThat(symbolA2.type.symbol).isSameAs(typeSymbol);
    assertThat(symbolA2.type).isSameAs(symbolA1.type);
    JavaSymbol symbolA3 = result.symbol("a1");
    assertThat(symbolA3.type.symbol).isSameAs(typeSymbol);
    assertThat(result.reference(12, 5)).isSameAs(result.symbol("foo", 8));
    assertThat(result.reference(13, 5)).isSameAs(result.symbol("foo", 9));

    // Check erasure
    TypeVariableJavaType STypeVariableType = (TypeVariableJavaType) typeSymbol.typeParameters.lookup("S").get(0).type;
    assertThat(STypeVariableType.erasure().getSymbol().getName()).isEqualTo("CharSequence");
    JavaType arrayErasure = typeSymbol.members().lookup("arrayErasure").get(0).type;
    assertThat(arrayErasure.isTagged(JavaType.ARRAY)).isTrue();
    assertThat(arrayErasure.erasure().isTagged(JavaType.ARRAY)).isTrue();
    assertThat(((ArrayJavaType) arrayErasure.erasure()).elementType().symbol.getName()).isEqualTo("CharSequence");

    IdentifierTree tree = result.referenceTree(20, 7);
    JavaType symbolType = (JavaType) tree.symbolType();
    assertThat(symbolType).isInstanceOf(ParametrizedTypeJavaType.class);
    ParametrizedTypeJavaType ptt = (ParametrizedTypeJavaType) symbolType;
    assertThat(ptt.symbol.getName()).isEqualTo("C");
    assertThat(ptt.typeSubstitution.size()).isEqualTo(1);
    assertThat(ptt.typeSubstitution.substitutedType(ptt.typeSubstitution.typeVariables().iterator().next()).symbol.getName()).isEqualTo("String");

    JavaSymbol.MethodJavaSymbol method1 = (JavaSymbol.MethodJavaSymbol) typeSymbol.members().lookup("method1").get(0);
    assertThat(((MethodJavaType) method1.type).resultType).isSameAs(STypeVariableType);

    JavaSymbol.MethodJavaSymbol method2 = (JavaSymbol.MethodJavaSymbol) typeSymbol.members().lookup("method2").get(0);
    TypeVariableJavaType PTypeVariableType = (TypeVariableJavaType) method2.typeParameters().lookup("P").get(0).type;
    assertThat(method2.getReturnType().type).isSameAs(PTypeVariableType);
    assertThat(method2.parameterTypes().get(0)).isSameAs(PTypeVariableType);

    // Type parameter defined in outer class
    JavaSymbol.TypeJavaSymbol classCSymbol = (JavaSymbol.TypeJavaSymbol) typeSymbol.members().lookup("C").get(0);
    JavaSymbol innerClassField = classCSymbol.members().lookup("innerClassField").get(0);
    assertThat(innerClassField.type).isSameAs(STypeVariableType);

    // Unknown parametrized type should be tagged as unknown
    MethodTree methodTree = (MethodTree) result.getTree(result.symbol("unknownSymbol"));
    VariableTree variableTree = (VariableTree) methodTree.block().body().get(0);
    assertThat(variableTree.type().symbolType().isUnknown()).isTrue();

    //Inner class referenced as type parameter in super class/interface
    assertThat(result.reference(68,53)).isSameAs(result.symbol("B", 69));

    JavaSymbol applyMethod = result.symbol("apply");
    assertThat(result.reference(83, 12)).isSameAs(applyMethod);
    assertThat(result.reference(89, 61)).isSameAs(applyMethod);
    // this method does not compile but is resolved whereas it should not.
    assertThat(result.reference(85, 12)).isSameAs(applyMethod);

    assertThat(applyMethod.usages()).hasSize(3);
  }

  @Test
  public void parameterized_method_type() throws Exception {
    Result result = Result.createFor("Generics");
    MethodTree method3 = (MethodTree) result.getTree(result.symbol("method3"));
    VariableTree variable = (VariableTree) method3.block().body().get(0);
    assertThat(variable.initializer().symbolType().symbol().name()).isEqualTo("String");

    MethodTree method4 = (MethodTree) result.getTree(result.symbol("method4"));
    variable = (VariableTree) method4.block().body().get(0);
    Type symbolType = variable.initializer().symbolType();
    assertThat(symbolType).isInstanceOf(ParametrizedTypeJavaType.class);
    ParametrizedTypeJavaType ptt = (ParametrizedTypeJavaType) symbolType;
    assertThat(ptt.typeSubstitution.substitutedTypes().iterator().next().getSymbol().getName()).isEqualTo("String");

    assertThat(result.reference(58, 25)).isSameAs(result.symbol("method_of_e"));
  }

  @Test
  public void extended_type_variables() throws Exception {
    String javaLangObject = "java.lang.Object";
    Result result = Result.createFor("Generics");
    Type MyClass = result.symbol("MyClass", 100).type();
    Type i = result.symbol("I").type();
    Type j = result.symbol("J").type();

    JavaSymbol.TypeJavaSymbol w = (JavaSymbol.TypeJavaSymbol) result.symbol("W", 103);
    assertThat(w.superClass().is(javaLangObject)).isTrue();
    assertThat(w.interfaces()).isEmpty();

    JavaSymbol.TypeJavaSymbol x = (JavaSymbol.TypeJavaSymbol) result.symbol("X", 103);
    assertThat(x.superClass()).isSameAs(MyClass);
    assertThat(x.interfaces()).isEmpty();

    JavaSymbol.TypeJavaSymbol y = (JavaSymbol.TypeJavaSymbol) result.symbol("Y", 103);
    assertThat(y.superClass().is(javaLangObject)).isTrue();
    assertThat(y.interfaces()).containsExactly(i);

    JavaSymbol.TypeJavaSymbol z = (JavaSymbol.TypeJavaSymbol) result.symbol("Z", 103);
    assertThat(z.superClass()).isSameAs(MyClass);
    assertThat(z.interfaces()).containsExactly(i, j);
  }

  @Test
  public void recursive_type_substitution() {
    Result result = Result.createFor("Generics");
    MethodTree ddt_method = (MethodTree) result.getTree(result.symbol("ddt_method"));
    VariableTree variable = (VariableTree) ddt_method.block().body().get(0);
    assertThat(variable.initializer().symbolType().name()).isEqualTo("String");
  }

  @Test
  public void ClassDeclaration() {
    Result result = Result.createFor("declarations/ClassDeclaration");
    JavaSymbol.TypeJavaSymbol typeSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("Declaration");
    JavaSymbol classDeclaration = result.symbol("ClassDeclaration");
    List<JavaSymbol> parameters = classDeclaration.type.symbol.typeParameters.lookup("T");
    assertThat(parameters).hasSize(1);
    assertThat(parameters.get(0).getName()).isEqualTo("T");
    parameters = classDeclaration.type.symbol.typeParameters.lookup("S");
    assertThat(parameters).hasSize(1);
    assertThat(parameters.get(0).getName()).isEqualTo("S");
    assertThat(typeSymbol.owner()).isSameAs(classDeclaration);
    assertThat(typeSymbol.flags()).isEqualTo(Flags.PRIVATE);
    assertThat(typeSymbol.getSuperclass()).isSameAs(result.symbol("Superclass").type);
    assertThat(typeSymbol.getInterfaces()).containsExactly(
      result.symbol("FirstInterface").type,
      result.symbol("SecondInterface").type);
    assertThat(typeSymbol.members.lookup("this")).isNotEmpty();
    assertThat(typeSymbol.members.lookup("super")).hasSize(1);
    JavaSymbol superSymbol = typeSymbol.members.lookup("super").get(0);

    typeSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("Superclass");
    assertThat(superSymbol.type.symbol).isSameAs(typeSymbol);
    assertThat(typeSymbol.symbolMetadata.isAnnotatedWith("java.lang.Override")).isFalse();
    assertThat(typeSymbol.members.lookup("super")).hasSize(1);
    superSymbol = typeSymbol.members.lookup("super").get(0);
    assertThat(superSymbol.owner).isSameAs(typeSymbol);
    assertThat(((JavaSymbol.VariableJavaSymbol) superSymbol).type.symbol).isSameAs(typeSymbol.getSuperclass().symbol);

    JavaSymbol superclass = typeSymbol.getSuperclass().symbol;
    assertThat(superclass.getName()).isEqualTo("Object");
    assertThat(superclass.owner).isInstanceOf(JavaSymbol.PackageJavaSymbol.class);
    assertThat(superclass.owner.getName()).isEqualTo("java.lang");

    assertThat(typeSymbol.getInterfaces()).isEmpty();

    typeSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("Foo");
    assertThat(typeSymbol.getSuperclass()).isSameAs(result.symbol("Baz").type);

    assertThat(result.reference(25, 21)).isSameAs(result.symbol("method"));

    SymbolMetadataResolve metadata = classDeclaration.metadata();
    assertThat(metadata.annotations()).hasSize(1);
    assertThat(metadata.valuesForAnnotation("java.lang.Override")).isNull();
    assertThat(metadata.isAnnotatedWith("java.lang.Override")).isFalse();
    assertThat(metadata.valuesForAnnotation("java.lang.SuppressWarnings")).hasSize(1);
    assertThat(metadata.isAnnotatedWith("java.lang.SuppressWarnings")).isTrue();
  }

  @Test
  public void DirectCyclingClassDeclaration() {
    expectedEx.expectMessage("Cycling class hierarchy detected with symbol : Foo");
    expectedEx.expect(IllegalStateException.class);
    Result.createForJavaFile("src/test/filesInError/DirectCyclingClassDeclaration");
  }

  @Test
  public void CyclingClassDeclaration() {
    expectedEx.expect(IllegalStateException.class);
    expectedEx.expectMessage("Cycling class hierarchy detected with symbol : Qix");
    Result.createForJavaFile("src/test/filesInError/CyclingClassDeclaration");
  }

  @Test
  public void AnonymousClassDeclaration() {
    Result result = Result.createFor("declarations/AnonymousClassDeclaration");

    JavaSymbol.TypeJavaSymbol typeSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("methodInAnonymousClass").owner();
    assertThat(typeSymbol.owner()).isSameAs(result.symbol("method"));
    assertThat(typeSymbol.flags()).isEqualTo(0);
    assertThat(typeSymbol.name).isEqualTo("");
    assertThat(typeSymbol.getSuperclass()).isSameAs(result.symbol("Superclass").type);
    assertThat(typeSymbol.getInterfaces()).isEmpty();
    assertThat(typeSymbol.members.lookup("this")).isNotEmpty();

    typeSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("methodInAnonymousClassInterface").owner();
    assertThat(typeSymbol.owner()).isSameAs(result.symbol("method"));
    assertThat(typeSymbol.flags()).isEqualTo(0);
    assertThat(typeSymbol.name).isEqualTo("");
    assertThat(typeSymbol.getSuperclass().getSymbol().getName()).isEqualTo("Object");
    assertThat(typeSymbol.getInterfaces()).hasSize(1);
    assertThat(typeSymbol.getInterfaces().get(0)).isSameAs(result.symbol("SuperInterface").type);
    assertThat(typeSymbol.members.lookup("this")).isNotEmpty();
  }

  @Test
  public void LocalClassDeclaration() {
    Result result = Result.createFor("declarations/LocalClassDeclaration");

    JavaSymbol.TypeJavaSymbol typeSymbol;
    // TODO no forward references here, for the moment considered as a really rare situation
    // typeSymbol = (Symbol.TypeSymbol) result.symbol("Declaration", 14);
    // assertThat(typeSymbol.getSuperclass()).isSameAs(result.symbol("Superclass", 9));

    typeSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("Declaration", 22);
    assertThat(typeSymbol.getSuperclass()).isSameAs(result.symbol("Superclass", 22 - 2).type);
    assertThat(typeSymbol.members.lookup("this")).isNotEmpty();

    typeSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("Declaration", 25);
    assertThat(typeSymbol.getSuperclass()).isSameAs(result.symbol("Superclass", 9).type);
  }

  @Test
  public void InterfaceDeclaration() {
    Result result = Result.createFor("declarations/InterfaceDeclaration");

    JavaSymbol.TypeJavaSymbol interfaceSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("Declaration");
    assertThat(interfaceSymbol.owner()).isSameAs(result.symbol("InterfaceDeclaration"));
    assertThat(interfaceSymbol.flags()).isEqualTo(Flags.PRIVATE | Flags.INTERFACE | Flags.STATIC);
    assertThat(interfaceSymbol.getSuperclass().getSymbol().getName()).isEqualTo("Object");
    assertThat(interfaceSymbol.getInterfaces()).containsExactly(
      result.symbol("FirstInterface").type,
      result.symbol("SecondInterface").type);
    assertThat(interfaceSymbol.members.lookup("this")).hasSize(1);
    assertThat(interfaceSymbol.members.lookup("super")).isEmpty();

    JavaSymbol.VariableJavaSymbol variableSymbol = (JavaSymbol.VariableJavaSymbol) result.symbol("FIRST_CONSTANT");
    assertThat(variableSymbol.owner()).isSameAs(interfaceSymbol);
    assertThat(variableSymbol.flags()).isEqualTo(Flags.PUBLIC | Flags.STATIC | Flags.FINAL);

    variableSymbol = (JavaSymbol.VariableJavaSymbol) result.symbol("SECOND_CONSTANT");
    assertThat(variableSymbol.owner()).isSameAs(interfaceSymbol);
    assertThat(variableSymbol.flags()).isEqualTo(Flags.PUBLIC | Flags.STATIC | Flags.FINAL);

    JavaSymbol.MethodJavaSymbol methodSymbol = (JavaSymbol.MethodJavaSymbol) result.symbol("method");
    assertThat(methodSymbol.owner()).isSameAs(interfaceSymbol);
    assertThat(methodSymbol.flags()).isEqualTo(Flags.PUBLIC | Flags.ABSTRACT);

    JavaSymbol.MethodJavaSymbol staticMethodSymbol = (JavaSymbol.MethodJavaSymbol) result.symbol("staticMethod");
    assertThat(staticMethodSymbol.owner()).isSameAs(interfaceSymbol);
    assertThat(staticMethodSymbol.flags()).isEqualTo(Flags.PUBLIC | Flags.STATIC);

    JavaSymbol.MethodJavaSymbol defaultMethodSymbol = (JavaSymbol.MethodJavaSymbol) result.symbol("defaultMethod");
    assertThat(defaultMethodSymbol.owner()).isSameAs(interfaceSymbol);
    assertThat(defaultMethodSymbol.flags()).isEqualTo(Flags.DEFAULT | Flags.PUBLIC);

    JavaSymbol.TypeJavaSymbol typeSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("NestedClass");
    assertThat(typeSymbol.owner()).isSameAs(interfaceSymbol);
    assertThat(typeSymbol.flags()).isEqualTo(Flags.PUBLIC | Flags.STATIC);

    typeSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("NestedInterface");
    assertThat(typeSymbol.owner()).isSameAs(interfaceSymbol);
    assertThat(typeSymbol.flags()).isEqualTo(Flags.PUBLIC | Flags.STATIC | Flags.INTERFACE);

    typeSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("NestedEnum");
    assertThat(typeSymbol.owner()).isSameAs(interfaceSymbol);
    assertThat(typeSymbol.flags()).isEqualTo(Flags.PUBLIC | Flags.ENUM | Flags.STATIC);
  }

  @Test
  public void EnumDeclaration() {
    Result result = Result.createFor("declarations/EnumDeclaration");

    JavaSymbol.TypeJavaSymbol enumSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("Declaration");
    assertThat(enumSymbol.owner()).isSameAs(result.symbol("EnumDeclaration"));
    assertThat(enumSymbol.flags()).isEqualTo(Flags.PRIVATE | Flags.ENUM | Flags.STATIC);

    ParametrizedTypeJavaType superType = (ParametrizedTypeJavaType) enumSymbol.getSuperclass();
    JavaSymbol.TypeJavaSymbol superclass = superType.symbol;
    assertThat(superclass.getName()).isEqualTo("Enum");
    assertThat(superclass.owner).isInstanceOf(JavaSymbol.PackageJavaSymbol.class);
    assertThat(superclass.owner.getName()).isEqualTo("java.lang");
    assertThat(superType.typeSubstitution.size()).isEqualTo(1);
    Map.Entry<TypeVariableJavaType, JavaType> entry = superType.typeSubstitution.substitutionEntries().iterator().next();
    assertThat(entry.getKey()).isSameAs(superclass.typeParameters.lookup("E").get(0).type);
    assertThat(entry.getValue()).isSameAs(enumSymbol.type);
    assertThat(enumSymbol.superClass()).isSameAs(result.symbol("parameterizedDeclaration").type);

    assertThat(enumSymbol.members.lookup("super")).hasSize(1);
    JavaSymbol.VariableJavaSymbol superSymbol = (JavaSymbol.VariableJavaSymbol) enumSymbol.members.lookup("super").get(0);
    assertThat(superSymbol.type).isSameAs(superType);

    assertThat(enumSymbol.getInterfaces()).containsExactly(
      result.symbol("FirstInterface").type,
      result.symbol("SecondInterface").type);
    assertThat(enumSymbol.members.lookup("this")).isNotEmpty();

    JavaSymbol.VariableJavaSymbol variableSymbol = (JavaSymbol.VariableJavaSymbol) result.symbol("FIRST_CONSTANT");
    assertThat(variableSymbol.owner()).isSameAs(enumSymbol);
    assertThat(variableSymbol.flags()).isEqualTo(Flags.PUBLIC | Flags.STATIC | Flags.FINAL | Flags.ENUM);

    JavaSymbol.TypeJavaSymbol anonymousSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("method", 11).owner();
    assertThat(anonymousSymbol.name).isEqualTo("");
    assertThat(anonymousSymbol.owner()).isSameAs(enumSymbol);
    assertThat(anonymousSymbol.flags()).isEqualTo(0); // FIXME should be ENUM
    assertThat(anonymousSymbol.getSuperclass()).isSameAs(result.symbol("Declaration").type);
    assertThat(anonymousSymbol.getInterfaces()).isEmpty();

    variableSymbol = (JavaSymbol.VariableJavaSymbol) result.symbol("SECOND_CONSTANT");
    assertThat(variableSymbol.owner()).isSameAs(enumSymbol);
    assertThat(variableSymbol.flags()).isEqualTo(Flags.PUBLIC | Flags.STATIC | Flags.FINAL | Flags.ENUM);

    anonymousSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("method", 16).owner();
    assertThat(anonymousSymbol.name).isEqualTo("");
    assertThat(anonymousSymbol.owner()).isSameAs(enumSymbol);
    assertThat(anonymousSymbol.flags()).isEqualTo(0); // FIXME should be ENUM
    assertThat(anonymousSymbol.getSuperclass()).isSameAs(result.symbol("Declaration").type);
    assertThat(anonymousSymbol.getInterfaces()).isEmpty();

    JavaSymbol.MethodJavaSymbol methodSymbol = (JavaSymbol.MethodJavaSymbol) result.symbol("method", 21);
    assertThat(methodSymbol.owner()).isSameAs(enumSymbol);
    assertThat(methodSymbol.flags()).isEqualTo(Flags.ABSTRACT);
    JavaSymbol.TypeJavaSymbol enumConstructorSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("ConstructorEnum");
    assertThat(enumConstructorSymbol).isNotNull();
    methodSymbol = (JavaSymbol.MethodJavaSymbol) enumConstructorSymbol.members().lookup("<init>").get(0);
    assertThat(methodSymbol.isPrivate()).isTrue();

    assertThat(result.reference(36, 5)).isSameAs(result.symbol("<init>", 38));
    assertThat(result.reference(37, 5)).isSameAs(result.symbol("<init>", 39));
  }

  @Test
  public void Enum() {
    Result result = Result.createFor("Enum");
    JavaSymbol.TypeJavaSymbol enumSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("Foo");
    assertThat(enumSymbol.flags()).isEqualTo(Flags.PUBLIC | Flags.ENUM);

    ParametrizedTypeJavaType superType = (ParametrizedTypeJavaType) enumSymbol.getSuperclass();
    JavaSymbol.TypeJavaSymbol superclass = superType.symbol;
    assertThat(superclass.getName()).isEqualTo("Enum");

    // usages of methods values() and valueOf(String s) from an enum when enum definition is in file
    JavaSymbol.MethodJavaSymbol valuesMethod = (JavaSymbol.MethodJavaSymbol) enumSymbol.lookupSymbols("values").iterator().next();
    assertThat(valuesMethod.declaration).isNull();
    assertThat(valuesMethod.isStatic()).isTrue();
    assertThat(valuesMethod.parameterTypes()).isEmpty();
    assertThat(((MethodJavaType) valuesMethod.type).resultType).isInstanceOf(ArrayJavaType.class);
    assertThat(((ArrayJavaType) (((MethodJavaType) valuesMethod.type).resultType)).elementType).isSameAs(enumSymbol.type);
    assertThat(result.reference(9, 19)).isSameAs(valuesMethod);
    assertThat(result.reference(9, 5)).isSameAs(result.symbol("useValues", 13));

    JavaSymbol.MethodJavaSymbol valueOfMethod = (JavaSymbol.MethodJavaSymbol) enumSymbol.lookupSymbols("valueOf").iterator().next();
    assertThat(valueOfMethod.declaration).isNull();
    assertThat(valueOfMethod.isStatic()).isTrue();
    assertThat(valueOfMethod.parameterTypes()).hasSize(1);
    assertThat(valueOfMethod.parameterTypes().get(0).is("java.lang.String")).isTrue();
    assertThat(((MethodJavaType) valueOfMethod.type).resultType).isSameAs(enumSymbol.type);
    assertThat(result.reference(10, 20)).isSameAs(valueOfMethod);
    assertThat(result.reference(10, 5)).isSameAs(result.symbol("useValueOf", 14));

    // usages of methods values() and valueOf(String s) from an enum when read from byte code
    assertThat(result.reference(17, 5)).isSameAs(result.symbol("useValues", 21));
    assertThat(result.reference(18, 5)).isSameAs(result.symbol("useValueOf", 22));
  }

  @Test
  public void AnnotationTypeDeclaration() {
    Result result = Result.createFor("declarations/AnnotationTypeDeclaration");

    JavaSymbol.TypeJavaSymbol annotationSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("Declaration");
    assertThat(annotationSymbol.owner()).isSameAs(result.symbol("AnnotationTypeDeclaration"));
    assertThat(annotationSymbol.flags()).isEqualTo(Flags.PRIVATE | Flags.INTERFACE | Flags.ANNOTATION);
    assertThat(annotationSymbol.getSuperclass()).isNull(); // TODO should it be java.lang.Object?

    JavaSymbol superinterface = Iterables.getOnlyElement(annotationSymbol.getInterfaces()).symbol;
    assertThat(superinterface.getName()).isEqualTo("Annotation");
    assertThat(superinterface.owner).isInstanceOf(JavaSymbol.PackageJavaSymbol.class);
    assertThat(superinterface.owner.getName()).isEqualTo("java.lang.annotation");

    assertThat(annotationSymbol.members.lookup("this")).isEmpty();

    JavaSymbol.VariableJavaSymbol variableSymbol = (JavaSymbol.VariableJavaSymbol) result.symbol("FIRST_CONSTANT");
    assertThat(variableSymbol.owner()).isSameAs(annotationSymbol);
    assertThat(variableSymbol.flags()).isEqualTo(Flags.PUBLIC | Flags.STATIC | Flags.FINAL);

    variableSymbol = (JavaSymbol.VariableJavaSymbol) result.symbol("SECOND_CONSTANT");
    assertThat(variableSymbol.owner()).isSameAs(annotationSymbol);
    assertThat(variableSymbol.flags()).isEqualTo(Flags.PUBLIC | Flags.STATIC | Flags.FINAL);

    JavaSymbol.MethodJavaSymbol methodSymbol = (JavaSymbol.MethodJavaSymbol) result.symbol("value", 15);
    assertThat(methodSymbol.owner()).isSameAs(annotationSymbol);
    assertThat(methodSymbol.flags()).isEqualTo(Flags.PUBLIC | Flags.ABSTRACT);

    JavaSymbol.TypeJavaSymbol typeSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("NestedClass");
    assertThat(typeSymbol.owner()).isSameAs(annotationSymbol);
    assertThat(typeSymbol.flags()).isEqualTo(Flags.PUBLIC | Flags.STATIC);

    typeSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("NestedInterface");
    assertThat(typeSymbol.owner()).isSameAs(annotationSymbol);
    assertThat(typeSymbol.flags()).isEqualTo(Flags.PUBLIC | Flags.STATIC | Flags.INTERFACE);

    typeSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("NestedEnum");
    assertThat(typeSymbol.owner()).isSameAs(annotationSymbol);
    assertThat(typeSymbol.flags()).isEqualTo(Flags.PUBLIC | Flags.ENUM | Flags.STATIC);

    typeSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("NestedAnnotationType");
    assertThat(typeSymbol.owner()).isSameAs(annotationSymbol);
    assertThat(typeSymbol.flags()).isEqualTo(Flags.PUBLIC | Flags.STATIC | Flags.INTERFACE | Flags.ANNOTATION);
  }

  @Test
  public void MethodDeclaration() {
    Result result = Result.createFor("declarations/MethodDeclaration");

    JavaSymbol.MethodJavaSymbol methodSymbol = (JavaSymbol.MethodJavaSymbol) result.symbol("declaration");
    assertThat(methodSymbol.owner()).isSameAs(result.symbol("MethodDeclaration"));
    assertThat(methodSymbol.flags()).isEqualTo(Flags.PROTECTED);
    assertThat(methodSymbol.getReturnType()).isSameAs(result.symbol("ReturnType"));
    assertThat(methodSymbol.thrownTypes()).containsExactly(
      result.symbol("FirstExceptionType").type(),
      result.symbol("SecondExceptionType").type());
  }

  @Test
  public void ConstructorDeclaration() {
    Result result = Result.createFor("declarations/ConstructorDeclaration");

    JavaSymbol.MethodJavaSymbol methodSymbol = (JavaSymbol.MethodJavaSymbol) result.symbol("<init>", 18);
    assertThat(methodSymbol.owner()).isSameAs(result.symbol("ConstructorDeclaration"));
    assertThat(methodSymbol.flags()).isEqualTo(0);
    assertThat(methodSymbol.getReturnType()).isNull();
    assertThat(methodSymbol.parameterTypes()).hasSize(1);
    assertThat(methodSymbol.thrownTypes()).containsExactly(
      result.symbol("FirstExceptionType").type(),
      result.symbol("SecondExceptionType").type());
    assertThat(result.reference(21, 35)).isEqualTo(methodSymbol);
    assertThat(((JavaSymbol.TypeJavaSymbol) methodSymbol.owner()).lookupSymbols("<init>")).as("Constructor with a declared constructor should not have a default one").hasSize(1);
    //Default constructor
    JavaSymbol defaultConstructor = result.reference(23, 26);
    assertThat(defaultConstructor.owner).isSameAs(result.symbol("ParameterType"));
    defaultConstructor = result.reference(28, 7);
    assertThat(defaultConstructor.isAbstract()).isFalse();

  }

  @Test
  public void ConstructorResolution() throws Exception {
    Result result = Result.createFor("PrivateConstructors");
    JavaSymbol.TypeJavaSymbol classSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("PrivateConstructorClass");
    List<JavaSymbol> constructors = classSymbol.members.lookup("<init>");
    JavaSymbol ObjectConstructor = constructors.get(0);
    JavaSymbol stringConstructor = constructors.get(1);

    JavaSymbol.MethodJavaSymbol constructorReference;

    // this(s) - > PrivateConstructorClass(s)
    constructorReference = (JavaSymbol.MethodJavaSymbol) result.reference(11, 7);
    assertThat(constructorReference.owner()).isSameAs(classSymbol);
    assertThat(constructorReference).isEqualTo(stringConstructor);

    // super(s) -> PrivateConstructorClass(s)
    constructorReference = (JavaSymbol.MethodJavaSymbol) result.reference(17, 7);
    assertThat(constructorReference.owner()).isSameAs(classSymbol);
    assertThat(constructorReference).isEqualTo(stringConstructor);

    // super(s) -> PrivateConstructorClass(o)
    constructorReference = (JavaSymbol.MethodJavaSymbol) result.reference(24, 5);
    assertThat(constructorReference.owner()).isSameAs(classSymbol);
    assertThat(constructorReference).isEqualTo(ObjectConstructor);

    assertThat(stringConstructor.usages()).hasSize(2);
    assertThat(ObjectConstructor.usages()).hasSize(1);
  }

  @Test
  public void ConstructorWithEnclosingClass() {
    Result result = Result.createFor("ConstructorWithEnclosingClass");

    assertThat(((JavaSymbol.TypeJavaSymbol) result.symbol("Inner")).members().scopeSymbols.get(0)).isSameAs(result.reference(9, 29));
    assertThat(((JavaSymbol.TypeJavaSymbol) result.symbol("Inner2")).members().scopeSymbols.get(0)).isSameAs(result.reference(21, 30));

    assertThat(((JavaSymbol.TypeJavaSymbol) result.symbol("Inner3")).members().scopeSymbols.get(0)).isSameAs(result.reference(34, 19));
    assertThat(((JavaSymbol.TypeJavaSymbol) result.symbol("Inner3")).members().scopeSymbols.get(1)).isSameAs(result.reference(34, 36));
    assertThat(((JavaSymbol.TypeJavaSymbol) result.symbol("Foo3")).members().scopeSymbols.get(1)).isSameAs(result.reference(34, 5));

    JavaSymbol.MethodJavaSymbol defaultConstructor = (JavaSymbol.MethodJavaSymbol) Iterables
      .getOnlyElement(((JavaSymbol.TypeJavaSymbol) result.symbol("Inner3")).lookupSymbols("<init>"));
    assertThat(defaultConstructor).isSameAs(result.reference(34, 19));
    assertThat(defaultConstructor.parameterTypes()).containsExactly(result.symbol("Outer3").type);
  }

  @Test
  public void SuperConstructorOfInnerClass() {
    Result result = Result.createFor("SuperConstructorOfInnerClass");

    assertThat(((JavaSymbol.TypeJavaSymbol) result.symbol("StaticInner")).members().lookup("<init>").get(0)).isSameAs(result.reference(7, 7));
    assertThat(((JavaSymbol.TypeJavaSymbol) result.symbol("ChildInner")).members().lookup("<init>").get(1)).isSameAs(result.reference(16, 7));
    assertThat(((JavaSymbol.TypeJavaSymbol) result.symbol("Inner")).members().lookup("<init>").get(0)).isSameAs(result.reference(20, 7));
  }

  @Test
  public void SuperConstructorOfExternalInnerClass() {
    Result result = Result.createFor("SuperConstructorOfExternalInnerClass");

    // FIXME SONARJAVA-1678 the constructor of the inner class is used
    assertThat(((JavaSymbol.TypeJavaSymbol) result.symbol("InnerA")).members().lookup("<init>").get(0).usages()).isEmpty();
    // assertThat(((JavaSymbol.TypeJavaSymbol) result.symbol("InnerA")).members().lookup("<init>").get(0))
    // .isEqualTo(result.reference(10, 9));
  }

  @Test
  public void ConstructorWithInference() throws Exception {
    Result result = Result.createFor("ConstructorWithInference");

    JavaSymbol.TypeJavaSymbol classSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("A");

    List<JavaSymbol> constructors = classSymbol.members.lookup("<init>");
    JavaSymbol noParamConstructor = constructors.get(0);
    JavaSymbol parametrizedConstructor = constructors.get(1);
    JavaSymbol wildcardConstructor = constructors.get(2);

    Type aObjectType = result.symbol("aObject").type();
    Type aStringType = result.symbol("aString").type();

    IdentifierTree constStringNoArg = result.referenceTree(14, 9);
    assertThat(constStringNoArg.symbol()).isSameAs(noParamConstructor);
    assertThat(getNewClassTreeType(constStringNoArg)).isSameAs(aStringType);

    IdentifierTree constDiamondNoArg = result.referenceTree(16, 9);
    assertThat(constDiamondNoArg.symbol()).isSameAs(noParamConstructor);
    assertThat(getNewClassTreeType(constDiamondNoArg)).isSameAs(aObjectType);

    constDiamondNoArg = result.referenceTree(17, 9);
    assertThat(constDiamondNoArg.symbol()).isSameAs(noParamConstructor);
    assertThat(getNewClassTreeType(constDiamondNoArg)).isSameAs(aObjectType);
    assertThat(result.reference(17, 15)).isSameAs(result.symbol("foo", 10));

    constDiamondNoArg = result.referenceTree(18, 13);
    assertThat(constDiamondNoArg.symbol()).isSameAs(noParamConstructor);
    assertThat(getNewClassTreeType(constDiamondNoArg)).isSameAs(aObjectType);

    constDiamondNoArg = result.referenceTree(20, 25);
    assertThat(constDiamondNoArg.symbol()).isSameAs(noParamConstructor);
    assertThat(getNewClassTreeType(constDiamondNoArg)).isSameAs(aStringType);

    constDiamondNoArg = result.referenceTree(21, 18);
    assertThat(constDiamondNoArg.symbol()).isSameAs(noParamConstructor);
    assertThat(getNewClassTreeType(constDiamondNoArg)).isSameAs(aObjectType);

    constDiamondNoArg = result.referenceTree(23, 13);
    assertThat(constDiamondNoArg.symbol()).isSameAs(noParamConstructor);
    assertThat(getNewClassTreeType(constDiamondNoArg)).isSameAs(aStringType);
    assertThat(result.reference(23, 5)).isSameAs(result.symbol("bar", 11));

    IdentifierTree constDiamondObject = result.referenceTree(25, 9);
    assertThat(constDiamondObject.symbol()).isSameAs(parametrizedConstructor);
    assertThat(getNewClassTreeType(constDiamondObject)).isSameAs(aObjectType);

    IdentifierTree constDiamondString = result.referenceTree(26, 9);
    assertThat(constDiamondString.symbol()).isSameAs(parametrizedConstructor);
    assertThat(getNewClassTreeType(constDiamondString)).isSameAs(aStringType);

    IdentifierTree constWildcardDiamond = result.referenceTree(28, 9);
    assertThat(constWildcardDiamond.symbol()).isSameAs(wildcardConstructor);
    assertThat(getNewClassTreeType(constWildcardDiamond)).isSameAs(aStringType);

    IdentifierTree returnConstDiamondNoArg = result.referenceTree(30, 16);
    assertThat(returnConstDiamondNoArg.symbol()).isSameAs(noParamConstructor);
    assertThat(getNewClassTreeType(returnConstDiamondNoArg)).isSameAs(aStringType);

    // inference allowing to deduce only partially the type parameters
    classSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("B");
    noParamConstructor = classSymbol.members.lookup("<init>").get(0);
    ParametrizedTypeJavaType type;

    constDiamondNoArg = result.referenceTree(42, 13);
    assertThat(constDiamondNoArg.symbol()).isSameAs(noParamConstructor);
    type = (ParametrizedTypeJavaType) getNewClassTreeType(constDiamondNoArg);
    assertThat(type.erasure()).isSameAs(classSymbol.type().erasure());
    assertThat(type.substitution(type.typeParameters().get(0)).is("java.lang.String")).isTrue();
    assertThat(type.substitution(type.typeParameters().get(1)).is("java.lang.Object")).isTrue();
    assertThat(type.substitution(type.typeParameters().get(2)).is("java.lang.Object")).isTrue();
    assertThat(result.reference(42, 5)).isSameAs(result.symbol("qix", 46));

    constDiamondNoArg = result.referenceTree(43, 13);
    assertThat(constDiamondNoArg.symbol()).isSameAs(noParamConstructor);
    type = (ParametrizedTypeJavaType) getNewClassTreeType(constDiamondNoArg);
    assertThat(type.erasure()).isSameAs(classSymbol.type().erasure());
    assertThat(type.substitution(type.typeParameters().get(0)).is("java.lang.Object")).isTrue();
    assertThat(type.substitution(type.typeParameters().get(1)).is("java.lang.String")).isTrue();
    assertThat(type.substitution(type.typeParameters().get(2)).is("java.lang.Integer")).isTrue();
    assertThat(result.reference(43, 5)).isSameAs(result.symbol("bar", 47));

    // erasure of bounded type parameters
    classSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("D");
    noParamConstructor = classSymbol.members.lookup("<init>").get(0);
    constDiamondNoArg = result.referenceTree(60, 9);
    assertThat(constDiamondNoArg.symbol()).isSameAs(noParamConstructor);
    assertThat(getNewClassTreeType(constDiamondNoArg)).isSameAs(result.symbol("dC").type());
    assertThat(result.reference(60, 15)).isSameAs(result.symbol("foo", 55));

    // enclosing expression
    classSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("F");
    constructors = classSymbol.members.lookup("<init>");
    noParamConstructor = constructors.get(0);
    parametrizedConstructor = constructors.get(1);

    IdentifierTree constructorCall = result.referenceTree(75, 15);
    assertThat(constructorCall.symbol()).isSameAs(noParamConstructor);
    assertThat(getNewClassTreeType(constructorCall)).isSameAs(result.symbol("fString").type());
    assertThat(result.reference(75, 5)).isSameAs(result.symbol("foo", 72));

    constructorCall = result.referenceTree(76, 15);
    assertThat(constructorCall.symbol()).isSameAs(parametrizedConstructor);
    assertThat(getNewClassTreeType(constructorCall)).isSameAs(result.symbol("fString").type());
    assertThat(result.reference(76, 5)).isSameAs(result.symbol("foo", 72));
  }

  @Test
  public void constructorWithTypeArguments() {
    Result result = Result.createFor("ConstructorWithTypeArguments");

    JavaSymbol.TypeJavaSymbol classSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("MyClass");
    List<JavaSymbol> constructors = classSymbol.members.lookup("<init>");

    assertThat(constructors.get(0).usages()).hasSize(1);
    assertThat(constructors.get(1).usages()).hasSize(1);
  }

  private static Type getNewClassTreeType(IdentifierTree constructorId) {
    Tree tree = constructorId;
    while (!tree.is(Tree.Kind.NEW_CLASS)) {
      tree = tree.parent();
    }
    return ((NewClassTree) tree).symbolType();
  }

  @Test
  public void CompleteHierarchyOfTypes() {
    Result result = Result.createFor("CompleteHierarchyOfTypes");

    JavaSymbol.TypeJavaSymbol typeSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("Foo");
    assertThat(typeSymbol.getSuperclass()).isSameAs(result.symbol("Baz").type);
  }

  @Test
  public void type_accessibility() throws Exception {
    Result result = Result.createForJavaFile("src/test/java/org/sonar/java/test/AccessibilityTestCase");
    JavaSymbol reference = result.reference(26, 7);
    MethodJavaType type = (MethodJavaType) reference.type;
    assertThat(type.resultType.symbol.name).isEqualTo("int");
  }

  @Test
  public void Accessibility() {
    Result result = Result.createFor("Accessibility");

    JavaSymbol.TypeJavaSymbol typeSymbol;
    typeSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("D1", 11);
    assertThat(typeSymbol.getSuperclass()).isSameAs(result.symbol("A1", 6).type);

    typeSymbol = (JavaSymbol.TypeJavaSymbol) result.symbol("D2", 31);
    assertThat(typeSymbol.getSuperclass()).isSameAs(result.symbol("A2", 19).type);

    JavaSymbol.VariableJavaSymbol variableSymbol;
    variableSymbol = (JavaSymbol.VariableJavaSymbol) result.reference(32, 15, "j");
    assertThat(variableSymbol).isSameAs(result.symbol("j", 17));

    JavaSymbol.MethodJavaSymbol methodSymbol;
    methodSymbol = (JavaSymbol.MethodJavaSymbol) result.reference(34, 9, "foo");
    assertThat(methodSymbol).isSameAs(result.symbol("foo", 22));
  }

  @Test
  public void Example() {
    Result.createFor("Example");
  }

  @Test
  public void ScopesAndSymbols() {
    Result.createFor("ScopesAndSymbols");
  }

  @Test
  public void TypesOfDeclarations() {
    Result result = Result.createFor("TypesOfDeclarations");
    assertThat(result.symbol("Class2").kind == JavaSymbol.TYP).isTrue();
    JavaSymbol.TypeJavaSymbol class1 = (JavaSymbol.TypeJavaSymbol) result.symbol("Class1");
    JavaSymbol.TypeJavaSymbol class2 = (JavaSymbol.TypeJavaSymbol) result.symbol("Class2");
    assertThat(class2.getSuperclass().symbol).isEqualTo(class1);
    assertThat(class1.getSuperclass()).isNotNull();
    assertThat(class1.getSuperclass().symbol.name).isEqualTo("Collection");
    JavaSymbol.TypeJavaSymbol interface1 = (JavaSymbol.TypeJavaSymbol) result.symbol("Interface1");
    assertThat(interface1.getInterfaces()).isNotEmpty();
    assertThat(interface1.getInterfaces().get(0).symbol.name).isEqualTo("List");
  }

  @Test
  public void Labels() {
    Result result = Result.createFor("references/Labels");

    assertThat(result.reference(8, 13)).isSameAs(result.symbol("label", 6));
    assertThat(result.reference(13, 13)).isSameAs(result.symbol("label", 11));
    assertThat(result.reference(20, 18)).isSameAs(result.symbol("label", 16));
  }

  @Test
  public void FieldAccess() {
    Result result = Result.createFor("references/FieldAccess");

    assertThat(result.reference(9, 5)).isSameAs(result.symbol("field"));

    assertThat(result.reference(10, 10)).isSameAs(result.symbol("field"));

    assertThat(result.reference(11, 5)).isSameAs(result.symbol("FieldAccess"));
    assertThat(result.reference(11, 17)).isSameAs(result.symbol("field"));

    // FIXME
    // assertThat(result.reference(12, 5)).isSameAs(/*package "references"*/);

    assertThat(result.reference(14, 5)).isSameAs(result.symbol("FirstStaticNestedClass"));
    assertThat(result.reference(14, 28)).isSameAs(result.symbol("field_in_FirstStaticNestedClass"));

    assertThat(result.reference(15, 5)).isSameAs(result.symbol("FirstStaticNestedClass"));
    assertThat(result.reference(15, 28)).isSameAs(result.symbol("SecondStaticNestedClass"));
    assertThat(result.reference(15, 52)).isSameAs(result.symbol("field_in_SecondStaticNestedClass"));

    assertThat(result.reference(16, 5)).isSameAs(result.symbol("field"));
    assertThat(result.reference(16, 11)).isSameAs(result.symbol("field_in_FirstStaticNestedClass"));

    assertThat(result.reference(17, 5)).isSameAs(result.symbol("field"));
    assertThat(result.reference(17, 11)).isSameAs(result.symbol("field_in_Superclass"));
  }

  @Test
  public void MethodParameterAccess() {
    Result result = Result.createFor("references/MethodParameterAccess");

    result.symbol("param");

    assertThat(result.reference(7, 5)).isSameAs(result.symbol("param"));

    assertThat(result.reference(8, 5)).isSameAs(result.symbol("param"));
    assertThat(result.reference(8, 11)).isSameAs(result.symbol("field"));
  }

  @Test
  public void ExpressionInAnnotation() {
    Result result = Result.createFor("references/ExpressionInAnnotation");

    assertThat(result.reference(3, 19)).isSameAs(result.symbol("ExpressionInAnnotation"));
    assertThat(result.reference(3, 42)).isSameAs(result.symbol("VALUE"));
    assertThat(result.reference(18, 6)).isSameAs(result.symbol("foo", 11));
    assertThat(result.reference(19, 6)).isSameAs(result.symbol("foo", 14));
    assertThat(result.reference(19, 14)).isSameAs(result.symbol("bar", 15));
  }

  @Test
  public void generic_method_call() throws Exception {
    Result result = Result.createFor("references/GenericMethodCall");
    JavaSymbol funMethod = result.symbol("fun");
    int startLine = 25;
    assertThat(result.reference(startLine, 5)).isSameAs(funMethod);
    assertThat(result.reference(startLine + 1, 5)).isSameAs(funMethod);
    assertThat(result.reference(startLine + 2, 5)).isSameAs(funMethod);
    assertThat(result.reference(startLine + 3, 5)).isSameAs(funMethod);
    JavaSymbol gulInt = result.symbol("gul", 34);
    JavaSymbol gulString = result.symbol("gul", 36);
    assertThat(((JavaSymbol.MethodJavaSymbol) gulInt).parameterTypes().get(0).is("java.lang.Integer")).isTrue();
    assertThat(gulString.usages()).hasSize(1);
    assertThat(gulInt.usages()).isEmpty();
  }

  @Test
  public void MethodCall() {
    Result result = Result.createFor("references/MethodCall");
    assertThat(result.reference(10, 5)).isSameAs(result.symbol("target"));
    assertThat(result.reference(11, 5)).isSameAs(result.symbol("foo", 17));
    assertThat(result.reference(30, 5)).isSameAs(result.symbol("fun", 22));
    assertThat(result.reference(42, 5)).isSameAs(result.symbol("bar", 35));
    assertThat(result.reference(52, 5)).isSameAs(result.symbol("bar", 35));
    assertThat(result.reference(61, 5)).isSameAs(result.symbol("bar", 57));
    assertThat(result.reference(67, 5)).isSameAs(result.symbol("bar", 35));
    assertThat(result.reference(79, 5)).isSameAs(result.symbol("defaultMethod", 72));
    assertThat(result.reference(88, 7)).isSameAs(result.symbol("func", 84));
    assertThat(result.reference(95, 5)).isSameAs(result.symbol("num", 94));
    assertThat(result.reference(102, 5)).isSameAs(result.symbol("varargs", 100));
    assertThat(result.reference(103, 5)).isSameAs(result.symbol("varargs", 100));
    assertThat(result.reference(104, 5)).isSameAs(result.symbol("varargs", 100));
    assertThat(result.reference(105, 5)).isSameAs(result.symbol("varargs", 100));
    assertThat(result.reference(106, 5)).isSameAs(result.symbol("varargs", 111));
    assertThat(result.reference(121, 5)).isSameAs(result.symbol("fun1", 115));
    assertThat(result.reference(122, 5)).isSameAs(result.symbol("fun2", 116));
    assertThat(result.reference(123, 5)).isSameAs(result.symbol("fun3", 117));
    assertThat(result.reference(124, 5)).isSameAs(result.symbol("fun4", 118));
    assertThat(result.reference(125, 5)).isSameAs(result.symbol("fun5", 119));
    assertThat(result.reference(132, 5)).isSameAs(result.symbol("fun", 131));
    assertThat(result.reference(134, 5)).isSameAs(result.symbol("fun", 131));

    assertThat(result.reference(143, 5)).isSameAs(result.symbol("process", 140));
    assertThat(result.reference(144, 5)).isSameAs(result.symbol("process", 141));

    assertThat(result.reference(149, 5)).isSameAs(result.symbol("process2", 147));
    assertThat(result.reference(150, 5)).isSameAs(result.symbol("process2", 146));

    assertThat(result.reference(156, 5)).isSameAs(result.symbol("process3", 153));
    assertThat(result.reference(157, 5)).isSameAs(result.symbol("process3", 154));

    assertThat(result.reference(169, 5)).isSameAs(result.symbol("varargs", 162));
    assertThat(result.reference(170, 5)).isSameAs(result.symbol("varargs", 165));

    assertThat(result.reference(180, 5)).isSameAs(result.symbol("varargs2", 173));
    assertThat(result.reference(181, 5)).isSameAs(result.symbol("varargs2", 176));

    assertThat(result.reference(204, 63, "<init>")).isSameAs(result.symbol("<init>", 188));
    assertThat(result.reference(205, 24)).isSameAs(result.symbol("genericMethod", 191));
    assertThat(result.reference(206, 77, "<init>")).isSameAs(result.symbol("<init>", 196));
    assertThat(result.reference(207, 31)).isSameAs(result.symbol("complexGenericMethod", 199));
    assertThat(result.reference(208, 40, "<init>")).isSameAs(result.symbol("<init>", 196));
    assertThat(result.reference(209, 43, "<init>")).isSameAs(result.symbol("<init>", 196));
    assertThat(result.reference(210, 64, "<init>")).isSameAs(result.symbol("<init>", 196));

    assertThat(result.reference(221, 5)).isSameAs(result.symbol("varargs3", 216));
    assertThat(result.reference(222, 5)).isSameAs(result.symbol("varargs4", 218));

    assertThat(result.reference(236, 5)).isSameAs(result.symbol("varargs5", 227));
    assertThat(result.reference(237, 5)).isSameAs(result.symbol("varargs5", 227));

    assertThat(result.reference(238, 5)).isSameAs(result.symbol("varargs6", 233));
    assertThat(result.reference(239, 5)).isSameAs(result.symbol("varargs6", 233));

    assertThat(result.reference(254, 9)).isSameAs(result.symbol("by", 251));
    assertThat(result.reference(264, 7)).isSameAs(result.symbol("by", 246));
    assertThat(result.reference(265, 7)).isSameAs(result.symbol("by", 251));

    assertThat(result.reference(256, 9)).isSameAs(result.symbol("of", 250));
    assertThat(result.reference(257, 9)).isSameAs(result.symbol("of", 245));
    assertThat(result.reference(267, 7)).isSameAs(result.symbol("of", 245));
    assertThat(result.reference(268, 7)).isSameAs(result.symbol("of", 250));
    assertThat(result.reference(269, 7)).isSameAs(result.symbol("of", 245));

    assertThat(result.reference(277, 5)).isSameAs(result.symbol("foo", 274));
    assertThat(result.reference(278, 13)).isSameAs(result.symbol("foo", 274));

    assertThat(result.reference(295, 5)).isSameAs(result.symbol("to", 290));
    assertThat(result.reference(296, 5)).isSameAs(result.symbol("to", 289));

    assertThat(result.reference(298, 5)).isSameAs(result.symbol("from", 302));
    assertThat(result.reference(297, 5)).isSameAs(result.symbol("from", 301));

    assertThat(result.reference(310, 5)).isSameAs(result.symbol("cast", 314));
    assertThat(result.reference(330, 31)).isSameAs(result.symbol("in", 320));
    assertThat(result.reference(330, 12)).isSameAs(result.symbol("removeIf", 325));

  }

  @Test
  public void FieldTypes() {
    Result result = Result.createFor("FieldTypes");
    assertThat(result.symbol("fieldBoolean").type.symbol.name).isEqualTo("Boolean");
    assertThat(result.symbol("fieldBoolean").type.symbol.owner().name).isEqualTo("java.lang");
    assertThat(result.symbol("fieldList").type.toString()).isEqualTo("List");
    assertThat(result.symbol("fieldList").type.symbol.owner.name).isEqualTo("java.util");
    assertThat(result.symbol("fieldInt").type).isNotNull();
    assertThat(result.symbol("fieldInt").type.symbol.name).isEqualTo("int");
  }

  @Test
  public void ArrayTypes() {
    Result result = Result.createFor("ArrayTypes");
    assertThat(result.symbol("strings1").type.symbol.name).isEqualTo("Array");
    assertThat(result.symbol("strings2").type.symbol.name).isEqualTo("Array");
    assertThat(result.symbol("strings3").type.symbol.name).isEqualTo("Array");
    assertThat(result.symbol("strings3").type.toString()).isEqualTo("String[][][]");
    assertThat(result.symbol("objects").type.toString()).isEqualTo("Object[][][]");
  }

  @Test
  public void ThisReference() {
    Result result = Result.createFor("references/ThisReference");
    JavaSymbol classA = result.symbol("A");
    assertThat(result.reference(7, 5).type.symbol).isEqualTo(classA);
    assertThat(result.reference(17, 17).type.symbol).isEqualTo(result.symbol("theHashtable").type.symbol);
  }

  @Test
  public void DeprecatedSymbols() {
    Result result = Result.createFor("DeprecatedSymbols");
    JavaSymbol sym = result.symbol("A");
    assertThat(sym.isDeprecated()).isTrue();
    sym = result.symbol("field");
    assertThat(sym.isDeprecated()).isTrue();
    sym = result.symbol("fun");
    assertThat(sym.isDeprecated()).isTrue();
  }

  @Test
  public void Lambdas() throws Exception {
    Result result = Result.createFor("Lambdas");
    JavaSymbol barMethod = result.symbol("bar");
    assertThat(barMethod.usages()).hasSize(1);
    MethodTree methodTree = (MethodTree) barMethod.declaration();
    Type Ftype = result.symbol("F").type();
    assertThat(((ReturnStatementTree) methodTree.block().body().get(0)).expression().symbolType()).isSameAs(Ftype);

    JavaSymbol qixMethod = result.symbol("qix");
    LambdaExpressionTree lamdba = ((LambdaExpressionTree) ((ReturnStatementTree) ((MethodTree) qixMethod.declaration()).block().body().get(0)).expression());
    assertThat(((ReturnStatementTree) ((BlockTree) lamdba.body()).body().get(0)).expression().symbolType()).isSameAs(result.symbol("F2").type());

    JavaSymbol fieldSymbol = result.symbol("field");
    assertThat(((VariableTree) fieldSymbol.declaration()).initializer().symbolType()).isSameAs(fieldSymbol.type());

    assertThat(((AssignmentExpressionTree) fieldSymbol.usages().get(0).parent()).expression().symbolType()).isSameAs(fieldSymbol.type());

    JavaSymbol bSymbol = result.symbol("b");
    assertThat(((NewClassTree) ((VariableTree) bSymbol.declaration()).initializer()).arguments().get(0).symbolType()).isSameAs(Ftype);

    JavaSymbol condMethod = result.symbol("cond");
    ConditionalExpressionTree conditionalExpression = (ConditionalExpressionTree) ((ReturnStatementTree) ((MethodTree) condMethod.declaration()).block().body().get(0)).expression();
    assertThat(conditionalExpression.symbolType()).isSameAs(Ftype);

    JavaSymbol parenthMethod = result.symbol("parenth");
    ParenthesizedTree parenthesizedTree = (ParenthesizedTree) ((ReturnStatementTree) ((MethodTree) parenthMethod.declaration()).block().body().get(0)).expression();
    assertThat(parenthesizedTree.symbolType()).isSameAs(Ftype);
    assertThat(result.symbol("s", 33).type().is("java.lang.String")).isTrue();


    JavaSymbol sym = result.symbol("o");
    assertThat(sym.type.toString()).isEqualTo("!Defered type!"); // unknown while we do the analysis with java 7
    assertThat(result.reference(8, 16)).isEqualTo(result.symbol("v", 8));
    assertThat(result.reference(9, 16)).isEqualTo(result.symbol("v", 9));

    JavaSymbol operations = result.symbol("operations");
    MethodInvocationTree mit = (MethodInvocationTree) operations.usages().get(0).parent().parent();
    assertThat(((ParametrizedTypeJavaType) operations.type).typeSubstitution.substitutedTypes().get(0)).isSameAs(mit.arguments().get(0).symbolType());

    JavaSymbol myStringParam = result.symbol("myStringParam");
    Symbol.MethodSymbol stringParamMethod = (Symbol.MethodSymbol) result.symbol("stringParamMethod");
    assertThat(stringParamMethod.usages()).hasSize(1);
    assertThat(myStringParam.type.is("java.lang.String")).isTrue();

  }
  void plop(String s) {}

  @Test
  public void MethodReference() throws Exception {
    Result result = Result.createFor("MethodReferences");
    JavaSymbol methodReference = result.symbol("methodReference");
    assertThat(methodReference.usages()).hasSize(3);

    JavaSymbol bar = result.symbol("bar");
    assertThat(bar.usages()).hasSize(3);
    JavaSymbol qix = result.symbol("qix");
    assertThat(result.reference(11, 27)).isSameAs(bar);
    assertThat(result.reference(12, 30)).isSameAs(bar);
    assertThat(result.reference(13, 24)).isSameAs(qix);
    assertThat(result.reference(14, 17)).isSameAs(bar);
    assertThat(result.reference(11, 21).owner).isSameAs(result.symbol("A"));
    assertThat(result.reference(11, 21).getName()).isEqualTo("this");
    assertThat(result.reference(12, 25).owner).isSameAs(result.symbol("A"));
    assertThat(result.reference(13, 21)).isSameAs(result.symbol("A"));

    JavaSymbol methodRefConstructor = result.symbol("methodRefConstructor");
    assertThat(methodRefConstructor.usages()).hasSize(1);
    assertThat(methodRefConstructor.isMethodSymbol()).isTrue();
    assertThat(((Symbol.MethodSymbol) methodRefConstructor).parameterTypes().get(0)).isSameAs(result.symbol("AProducer").type);

  }

  @Test
  public void UnionType() throws Exception {
    Result result = Result.createFor("UnionTypes");

    JavaSymbol exceptionVariableSymbol = result.symbol("e0");
    assertThat(exceptionVariableSymbol.type()).isNotSameAs(Symbols.unknownType);
    assertThat(exceptionVariableSymbol.type().is("java.lang.Exception")).isTrue();
    JavaSymbol methodSymbol = result.reference(6, 13);
    assertThat(methodSymbol.owner).isSameAs(result.symbol("UnionTypes"));
    JavaSymbol methodDeclarationSymbol = result.symbol("unwrapException", 18);
    assertThat(methodSymbol).isEqualTo(methodDeclarationSymbol);
    assertThat(methodDeclarationSymbol.usages()).hasSize(1);

    exceptionVariableSymbol = result.symbol("e1");
    assertThat(exceptionVariableSymbol.type()).isNotSameAs(Symbols.unknownType);
    assertThat(exceptionVariableSymbol.type().is("UnionTypes$B")).isTrue();
    methodSymbol = result.reference(8, 13);
    assertThat(methodSymbol.owner).isSameAs(result.symbol("UnionTypes"));
    methodDeclarationSymbol = result.symbol("unwrapException", 22);
    assertThat(methodSymbol).isEqualTo(methodDeclarationSymbol);
    assertThat(methodDeclarationSymbol.usages()).hasSize(1);

    assertThat(exceptionVariableSymbol.usages()).hasSize(1);
    try {
      exceptionVariableSymbol.usages().clear();
      fail("list of usages of a symbol is not immutable");
    } catch (UnsupportedOperationException uoe) {
      assertThat(exceptionVariableSymbol.usages()).hasSize(1);
    }


    exceptionVariableSymbol = result.symbol("e2");
    assertThat(exceptionVariableSymbol.type()).isEqualTo(Symbols.unknownType);
    methodDeclarationSymbol = result.symbol("unwrapException", 26);
    assertThat(methodDeclarationSymbol.usages()).isEmpty();
  }

  @Test
  public void symbolNotFound() throws Exception {
    Result result = Result.createFor("SymbolsNotFound");

    MethodTree methodDeclaration = (MethodTree) result.symbol("method").declaration();
    ExpressionStatementTree expression = (ExpressionStatementTree) methodDeclaration.block().body().get(0);
    MethodInvocationTree methodInvocation = (MethodInvocationTree) expression.expression();

    Symbol symbolNotFound = methodInvocation.symbol();
    assertThat(symbolNotFound).isNotNull();
    assertThat(symbolNotFound.name()).isNull();
    assertThat(symbolNotFound.owner()).isSameAs(Symbols.unknownSymbol);
  }

  @Test
  public void annotations_on_fields() throws Exception {
    Result result = Result.createFor("AnnotationOnFields");

    JavaSymbol.TypeSymbol app = (JavaSymbol.TypeSymbol) result.symbol("App");
    for (Symbol sym : app.memberSymbols()) {
      if (!sym.isMethodSymbol() && !(sym.name().equals("super") || sym.name().equals("this"))) {
        assertThat(sym.metadata().isAnnotatedWith("java.lang.Deprecated")).isTrue();
      }
    }
  }

  @Test
  public void try_with_resources() {
    Result result = Result.createFor("references/TryWithResources");
    assertThat(result.symbol("foo3", 4)).isSameAs(result.reference(5, 7));
    assertThat(result.symbol("foo3", 7)).isSameAs(result.reference(8, 7));
  }

  @Test
  public void switch_statement() {
    Result result = Result.createFor("SwitchStatement");
    assertThat(result.symbol("a", 16)).isSameAs(result.reference(18, 28));
    assertThat(result.symbol("a", 20)).isSameAs(result.reference(21, 24));

  }

  @Test
  public void wildcard_invocation_inference() {
    Result result = Result.createFor("WildcardsInvocation");
    assertThat(result.symbol("fun")).isSameAs(result.reference(11, 5));
    assertThat(result.symbol("foo")).isSameAs(result.reference(20, 5));
  }

  @Test
  public void inference_on_parameterized_method_with_no_arg() {
    Result result = Result.createFor("ParameterizedMethodInvocation");
    assertThat(result.symbol("method")).isSameAs(result.reference(3, 9));
    assertThat(result.symbol("fun")).isSameAs(result.reference(3, 5));
  }
}
