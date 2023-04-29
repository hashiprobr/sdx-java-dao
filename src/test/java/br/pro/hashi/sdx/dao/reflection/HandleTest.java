package br.pro.hashi.sdx.dao.reflection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.google.cloud.firestore.FieldValue;

import br.pro.hashi.sdx.dao.DaoConverter;
import br.pro.hashi.sdx.dao.reflection.Handle.Construction;
import br.pro.hashi.sdx.dao.reflection.exception.AnnotationException;
import br.pro.hashi.sdx.dao.reflection.exception.ReflectionException;
import br.pro.hashi.sdx.dao.reflection.mock.handle.BlankCollectionName;
import br.pro.hashi.sdx.dao.reflection.mock.handle.BlankPropertyName;
import br.pro.hashi.sdx.dao.reflection.mock.handle.Child;
import br.pro.hashi.sdx.dao.reflection.mock.handle.ConvertableFields;
import br.pro.hashi.sdx.dao.reflection.mock.handle.ConvertedFileField;
import br.pro.hashi.sdx.dao.reflection.mock.handle.ConvertedKeyField;
import br.pro.hashi.sdx.dao.reflection.mock.handle.Default;
import br.pro.hashi.sdx.dao.reflection.mock.handle.DottedPropertyName;
import br.pro.hashi.sdx.dao.reflection.mock.handle.FileKeyField;
import br.pro.hashi.sdx.dao.reflection.mock.handle.GrandChild;
import br.pro.hashi.sdx.dao.reflection.mock.handle.NoKeyFields;
import br.pro.hashi.sdx.dao.reflection.mock.handle.NonConvertableField;
import br.pro.hashi.sdx.dao.reflection.mock.handle.NonFileWebField;
import br.pro.hashi.sdx.dao.reflection.mock.handle.NonKeyAutoField;
import br.pro.hashi.sdx.dao.reflection.mock.handle.NonStringAutoField;
import br.pro.hashi.sdx.dao.reflection.mock.handle.NonStringFileField;
import br.pro.hashi.sdx.dao.reflection.mock.handle.Parent;
import br.pro.hashi.sdx.dao.reflection.mock.handle.PluralEntities;
import br.pro.hashi.sdx.dao.reflection.mock.handle.SingularEntity;
import br.pro.hashi.sdx.dao.reflection.mock.handle.ThrowerConstructor;
import br.pro.hashi.sdx.dao.reflection.mock.handle.TwoKeyFields;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.Address;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.Email;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.Sheet;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.Wrapper;

class HandleTest {
	private static final Lookup LOOKUP = MethodHandles.lookup();
	private static final double DELTA = 0.000001;

	private Reflector reflector;
	private ConverterFactory factory;

	@BeforeEach
	<E, F> void setUp() {
		reflector = mock(Reflector.class);
		when(reflector.getCreator(any(), any(String.class))).thenAnswer((invocation) -> {
			Class<E> type = invocation.getArgument(0);
			Constructor<E> constructor = type.getDeclaredConstructor();
			return LOOKUP.unreflectConstructor(constructor);
		});
		when(reflector.invokeCreator(any(MethodHandle.class))).thenAnswer((invocation) -> {
			MethodHandle creator = invocation.getArgument(0);
			return creator.invoke();
		});
		when(reflector.unreflectGetter(any(Field.class))).thenAnswer((invocation) -> {
			Field field = invocation.getArgument(0);
			return LOOKUP.unreflectGetter(field);
		});
		when(reflector.unreflectSetter(any(Field.class))).thenAnswer((invocation) -> {
			Field field = invocation.getArgument(0);
			return LOOKUP.unreflectSetter(field);
		});
		when(reflector.invokeGetter(any(MethodHandle.class), any())).thenAnswer((invocation) -> {
			MethodHandle getter = invocation.getArgument(0);
			E instance = invocation.getArgument(1);
			return getter.invoke(instance);
		});
		doAnswer((invocation) -> {
			MethodHandle setter = invocation.getArgument(0);
			E instance = invocation.getArgument(1);
			F value = invocation.getArgument(2);
			setter.invoke(instance, value);
			return null;
		}).when(reflector).invokeSetter(any(MethodHandle.class), any(), any());
		factory = mock(ConverterFactory.class);
		when(factory.get(any())).thenAnswer((invocation) -> {
			Class<? extends DaoConverter<?, ?>> type = invocation.getArgument(0);
			Constructor<? extends DaoConverter<?, ?>> constructor = type.getDeclaredConstructor();
			return constructor.newInstance();
		});
		when(factory.getSourceType(any(DaoConverter.class))).thenAnswer((invocation) -> {
			DaoConverter<?, ?> converter = invocation.getArgument(0);
			Class<?> type = converter.getClass();
			Type[] genericInterfaces = type.getGenericInterfaces();
			ParameterizedType genericInterface = (ParameterizedType) genericInterfaces[0];
			Type[] types = genericInterface.getActualTypeArguments();
			return types[0];
		});
	}

