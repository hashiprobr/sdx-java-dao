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
import static org.mockito.Mockito.doReturn;
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
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.google.cloud.firestore.annotation.PropertyName;

import br.pro.hashi.sdx.dao.DaoConverter;
import br.pro.hashi.sdx.dao.reflection.mock.handle.Child;
import br.pro.hashi.sdx.dao.reflection.mock.handle.ConvertableFields;
import br.pro.hashi.sdx.dao.reflection.mock.handle.GrandChild;
import br.pro.hashi.sdx.dao.reflection.mock.handle.Parent;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.Address;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.AddressConverter;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.BooleanWrapperConverter;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.ByteWrapperConverter;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.Email;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.EmailConverter;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.Sheet;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.SheetConverter;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.Wrapper;
import javassist.ClassPool;
import javassist.CtClass;

class CompilerTest {
	private static final ClassPool POOL = ClassPool.getDefault();
	private static final Lookup LOOKUP = MethodHandles.lookup();
	private static final double DELTA = 0.000001;

	private Handle handle;
	private Reflector reflector;
	private ConverterFactory converterFactory;
	private HandleFactory handleFactory;
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
		converterFactory = mock(ConverterFactory.class);
		handleFactory = mock(HandleFactory.class);
		when(handleFactory.get(any())).thenReturn(handle);
		handleStatic = mockStatic(Handle.class);
		handleStatic.when(() -> Handle.of(any())).thenReturn(handle);
		c = new Compiler(reflector, converterFactory, handleFactory);
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
	void constructsWithDefaultReflectorAndFactories() {
		Compiler compiler;
		try (MockedStatic<Reflector> reflectorStatic = mockStatic(Reflector.class)) {
			reflectorStatic.when(() -> Reflector.getInstance()).thenReturn(reflector);
			try (MockedStatic<ConverterFactory> converterStatic = mockStatic(ConverterFactory.class)) {
				converterStatic.when(() -> ConverterFactory.getInstance()).thenReturn(converterFactory);
				try (MockedStatic<HandleFactory> handleStatic = mockStatic(HandleFactory.class)) {
					handleStatic.when(() -> HandleFactory.getInstance()).thenReturn(handleFactory);
					compiler = new Compiler();
				}
				assertSame(handleFactory, compiler.getHandleFactory());
			}
			assertSame(converterFactory, compiler.getConverterFactory());
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
		doReturn(Parent.class).when(handle).getType();
		when(handle.getFieldNames()).thenReturn(Set.of("booleanValue", "intValue", "stringValue"));
		when(handle.getPropertyName("booleanValue")).thenReturn("boolean_value");
		when(handle.getPropertyName("intValue")).thenReturn(null);
		when(handle.getPropertyName("stringValue")).thenReturn("string_value");
		when(handle.getFieldTypeName("booleanValue")).thenReturn("boolean");
		when(handle.getFieldTypeName("intValue")).thenReturn("int");
		when(handle.getFieldTypeName("stringValue")).thenReturn("java.lang.String");
		Class<?> proxyType = c.getProxyType(handle);

		Parent instance = new Parent();
		when(handle.create()).thenReturn(instance);
		Object proxy = newInstance(proxyType);
		assertSame(handle, getHandle(proxyType, proxy));
		assertSame(instance, c.getInstance(proxy));

		instance = new Parent();
		proxy = c.getProxy(handle, instance);
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
		doReturn(Child.class).when(handle).getType();
		when(handle.getFieldNames()).thenReturn(Set.of("booleanValue", "intValue", "doubleValue", "stringValue"));
		when(handle.getPropertyName("booleanValue")).thenReturn(null);
		when(handle.getPropertyName("intValue")).thenReturn(null);
		when(handle.getPropertyName("doubleValue")).thenReturn(null);
		when(handle.getPropertyName("stringValue")).thenReturn(null);
		when(handle.getFieldTypeName("booleanValue")).thenReturn("boolean");
		when(handle.getFieldTypeName("intValue")).thenReturn("int");
		when(handle.getFieldTypeName("doubleValue")).thenReturn("double");
		when(handle.getFieldTypeName("stringValue")).thenReturn("java.lang.String");
		Class<?> proxyType = c.getProxyType(handle);

		Child instance = new Child();
		when(handle.create()).thenReturn(instance);
		Object proxy = newInstance(proxyType);
		assertSame(handle, getHandle(proxyType, proxy));
		assertSame(instance, c.getInstance(proxy));

		instance = new Child();
		proxy = c.getProxy(handle, instance);
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
		doReturn(GrandChild.class).when(handle).getType();
		when(handle.getFieldNames()).thenReturn(Set.of("booleanValue", "intValue", "doubleValue", "stringValue"));
		when(handle.getPropertyName("booleanValue")).thenReturn("boolean_value");
		when(handle.getPropertyName("intValue")).thenReturn(null);
		when(handle.getPropertyName("doubleValue")).thenReturn(null);
		when(handle.getPropertyName("stringValue")).thenReturn("string_value");
		when(handle.getFieldTypeName("booleanValue")).thenReturn("boolean");
		when(handle.getFieldTypeName("intValue")).thenReturn("int");
		when(handle.getFieldTypeName("doubleValue")).thenReturn("double");
		when(handle.getFieldTypeName("stringValue")).thenReturn("java.lang.String");
		Class<?> proxyType = c.getProxyType(handle);

		GrandChild instance = new GrandChild();
		when(handle.create()).thenReturn(instance);
		Object proxy = newInstance(proxyType);
		assertSame(handle, getHandle(proxyType, proxy));
		assertSame(instance, c.getInstance(proxy));

		instance = new GrandChild();
		proxy = c.getProxy(handle, instance);
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

	@Test
	void compilesConvertableFields() {
		DaoConverter<?, ?> emailConverter = new EmailConverter();
		DaoConverter<?, ?> addressConverter = new AddressConverter();
		DaoConverter<?, ?> sheetConverter = new SheetConverter();
		DaoConverter<?, ?> booleanWrapperConverter = new BooleanWrapperConverter();
		DaoConverter<?, ?> byteWrapperConverter = new ByteWrapperConverter();

		when(converterFactory.getTargetType(emailConverter)).thenReturn(String.class);
		when(converterFactory.getTargetType(addressConverter)).thenReturn(List.class);
		when(converterFactory.getTargetType(sheetConverter)).thenReturn(List.class);
		when(converterFactory.getTargetType(booleanWrapperConverter)).thenReturn(String.class);
		when(converterFactory.getTargetType(byteWrapperConverter)).thenReturn(List.class);

		doReturn(ConvertableFields.class).when(handle).getType();
		when(handle.getConverter("key")).thenReturn(null);
		when(handle.getConverter("value")).thenReturn(null);
		doReturn(emailConverter).when(handle).getConverter("email");
		doReturn(addressConverter).when(handle).getConverter("address");
		doReturn(sheetConverter).when(handle).getConverter("sheet");
		doReturn(booleanWrapperConverter).when(handle).getConverter("booleanWrapper");
		doReturn(byteWrapperConverter).when(handle).getConverter("byteWrapper");
		when(handle.getFieldNames()).thenReturn(Set.of("key", "value", "email", "address", "sheet", "booleanWrapper", "byteWrapper"));
		when(handle.getPropertyName("key")).thenReturn(null);
		when(handle.getPropertyName("value")).thenReturn(null);
		when(handle.getPropertyName("email")).thenReturn(null);
		when(handle.getPropertyName("address")).thenReturn(null);
		when(handle.getPropertyName("sheet")).thenReturn(null);
		when(handle.getPropertyName("booleanWrapper")).thenReturn("boolean_wrapper");
		when(handle.getPropertyName("byteWrapper")).thenReturn("byte_wrapper");
		when(handle.getFieldTypeName("key")).thenReturn("java.lang.String");
		when(handle.getFieldTypeName("value")).thenReturn("java.lang.String");
		when(handle.getFieldTypeName("email")).thenReturn(Email.class.getName());
		when(handle.getFieldTypeName("address")).thenReturn(Address.class.getName());
		when(handle.getFieldTypeName("sheet")).thenReturn(Sheet.class.getName());
		when(handle.getFieldTypeName("booleanWrapper")).thenReturn(Wrapper.class.getName());
		when(handle.getFieldTypeName("byteWrapper")).thenReturn(Wrapper.class.getName());
		Class<?> proxyType = c.getProxyType(handle);

		ConvertableFields instance = new ConvertableFields();
		when(handle.create()).thenReturn(instance);
		Object proxy = newInstance(proxyType);
		assertSame(handle, getHandle(proxyType, proxy));
		assertSame(instance, c.getInstance(proxy));

		instance = new ConvertableFields();
		proxy = c.getProxy(handle, instance);
		assertInstanceOf(proxyType, proxy);
		assertSame(handle, getHandle(proxyType, proxy));
		assertSame(instance, c.getInstance(proxy));

		assertNull(getDeclaredAnnotation(proxyType, proxy, "getKey"));
		assertNull(getDeclaredAnnotation(proxyType, proxy, "getValue"));
		assertNull(getDeclaredAnnotation(proxyType, proxy, "getEmail"));
		assertNull(getDeclaredAnnotation(proxyType, proxy, "getAddress"));
		assertNull(getDeclaredAnnotation(proxyType, proxy, "getSheet"));
		assertEquals("boolean_wrapper", getDeclaredAnnotation(proxyType, proxy, "getBooleanWrapper").value());
		assertEquals("byte_wrapper", getDeclaredAnnotation(proxyType, proxy, "getByteWrapper").value());

		assertNull(getDeclaredAnnotation(proxyType, proxy, "setKey", String.class));
		assertNull(getDeclaredAnnotation(proxyType, proxy, "setValue", String.class));
		assertNull(getDeclaredAnnotation(proxyType, proxy, "setEmail", String.class));
		assertNull(getDeclaredAnnotation(proxyType, proxy, "setAddress", List.class));
		assertNull(getDeclaredAnnotation(proxyType, proxy, "setSheet", List.class));
		assertEquals("boolean_wrapper", getDeclaredAnnotation(proxyType, proxy, "setBooleanWrapper", String.class).value());
		assertEquals("byte_wrapper", getDeclaredAnnotation(proxyType, proxy, "setByteWrapper", List.class).value());

		Email email = new Email();
		email.setLogin("convertable");
		email.setDomain("email.com");
		Sheet sheet = new Sheet();
		sheet.addRow("Street 0", 0, "City 0");
		sheet.addRow("Street 1", 1, "City 1");
		when(handle.get("key", instance)).thenReturn("key");
		when(handle.get("value", instance)).thenReturn("value");
		when(handle.get("email", instance)).thenReturn("convertable@email.com");
		when(handle.get("address", instance)).thenReturn(List.of("Convertable City", "0", "Convertable Street"));
		when(handle.get("sheet", instance)).thenReturn(List.of(new Address("Street 0", 0, "City 0"), new Address("Street 1", 1, "City 1")));
		when(handle.get("booleanWrapper", instance)).thenReturn("true");
		when(handle.get("byteWrapper", instance)).thenReturn(List.of('1', '2', '7'));

		assertEquals("key", invokeGetter(proxyType, proxy, "getKey"));
		assertEquals("value", invokeGetter(proxyType, proxy, "getValue"));
		assertEquals("convertable@email.com", invokeGetter(proxyType, proxy, "getEmail"));
		assertEquals(List.of("Convertable City", "0", "Convertable Street"), invokeGetter(proxyType, proxy, "getAddress"));
		List<?> addresses = assertInstanceOf(List.class, invokeGetter(proxyType, proxy, "getSheet"));
		for (int i = 0; i < addresses.size(); i++) {
			Address address = assertInstanceOf(Address.class, addresses.get(i));
			assertEquals("Street %d".formatted(i), address.getStreet());
			assertEquals(i, address.getNumber());
			assertEquals("City %d".formatted(i), address.getCity());
		}
		assertEquals("true", invokeGetter(proxyType, proxy, "getBooleanWrapper"));
		assertEquals(List.of('1', '2', '7'), invokeGetter(proxyType, proxy, "getByteWrapper"));

		verify(handle).get("key", instance);
		verify(handle).get("value", instance);
		verify(handle).get("email", instance);
		verify(handle).get("address", instance);
		verify(handle).get("sheet", instance);
		verify(handle).get("booleanWrapper", instance);
		verify(handle).get("byteWrapper", instance);

		addresses = List.of(new Address("1 Street", 1, "1 City"), new Address("0 Street", 0, "0 City"));
		invokeSetter(proxyType, proxy, "setKey", String.class, null);
		invokeSetter(proxyType, proxy, "setValue", String.class, null);
		invokeSetter(proxyType, proxy, "setEmail", String.class, "email@convertable.com");
		invokeSetter(proxyType, proxy, "setAddress", List.class, List.of("City Convertable", "1", "Street Convertable"));
		invokeSetter(proxyType, proxy, "setSheet", List.class, addresses);
		invokeSetter(proxyType, proxy, "setBooleanWrapper", String.class, "false");
		invokeSetter(proxyType, proxy, "setByteWrapper", List.class, List.of('6', '3'));

		verify(handle).set("key", instance, null);
		verify(handle).set("value", instance, null);
		verify(handle).set("email", instance, "email@convertable.com");
		verify(handle).set("address", instance, List.of("City Convertable", "1", "Street Convertable"));
		verify(handle).set("sheet", instance, addresses);
		verify(handle).set("booleanWrapper", instance, "false");
		verify(handle).set("byteWrapper", instance, List.of('6', '3'));
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
