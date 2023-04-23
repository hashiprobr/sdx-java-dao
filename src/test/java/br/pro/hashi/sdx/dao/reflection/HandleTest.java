package br.pro.hashi.sdx.dao.reflection;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
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

import br.pro.hashi.sdx.dao.DaoConverter;
import br.pro.hashi.sdx.dao.reflection.exception.AnnotationException;
import br.pro.hashi.sdx.dao.reflection.exception.ReflectionException;
import br.pro.hashi.sdx.dao.reflection.mock.converter.DefaultImplementation;
import br.pro.hashi.sdx.dao.reflection.mock.handle.BlankCollection;
import br.pro.hashi.sdx.dao.reflection.mock.handle.BlankProperty;
import br.pro.hashi.sdx.dao.reflection.mock.handle.Child;
import br.pro.hashi.sdx.dao.reflection.mock.handle.ConvertableFields;
import br.pro.hashi.sdx.dao.reflection.mock.handle.ConvertedFileField;
import br.pro.hashi.sdx.dao.reflection.mock.handle.ConvertedKeyField;
import br.pro.hashi.sdx.dao.reflection.mock.handle.Default;
import br.pro.hashi.sdx.dao.reflection.mock.handle.DottedProperty;
import br.pro.hashi.sdx.dao.reflection.mock.handle.FileKeyField;
import br.pro.hashi.sdx.dao.reflection.mock.handle.GrandChild;
import br.pro.hashi.sdx.dao.reflection.mock.handle.NoKeys;
import br.pro.hashi.sdx.dao.reflection.mock.handle.NonConvertableField;
import br.pro.hashi.sdx.dao.reflection.mock.handle.NonFileWebField;
import br.pro.hashi.sdx.dao.reflection.mock.handle.NonKeyAutoField;
import br.pro.hashi.sdx.dao.reflection.mock.handle.NonStringFileField;
import br.pro.hashi.sdx.dao.reflection.mock.handle.Parent;
import br.pro.hashi.sdx.dao.reflection.mock.handle.PluralEntities;
import br.pro.hashi.sdx.dao.reflection.mock.handle.SingularEntity;
import br.pro.hashi.sdx.dao.reflection.mock.handle.ThrowerConstructor;
import br.pro.hashi.sdx.dao.reflection.mock.handle.TwoKeys;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.Address;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.AddressConverter;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.BooleanWrapperConverter;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.ByteWrapperConverter;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.Email;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.EmailConverter;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.Sheet;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.SheetConverter;

class HandleTest {
	private static final Lookup LOOKUP = MethodHandles.lookup();
	private static final double DELTA = 0.000001;

	private Reflector reflector;
	private ConverterFactory factory;
	private Handle h;

	@BeforeEach
	void setUp() {
		reflector = mock(Reflector.class);
		factory = mock(ConverterFactory.class);
	}

	@Test
	void creates() {
		mockCreator(Default.class);
		h = newHandle(Default.class);
		HandleFactory handleFactory = mock(HandleFactory.class);
		when(handleFactory.get(Default.class)).thenReturn(h);
		try (MockedStatic<HandleFactory> factoryStatic = mockStatic(HandleFactory.class)) {
			factoryStatic.when(() -> HandleFactory.getInstance()).thenReturn(handleFactory);
			assertSame(h, Handle.of(Default.class));
		}
	}

	@Test
	void constructsWithDefaultReflectorAndFactory() {
		mockCreator(Default.class);
		Handle handle;
		try (MockedStatic<Reflector> reflectorStatic = mockStatic(Reflector.class)) {
			reflectorStatic.when(() -> Reflector.getInstance()).thenReturn(reflector);
			try (MockedStatic<ConverterFactory> factoryStatic = mockStatic(ConverterFactory.class)) {
				factoryStatic.when(() -> ConverterFactory.getInstance()).thenReturn(factory);
				handle = new Handle(Default.class);
				assertEquals(Default.class, handle.getType());
			}
			assertSame(factory, handle.getFactory());
		}
		assertSame(reflector, handle.getReflector());
	}

