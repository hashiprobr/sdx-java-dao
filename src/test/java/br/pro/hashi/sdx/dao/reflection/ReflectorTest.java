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
import br.pro.hashi.sdx.dao.reflection.mock.reflector.Abstract;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.ArgumentConstructor;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.DefaultConstructor;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.Fields;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.PackageConstructor;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.PrivateConstructor;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.ProtectedConstructor;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.PublicConstructor;
import br.pro.hashi.sdx.dao.reflection.mock.reflector.ThrowerArgumentConstructor;
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
	void getsAndInvokesExternalCreator(Class<?> type) {
		MethodHandle creator = r.getExternalCreator(type);
		Object instance = r.invokeCreator(creator);
		assertInstanceOf(type, instance);
	}

	@Test
	void getsAndInvokesNoArgsCreator() {
		MethodHandle creator = r.getCreator(DefaultConstructor.class);
		Object instance = r.invokeCreator(creator);
		assertInstanceOf(DefaultConstructor.class, instance);
	}

	@Test
	void getsAndInvokesOneArgCreator() {
		MethodHandle creator = r.getCreator(ArgumentConstructor.class, boolean.class);
		Object instance = r.invokeCreator(creator, true);
		assertInstanceOf(ArgumentConstructor.class, instance);
	}

	@Test
	void doesNotGetAbstractCreator() {
		assertThrows(ReflectionException.class, () -> {
			r.getExternalCreator(Abstract.class);
		});
	}

	@Test
	void doesNotGetExternalCreator() {
		assertThrows(ReflectionException.class, () -> {
			r.getExternalCreator(ArgumentConstructor.class);
		});
	}

	@Test
	void doesNotGetNoArgsCreator() {
		assertThrows(AssertionError.class, () -> {
			r.getCreator(ArgumentConstructor.class);
		});
	}

	@Test
	void doesNotGetOneArgCreator() {
		assertThrows(AssertionError.class, () -> {
			r.getCreator(DefaultConstructor.class, boolean.class);
		});
	}

	@ParameterizedTest
	@ValueSource(classes = {
			ProtectedConstructor.class,
			PackageConstructor.class,
			PrivateConstructor.class })
	<T> void doesNotUnreflectIllegalConstructor(Class<T> type) {
		Constructor<T> constructor = assertDoesNotThrow(() -> {
			return type.getDeclaredConstructor();
		});
		assertThrows(AssertionError.class, () -> {
			r.unreflectConstructor(constructor);
		});
	}

	@Test
	void doesNotInvokeThrowerCreator() {
		MethodHandle creator = r.getExternalCreator(ThrowerConstructor.class);
		assertThrows(AssertionError.class, () -> {
			r.invokeCreator(creator);
		});
	}

	@Test
	void doesNotInvokeThrowerArgumentCreator() {
		MethodHandle creator = r.getCreator(ThrowerArgumentConstructor.class, boolean.class);
		assertThrows(AssertionError.class, () -> {
			r.invokeCreator(creator, true);
		});
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