	@Test
	void constructsWithDefaultReflector() {
		Handle<Default> handle;
		try (MockedStatic<Reflector> reflectorStatic = mockStatic(Reflector.class)) {
			reflectorStatic.when(() -> Reflector.getInstance()).thenReturn(reflector);
			handle = Construction.of(factory, Default.class);
		}
		assertSame(reflector, handle.getReflector());
	}

	@Test
	void constructsWithParent() {
		Handle<Parent> h = newHandle(Parent.class);
		assertEquals("Parents", h.getCollectionName());
		assertEquals(Set.of("booleanValue", "intValue", "stringValue"), h.getFieldNames());
		assertFalse(h.hasAutoKey());
		assertEquals(Set.of("stringValue"), h.getFileFieldNames());

		assertNull(h.getContentType("booleanValue"));
		assertNull(h.getContentType("intValue"));
		assertEquals("application/octet-stream", h.getContentType("stringValue"));

		assertFalse(h.isWeb("booleanValue"));
		assertFalse(h.isWeb("intValue"));
		assertTrue(h.isWeb("stringValue"));

		Parent instance;

		instance = h.toInstance(Map.of());
		assertTrue(instance.isBooleanValue());
		assertEquals(1, instance.getIntValue());
		assertEquals("p", instance.getStringValue());

		instance = h.toInstance(Map.of(
				"boolean_value", false,
				"intValue", 0,
				"string_value", ""));
		assertFalse(instance.isBooleanValue());
		assertEquals(0, instance.getIntValue());
		assertEquals("", instance.getStringValue());

		assertTrue(h.toValues(Map.of()).isEmpty());

		Map<String, Object> values = h.toValues(Map.of(
				"boolean_value", true,
				"intValue", 1,
				"string_value", "p"));
		assertTrue((boolean) values.get("booleanValue"));
		assertEquals(1, values.get("intValue"));
		assertEquals("p", values.get("stringValue"));

		Map<String, Object> data;

		data = h.toData(instance, false, false);
		assertFalse(data.containsKey("boolean_value"));
		assertEquals(0, data.get("intValue"));
		assertFalse(data.containsKey("string_value"));

		data = h.toData(instance, false, true);
		assertFalse((boolean) data.get("boolean_value"));
		assertEquals(0, data.get("intValue"));
		assertFalse(data.containsKey("string_value"));

		data = h.toData(instance, true, false);
		assertFalse(data.containsKey("boolean_value"));
		assertEquals(0, data.get("intValue"));
		assertTrue(data.containsKey("string_value"));
		assertNull(data.get("string_value"));

		data = h.toData(instance, true, true);
		assertFalse((boolean) data.get("boolean_value"));
		assertEquals(0, data.get("intValue"));
		assertTrue(data.containsKey("string_value"));
		assertNull(data.get("string_value"));

		values.remove("booleanValue");
		values.remove("stringValue");

		data = h.toData(values);
		assertFalse(data.containsKey("boolean_value"));
		assertEquals(1, data.get("intValue"));
		assertFalse(data.containsKey("string_value"));
	}

