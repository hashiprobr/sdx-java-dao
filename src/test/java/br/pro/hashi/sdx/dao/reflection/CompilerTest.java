package br.pro.hashi.sdx.dao.reflection;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.google.cloud.firestore.annotation.PropertyName;

import br.pro.hashi.sdx.dao.reflection.mock.handle.Child;
import br.pro.hashi.sdx.dao.reflection.mock.handle.GrandChild;
import br.pro.hashi.sdx.dao.reflection.mock.handle.Parent;
import javassist.ClassPool;
import javassist.CtClass;

class CompilerTest {
	private static final ClassPool POOL = ClassPool.getDefault();
	private static final Lookup LOOKUP = MethodHandles.lookup();
	private static final double DELTA = 0.000001;

	private Handle handle;
	private Reflector reflector;
	private HandleFactory factory;
	private MockedStatic<Handle> handleStatic;
	private Compiler c;

	@BeforeEach
	void setUp() {
		handle = mock(Handle.class);
		reflector = mock(Reflector.class);
		when(reflector.getCreator(any(), eq(Handle.class), any())).thenAnswer((invocation) -> {
			Class<?> proxyType = invocation.getArgument(0);
			Class<Handle> handleType = invocation.getArgument(1);
			Class<?> type = invocation.getArgument(2);
			Constructor<?> constructor = proxyType.getDeclaredConstructor(handleType, type);
			return LOOKUP.unreflectConstructor(constructor);
		});
		when(reflector.invokeCreator(any(MethodHandle.class), eq(handle), any())).thenAnswer((invocation) -> {
			MethodHandle creator = invocation.getArgument(0);
			Handle handle = invocation.getArgument(1);
			Object instance = invocation.getArgument(2);
			return creator.invoke(handle, instance);
		});
		when(reflector.unreflectGetter(any(Field.class))).thenAnswer((invocation) -> {
			Field field = invocation.getArgument(0);
			return LOOKUP.unreflectGetter(field);
		});
		when(reflector.invokeGetter(any(MethodHandle.class), any())).thenAnswer((invocation) -> {
			MethodHandle getter = invocation.getArgument(0);
			Object proxy = invocation.getArgument(1);
			return getter.invoke(proxy);
		});
		factory = mock(HandleFactory.class);
		when(factory.get(any())).thenReturn(handle);
		handleStatic = mockStatic(Handle.class);
		handleStatic.when(() -> Handle.of(any())).thenReturn(handle);
		c = new Compiler(reflector, factory);
	}

	@AfterEach
	void tearDown() {
		assertDoesNotThrow(() -> {
			Thread.sleep(1);
		});
		handleStatic.close();
	}

	@Test
	void getsInstance() {
		assertInstanceOf(Compiler.class, Compiler.getInstance());
	}

	@Test
	void constructsWithDefaultReflectorAndFactory() {
		Compiler compiler;
		try (MockedStatic<Reflector> reflectorStatic = mockStatic(Reflector.class)) {
			reflectorStatic.when(() -> Reflector.getInstance()).thenReturn(reflector);
			try (MockedStatic<HandleFactory> factoryStatic = mockStatic(HandleFactory.class)) {
				factoryStatic.when(() -> HandleFactory.getInstance()).thenReturn(factory);
				compiler = new Compiler();
			}
			assertSame(factory, compiler.getFactory());
		}
		assertSame(reflector, compiler.getReflector());
	}

	@Test
	void doesNotUnreflectInstanceGetter() {
		assertThrows(AssertionError.class, () -> {
			c.unreflectInstanceGetter(Object.class);
		});
	}