	@Test
	void constructsWithParent() {
		Parent instance = new Parent();
		mockCreator(instance);
		mockGetterAndSetter(Parent.class, "booleanValue", instance);
		mockGetterAndSetter(Parent.class, "intValue", instance);
		mockGetterAndSetter(Parent.class, "stringValue", instance);

		h = newHandle(Parent.class);
		assertEquals("Parents", h.getCollectionName());
		assertEquals(Set.of("booleanValue", "intValue", "stringValue"), h.getFieldNames());

		assertTrue(h.hasAutoKey());

		assertFalse(h.isFile("booleanValue"));
		assertFalse(h.isFile("intValue"));
		assertTrue(h.isFile("stringValue"));

		assertFalse(h.isWeb("booleanValue"));
		assertFalse(h.isWeb("intValue"));
		assertTrue(h.isWeb("stringValue"));

		assertEquals("boolean_value", h.getPropertyName("booleanValue"));
		assertNull(h.getPropertyName("intValue"));
		assertEquals("string_value", h.getPropertyName("stringValue"));

		assertEquals("boolean", h.getFieldTypeName("booleanValue"));
		assertEquals("int", h.getFieldTypeName("intValue"));
		assertEquals("java.lang.String", h.getFieldTypeName("stringValue"));

		assertSame(instance, h.create());

		assertEquals("true", h.getKeyString(instance));
		assertEquals(1, h.get("intValue", instance));
		assertEquals("p", h.getFile("stringValue", instance));

		h.set("booleanValue", instance, false);
		h.set("intValue", instance, 0);
		h.setFile("stringValue", instance, null);

		assertFalse(instance.isBooleanValue());
		assertEquals(0, instance.getIntValue());
		assertNull(instance.getStringValue());
	}

	@Test
	void constructsWithChild() {
		Child instance = new Child();
		mockCreator(instance);
		mockGetterAndSetter(Parent.class, "booleanValue", instance);
		mockGetterAndSetter(Parent.class, "intValue", instance);
		mockGetterAndSetter(Parent.class, "stringValue", instance);
		mockGetterAndSetter(Child.class, "booleanValue", instance);
		mockGetterAndSetter(Child.class, "doubleValue", instance);
		mockGetterAndSetter(Child.class, "stringValue", instance);

		h = newHandle(Child.class);
		assertEquals("Children", h.getCollectionName());
		assertEquals(Set.of("booleanValue", "intValue", "doubleValue", "stringValue"), h.getFieldNames());

		assertFalse(h.hasAutoKey());

		assertFalse(h.isFile("booleanValue"));
		assertFalse(h.isFile("intValue"));
		assertFalse(h.isFile("doubleValue"));
		assertFalse(h.isFile("stringValue"));

		assertFalse(h.isWeb("booleanValue"));
		assertFalse(h.isWeb("intValue"));
		assertFalse(h.isWeb("doubleValue"));
		assertFalse(h.isWeb("stringValue"));

		assertNull(h.getPropertyName("booleanValue"));
		assertNull(h.getPropertyName("intValue"));
		assertNull(h.getPropertyName("doubleValue"));
		assertNull(h.getPropertyName("stringValue"));

		assertEquals("boolean", h.getFieldTypeName("booleanValue"));
		assertEquals("int", h.getFieldTypeName("intValue"));
		assertEquals("double", h.getFieldTypeName("doubleValue"));
		assertEquals("java.lang.String", h.getFieldTypeName("stringValue"));

		assertSame(instance, h.create());

		assertFalse((boolean) h.get("booleanValue", instance));
		assertEquals(1, h.get("intValue", instance));
		assertEquals("2.0", h.getKeyString(instance));
		assertEquals("c", h.getFile("stringValue", instance));

		h.set("booleanValue", instance, true);
		h.set("intValue", instance, 0);
		h.set("doubleValue", instance, 1);
		h.setFile("stringValue", instance, null);

		assertTrue(instance.isBooleanValue());
		assertEquals(0, instance.getIntValue());
		assertEquals(1, instance.getDoubleValue(), DELTA);
		assertNull(instance.getStringValue());

	}