	@Test
	void constructsWithChild() {
		Handle<Child> h = newHandle(Child.class);
		assertEquals("Children", h.getCollectionName());
		assertEquals(Set.of("booleanValue", "intValue", "doubleValue", "stringValue"), h.getFieldNames());
		assertFalse(h.hasAutoKey());
		assertEquals(Set.of(), h.getFileFieldNames());

		assertNull(h.getContentType("booleanValue"));
		assertNull(h.getContentType("intValue"));
		assertNull(h.getContentType("doubleValue"));
		assertNull(h.getContentType("stringValue"));

		assertFalse(h.isWeb("booleanValue"));
		assertFalse(h.isWeb("intValue"));
		assertFalse(h.isWeb("doubleValue"));
		assertFalse(h.isWeb("stringValue"));

		Child instance;

		instance = h.toInstance(Map.of());
		assertFalse(instance.isBooleanValue());
		assertEquals(1, instance.getIntValue());
		assertEquals(2, instance.getDoubleValue(), DELTA);
		assertEquals("c", instance.getStringValue());

		instance = h.toInstance(Map.of(
				"booleanValue", true,
				"intValue", 0,
				"doubleValue", 1,
				"stringValue", ""));
		assertTrue(instance.isBooleanValue());
		assertEquals(0, instance.getIntValue());
		assertEquals(1, instance.getDoubleValue(), DELTA);
		assertEquals("", instance.getStringValue());

		assertTrue(h.toValues(Map.of()).isEmpty());

		Map<String, Object> values = h.toValues(Map.of(
				"booleanValue", false,
				"intValue", 1,
				"doubleValue", 2.0,
				"stringValue", "c"));
		assertFalse((boolean) values.get("booleanValue"));
		assertEquals(1, values.get("intValue"));
		assertEquals(2, (double) values.get("doubleValue"), DELTA);
		assertEquals("c", values.get("stringValue"));

		Map<String, Object> data;

		data = h.toData(instance, false, false);
		assertTrue((boolean) data.get("booleanValue"));
		assertEquals(0, data.get("intValue"));
		assertFalse(data.containsKey("doubleValue"));
		assertEquals("", data.get("stringValue"));

		data = h.toData(instance, false, true);
		assertTrue((boolean) data.get("booleanValue"));
		assertEquals(0, data.get("intValue"));
		assertEquals(1, (double) data.get("doubleValue"), DELTA);
		assertEquals("", data.get("stringValue"));

		data = h.toData(instance, true, false);
		assertTrue((boolean) data.get("booleanValue"));
		assertEquals(0, data.get("intValue"));
		assertFalse(data.containsKey("doubleValue"));
		assertEquals("", data.get("stringValue"));

		data = h.toData(instance, true, true);
		assertTrue((boolean) data.get("booleanValue"));
		assertEquals(0, data.get("intValue"));
		assertEquals(1, (double) data.get("doubleValue"), DELTA);
		assertEquals("", data.get("stringValue"));

		values.remove("doubleValue");

		data = h.toData(values);
		assertFalse((boolean) data.get("booleanValue"));
		assertEquals(1, data.get("intValue"));
		assertFalse(data.containsKey("doubleValue"));
		assertEquals("c", data.get("stringValue"));
	}