	@Test
	void compilesParent() {
		when(handle.getFieldNames()).thenReturn(Set.of("booleanValue", "intValue", "stringValue"));
		when(handle.getPropertyName("booleanValue")).thenReturn("boolean_value");
		when(handle.getPropertyName("intValue")).thenReturn(null);
		when(handle.getPropertyName("stringValue")).thenReturn("string_value");
		when(handle.getFieldTypeName("booleanValue")).thenReturn("boolean");
		when(handle.getFieldTypeName("intValue")).thenReturn("int");
		when(handle.getFieldTypeName("stringValue")).thenReturn("java.lang.String");

		Class<?> proxyType = c.getProxyType(Parent.class);

		Parent instance = new Parent();
		when(handle.create()).thenReturn(instance);
		Object proxy = newInstance(proxyType);
		assertSame(handle, getHandle(proxyType, proxy));
		assertSame(instance, c.getInstance(proxy));

		instance = new Parent();
		proxy = c.getProxy(Parent.class, handle, instance);
		assertInstanceOf(proxyType, proxy);
		assertSame(handle, getHandle(proxyType, proxy));
		assertSame(instance, c.getInstance(proxy));

		assertEquals("boolean_value", getDeclaredAnnotation(proxyType, proxy, "getBooleanValue").value());
		assertNull(getDeclaredAnnotation(proxyType, proxy, "getIntValue"));
		assertEquals("string_value", getDeclaredAnnotation(proxyType, proxy, "getStringValue").value());

		assertEquals("boolean_value", getDeclaredAnnotation(proxyType, proxy, "setBooleanValue", boolean.class).value());
		assertNull(getDeclaredAnnotation(proxyType, proxy, "setIntValue", int.class));
		assertEquals("string_value", getDeclaredAnnotation(proxyType, proxy, "setStringValue", String.class).value());

		when(handle.get("booleanValue", instance)).thenReturn(true);
		when(handle.get("intValue", instance)).thenReturn(1);
		when(handle.get("stringValue", instance)).thenReturn("p");

		assertTrue((boolean) invokeGetter(proxyType, proxy, "getBooleanValue"));
		assertEquals(1, invokeGetter(proxyType, proxy, "getIntValue"));
		assertEquals("p", invokeGetter(proxyType, proxy, "getStringValue"));

		verify(handle).get("booleanValue", instance);
		verify(handle).get("intValue", instance);
		verify(handle).get("stringValue", instance);

		invokeSetter(proxyType, proxy, "setBooleanValue", boolean.class, false);
		invokeSetter(proxyType, proxy, "setIntValue", int.class, 0);
		invokeSetter(proxyType, proxy, "setStringValue", String.class, null);

		verify(handle).set("booleanValue", instance, false);
		verify(handle).set("intValue", instance, 0);
		verify(handle).set("stringValue", instance, null);
	}

	@Test
	void compilesChild() {
		when(handle.getFieldNames()).thenReturn(Set.of("booleanValue", "intValue", "doubleValue", "stringValue"));
		when(handle.getPropertyName("booleanValue")).thenReturn(null);
		when(handle.getPropertyName("intValue")).thenReturn(null);
		when(handle.getPropertyName("doubleValue")).thenReturn(null);
		when(handle.getPropertyName("stringValue")).thenReturn(null);
		when(handle.getFieldTypeName("booleanValue")).thenReturn("boolean");
		when(handle.getFieldTypeName("intValue")).thenReturn("int");
		when(handle.getFieldTypeName("doubleValue")).thenReturn("double");
		when(handle.getFieldTypeName("stringValue")).thenReturn("java.lang.String");

		Class<?> proxyType = c.getProxyType(Child.class);

		Child instance = new Child();
		when(handle.create()).thenReturn(instance);
		Object proxy = newInstance(proxyType);
		assertSame(handle, getHandle(proxyType, proxy));
		assertSame(instance, c.getInstance(proxy));

		instance = new Child();
		proxy = c.getProxy(Child.class, handle, instance);
		assertInstanceOf(proxyType, proxy);
		assertSame(handle, getHandle(proxyType, proxy));
		assertSame(instance, c.getInstance(proxy));

		assertNull(getDeclaredAnnotation(proxyType, proxy, "getBooleanValue"));
		assertNull(getDeclaredAnnotation(proxyType, proxy, "getIntValue"));
		assertNull(getDeclaredAnnotation(proxyType, proxy, "getDoubleValue"));
		assertNull(getDeclaredAnnotation(proxyType, proxy, "getStringValue"));

		assertNull(getDeclaredAnnotation(proxyType, proxy, "setBooleanValue", boolean.class));
		assertNull(getDeclaredAnnotation(proxyType, proxy, "setIntValue", int.class));
		assertNull(getDeclaredAnnotation(proxyType, proxy, "setDoubleValue", double.class));
		assertNull(getDeclaredAnnotation(proxyType, proxy, "setStringValue", String.class));

		when(handle.get("booleanValue", instance)).thenReturn(false);
		when(handle.get("intValue", instance)).thenReturn(1);
		when(handle.get("doubleValue", instance)).thenReturn(2.0);
		when(handle.get("stringValue", instance)).thenReturn("c");

		assertFalse((boolean) invokeGetter(proxyType, proxy, "getBooleanValue"));
		assertEquals(1, invokeGetter(proxyType, proxy, "getIntValue"));
		assertEquals(2, (double) invokeGetter(proxyType, proxy, "getDoubleValue"), DELTA);
		assertEquals("c", invokeGetter(proxyType, proxy, "getStringValue"));

		verify(handle).get("booleanValue", instance);
		verify(handle).get("intValue", instance);
		verify(handle).get("doubleValue", instance);
		verify(handle).get("stringValue", instance);

		invokeSetter(proxyType, proxy, "setBooleanValue", boolean.class, true);
		invokeSetter(proxyType, proxy, "setIntValue", int.class, 0);
		invokeSetter(proxyType, proxy, "setDoubleValue", double.class, 1);
		invokeSetter(proxyType, proxy, "setStringValue", String.class, null);

		verify(handle).set("booleanValue", instance, true);
		verify(handle).set("intValue", instance, 0);
		verify(handle).set(eq("doubleValue"), eq(instance), eq(1, DELTA));
		verify(handle).set("stringValue", instance, null);
	}