	@Test
	void constructsWithGrandChild() {
		GrandChild instance = new GrandChild();
		mockCreator(instance);
		mockGetterAndSetter(Parent.class, "booleanValue", instance);
		mockGetterAndSetter(Parent.class, "intValue", instance);
		mockGetterAndSetter(Parent.class, "stringValue", instance);
		mockGetterAndSetter(Child.class, "booleanValue", instance);
		mockGetterAndSetter(Child.class, "doubleValue", instance);
		mockGetterAndSetter(Child.class, "stringValue", instance);
		mockGetterAndSetter(GrandChild.class, "booleanValue", instance);
		mockGetterAndSetter(GrandChild.class, "doubleValue", instance);
		mockGetterAndSetter(GrandChild.class, "stringValue", instance);

		h = newHandle(GrandChild.class);
		assertEquals("GrandChilds", h.getCollectionName());
		assertEquals(Set.of("booleanValue", "intValue", "doubleValue", "stringValue"), h.getFieldNames());

		assertTrue(h.hasAutoKey());

		assertFalse(h.isFile("booleanValue"));
		assertFalse(h.isFile("intValue"));
		assertFalse(h.isFile("doubleValue"));
		assertTrue(h.isFile("stringValue"));

		assertFalse(h.isWeb("booleanValue"));
		assertFalse(h.isWeb("intValue"));
		assertFalse(h.isWeb("doubleValue"));
		assertTrue(h.isWeb("stringValue"));

		assertEquals("boolean_value", h.getPropertyName("booleanValue"));
		assertNull(h.getPropertyName("intValue"));
		assertNull(h.getPropertyName("doubleValue"));
		assertEquals("string_value", h.getPropertyName("stringValue"));

		assertEquals("boolean", h.getFieldTypeName("booleanValue"));
		assertEquals("int", h.getFieldTypeName("intValue"));
		assertEquals("double", h.getFieldTypeName("doubleValue"));
		assertEquals("java.lang.String", h.getFieldTypeName("stringValue"));

		assertSame(instance, h.create());

		assertEquals("true", h.getKeyString(instance));
		assertEquals(1, h.get("intValue", instance));
		assertEquals(3, (double) h.get("doubleValue", instance), DELTA);
		assertEquals("g", h.getFile("stringValue", instance));

		h.set("booleanValue", instance, false);
		h.set("intValue", instance, 0);
		h.set("doubleValue", instance, 2);
		h.setFile("stringValue", instance, null);

		assertFalse(instance.isBooleanValue());
		assertEquals(0, instance.getIntValue());
		assertEquals(2, instance.getDoubleValue(), DELTA);
		assertNull(instance.getStringValue());
	}

