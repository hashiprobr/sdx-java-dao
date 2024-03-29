package br.pro.hashi.sdx.dao.reflection;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.pro.hashi.sdx.dao.reflection.exception.ReflectionException;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.ChildWithBoth;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.ChildWithLeft;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.ChildWithNeither;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.ChildWithRight;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.FinalChildWithBoth;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.FinalChildWithLeft;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.FinalChildWithNeither;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.FinalChildWithRight;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.FinalImplementationWithBoth;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.FinalImplementationWithDiamond;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.FinalImplementationWithLeft;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.FinalImplementationWithNeither;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.FinalImplementationWithRight;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.FinalMixedWithBoth;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.FinalMixedWithLeft;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.FinalMixedWithNeither;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.FinalMixedWithRight;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.GenericInterface;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.GenericParent;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.ImplementationWithBoth;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.ImplementationWithDiamond;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.ImplementationWithLeft;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.ImplementationWithNeither;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.ImplementationWithRight;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.Methods;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.MixedWithBoth;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.MixedWithLeft;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.MixedWithNeither;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.MixedWithRight;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.PartialGenericInterface;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.PartialGenericParent;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.handle.AbstractConstructor;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.handle.ArgumentConstructor;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.handle.DefaultConstructor;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.handle.Fields;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.handle.GenericConstructor;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.handle.PackageConstructor;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.handle.PrivateConstructor;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.handle.ProtectedConstructor;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.handle.PublicConstructor;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.handle.ThrowerConstructor;

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
			PrivateConstructor.class })
	<E> void getsAndInvokesCreator(Class<E> type) {
		MethodHandle creator = getCreator(type);
		assertInstanceOf(type, r.invokeCreator(creator));
	}

	@Test
	void doesNotGetAbstractCreator() {
		assertThrows(ReflectionException.class, () -> {
			getCreator(AbstractConstructor.class);
		});
	}

	@Test
	void doesNotGetGenericCreator() {
		assertThrows(ReflectionException.class, () -> {
			getCreator(GenericConstructor.class);
		});
	}

	@Test
	void doesNotGetArgumentCreator() {
		assertThrows(ReflectionException.class, () -> {
			getCreator(ArgumentConstructor.class);
		});
	}

	@ParameterizedTest
	@ValueSource(classes = {
			ProtectedConstructor.class,
			PackageConstructor.class,
			PrivateConstructor.class })
	<E> void doesNotUnreflectIllegalConstructor(Class<E> type) {
		Constructor<E> constructor = assertDoesNotThrow(() -> {
			return type.getDeclaredConstructor();
		});
		assertThrows(AssertionError.class, () -> {
			r.unreflectConstructor(constructor);
		});
	}

	@Test
	void doesNotInvokeThrowerCreator() {
		MethodHandle creator = getCreator(ThrowerConstructor.class);
		assertThrows(AssertionError.class, () -> {
			r.invokeCreator(creator);
		});
	}

	private <E> MethodHandle getCreator(Class<E> type) {
		return r.getCreator(type, type.getName());
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"publicValue",
			"protectedValue",
			"packageValue",
			"privateValue" })
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
			"privateValue" })
	void unreflectsAndInvokesSetter(String fieldName) {
		Field field = getDeclaredFieldAndSetAccessible(fieldName);
		MethodHandle setter = r.unreflectSetter(field);
		Fields instance = new Fields();
		r.invokeSetter(setter, instance, false);
		boolean value = (boolean) assertDoesNotThrow(() -> {
			return field.get(instance);
		});
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
			"privateValue" })
	void doesNotUnreflectIllegalGetter(String fieldName) {
		Field field = getDeclaredField(fieldName);
		assertThrows(AssertionError.class, () -> {
			r.unreflectGetter(field);
		});
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"protectedValue",
			"packageValue",
			"privateValue" })
	void doesNotUnreflectIllegalSetter(String fieldName) {
		Field field = getDeclaredField(fieldName);
		assertThrows(AssertionError.class, () -> {
			r.unreflectSetter(field);
		});
	}

	@Test
	void doesNotInvokeNullGetter() {
		Field field = getDeclaredField("publicValue");
		MethodHandle getter = r.unreflectGetter(field);
		assertThrows(AssertionError.class, () -> {
			r.invokeGetter(getter, null);
		});
	}

	@Test
	void doesNotInvokeNullSetter() {
		Field field = getDeclaredField("publicValue");
		MethodHandle setter = r.unreflectSetter(field);
		assertThrows(AssertionError.class, () -> {
			r.invokeSetter(setter, null, false);
		});
	}

	private Field getDeclaredField(String fieldName) {
		Field field = assertDoesNotThrow(() -> {
			return Fields.class.getDeclaredField(fieldName);
		});
		return field;
	}

	@Test
	void unreflects() {
		Method method = getDeclaredMethod("legal");
		assertDoesNotThrow(() -> {
			r.unreflect(method);
		});
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"illegalProtected",
			"illegalPackage",
			"illegalPrivate" })
	void doesNotUnreflectIllegal(String methodName) {
		Method method = getDeclaredMethod(methodName);
		assertThrows(AssertionError.class, () -> {
			r.unreflect(method);
		});
	}

	private Method getDeclaredMethod(String methodName) {
		Method method = assertDoesNotThrow(() -> {
			return Methods.class.getDeclaredMethod(methodName);
		});
		return method;
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
		assertThrows(ReflectionException.class, () -> {
			r.getSpecificType(object, rootType, rootIndex);
		});
	}
}
