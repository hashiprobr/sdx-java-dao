package br.pro.hashi.sdx.dao.reflection;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.pro.hashi.sdx.dao.reflection.exception.ReflectionException;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.AbstractConstructor;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.ArgumentConstructor;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.DefaultConstructor;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.Fields;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.PackageConstructor;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.PrivateConstructor;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.ProtectedConstructor;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.PublicConstructor;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.ThrowerConstructor;

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
		Field field = getDeclaredField(fieldName);
		field.setAccessible(true);
		MethodHandle getter = r.unreflectGetter(field);
		Fields instance = new Fields();
		assertEquals(true, r.invokeGetter(getter, instance));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"publicValue",
			"protectedValue",
			"packageValue",
			"privateValue" })
	void unreflectsAndInvokesSetter(String fieldName) {
		Field field = getDeclaredField(fieldName);
		field.setAccessible(true);
		MethodHandle setter = r.unreflectSetter(field);
		Fields instance = new Fields();
		r.invokeSetter(setter, instance, false);
		Object value = assertDoesNotThrow(() -> {
			return field.get(instance);
		});
		assertEquals(false, value);
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
	void doesNotInvokeThrowerGetter() {
		Field field = getDeclaredField("publicValue");
		MethodHandle getter = r.unreflectGetter(field);
		assertThrows(AssertionError.class, () -> {
			r.invokeGetter(getter, null);
		});
	}

	@Test
	void doesNotInvokeThrowerSetter() {
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
}