	@Test
	void constructsWithConvertableFields() {
		ConvertableFields instance = new ConvertableFields();
		mockCreator(instance);
		mockGetterAndSetter(ConvertableFields.class, "key", instance);
		mockGetterAndSetter(ConvertableFields.class, "value", instance);
		mockGetterAndSetter(ConvertableFields.class, "email", instance);
		mockGetterAndSetter(ConvertableFields.class, "address", instance);
		mockGetterAndSetter(ConvertableFields.class, "sheet", instance);
		mockGetterAndSetter(ConvertableFields.class, "booleanWrapper", instance);
		mockGetterAndSetter(ConvertableFields.class, "byteWrapper", instance);
		mockSourceType(EmailConverter.class);
		mockSourceType(AddressConverter.class);
		mockSourceType(SheetConverter.class);
		mockSourceType(BooleanWrapperConverter.class);
		mockSourceType(ByteWrapperConverter.class);

		h = newHandle(ConvertableFields.class);

		assertInstanceOf(EmailConverter.class, h.getConverter("email"));
		assertInstanceOf(AddressConverter.class, h.getConverter("address"));
		assertInstanceOf(SheetConverter.class, h.getConverter("sheet"));
		assertInstanceOf(BooleanWrapperConverter.class, h.getConverter("booleanWrapper"));
		assertInstanceOf(ByteWrapperConverter.class, h.getConverter("byteWrapper"));

		assertEquals("key", h.getKeyString(instance));
		assertEquals("value", h.get("value", instance));
		assertEquals("convertable@email.com", h.get("email", instance));
		assertEquals(List.of("Convertable City", "0", "Convertable Street"), h.get("address", instance));
		List<?> addresses = assertInstanceOf(List.class, h.get("sheet", instance));
		for (int i = 0; i < addresses.size(); i++) {
			Address address = assertInstanceOf(Address.class, addresses.get(i));
			assertEquals("Street %d".formatted(i), address.getStreet());
			assertEquals(i, address.getNumber());
			assertEquals("City %d".formatted(i), address.getCity());
		}
		assertEquals("true", h.get("booleanWrapper", instance));
		assertEquals(List.of('1', '2', '7'), h.get("byteWrapper", instance));

		h.setAutoKey(instance, null);
		h.set("value", instance, null);
		h.set("email", instance, "email@convertable.com");
		h.set("address", instance, List.of("City Convertable", "1", "Street Convertable"));
		h.set("sheet", instance, List.of(new Address("1 Street", 1, "1 City"), new Address("0 Street", 0, "0 City")));
		h.set("booleanWrapper", instance, "false");
		h.set("byteWrapper", instance, List.of('6', '3'));

		assertNull(instance.getKey());
		assertNull(instance.getValue());
		Email email = instance.getEmail();
		assertEquals("email", email.getLogin());
		assertEquals("convertable.com", email.getDomain());
		Address address = instance.getAddress();
		assertEquals("Street Convertable", address.getStreet());
		assertEquals(1, address.getNumber());
		assertEquals("City Convertable", address.getCity());
		Sheet sheet = instance.getSheet();
		int i = 1;
		for (List<String> row : sheet.getRows()) {
			assertEquals("%d Street".formatted(i), row.get(0));
			assertEquals(Integer.toString(i), row.get(1));
			assertEquals("%d City".formatted(i), row.get(2));
			i--;
		}
		assertFalse(instance.getBooleanWrapper().getValue());
		assertEquals(63, (byte) instance.getByteWrapper().getValue());
	}

	@Test
	void createsInstanceFromData() {
		ConvertableFields instance = new ConvertableFields();
		mockCreator(instance);
		mockGetterAndSetter(ConvertableFields.class, "key", instance);
		mockGetterAndSetter(ConvertableFields.class, "value", instance);
		mockGetterAndSetter(ConvertableFields.class, "email", instance);
		mockGetterAndSetter(ConvertableFields.class, "address", instance);
		mockGetterAndSetter(ConvertableFields.class, "sheet", instance);
		mockGetterAndSetter(ConvertableFields.class, "booleanWrapper", instance);
		mockGetterAndSetter(ConvertableFields.class, "byteWrapper", instance);
		mockSourceType(EmailConverter.class);
		mockSourceType(AddressConverter.class);
		mockSourceType(SheetConverter.class);
		mockSourceType(BooleanWrapperConverter.class);
		mockSourceType(ByteWrapperConverter.class);

		h = newHandle(ConvertableFields.class);

		assertSame(instance, h.toInstance(Map.of(
				"email", "email@convertable.com",
				"address", List.of("City Convertable", "1", "Street Convertable"),
				"sheet", List.of(new Address("1 Street", 1, "1 City"), new Address("0 Street", 0, "0 City")),
				"boolean_wrapper", "false",
				"byte_wrapper", List.of('6', '3'))));

		assertEquals("key", instance.getKey());
		assertEquals("value", instance.getValue());
		Email email = instance.getEmail();
		assertEquals("email", email.getLogin());
		assertEquals("convertable.com", email.getDomain());
		Address address = instance.getAddress();
		assertEquals("Street Convertable", address.getStreet());
		assertEquals(1, address.getNumber());
		assertEquals("City Convertable", address.getCity());
		Sheet sheet = instance.getSheet();
		int i = 1;
		for (List<String> row : sheet.getRows()) {
			assertEquals("%d Street".formatted(i), row.get(0));
			assertEquals(Integer.toString(i), row.get(1));
			assertEquals("%d City".formatted(i), row.get(2));
			i--;
		}
		assertFalse(instance.getBooleanWrapper().getValue());
		assertEquals(63, (byte) instance.getByteWrapper().getValue());
	}