	@Test
	void compilesGrandChild() {
		when(handle.getFieldNames()).thenReturn(Set.of("booleanValue", "intValue", "doubleValue", "stringValue"));
		when(handle.getPropertyName("booleanValue")).thenReturn("boolean_value");
		when(handle.getPropertyName("intValue")).thenReturn(null);
		when(handle.getPropertyName("doubleValue")).thenReturn(null);
		when(handle.getPropertyName("stringValue")).thenReturn("string_value");
		when(handle.getFieldTypeName("booleanValue")).thenReturn("boolean");
		when(handle.getFieldTypeName("intValue")).thenReturn("int");
		when(handle.getFieldTypeName("doubleValue")).thenReturn("double");
		when(handle.getFieldTypeName("stringValue")).thenReturn("java.lang.String");

		Class<?> proxyType = c.getProxyType(GrandChild.class);

		GrandChild instance = new GrandChild();
		when(handle.create()).thenReturn(instance);
		Object proxy = newInstance(proxyType);
		assertSame(handle, getHandle(proxyType, proxy));
		assertSame(instance, c.getInstance(proxy));

		instance = new GrandChild();
		proxy = c.getProxy(GrandChild.class, handle, instance);
		assertInstanceOf(proxyType, proxy);
		assertSame(handle, getHandle(proxyType, proxy));
		assertSame(instance, c.getInstance(proxy));

		assertEquals("boolean_value", getDeclaredAnnotation(proxyType, proxy, "getBooleanValue").value());
		assertNull(getDeclaredAnnotation(proxyType, proxy, "getIntValue"));
		assertNull(getDeclaredAnnotation(proxyType, proxy, "getDoubleValue"));
		assertEquals("string_value", getDeclaredAnnotation(proxyType, proxy, "getStringValue").value());

		assertEquals("boolean_value", getDeclaredAnnotation(proxyType, proxy, "setBooleanValue", boolean.class).value());
		assertNull(getDeclaredAnnotation(proxyType, proxy, "setIntValue", int.class));
		assertNull(getDeclaredAnnotation(proxyType, proxy, "setDoubleValue", double.class));
		assertEquals("string_value", getDeclaredAnnotation(proxyType, proxy, "setStringValue", String.class).value());

		when(handle.get("booleanValue", instance)).thenReturn(true);
		when(handle.get("intValue", instance)).thenReturn(1);
		when(handle.get("doubleValue", instance)).thenReturn(3.0);
		when(handle.get("stringValue", instance)).thenReturn("g");

		assertTrue((boolean) invokeGetter(proxyType, proxy, "getBooleanValue"));
		assertEquals(1, invokeGetter(proxyType, proxy, "getIntValue"));
		assertEquals(3, (double) invokeGetter(proxyType, proxy, "getDoubleValue"), DELTA);
		assertEquals("g", invokeGetter(proxyType, proxy, "getStringValue"));

		verify(handle).get("booleanValue", instance);
		verify(handle).get("intValue", instance);
		verify(handle).get("doubleValue", instance);
		verify(handle).get("stringValue", instance);

		invokeSetter(proxyType, proxy, "setBooleanValue", boolean.class, false);
		invokeSetter(proxyType, proxy, "setIntValue", int.class, 0);
		invokeSetter(proxyType, proxy, "setDoubleValue", double.class, 2);
		invokeSetter(proxyType, proxy, "setStringValue", String.class, null);

		verify(handle).set("booleanValue", instance, false);
		verify(handle).set("intValue", instance, 0);
		verify(handle).set(eq("doubleValue"), eq(instance), eq(2, DELTA));
		verify(handle).set("stringValue", instance, null);
	}