	@Test
	void constructsWithGrandChild() {
		Handle<GrandChild> h = newHandle(GrandChild.class);
		assertEquals("GrandChilds", h.getCollectionName());
		assertEquals(Set.of("booleanValue", "intValue", "doubleValue", "stringValue"), h.getFieldNames());
		assertFalse(h.hasAutoKey());
		assertEquals(Set.of("stringValue"), h.getFileFieldNames());

		assertNull(h.getContentType("booleanValue"));
		assertNull(h.getContentType("intValue"));
		assertNull(h.getContentType("doubleValue"));
		assertEquals("image/png", h.getContentType("stringValue"));

		assertFalse(h.isWeb("booleanValue"));
		assertFalse(h.isWeb("intValue"));
		assertFalse(h.isWeb("doubleValue"));
		assertTrue(h.isWeb("stringValue"));

		GrandChild instance;

		instance = h.toInstance(Map.of());
		assertTrue(instance.isBooleanValue());
		assertEquals(1, instance.getIntValue());
		assertEquals(3, instance.getDoubleValue(), DELTA);
		assertEquals("g", instance.getStringValue());

		instance = h.toInstance(Map.of(
				"boolean_value", false,
				"intValue", 0,
				"doubleValue", 2,
				"string_value", ""));
		assertFalse(instance.isBooleanValue());
		assertEquals(0, instance.getIntValue());
		assertEquals(2, instance.getDoubleValue(), DELTA);
		assertEquals("", instance.getStringValue());

		assertTrue(h.toValues(Map.of()).isEmpty());

		Map<String, Object> values = h.toValues(Map.of(
				"boolean_value", true,
				"intValue", 1,
				"doubleValue", 3.0,
				"string_value", "g"));
		assertTrue((boolean) values.get("booleanValue"));
		assertEquals(1, values.get("intValue"));
		assertEquals(3, (double) values.get("doubleValue"), DELTA);
		assertEquals("g", values.get("stringValue"));

		Map<String, Object> data;

		data = h.toData(instance, false, false);
		assertFalse(data.containsKey("boolean_value"));
		assertEquals(0, data.get("intValue"));
		assertEquals(2, (double) data.get("doubleValue"), DELTA);
		assertFalse(data.containsKey("string_value"));

		data = h.toData(instance, false, true);
		assertFalse((boolean) data.get("boolean_value"));
		assertEquals(0, data.get("intValue"));
		assertEquals(2, (double) data.get("doubleValue"), DELTA);
		assertFalse(data.containsKey("string_value"));

		data = h.toData(instance, true, false);
		assertFalse(data.containsKey("boolean_value"));
		assertEquals(0, data.get("intValue"));
		assertEquals(2, (double) data.get("doubleValue"), DELTA);
		assertTrue(data.containsKey("string_value"));
		assertNull(data.get("string_value"));

		data = h.toData(instance, true, true);
		assertFalse((boolean) data.get("boolean_value"));
		assertEquals(0, data.get("intValue"));
		assertEquals(2, (double) data.get("doubleValue"), DELTA);
		assertTrue(data.containsKey("string_value"));
		assertNull(data.get("string_value"));

		values.remove("booleanValue");
		values.remove("stringValue");

		data = h.toData(values);
		assertFalse(data.containsKey("boolean_value"));
		assertEquals(1, data.get("intValue"));
		assertEquals(3, (double) data.get("doubleValue"), DELTA);
		assertFalse(data.containsKey("string_value"));
	}