	@Test
	void createsDataFromInstance() {
		ConvertableFields instance = new ConvertableFields();
		mockCreator(instance);
		mockGetterAndSetter(ConvertableFields.class, "key", instance);
		mockGetterAndSetter(ConvertableFields.class, "value", instance);
		mockGetterAndSetter(ConvertableFields.class, "email", instance);
		mockGetterAndSetter(ConvertableFields.class, "address", instance);
		mockGetterAndSetter(ConvertableFields.class, "sheet", instance);
		mockGetterAndSetter(ConvertableFields.class, "booleanWrapper", instance);
		mockGetterAndSetter(ConvertableFields.class, "byteWrapper", instance);
		mockSourceType(EmailConverter.class);
		mockSourceType(AddressConverter.class);
		mockSourceType(SheetConverter.class);
		mockSourceType(BooleanWrapperConverter.class);
		mockSourceType(ByteWrapperConverter.class);

		h = newHandle(ConvertableFields.class);

		Map<String, Object> data = h.toData(instance);

		assertNull(data.get("key"));
		assertEquals("value", data.get("value"));
		assertEquals("convertable@email.com", data.get("email"));
		assertEquals(List.of("Convertable City", "0", "Convertable Street"), data.get("address"));
		List<?> addresses = assertInstanceOf(List.class, data.get("sheet"));
		for (int i = 0; i < addresses.size(); i++) {
			Address address = assertInstanceOf(Address.class, addresses.get(i));
			assertEquals("Street %d".formatted(i), address.getStreet());
			assertEquals(i, address.getNumber());
			assertEquals("City %d".formatted(i), address.getCity());
		}
		assertEquals("true", data.get("boolean_wrapper"));
		assertEquals(List.of('1', '2', '7'), data.get("byte_wrapper"));
	}

	@Test
	void createsDataFromValues() {
		ConvertableFields instance = new ConvertableFields();
		mockCreator(instance);
		mockGetterAndSetter(ConvertableFields.class, "key", instance);
		mockGetterAndSetter(ConvertableFields.class, "value", instance);
		mockGetterAndSetter(ConvertableFields.class, "email", instance);
		mockGetterAndSetter(ConvertableFields.class, "address", instance);
		mockGetterAndSetter(ConvertableFields.class, "sheet", instance);
		mockGetterAndSetter(ConvertableFields.class, "booleanWrapper", instance);
		mockGetterAndSetter(ConvertableFields.class, "byteWrapper", instance);
		mockSourceType(EmailConverter.class);
		mockSourceType(AddressConverter.class);
		mockSourceType(SheetConverter.class);
		mockSourceType(BooleanWrapperConverter.class);
		mockSourceType(ByteWrapperConverter.class);

		h = newHandle(ConvertableFields.class);

		Map<String, Object> data;

		data = h.toData(Map.of(
				" \t\nvalue \t\n", instance.getValue(),
				" \t\nemail \t\n", instance.getEmail(),
				" \t\naddress \t\n", instance.getAddress(),
				" \t\nsheet \t\n", instance.getSheet(),
				" \t\nbooleanWrapper \t\n", instance.getBooleanWrapper(),
				" \t\nbyteWrapper \t\n", instance.getByteWrapper()));

		assertEquals("value", data.get("value"));
		assertEquals("convertable@email.com", data.get("email"));
		assertEquals(List.of("Convertable City", "0", "Convertable Street"), data.get("address"));
		List<?> addresses = assertInstanceOf(List.class, data.get("sheet"));
		for (int i = 0; i < addresses.size(); i++) {
			Address address = assertInstanceOf(Address.class, addresses.get(i));
			assertEquals("Street %d".formatted(i), address.getStreet());
			assertEquals(i, address.getNumber());
			assertEquals("City %d".formatted(i), address.getCity());
		}
		assertEquals("true", data.get("boolean_wrapper"));
		assertEquals(List.of('1', '2', '7'), data.get("byte_wrapper"));

		data = h.toData(Map.of(
				" \t\nvalue \t\n", instance.getValue(),
				" \t\nemail \t\n", instance.getEmail(),
				" \t\naddress \t\n", instance.getAddress(),
				" \t\nsheet.name \t\n", instance.getSheet(),
				" \t\nbooleanWrapper.name \t\n", instance.getBooleanWrapper(),
				" \t\nbyteWrapper.name \t\n", instance.getByteWrapper()));

		assertEquals("value", data.get("value"));
		assertEquals("convertable@email.com", data.get("email"));
		assertEquals(List.of("Convertable City", "0", "Convertable Street"), data.get("address"));
		assertSame(instance.getSheet(), data.get("sheet.name"));
		assertSame(instance.getBooleanWrapper(), data.get("boolean_wrapper.name"));
		assertSame(instance.getByteWrapper(), data.get("byte_wrapper.name"));
	}

