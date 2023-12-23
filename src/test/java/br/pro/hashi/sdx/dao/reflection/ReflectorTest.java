/*
 * Copyright (c) 2023 Marcelo Hashimoto
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package br.pro.hashi.sdx.dao.reflection;

import br.pro.hashi.sdx.dao.reflection.example.reflector.invoke.*;
import br.pro.hashi.sdx.dao.reflection.example.reflector.specific.*;
import br.pro.hashi.sdx.dao.reflection.exception.ReflectionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objenesis.instantiator.ObjectInstantiator;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class ReflectorTest {
    private Reflector r;

    @BeforeEach
    void setUp() {
        r = new Reflector();
    }

    @Test
    void getsInstance() {
        assertInstanceOf(Reflector.class, Reflector.getInstance());
    }

    @ParameterizedTest
    @ValueSource(classes = {
            DefaultConstructor.class,
            PublicConstructor.class,
            ProtectedConstructor.class,
            PackageConstructor.class,
            PrivateConstructor.class,
            ArgumentConstructor.class})
    <E> void getsAndInvokesInstantiator(Class<E> type) {
        ObjectInstantiator<E> instantiator = getInstantiator(type);
        assertInstanceOf(type, instantiator.newInstance());
    }

    @ParameterizedTest
    @ValueSource(classes = {
            AbstractConstructor.class,
            GenericConstructor.class})
    void doesNotGetInstantiator(Class<?> type) {
        assertThrows(ReflectionException.class, () -> getInstantiator(type));
    }

    private <E> ObjectInstantiator<E> getInstantiator(Class<E> type) {
        return r.getInstantiator(type, type.getName());
    }

    @ParameterizedTest
    @ValueSource(classes = {
            DefaultConstructor.class,
            PublicConstructor.class,
            ProtectedConstructor.class,
            PackageConstructor.class,
            PrivateConstructor.class})
    void getsCreator(Class<?> type) {
        MethodHandle creator = getCreator(type);
        Object instance = assertDoesNotThrow(() -> creator.invoke());
        assertInstanceOf(type, instance);
    }

    @ParameterizedTest
    @ValueSource(classes = {
            AbstractConstructor.class,
            GenericConstructor.class,
            ArgumentConstructor.class})
    void doesNotGetCreator(Class<?> type) {
        assertThrows(ReflectionException.class, () -> getCreator(type));
    }

    @ParameterizedTest
    @ValueSource(classes = {
            ProtectedConstructor.class,
            PackageConstructor.class,
            PrivateConstructor.class})
    <E> void doesNotUnreflectConstructor(Class<E> type) {
        Constructor<E> constructor = assertDoesNotThrow(() -> type.getDeclaredConstructor());
        assertThrows(AssertionError.class, () -> r.unreflectConstructor(constructor));
    }

    private MethodHandle getCreator(Class<?> type) {
        return r.getCreator(type, type.getName());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "publicValue",
            "protectedValue",
            "packageValue",
            "privateValue"})
    void unreflectsAndInvokesGetter(String fieldName) {
        Field field = getDeclaredFieldAndSetAccessible(fieldName);
        MethodHandle getter = r.unreflectGetter(field);
        Fields instance = new Fields();
        boolean value = r.invokeGetter(getter, instance);
        assertTrue(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "publicValue",
            "protectedValue",
            "packageValue",
            "privateValue"})
    void unreflectsAndInvokesSetter(String fieldName) {
        Field field = getDeclaredFieldAndSetAccessible(fieldName);
        MethodHandle setter = r.unreflectSetter(field);
        Fields instance = new Fields();
        r.invokeSetter(setter, instance, false);
        boolean value = (boolean) assertDoesNotThrow(() -> field.get(instance));
        assertFalse(value);
    }

    private Field getDeclaredFieldAndSetAccessible(String fieldName) {
        Field field = getDeclaredField(fieldName);
        if (!Modifier.isPublic(field.getModifiers())) {
            field.setAccessible(true);
        }
        return field;
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "protectedValue",
            "packageValue",
            "privateValue"})
    void doesNotUnreflectGetter(String fieldName) {
        Field field = getDeclaredField(fieldName);
        assertThrows(AssertionError.class, () -> r.unreflectGetter(field));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "protectedValue",
            "packageValue",
            "privateValue"})
    void doesNotUnreflectSetter(String fieldName) {
        Field field = getDeclaredField(fieldName);
        assertThrows(AssertionError.class, () -> r.unreflectSetter(field));
    }

    @Test
    void doesNotInvokeNullGetter() {
        Field field = getDeclaredField("publicValue");
        MethodHandle getter = r.unreflectGetter(field);
        assertThrows(AssertionError.class, () -> r.invokeGetter(getter, null));
    }

    @Test
    void doesNotInvokeNullSetter() {
        Field field = getDeclaredField("publicValue");
        MethodHandle setter = r.unreflectSetter(field);
        assertThrows(AssertionError.class, () -> r.invokeSetter(setter, null, false));
    }

    private Field getDeclaredField(String fieldName) {
        return assertDoesNotThrow(() -> Fields.class.getDeclaredField(fieldName));
    }

    @Test
    void unreflects() {
        Method method = getDeclaredMethod("legal");
        assertDoesNotThrow(() -> r.unreflect(method));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "illegalProtected",
            "illegalPackage",
            "illegalPrivate"})
    void doesNotUnreflect(String methodName) {
        Method method = getDeclaredMethod(methodName);
        assertThrows(AssertionError.class, () -> r.unreflect(method));
    }

    private Method getDeclaredMethod(String methodName) {
        return assertDoesNotThrow(() -> Methods.class.getDeclaredMethod(methodName));
    }

    @Test
    void getsBothSpecificTypesFromFinalChildWithBoth() {
        FinalChildWithBoth object = new FinalChildWithBoth();
        assertBothSpecificTypesExist(object, GenericParent.class);
    }

    @Test
    void getsBothSpecificTypesFromFinalChildWithLeft() {
        FinalChildWithLeft object = new FinalChildWithLeft();
        assertBothSpecificTypesExist(object, GenericParent.class);
    }

    @Test
    void getsBothSpecificTypesFromFinalChildWithRight() {
        FinalChildWithRight object = new FinalChildWithRight();
        assertBothSpecificTypesExist(object, GenericParent.class);
    }

    @Test
    void getsBothSpecificTypesFromFinalChildWithNeither() {
        FinalChildWithNeither object = new FinalChildWithNeither();
        assertBothSpecificTypesExist(object, GenericParent.class);
    }

    @Test
    void getsBothSpecificTypesFromChildWithBoth() {
        ChildWithBoth object = new ChildWithBoth();
        assertBothSpecificTypesExist(object, GenericParent.class);
    }

    @Test
    void getsLeftSpecificTypeFromChildWithLeft() {
        ChildWithLeft<Double> object = new ChildWithLeft<>();
        assertLeftSpecificTypeExists(object, GenericParent.class);
        assertRightSpecificTypeNotExists(object, GenericParent.class);
    }

    @Test
    void getsRightSpecificTypeFromChildWithRight() {
        ChildWithRight<Integer> object = new ChildWithRight<>();
        assertLeftSpecificTypeNotExists(object, GenericParent.class);
        assertRightSpecificTypeExists(object, GenericParent.class);
    }

    @Test
    void getsNeitherSpecificTypeFromChildWithNeither() {
        ChildWithNeither<Integer, Double> object = new ChildWithNeither<>();
        assertBothSpecificTypesNotExist(object, GenericParent.class);
    }

    @Test
    void getsBothSpecificTypesFromFinalImplementationWithDiamond() {
        FinalImplementationWithDiamond object = new FinalImplementationWithDiamond();
        assertBothSpecificTypesExist(object, GenericInterface.class);
    }

    @Test
    void getsBothSpecificTypesFromFinalImplementationWithBoth() {
        FinalImplementationWithBoth object = new FinalImplementationWithBoth();
        assertBothSpecificTypesExist(object, GenericInterface.class);
    }

    @Test
    void getsBothSpecificTypesFromFinalImplementationWithLeft() {
        FinalImplementationWithLeft object = new FinalImplementationWithLeft();
        assertBothSpecificTypesExist(object, GenericInterface.class);
    }

    @Test
    void getsBothSpecificTypesFromFinalImplementationWithRight() {
        FinalImplementationWithRight object = new FinalImplementationWithRight();
        assertBothSpecificTypesExist(object, GenericInterface.class);
    }

    @Test
    void getsBothSpecificTypesFromFinalImplementationWithNeither() {
        FinalImplementationWithNeither object = new FinalImplementationWithNeither();
        assertBothSpecificTypesExist(object, GenericInterface.class);
    }

    @Test
    void getsBothSpecificTypesFromImplementationWithDiamond() {
        ImplementationWithDiamond object = new ImplementationWithDiamond();
        assertBothSpecificTypesExist(object, GenericInterface.class);
    }

    @Test
    void getsBothSpecificTypesFromImplementationWithBoth() {
        ImplementationWithBoth object = new ImplementationWithBoth();
        assertBothSpecificTypesExist(object, GenericInterface.class);
    }

    @Test
    void getsLeftSpecificTypeFromImplementationWithLeft() {
        ImplementationWithLeft<Double> object = new ImplementationWithLeft<>();
        assertLeftSpecificTypeExists(object, GenericInterface.class);
        assertRightSpecificTypeNotExists(object, GenericInterface.class);
    }

    @Test
    void getsRightSpecificTypeFromImplementationWithRight() {
        ImplementationWithRight<Integer> object = new ImplementationWithRight<>();
        assertLeftSpecificTypeNotExists(object, GenericInterface.class);
        assertRightSpecificTypeExists(object, GenericInterface.class);
    }

    @Test
    void getsNeitherSpecificTypeFromImplementationWithNeither() {
        ImplementationWithNeither<Integer, Double> object = new ImplementationWithNeither<>();
        assertBothSpecificTypesNotExist(object, GenericInterface.class);
    }

    private <T, S extends T> void assertBothSpecificTypesExist(S object, Class<T> rootType) {
        assertLeftSpecificTypeExists(object, rootType);
        assertRightSpecificTypeExists(object, rootType);
    }

    private <T, S extends T> void assertLeftSpecificTypeExists(S object, Class<T> rootType) {
        assertSpecificTypeEquals(Integer.class, object, rootType, 0);
    }

    private <T, S extends T> void assertRightSpecificTypeExists(S object, Class<T> rootType) {
        assertSpecificTypeEquals(Double.class, object, rootType, 1);
    }

    private <T, S extends T> void assertBothSpecificTypesNotExist(S object, Class<T> rootType) {
        assertLeftSpecificTypeNotExists(object, rootType);
        assertRightSpecificTypeNotExists(object, rootType);
    }

    private <T, S extends T> void assertLeftSpecificTypeNotExists(S object, Class<T> rootType) {
        assertSpecificTypeThrows(object, rootType, 0);
    }

    private <T, S extends T> void assertRightSpecificTypeNotExists(S object, Class<T> rootType) {
        assertSpecificTypeThrows(object, rootType, 1);
    }

    @Test
    void getsBothSpecificTypesFromFinalMixedWithBoth() {
        FinalMixedWithBoth object = new FinalMixedWithBoth();
        assertBothSpecificTypesExist(object);
    }

    @Test
    void getsBothSpecificTypesFromFinalMixedWithLeft() {
        FinalMixedWithLeft object = new FinalMixedWithLeft();
        assertBothSpecificTypesExist(object);
    }

    @Test
    void getsBothSpecificTypesFromFinalMixedWithRight() {
        FinalMixedWithRight object = new FinalMixedWithRight();
        assertBothSpecificTypesExist(object);
    }

    @Test
    void getsBothSpecificTypesFromFinalMixedWithNeither() {
        FinalMixedWithNeither object = new FinalMixedWithNeither();
        assertBothSpecificTypesExist(object);
    }

    @Test
    void getsBothSpecificTypesFromMixedWithBoth() {
        MixedWithBoth object = new MixedWithBoth();
        assertBothSpecificTypesExist(object);
    }

    @Test
    void getsLeftSpecificTypeFromMixedWithLeft() {
        MixedWithLeft<Integer> object = new MixedWithLeft<>();
        assertLeftSpecificTypeExists(object);
        assertRightSpecificTypeNotExists(object);
    }

    @Test
    void getsRightSpecificTypeFromMixedWithRight() {
        MixedWithRight<Integer> object = new MixedWithRight<>();
        assertLeftSpecificTypeNotExists(object);
        assertRightSpecificTypeExists(object);
    }

    @Test
    void getsNeitherSpecificTypeFromMixedWithNeither() {
        MixedWithNeither<Integer, Double> object = new MixedWithNeither<>();
        assertLeftSpecificTypeNotExists(object);
        assertRightSpecificTypeNotExists(object);
    }

    private <S extends PartialGenericParent<?> & PartialGenericInterface<?>> void assertBothSpecificTypesExist(S object) {
        assertLeftSpecificTypeExists(object);
        assertRightSpecificTypeExists(object);
    }

    private <S extends PartialGenericParent<?> & PartialGenericInterface<?>> void assertLeftSpecificTypeExists(S object) {
        assertSpecificTypeEquals(Integer.class, object, PartialGenericParent.class, 0);
    }

    private <S extends PartialGenericParent<?> & PartialGenericInterface<?>> void assertRightSpecificTypeExists(S object) {
        assertSpecificTypeEquals(Double.class, object, PartialGenericInterface.class, 0);
    }

    private <S extends PartialGenericParent<?> & PartialGenericInterface<?>> void assertLeftSpecificTypeNotExists(S object) {
        assertSpecificTypeThrows(object, PartialGenericParent.class, 0);
    }

    private <S extends PartialGenericParent<?> & PartialGenericInterface<?>> void assertRightSpecificTypeNotExists(S object) {
        assertSpecificTypeThrows(object, PartialGenericInterface.class, 0);
    }

    private <T, S extends T> void assertSpecificTypeEquals(Class<?> expected, S object, Class<T> rootType, int rootIndex) {
        assertEquals(expected, r.getSpecificType(object, rootType, rootIndex));
    }

    private <T, S extends T> void assertSpecificTypeThrows(S object, Class<T> rootType, int rootIndex) {
        assertThrows(ReflectionException.class, () -> r.getSpecificType(object, rootType, rootIndex));
    }
}