	@Test
	void constructsWithConvertableFields() {
		Handle<ConvertableFields> h = newHandle(ConvertableFields.class);
		assertEquals("ConvertableFields", h.getCollectionName());
		assertEquals(Set.of("key", "value", "email", "address", "sheet", "booleanWrapper", "byteWrapper"), h.getFieldNames());
		assertTrue(h.hasAutoKey());
		assertEquals(Set.of(), h.getFileFieldNames());

		assertNull(h.getContentType("key"));
		assertNull(h.getContentType("value"));
		assertNull(h.getContentType("email"));
		assertNull(h.getContentType("address"));
		assertNull(h.getContentType("sheet"));
		assertNull(h.getContentType("booleanWrapper"));
		assertNull(h.getContentType("byteWrapper"));

		assertFalse(h.isWeb("key"));
		assertFalse(h.isWeb("value"));
		assertFalse(h.isWeb("email"));
		assertFalse(h.isWeb("address"));
		assertFalse(h.isWeb("sheet"));
		assertFalse(h.isWeb("booleanWrapper"));
		assertFalse(h.isWeb("byteWrapper"));

		Email email;
		Address address;
		Sheet sheet;

		ConvertableFields instance = h.toInstance(Map.of(
				"value", "",
				"email", "email@convertable.com",
				"address", List.of("City Convertable", "1", "Street Convertable"),
				"sheet", List.of(new Address("1 Street", 1, "1 City"), new Address("0 Street", 0, "0 City")),
				"boolean_wrapper", "false",
				"byte_wrapper", List.of('6', '3')));
		h.setAutoKey(instance, "");
		assertEquals("", h.getKey(instance));
		assertEquals("", instance.getValue());
		email = instance.getEmail();
		assertEquals("email", email.getLogin());
		assertEquals("convertable.com", email.getDomain());
		address = instance.getAddress();
		assertEquals("Street Convertable", address.getStreet());
		assertEquals(1, address.getNumber());
		assertEquals("City Convertable", address.getCity());
		sheet = instance.getSheet();
		int i = 1;
		for (List<String> row : sheet.getRows()) {
			assertEquals("%d Street".formatted(i), row.get(0));
			assertEquals(Integer.toString(i), row.get(1));
			assertEquals("%d City".formatted(i), row.get(2));
			i--;
		}
		assertFalse(instance.getBooleanWrapper().getValue());
		assertEquals(63, (byte) instance.getByteWrapper().getValue());

		Map<String, Object> values = h.toValues(Map.of(
				"value", "value",
				"email", "convertable@email.com",
				"address", List.of("Convertable City", "0", "Convertable Street"),
				"sheet", List.of(new Address("Street 0", 0, "City 0"), new Address("Street 1", 1, "City 1")),
				"boolean_wrapper", "true",
				"byte_wrapper", List.of('1', '2', '7')));
		h.putAutoKey(values, "key");
		assertEquals("key", values.get("key"));
		assertEquals("value", values.get("value"));
		email = assertInstanceOf(Email.class, values.get("email"));
		assertEquals("convertable", email.getLogin());
		assertEquals("email.com", email.getDomain());
		address = assertInstanceOf(Address.class, values.get("address"));
		assertEquals("Convertable Street", address.getStreet());
		assertEquals(0, address.getNumber());
		assertEquals("Convertable City", address.getCity());
		sheet = assertInstanceOf(Sheet.class, values.get("sheet"));
		i = 0;
		for (List<String> row : sheet.getRows()) {
			assertEquals("Street %d".formatted(i), row.get(0));
			assertEquals(Integer.toString(i), row.get(1));
			assertEquals("City %d".formatted(i), row.get(2));
			i++;
		}
		assertTrue((boolean) assertInstanceOf(Wrapper.class, values.get("booleanWrapper")).getValue());
		assertEquals(127, (byte) assertInstanceOf(Wrapper.class, values.get("byteWrapper")).getValue());

		Map<String, Object> data;
		List<?> addresses;

		instance.setValue(null);

		data = h.toData(instance, false, false);
		assertFalse(data.containsKey("key"));
		assertFalse(data.containsKey("value"));
		assertEquals("email@convertable.com", data.get("email"));
		assertEquals(List.of("City Convertable", "1", "Street Convertable"), data.get("address"));
		addresses = assertInstanceOf(List.class, data.get("sheet"));
		for (i = 1; i >= 0; i--) {
			address = assertInstanceOf(Address.class, addresses.get(1 - i));
			assertEquals("%d Street".formatted(i), address.getStreet());
			assertEquals(i, address.getNumber());
			assertEquals("%d City".formatted(i), address.getCity());
		}
		assertEquals("false", data.get("boolean_wrapper"));
		assertEquals(List.of('6', '3'), data.get("byte_wrapper"));

		values.remove("key");
		values.put("value", null);
		values.put("", "");
		values.put(null, null);

		data = h.toData(values);
		assertFalse(data.containsKey("key"));
		assertFalse(data.containsKey("value"));
		assertEquals("convertable@email.com", data.get("email"));
		assertEquals(List.of("Convertable City", "0", "Convertable Street"), data.get("address"));
		addresses = assertInstanceOf(List.class, data.get("sheet"));
		for (i = 0; i <= 1; i++) {
			address = assertInstanceOf(Address.class, addresses.get(i));
			assertEquals("Street %d".formatted(i), address.getStreet());
			assertEquals(i, address.getNumber());
			assertEquals("City %d".formatted(i), address.getCity());
		}
		assertEquals("true", data.get("boolean_wrapper"));
		assertEquals(List.of('1', '2', '7'), data.get("byte_wrapper"));

		data = h.toData(Map.of(
				".", "",
				"value", FieldValue.delete(),
				"email", instance.getEmail(),
				"address.", instance.getAddress(),
				"address.field", instance.getAddress(),
				"booleanWrapper", instance.getBooleanWrapper(),
				"byteWrapper.field", instance.getByteWrapper()));
		assertSame(FieldValue.delete(), data.get("value"));
		assertEquals("email@convertable.com", data.get("email"));
		assertSame(instance.getAddress(), data.get("address."));
		assertSame(instance.getAddress(), data.get("address.field"));
		assertEquals("false", data.get("boolean_wrapper"));
		assertSame(instance.getByteWrapper(), data.get("byte_wrapper.field"));

		data.put(".", null);

		values = h.toValues(data);
		assertSame(FieldValue.delete(), values.get("value"));
		email = assertInstanceOf(Email.class, values.get("email"));
		assertEquals("email", email.getLogin());
		assertEquals("convertable.com", email.getDomain());
		assertSame(instance.getAddress(), values.get("address."));
		assertSame(instance.getAddress(), values.get("address.field"));
		assertFalse((boolean) assertInstanceOf(Wrapper.class, values.get("booleanWrapper")).getValue());
		assertSame(instance.getByteWrapper(), values.get("byteWrapper.field"));
	}