	@Test
	void doesNotCreateDataFromValues() {
		ConvertableFields instance = new ConvertableFields();
		mockCreator(instance);
		mockGetterAndSetter(ConvertableFields.class, "key", instance);
		mockGetterAndSetter(ConvertableFields.class, "value", instance);
		mockGetterAndSetter(ConvertableFields.class, "email", instance);
		mockGetterAndSetter(ConvertableFields.class, "address", instance);
		mockGetterAndSetter(ConvertableFields.class, "sheet", instance);
		mockGetterAndSetter(ConvertableFields.class, "booleanWrapper", instance);
		mockGetterAndSetter(ConvertableFields.class, "byteWrapper", instance);
		mockSourceType(EmailConverter.class);
		mockSourceType(AddressConverter.class);
		mockSourceType(SheetConverter.class);
		mockSourceType(BooleanWrapperConverter.class);
		mockSourceType(ByteWrapperConverter.class);

		h = newHandle(ConvertableFields.class);

		assertThrows(IllegalArgumentException.class, () -> {
			h.toData(Map.of(
					" \t\nkey \t\n", instance.getKey(),
					" \t\nvalue \t\n", instance.getValue(),
					" \t\nemail \t\n", instance.getEmail(),
					" \t\naddress \t\n", instance.getAddress(),
					" \t\nsheet \t\n", instance.getSheet(),
					" \t\nbooleanWrapper \t\n", instance.getBooleanWrapper(),
					" \t\nbyteWrapper \t\n", instance.getByteWrapper()));
		});

		assertThrows(IllegalArgumentException.class, () -> {
			h.toData(Map.of(
					" \t\nkey.name \t\n", instance.getValue(),
					" \t\nvalue.name \t\n", instance.getValue(),
					" \t\nemail.name \t\n", instance.getEmail(),
					" \t\naddress.name \t\n", instance.getAddress(),
					" \t\nsheet \t\n", instance.getSheet(),
					" \t\nbooleanWrapper \t\n", instance.getBooleanWrapper(),
					" \t\nbyteWrapper \t\n", instance.getByteWrapper()));
		});
	}

	private void mockCreator(Object instance) {
		Class<?> type = instance.getClass();
		MethodHandle creator = mockCreator(type);
		when(reflector.invokeCreator(creator)).thenReturn(instance);
	}

	private void mockGetterAndSetter(Class<?> type, String fieldName, Object instance) {
		Field field = assertDoesNotThrow(() -> {
			return type.getDeclaredField(fieldName);
		});
		field.setAccessible(true);
		MethodHandle getter = assertDoesNotThrow(() -> {
			return LOOKUP.unreflectGetter(field);
		});
		MethodHandle setter = assertDoesNotThrow(() -> {
			return LOOKUP.unreflectSetter(field);
		});
		when(reflector.unreflectGetter(field)).thenReturn(getter);
		when(reflector.unreflectSetter(field)).thenReturn(setter);
		when(reflector.invokeGetter(getter, instance)).thenAnswer((invocation) -> {
			MethodHandle handle = invocation.getArgument(0);
			Object object = invocation.getArgument(1);
			return handle.invoke(object);
		});
		doAnswer((invocation) -> {
			MethodHandle handle = invocation.getArgument(0);
			Object object = invocation.getArgument(1);
			Object value = invocation.getArgument(2);
			return handle.invoke(object, value);
		}).when(reflector).invokeSetter(eq(setter), eq(instance), any());
	}