	private <T> Object newInstance(Class<T> proxyType) {
		Object instance = assertDoesNotThrow(() -> {
			Constructor<T> constructor = proxyType.getDeclaredConstructor();
			return constructor.newInstance();
		});
		return instance;
	}

	private Object getHandle(Class<?> type, Object proxy) {
		Field field = assertDoesNotThrow(() -> {
			return type.getDeclaredField("handle");
		});
		field.setAccessible(true);
		Object handle = assertDoesNotThrow(() -> {
			return field.get(proxy);
		});
		return handle;
	}

	private PropertyName getDeclaredAnnotation(Class<?> proxyType, Object proxy, String methodName, Class<?>... argTypes) {
		Method method = assertDoesNotThrow(() -> {
			return proxyType.getDeclaredMethod(methodName, argTypes);
		});
		return method.getDeclaredAnnotation(PropertyName.class);
	}

	private Object invokeGetter(Class<?> proxyType, Object proxy, String methodName) {
		Object value = assertDoesNotThrow(() -> {
			Method method = proxyType.getDeclaredMethod(methodName);
			return method.invoke(proxy);
		});
		return value;
	}

	private void invokeSetter(Class<?> proxyType, Object proxy, String methodName, Class<?> valueType, Object value) {
		assertDoesNotThrow(() -> {
			Method method = proxyType.getDeclaredMethod(methodName, valueType);
			method.invoke(proxy, value);
		});
	}

	@Test
	void doesNotCreateCtHandleField() {
		CtClass ctHandleType = getCtClass(Handle.class);
		assertThrows(AssertionError.class, () -> {
			c.createAndAddCtHandleField(CtClass.voidType, ctHandleType);
		});
	}

	@Test
	void doesNotAddCtHandleField() {
		CtClass ctType = getCtClass(Object.class);
		CtClass ctHandleType = getCtClass(Handle.class);
		c.createAndAddCtHandleField(ctType, ctHandleType);
		assertThrows(AssertionError.class, () -> {
			c.createAndAddCtHandleField(ctType, ctHandleType);
		});
	}

	@Test
	void doesNotCreateAndAddCtConstructor() {
		CtClass ctType = getCtClass(Object.class);
		assertThrows(AssertionError.class, () -> {
			c.createAndAddCtConstructor(ctType, null, null);
		});
	}

	@Test
	void doesNotCreateCtMethod() {
		CtClass ctType = getCtClass(Object.class);
		assertThrows(AssertionError.class, () -> {
			c.createAndAddCtMethod(ctType, CtClass.voidType, "", null, "", null);
		});
	}

	@Test
	void doesNotAddCtMethod() {
		CtClass ctType = getCtClass(Object.class);
		c.createAndAddCtMethod(ctType, CtClass.voidType, "", null, "{}", null);
		assertThrows(AssertionError.class, () -> {
			c.createAndAddCtMethod(ctType, CtClass.voidType, "", null, "{}", null);
		});
	}

	@Test
	void doesNotCreateCtClass() {
		CtClass ctType = getCtClass(Object.class);
		assertThrows(AssertionError.class, () -> {
			c.toClass(ctType);
		});
	}

	private CtClass getCtClass(Class<?> type) {
		String typeName = type.getName();
		CtClass ctType = assertDoesNotThrow(() -> {
			return POOL.get(typeName);
		});
		return ctType;
	}

	@Test
	void doesNotGetCtClass() {
		assertThrows(AssertionError.class, () -> {
			c.getCtClass(null);
		});
	}
}