	@Test
	void constructsWithSingularEntity() {
		Handle<SingularEntity> h = newHandle(SingularEntity.class);
		assertEquals("SingularEntities", h.getCollectionName());
	}

	@Test
	void constructsWithPluralEntities() {
		Handle<PluralEntities> h = newHandle(PluralEntities.class);
		assertEquals("PluralEntities", h.getCollectionName());
	}

	@Test
	void doesNotConstructWithThrowerConstructor() {
		assertThrows(ReflectionException.class, () -> {
			newHandle(ThrowerConstructor.class);
		});
	}

	@Test
	void doesNotConstructWithBlankCollectionName() {
		assertThrows(AnnotationException.class, () -> {
			newHandle(BlankCollectionName.class);
		});
	}

	@Test
	void doesNotConstructWithNonConvertableField() {
		assertThrows(AnnotationException.class, () -> {
			newHandle(NonConvertableField.class);
		});
	}

	@Test
	void doesNotConstructWithBlankPropertyName() {
		assertThrows(AnnotationException.class, () -> {
			newHandle(BlankPropertyName.class);
		});
	}

	@Test
	void doesNotConstructWithDottedPropertyName() {
		assertThrows(AnnotationException.class, () -> {
			newHandle(DottedPropertyName.class);
		});
	}

	@Test
	void doesNotConstructWithNonFileWebField() {
		assertThrows(AnnotationException.class, () -> {
			newHandle(NonFileWebField.class);
		});
	}

	@Test
	void doesNotConstructWithConvertedFileField() {
		assertThrows(AnnotationException.class, () -> {
			newHandle(ConvertedFileField.class);
		});
	}

	@Test
	void doesNotConstructWithNonStringFileField() {
		assertThrows(AnnotationException.class, () -> {
			newHandle(NonStringFileField.class);
		});
	}

	@Test
	void doesNotConstructWithNonKeyAutoField() {
		assertThrows(AnnotationException.class, () -> {
			newHandle(NonKeyAutoField.class);
		});
	}

	@Test
	void doesNotConstructWithConvertedKeyField() {
		assertThrows(AnnotationException.class, () -> {
			newHandle(ConvertedKeyField.class);
		});
	}

	@Test
	void doesNotConstructWithFileKeyField() {
		assertThrows(AnnotationException.class, () -> {
			newHandle(FileKeyField.class);
		});
	}

	@Test
	void doesNotConstructWithTwoKeyFields() {
		assertThrows(AnnotationException.class, () -> {
			newHandle(TwoKeyFields.class);
		});
	}

	@Test
	void doesNotConstructWithNonStringAutoField() {
		assertThrows(AnnotationException.class, () -> {
			newHandle(NonStringAutoField.class);
		});
	}

	@Test
	void doesNotConstructWithNoKeyFields() {
		assertThrows(AnnotationException.class, () -> {
			newHandle(NoKeyFields.class);
		});
	}

	@Test
	void doesNotOverwriteFileField() {
		Handle<Default> h = newHandle(Default.class);
		assertThrows(IllegalArgumentException.class, () -> {
			h.toData(Map.of("stringValue", ""));
		});
	}

	@Test
	void doesNotOverwriteKeyField() {
		Handle<Default> h = newHandle(Default.class);
		assertThrows(IllegalArgumentException.class, () -> {
			h.toData(Map.of("booleanValue", false));
		});
	}

	@Test
	void doesNotModifyFileField() {
		Handle<Default> h = newHandle(Default.class);
		assertThrows(IllegalArgumentException.class, () -> {
			h.toData(Map.of("stringValue.field", ""));
		});
	}

	@Test
	void doesNotModifyKeyField() {
		Handle<Default> h = newHandle(Default.class);
		assertThrows(IllegalArgumentException.class, () -> {
			h.toData(Map.of("booleanValue.field", false));
		});
	}

	private <E> Handle<E> newHandle(Class<E> type) {
		return new Handle<>(reflector, factory, type);
	}
}