	private void mockSourceType(Class<? extends DaoConverter<?, ?>> type) {
		DaoConverter<?, ?> converter = assertDoesNotThrow(() -> {
			Constructor<? extends DaoConverter<?, ?>> constructor = type.getDeclaredConstructor();
			return constructor.newInstance();
		});
		Type[] genericInterfaces = type.getGenericInterfaces();
		ParameterizedType genericInterface = (ParameterizedType) genericInterfaces[0];
		Type[] types = genericInterface.getActualTypeArguments();
		doReturn(converter).when(factory).get(type);
		when(factory.getSourceType(converter)).thenReturn(types[0]);
	}

	@Test
	void constructsWithSingularEntity() {
		mockCreator(SingularEntity.class);
		h = newHandle(SingularEntity.class);
		assertEquals("SingularEntities", h.getCollectionName());
	}

	@Test
	void constructsWithPluralEntities() {
		mockCreator(PluralEntities.class);
		h = newHandle(PluralEntities.class);
		assertEquals("PluralEntities", h.getCollectionName());
	}

	@Test
	void doesNotConstructWithThrowerConstructor() {
		mockCreator(ThrowerConstructor.class);
		assertThrows(ReflectionException.class, () -> {
			newHandle(ThrowerConstructor.class);
		});
	}

	@Test
	void doesNotConstructWithBlankCollection() {
		mockCreator(BlankCollection.class);
		assertThrows(AnnotationException.class, () -> {
			newHandle(BlankCollection.class);
		});
	}

	@Test
	void doesNotConstructWithNonConvertableField() {
		mockCreator(NonConvertableField.class);
		assertThrows(AnnotationException.class, () -> {
			newHandle(NonConvertableField.class);
		});
	}

	@Test
	void doesNotConstructWithBlankProperty() {
		mockCreator(BlankProperty.class);
		assertThrows(AnnotationException.class, () -> {
			newHandle(BlankProperty.class);
		});
	}

	@Test
	void doesNotConstructWithDottedProperty() {
		mockCreator(DottedProperty.class);
		assertThrows(AnnotationException.class, () -> {
			newHandle(DottedProperty.class);
		});
	}

	@Test
	void doesNotConstructWithNonFileWebField() {
		mockCreator(NonFileWebField.class);
		assertThrows(AnnotationException.class, () -> {
			newHandle(NonFileWebField.class);
		});
	}

	@Test
	void doesNotConstructWithConvertedFileField() {
		mockCreator(ConvertedFileField.class);
		mockSourceType(DefaultImplementation.class);
		assertThrows(AnnotationException.class, () -> {
			newHandle(ConvertedFileField.class);
		});
	}

	@Test
	void doesNotConstructWithNonStringFileField() {
		mockCreator(NonStringFileField.class);
		assertThrows(AnnotationException.class, () -> {
			newHandle(NonStringFileField.class);
		});
	}

	@Test
	void doesNotConstructWithNonKeyAutoField() {
		mockCreator(NonKeyAutoField.class);
		assertThrows(AnnotationException.class, () -> {
			newHandle(NonKeyAutoField.class);
		});
	}

	@Test
	void doesNotConstructWithConvertedKeyField() {
		mockCreator(ConvertedKeyField.class);
		mockSourceType(DefaultImplementation.class);
		assertThrows(AnnotationException.class, () -> {
			newHandle(ConvertedKeyField.class);
		});
	}

	@Test
	void doesNotConstructWithFileKeyField() {
		mockCreator(FileKeyField.class);
		assertThrows(AnnotationException.class, () -> {
			newHandle(FileKeyField.class);
		});
	}

	@Test
	void doesNotConstructWithTwoKeys() {
		mockCreator(TwoKeys.class);
		assertThrows(AnnotationException.class, () -> {
			newHandle(TwoKeys.class);
		});
	}

	@Test
	void doesNotConstructWithNoKeys() {
		mockCreator(NoKeys.class);
		assertThrows(AnnotationException.class, () -> {
			newHandle(NoKeys.class);
		});
	}

	private <T> MethodHandle mockCreator(Class<T> type) {
		MethodHandle creator = assertDoesNotThrow(() -> {
			Constructor<T> constructor = type.getDeclaredConstructor();
			return LOOKUP.unreflectConstructor(constructor);
		});
		when(reflector.getExternalCreator(type)).thenReturn(creator);
		return creator;
	}

	private Handle newHandle(Class<?> type) {
		return new Handle(reflector, factory, type);
	}
}
