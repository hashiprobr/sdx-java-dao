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

import br.pro.hashi.sdx.dao.DaoConverter;
import br.pro.hashi.sdx.dao.reflection.example.handle.*;
import br.pro.hashi.sdx.dao.reflection.example.handle.converter.Address;
import br.pro.hashi.sdx.dao.reflection.example.handle.converter.Email;
import br.pro.hashi.sdx.dao.reflection.example.handle.converter.Wrapper;
import br.pro.hashi.sdx.dao.reflection.example.handle.path.FieldNames;
import br.pro.hashi.sdx.dao.reflection.example.handle.path.PropertyNames;
import br.pro.hashi.sdx.dao.reflection.example.handle.type.*;
import br.pro.hashi.sdx.dao.reflection.exception.AnnotationException;
import br.pro.hashi.sdx.dao.reflection.exception.ConversionException;
import br.pro.hashi.sdx.dao.reflection.exception.ReflectionException;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Blob;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.GeoPoint;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class HandleTest {
    private static final Objenesis OBJENESIS = new ObjenesisStd();
    private static final Lookup LOOKUP = MethodHandles.lookup();

    private AutoCloseable mocks;
    private @Mock Reflector reflector;
    private @Mock ParserFactory parserFactory;
    private @Mock ConverterFactory converterFactory;
    private @Mock HandleFactory handleFactory;

    @BeforeEach
    <E, F> void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        when(reflector.getInstantiator(any(), any(String.class))).thenAnswer((invocation) -> {
            Class<E> type = invocation.getArgument(0);
            return OBJENESIS.getInstantiatorOf(type);
        });
        when(reflector.getCreator(any(), any(String.class))).thenAnswer((invocation) -> {
            Class<E> type = invocation.getArgument(0);
            Constructor<E> constructor = type.getDeclaredConstructor();
            return LOOKUP.unreflectConstructor(constructor);
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
        when(reflector.getSpecificType(any(DaoConverter.class), eq(DaoConverter.class), any(int.class))).thenAnswer((invocation) -> {
            DaoConverter<?, ?> object = invocation.getArgument(0);
            int rootIndex = invocation.getArgument(2);
            Class<?> type = object.getClass();
            Type[] genericInterfaces = type.getGenericInterfaces();
            ParameterizedType genericInterface = (ParameterizedType) genericInterfaces[0];
            Type[] types = genericInterface.getActualTypeArguments();
            return types[rootIndex];
        });

        when(parserFactory.get(Integer.class)).thenReturn(Integer::valueOf);
        when(parserFactory.get(String.class)).thenReturn((valueString) -> valueString);
        when(parserFactory.get(List.class)).thenThrow(IllegalArgumentException.class);
        when(parserFactory.get(Object.class)).thenThrow(IllegalArgumentException.class);

        when(converterFactory.get(any())).thenAnswer((invocation) -> {
            Class<? extends DaoConverter<?, ?>> type = invocation.getArgument(0);
            Constructor<? extends DaoConverter<?, ?>> constructor = type.getDeclaredConstructor();
            return constructor.newInstance();
        });

        when(handleFactory.get(any())).thenAnswer((invocation) -> {
            Class<E> type = invocation.getArgument(0);
            return newHandle(type);
        });
    }

    @AfterEach
    void tearDown() {
        assertDoesNotThrow(() -> mocks.close());
    }

    @Test
    void getsInstance() {
        @SuppressWarnings("rawtypes")
        MockedConstruction<Handle> construction = mockConstruction(Handle.class);
        assertInstanceOf(Handle.class, Handle.newInstance(Default.class));
        construction.close();
    }

    @Test
    void constructsAndBuildsFromGrandParent() {
        Handle<GrandParent> h = newHandle(GrandParent.class);
        assertEquals("GrandParents", h.getCollectionName());

        assertEquals(Set.of("file", "key", "notFileOrKey", "parent", "list", "map"), h.getFieldNames());
        assertEquals(Set.of("file"), h.getFileFieldNames());

        assertEquals("image/png", h.getContentType("file"));
        assertNull(h.getContentType("key"));
        assertNull(h.getContentType("notFileOrKey"));
        assertNull(h.getContentType("parent"));
        assertNull(h.getContentType("list"));
        assertNull(h.getContentType("map"));

        assertFalse(h.isWeb("file"));
        assertFalse(h.isWeb("key"));
        assertFalse(h.isWeb("notFileOrKey"));
        assertFalse(h.isWeb("parent"));
        assertFalse(h.isWeb("list"));
        assertFalse(h.isWeb("map"));

        assertTrue(h.hasKey());
        assertTrue(h.containsKey(new String[]{"key"}));
        assertFalse(h.containsKey(new String[]{"file", "notFileOrKey", "parent", "list", "map"}));
        assertFalse(h.hasAutoKey());

        GrandParent instance = new GrandParent();
        instance.setKey(3);
        assertEquals(Integer.valueOf(3), h.getKey(instance));

        instance.setNotFileOrKey(true);
        instance.parent = new Parent();
        instance.list = List.of(new Parent());
        instance.map = Map.of(0, new Parent());

        Map<String, Object> map = new HashMap<>();
        map.put("file", null);
        map.put("key", 0.0);
        map.put("not_file_or_key", 0F);
        map.put("parent", null);
        map.put("array", null);
        map.put("list", null);
        map.put("map", null);

        Map<String, Object> data;

        data = h.buildCreateData(instance);
        assertEquals(6, data.size());
        assertTrue(data.containsKey("file"));
        assertNull(data.get("file"));
        assertEquals(3, data.get("key"));
        assertTrue((boolean) data.get("notFileOrKey"));
        assertEquals(map, data.get("parent"));
        assertEquals(List.of(map), data.get("list"));
        assertEquals(Map.of("0", map), data.get("map"));

        data = h.buildUpdateData(instance);
        assertEquals(4, data.size());
        assertTrue((boolean) data.get("notFileOrKey"));
        assertEquals(map, data.get("parent"));
        assertEquals(List.of(map), data.get("list"));
        assertEquals(Map.of("0", map), data.get("map"));

        map.put("key", 0L);
        map.put("not_file_or_key", 0.0);

        data = Map.of(
                "file", "f",
                "key", 4L,
                "notFileOrKey", true,
                "parent", map,
                "list", List.of(map),
                "map", Map.of("0", map));

        Parent parent = new Parent();

        instance = h.buildInstance(data);
        assertEquals("f", instance.getFile());
        assertEquals(4, instance.getKey());
        assertTrue(instance.isNotFileOrKey());
        assertEquals(parent, instance.parent);
        assertEquals(List.of(parent), instance.list);
        assertEquals(Map.of(0, parent), instance.map);

        Map<String, Object> values = h.buildValues(data);
        assertEquals(6, values.size());
        assertEquals("f", values.get("file"));
        assertEquals(4, values.get("key"));
        assertTrue((boolean) values.get("notFileOrKey"));
        assertEquals(parent, values.get("parent"));
        assertEquals(List.of(parent), values.get("list"));
        assertEquals(Map.of(0, parent), values.get("map"));
    }

    @Test
    void constructsAndBuildsFromParent() {
        Handle<Parent> h = newHandle(Parent.class);
        assertEquals("Parents", h.getCollectionName());

        assertEquals(Set.of("file", "key", "notFileOrKey", "parent", "array", "list", "map"), h.getFieldNames());
        assertEquals(Set.of(), h.getFileFieldNames());

        assertNull(h.getContentType("file"));
        assertNull(h.getContentType("key"));
        assertNull(h.getContentType("notFileOrKey"));
        assertNull(h.getContentType("parent"));
        assertNull(h.getContentType("array"));
        assertNull(h.getContentType("list"));
        assertNull(h.getContentType("map"));

        assertFalse(h.isWeb("file"));
        assertFalse(h.isWeb("key"));
        assertFalse(h.isWeb("notFileOrKey"));
        assertFalse(h.isWeb("parent"));
        assertFalse(h.isWeb("array"));
        assertFalse(h.isWeb("list"));
        assertFalse(h.isWeb("map"));

        assertFalse(h.hasKey());
        assertFalse(h.containsKey(new String[]{"file", "key", "notFileOrKey", "parent", "array", "list", "map"}));

        Parent instance = new Parent();

        instance.setFile("f");
        instance.setBoxedKey(3);
        instance.setNotFileOrKey(5.5F);
        instance.parent = new Parent();
        instance.array = new Parent[]{new Parent()};
        instance.list = List.of(Map.of(0, new Parent()));
        instance.map = Map.of(1, List.of(new Parent()));

        Map<String, Object> map = new HashMap<>();
        map.put("file", null);
        map.put("key", 0.0);
        map.put("not_file_or_key", 0F);
        map.put("parent", null);
        map.put("array", null);
        map.put("list", null);
        map.put("map", null);

        Map<String, Object> data;

        data = h.buildCreateData(instance);
        assertEquals(7, data.size());
        assertEquals("f", data.get("file"));
        assertEquals(3.0, data.get("key"));
        assertEquals(5.5F, data.get("not_file_or_key"));
        assertEquals(map, data.get("parent"));
        assertEquals(List.of(map), data.get("array"));
        assertEquals(List.of(Map.of("0", map)), data.get("list"));
        assertEquals(Map.of("1", List.of(map)), data.get("map"));

        data = h.buildUpdateData(instance);
        assertEquals(7, data.size());
        assertEquals("f", data.get("file"));
        assertEquals(3.0, data.get("key"));
        assertEquals(5.5F, data.get("not_file_or_key"));
        assertEquals(map, data.get("parent"));
        assertEquals(List.of(map), data.get("array"));
        assertEquals(List.of(Map.of("0", map)), data.get("list"));
        assertEquals(Map.of("1", List.of(map)), data.get("map"));

        map.put("key", 0L);
        map.put("not_file_or_key", 0.0);

        data = Map.of(
                "file", "f",
                "key", 6.6,
                "not_file_or_key", 6.6,
                "parent", map,
                "array", List.of(map),
                "list", List.of(Map.of("0", map)),
                "map", Map.of("1", List.of(map)));

        Parent parent = new Parent();

        instance = h.buildInstance(data);
        assertEquals("f", instance.getFile());
        assertEquals(6, instance.getBoxedKey());
        assertEquals(6.6F, instance.getNotFileOrKey());
        assertEquals(parent, instance.parent);
        assertArrayEquals(new Parent[]{parent}, instance.array);
        assertEquals(List.of(Map.of(0, parent)), instance.list);
        assertEquals(Map.of(1, List.of(parent)), instance.map);

        Map<String, Object> values = h.buildValues(data);
        assertEquals(7, values.size());
        assertEquals("f", values.get("file"));
        assertEquals(6, values.get("key"));
        assertEquals(6.6F, values.get("notFileOrKey"));
        assertEquals(parent, values.get("parent"));
        assertArrayEquals(new Parent[]{parent}, (Parent[]) values.get("array"));
        assertEquals(List.of(Map.of(0, parent)), values.get("list"));
        assertEquals(Map.of(1, List.of(parent)), values.get("map"));
    }

    @Test
    void constructsAndBuildsFromChild() {
        Handle<Child> h = newHandle(Child.class);
        assertEquals("Children", h.getCollectionName());

        assertEquals(Set.of("file", "key", "notFileOrKey", "parent", "array", "list", "map"), h.getFieldNames());
        assertEquals(Set.of("file"), h.getFileFieldNames());

        assertEquals("application/octet-stream", h.getContentType("file"));
        assertNull(h.getContentType("key"));
        assertNull(h.getContentType("notFileOrKey"));
        assertNull(h.getContentType("parent"));
        assertNull(h.getContentType("array"));
        assertNull(h.getContentType("list"));
        assertNull(h.getContentType("map"));

        assertTrue(h.isWeb("file"));
        assertFalse(h.isWeb("key"));
        assertFalse(h.isWeb("notFileOrKey"));
        assertFalse(h.isWeb("parent"));
        assertFalse(h.isWeb("array"));
        assertFalse(h.isWeb("list"));
        assertFalse(h.isWeb("map"));

        assertTrue(h.hasKey());
        assertTrue(h.containsKey(new String[]{"key"}));
        assertFalse(h.containsKey(new String[]{"file", "notFileOrKey", "parent", "array", "list", "map"}));
        assertTrue(h.hasAutoKey());

        Child instance = new Child();
        h.setAutoKey(instance, "k");
        assertEquals("k", h.getKey(instance));

        instance.setNotFileOrKey(5.5F);
        instance.parent = new Parent();
        instance.array = new Parent[]{new Parent()};
        instance.list = List.of(Map.of(0, new Parent[]{new Parent()}));
        instance.map = Map.of(1, List.of(Map.of(2, new Parent())));

        Map<String, Object> map = new HashMap<>();
        map.put("file", null);
        map.put("key", 0.0);
        map.put("not_file_or_key", 0F);
        map.put("parent", null);
        map.put("array", null);
        map.put("list", null);
        map.put("map", null);

        Map<String, Object> data;

        data = h.buildCreateData(instance);
        assertEquals(6, data.size());
        assertTrue(data.containsKey("file"));
        assertNull(data.get("file"));
        assertEquals(5.5F, data.get("not_file_or_key"));
        assertEquals(map, data.get("parent"));
        assertEquals(List.of(map), data.get("array"));
        assertEquals(List.of(Map.of("0", List.of(map))), data.get("list"));
        assertEquals(Map.of("1", List.of(Map.of("2", map))), data.get("map"));

        data = h.buildUpdateData(instance);
        assertEquals(5, data.size());
        assertEquals(5.5F, data.get("not_file_or_key"));
        assertEquals(map, data.get("parent"));
        assertEquals(List.of(map), data.get("array"));
        assertEquals(List.of(Map.of("0", List.of(map))), data.get("list"));
        assertEquals(Map.of("1", List.of(Map.of("2", map))), data.get("map"));

        map.put("key", 0L);
        map.put("not_file_or_key", 0.0);

        data = Map.of(
                "file", "f",
                "not_file_or_key", 6.6,
                "parent", map,
                "array", List.of(map),
                "list", List.of(Map.of("0", List.of(map))),
                "map", Map.of("1", List.of(Map.of("2", map))));

        Parent parent = new Parent();

        List<?> mapList;
        Map<?, ?> arrayMap;

        instance = h.buildInstance(data);
        assertEquals("f", instance.getFile());
        assertNull(instance.key);
        assertEquals(6.6F, instance.getNotFileOrKey());
        assertEquals(parent, instance.parent);
        assertArrayEquals(new Parent[]{parent}, instance.array);
        mapList = assertInstanceOf(List.class, instance.list);
        assertEquals(1, mapList.size());
        arrayMap = assertInstanceOf(Map.class, mapList.get(0));
        assertEquals(1, arrayMap.size());
        assertArrayEquals(new Parent[]{parent}, (Parent[]) arrayMap.get(0));
        assertEquals(Map.of(1, List.of(Map.of(2, parent))), instance.map);

        Map<String, Object> values = h.buildValues(data);
        assertEquals(6, values.size());
        assertEquals("f", values.get("file"));
        assertEquals(6.6F, values.get("notFileOrKey"));
        assertEquals(parent, values.get("parent"));
        assertArrayEquals(new Parent[]{parent}, (Parent[]) values.get("array"));
        mapList = assertInstanceOf(List.class, values.get("list"));
        assertEquals(1, mapList.size());
        arrayMap = assertInstanceOf(Map.class, mapList.get(0));
        assertEquals(1, arrayMap.size());
        assertArrayEquals(new Parent[]{parent}, (Parent[]) arrayMap.get(0));
        assertEquals(Map.of(1, List.of(Map.of(2, parent))), values.get("map"));

        h.putAutoKey(values, "k");
        assertEquals("k", values.get("key"));
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
    void doesNotConstructWithBlankCollectionName() {
        assertThrows(AnnotationException.class, () -> newHandle(BlankCollectionName.class));
    }

    @Test
    void doesNotConstructWithDottedCollectionName() {
        assertThrows(AnnotationException.class, () -> newHandle(DottedCollectionName.class));
    }

    @Test
    void doesNotConstructWithSlashedCollectionName() {
        assertThrows(AnnotationException.class, () -> newHandle(SlashedCollectionName.class));
    }

    @Test
    void doesNotConstructWithClashingFieldName() {
        assertThrows(AnnotationException.class, () -> newHandle(ClashingFieldName.class));
    }

    @Test
    void doesNotConstructWithFieldValueField() {
        assertThrows(ReflectionException.class, () -> newHandle(FieldValueField.class));
    }

    @Test
    void doesNotConstructWithNonConvertableField() {
        assertThrows(AnnotationException.class, () -> newHandle(NonConvertableField.class));
    }

    @Test
    void doesNotConstructWithBlankPropertyName() {
        assertThrows(AnnotationException.class, () -> newHandle(BlankPropertyName.class));
    }

    @Test
    void doesNotConstructWithDottedPropertyName() {
        assertThrows(AnnotationException.class, () -> newHandle(DottedPropertyName.class));
    }

    @Test
    void doesNotConstructWithSlashedPropertyName() {
        assertThrows(AnnotationException.class, () -> newHandle(SlashedPropertyName.class));
    }

    @Test
    void doesNotConstructWithClashingPropertyName() {
        assertThrows(AnnotationException.class, () -> newHandle(ClashingPropertyName.class));
    }

    @Test
    void doesNotConstructWithNonFileWebField() {
        assertThrows(AnnotationException.class, () -> newHandle(NonFileWebField.class));
    }

    @Test
    void doesNotConstructWithConvertedFileField() {
        assertThrows(AnnotationException.class, () -> newHandle(ConvertedFileField.class));
    }

    @Test
    void doesNotConstructWithNonStringFileField() {
        assertThrows(AnnotationException.class, () -> newHandle(NonStringFileField.class));
    }

    @Test
    void doesNotConstructWithNonKeyAutoField() {
        assertThrows(AnnotationException.class, () -> newHandle(NonKeyAutoField.class));
    }

    @Test
    void doesNotConstructWithConvertedKeyField() {
        assertThrows(AnnotationException.class, () -> newHandle(ConvertedKeyField.class));
    }

    @Test
    void doesNotConstructWithFileKeyField() {
        assertThrows(AnnotationException.class, () -> newHandle(FileKeyField.class));
    }

    @Test
    void doesNotConstructWithTwoKeyFields() {
        assertThrows(AnnotationException.class, () -> newHandle(TwoKeyFields.class));
    }

    @Test
    void doesNotConstructWithNonStringAutoField() {
        assertThrows(AnnotationException.class, () -> newHandle(NonStringAutoField.class));
    }

    @Test
    void doesNotBuildCreateData() {
        Handle<Default> h = newHandle(Default.class);
        Default instance = new Default();
        assertThrows(IllegalArgumentException.class, () -> h.buildCreateData(instance));
    }

    @Test
    void doesNotBuildUpdateData() {
        Handle<Default> h = newHandle(Default.class);
        Map<String, Object> values = new HashMap<>();
        values.put(null, "");
        assertThrows(NullPointerException.class, () -> h.buildData(values));
    }

    @Test
    void buildsEntryPathsFromFieldNames() {
        Handle<FieldNames> h = newHandle(FieldNames.class);
        assertBuildsEntryPath("file", h, "file");
        assertBuildsEntryPath("zeroProperty", h, "zeroProperty");
        assertBuildsEntryPath("zeroProperty.key", h, "zeroProperty.key");
        assertBuildsEntryPath("zeroProperty.zero_map", h, "zeroProperty.zeroMap");
        assertBuildsEntryPath("zeroProperty.zero_map.name", h, "zeroProperty.zeroMap.name");
        assertBuildsEntryPath("zeroProperty.zero_field", h, "zeroProperty.zeroField");
        assertBuildsEntryPath("zeroProperty.zero_field.file", h, "zeroProperty.zeroField.file");
        assertBuildsEntryPath("zeroProperty.zero_field.zeroProperty", h, "zeroProperty.zeroField.zeroProperty");
        assertBuildsEntryPath("zeroProperty.zero_field.oneProperty", h, "zeroProperty.zeroField.oneProperty");
        assertBuildsEntryPath("zeroProperty.zero_field.twoProperty", h, "zeroProperty.zeroField.twoProperty");
        assertBuildsEntryPath("zeroProperty.one_field", h, "zeroProperty.oneField");
        assertBuildsEntryPath("zeroProperty.one_field.one", h, "zeroProperty.oneField.one");
        assertBuildsEntryPath("zeroProperty.one_field.one.file", h, "zeroProperty.oneField.one.file");
        assertBuildsEntryPath("zeroProperty.one_field.one.zeroProperty", h, "zeroProperty.oneField.one.zeroProperty");
        assertBuildsEntryPath("zeroProperty.one_field.one.oneProperty", h, "zeroProperty.oneField.one.oneProperty");
        assertBuildsEntryPath("zeroProperty.one_field.one.twoProperty", h, "zeroProperty.oneField.one.twoProperty");
        assertBuildsEntryPath("zeroProperty.two_field", h, "zeroProperty.twoField");
        assertBuildsEntryPath("zeroProperty.two_field.one", h, "zeroProperty.twoField.one");
        assertBuildsEntryPath("zeroProperty.two_field.one.two", h, "zeroProperty.twoField.one.two");
        assertBuildsEntryPath("zeroProperty.two_field.one.two.file", h, "zeroProperty.twoField.one.two.file");
        assertBuildsEntryPath("zeroProperty.two_field.one.two.zeroProperty", h, "zeroProperty.twoField.one.two.zeroProperty");
        assertBuildsEntryPath("zeroProperty.two_field.one.two.oneProperty", h, "zeroProperty.twoField.one.two.oneProperty");
        assertBuildsEntryPath("zeroProperty.two_field.one.two.twoProperty", h, "zeroProperty.twoField.one.two.twoProperty");
        assertBuildsEntryPath("oneProperty", h, "oneProperty");
        assertBuildsEntryPath("oneProperty.one", h, "oneProperty.one");
        assertBuildsEntryPath("oneProperty.one.key", h, "oneProperty.one.key");
        assertBuildsEntryPath("oneProperty.one.zero_map", h, "oneProperty.one.zeroMap");
        assertBuildsEntryPath("oneProperty.one.zero_map.name", h, "oneProperty.one.zeroMap.name");
        assertBuildsEntryPath("oneProperty.one.zero_field", h, "oneProperty.one.zeroField");
        assertBuildsEntryPath("oneProperty.one.zero_field.file", h, "oneProperty.one.zeroField.file");
        assertBuildsEntryPath("oneProperty.one.zero_field.zeroProperty", h, "oneProperty.one.zeroField.zeroProperty");
        assertBuildsEntryPath("oneProperty.one.zero_field.oneProperty", h, "oneProperty.one.zeroField.oneProperty");
        assertBuildsEntryPath("oneProperty.one.zero_field.twoProperty", h, "oneProperty.one.zeroField.twoProperty");
        assertBuildsEntryPath("oneProperty.one.one_field", h, "oneProperty.one.oneField");
        assertBuildsEntryPath("oneProperty.one.one_field.one", h, "oneProperty.one.oneField.one");
        assertBuildsEntryPath("oneProperty.one.one_field.one.file", h, "oneProperty.one.oneField.one.file");
        assertBuildsEntryPath("oneProperty.one.one_field.one.zeroProperty", h, "oneProperty.one.oneField.one.zeroProperty");
        assertBuildsEntryPath("oneProperty.one.one_field.one.oneProperty", h, "oneProperty.one.oneField.one.oneProperty");
        assertBuildsEntryPath("oneProperty.one.one_field.one.twoProperty", h, "oneProperty.one.oneField.one.twoProperty");
        assertBuildsEntryPath("oneProperty.one.two_field", h, "oneProperty.one.twoField");
        assertBuildsEntryPath("oneProperty.one.two_field.one", h, "oneProperty.one.twoField.one");
        assertBuildsEntryPath("oneProperty.one.two_field.one.two", h, "oneProperty.one.twoField.one.two");
        assertBuildsEntryPath("oneProperty.one.two_field.one.two.file", h, "oneProperty.one.twoField.one.two.file");
        assertBuildsEntryPath("oneProperty.one.two_field.one.two.zeroProperty", h, "oneProperty.one.twoField.one.two.zeroProperty");
        assertBuildsEntryPath("oneProperty.one.two_field.one.two.oneProperty", h, "oneProperty.one.twoField.one.two.oneProperty");
        assertBuildsEntryPath("oneProperty.one.two_field.one.two.twoProperty", h, "oneProperty.one.twoField.one.two.twoProperty");
        assertBuildsEntryPath("twoProperty", h, "twoProperty");
        assertBuildsEntryPath("twoProperty.one", h, "twoProperty.one");
        assertBuildsEntryPath("twoProperty.one.two", h, "twoProperty.one.two");
        assertBuildsEntryPath("twoProperty.one.two.key", h, "twoProperty.one.two.key");
        assertBuildsEntryPath("twoProperty.one.two.zero_map", h, "twoProperty.one.two.zeroMap");
        assertBuildsEntryPath("twoProperty.one.two.zero_map.name", h, "twoProperty.one.two.zeroMap.name");
        assertBuildsEntryPath("twoProperty.one.two.zero_field", h, "twoProperty.one.two.zeroField");
        assertBuildsEntryPath("twoProperty.one.two.zero_field.file", h, "twoProperty.one.two.zeroField.file");
        assertBuildsEntryPath("twoProperty.one.two.zero_field.oneProperty", h, "twoProperty.one.two.zeroField.oneProperty");
        assertBuildsEntryPath("twoProperty.one.two.zero_field.twoProperty", h, "twoProperty.one.two.zeroField.twoProperty");
        assertBuildsEntryPath("twoProperty.one.two.one_field", h, "twoProperty.one.two.oneField");
        assertBuildsEntryPath("twoProperty.one.two.one_field.one", h, "twoProperty.one.two.oneField.one");
        assertBuildsEntryPath("twoProperty.one.two.one_field.one.file", h, "twoProperty.one.two.oneField.one.file");
        assertBuildsEntryPath("twoProperty.one.two.one_field.one.oneProperty", h, "twoProperty.one.two.oneField.one.oneProperty");
        assertBuildsEntryPath("twoProperty.one.two.one_field.one.twoProperty", h, "twoProperty.one.two.oneField.one.twoProperty");
        assertBuildsEntryPath("twoProperty.one.two.two_field", h, "twoProperty.one.two.twoField");
        assertBuildsEntryPath("twoProperty.one.two.two_field.one", h, "twoProperty.one.two.twoField.one");
        assertBuildsEntryPath("twoProperty.one.two.two_field.one.two", h, "twoProperty.one.two.twoField.one.two");
        assertBuildsEntryPath("twoProperty.one.two.two_field.one.two.file", h, "twoProperty.one.two.twoField.one.two.file");
        assertBuildsEntryPath("twoProperty.one.two.two_field.one.two.oneProperty", h, "twoProperty.one.two.twoField.one.two.oneProperty");
        assertBuildsEntryPath("twoProperty.one.two.two_field.one.two.twoProperty", h, "twoProperty.one.two.twoField.one.two.twoProperty");
    }

    @Test
    void buildsEntryPathsFromPropertyNames() {
        Handle<PropertyNames> h = newHandle(PropertyNames.class);
        assertBuildsEntryPath("key", h, "key");
        assertBuildsEntryPath("zero_field", h, "zeroField");
        assertBuildsEntryPath("zero_field.file", h, "zeroField.file");
        assertBuildsEntryPath("zero_field.zeroMap", h, "zeroField.zeroMap");
        assertBuildsEntryPath("zero_field.zeroMap.name", h, "zeroField.zeroMap.name");
        assertBuildsEntryPath("zero_field.zeroProperty", h, "zeroField.zeroProperty");
        assertBuildsEntryPath("zero_field.zeroProperty.key", h, "zeroField.zeroProperty.key");
        assertBuildsEntryPath("zero_field.zeroProperty.zero_field", h, "zeroField.zeroProperty.zeroField");
        assertBuildsEntryPath("zero_field.zeroProperty.one_field", h, "zeroField.zeroProperty.oneField");
        assertBuildsEntryPath("zero_field.zeroProperty.two_field", h, "zeroField.zeroProperty.twoField");
        assertBuildsEntryPath("zero_field.oneProperty", h, "zeroField.oneProperty");
        assertBuildsEntryPath("zero_field.oneProperty.one", h, "zeroField.oneProperty.one");
        assertBuildsEntryPath("zero_field.oneProperty.one.key", h, "zeroField.oneProperty.one.key");
        assertBuildsEntryPath("zero_field.oneProperty.one.zero_field", h, "zeroField.oneProperty.one.zeroField");
        assertBuildsEntryPath("zero_field.oneProperty.one.one_field", h, "zeroField.oneProperty.one.oneField");
        assertBuildsEntryPath("zero_field.oneProperty.one.two_field", h, "zeroField.oneProperty.one.twoField");
        assertBuildsEntryPath("zero_field.twoProperty", h, "zeroField.twoProperty");
        assertBuildsEntryPath("zero_field.twoProperty.one", h, "zeroField.twoProperty.one");
        assertBuildsEntryPath("zero_field.twoProperty.one.two", h, "zeroField.twoProperty.one.two");
        assertBuildsEntryPath("zero_field.twoProperty.one.two.key", h, "zeroField.twoProperty.one.two.key");
        assertBuildsEntryPath("zero_field.twoProperty.one.two.zero_field", h, "zeroField.twoProperty.one.two.zeroField");
        assertBuildsEntryPath("zero_field.twoProperty.one.two.one_field", h, "zeroField.twoProperty.one.two.oneField");
        assertBuildsEntryPath("zero_field.twoProperty.one.two.two_field", h, "zeroField.twoProperty.one.two.twoField");
        assertBuildsEntryPath("one_field", h, "oneField");
        assertBuildsEntryPath("one_field.one", h, "oneField.one");
        assertBuildsEntryPath("one_field.one.file", h, "oneField.one.file");
        assertBuildsEntryPath("one_field.one.zeroMap", h, "oneField.one.zeroMap");
        assertBuildsEntryPath("one_field.one.zeroMap.name", h, "oneField.one.zeroMap.name");
        assertBuildsEntryPath("one_field.one.zeroProperty", h, "oneField.one.zeroProperty");
        assertBuildsEntryPath("one_field.one.zeroProperty.key", h, "oneField.one.zeroProperty.key");
        assertBuildsEntryPath("one_field.one.zeroProperty.zero_field", h, "oneField.one.zeroProperty.zeroField");
        assertBuildsEntryPath("one_field.one.zeroProperty.one_field", h, "oneField.one.zeroProperty.oneField");
        assertBuildsEntryPath("one_field.one.zeroProperty.two_field", h, "oneField.one.zeroProperty.twoField");
        assertBuildsEntryPath("one_field.one.oneProperty", h, "oneField.one.oneProperty");
        assertBuildsEntryPath("one_field.one.oneProperty.one", h, "oneField.one.oneProperty.one");
        assertBuildsEntryPath("one_field.one.oneProperty.one.key", h, "oneField.one.oneProperty.one.key");
        assertBuildsEntryPath("one_field.one.oneProperty.one.zero_field", h, "oneField.one.oneProperty.one.zeroField");
        assertBuildsEntryPath("one_field.one.oneProperty.one.one_field", h, "oneField.one.oneProperty.one.oneField");
        assertBuildsEntryPath("one_field.one.oneProperty.one.two_field", h, "oneField.one.oneProperty.one.twoField");
        assertBuildsEntryPath("one_field.one.twoProperty", h, "oneField.one.twoProperty");
        assertBuildsEntryPath("one_field.one.twoProperty.one", h, "oneField.one.twoProperty.one");
        assertBuildsEntryPath("one_field.one.twoProperty.one.two", h, "oneField.one.twoProperty.one.two");
        assertBuildsEntryPath("one_field.one.twoProperty.one.two.key", h, "oneField.one.twoProperty.one.two.key");
        assertBuildsEntryPath("one_field.one.twoProperty.one.two.zero_field", h, "oneField.one.twoProperty.one.two.zeroField");
        assertBuildsEntryPath("one_field.one.twoProperty.one.two.one_field", h, "oneField.one.twoProperty.one.two.oneField");
        assertBuildsEntryPath("one_field.one.twoProperty.one.two.two_field", h, "oneField.one.twoProperty.one.two.twoField");
        assertBuildsEntryPath("two_field", h, "twoField");
        assertBuildsEntryPath("two_field.one", h, "twoField.one");
        assertBuildsEntryPath("two_field.one.two", h, "twoField.one.two");
        assertBuildsEntryPath("two_field.one.two.file", h, "twoField.one.two.file");
        assertBuildsEntryPath("two_field.one.two.zeroMap", h, "twoField.one.two.zeroMap");
        assertBuildsEntryPath("two_field.one.two.zeroMap.name", h, "twoField.one.two.zeroMap.name");
        assertBuildsEntryPath("two_field.one.two.zeroProperty", h, "twoField.one.two.zeroProperty");
        assertBuildsEntryPath("two_field.one.two.zeroProperty.key", h, "twoField.one.two.zeroProperty.key");
        assertBuildsEntryPath("two_field.one.two.zeroProperty.zero_field", h, "twoField.one.two.zeroProperty.zeroField");
        assertBuildsEntryPath("two_field.one.two.zeroProperty.one_field", h, "twoField.one.two.zeroProperty.oneField");
        assertBuildsEntryPath("two_field.one.two.zeroProperty.two_field", h, "twoField.one.two.zeroProperty.twoField");
        assertBuildsEntryPath("two_field.one.two.oneProperty", h, "twoField.one.two.oneProperty");
        assertBuildsEntryPath("two_field.one.two.oneProperty.one", h, "twoField.one.two.oneProperty.one");
        assertBuildsEntryPath("two_field.one.two.oneProperty.one.key", h, "twoField.one.two.oneProperty.one.key");
        assertBuildsEntryPath("two_field.one.two.oneProperty.one.zero_field", h, "twoField.one.two.oneProperty.one.zeroField");
        assertBuildsEntryPath("two_field.one.two.oneProperty.one.one_field", h, "twoField.one.two.oneProperty.one.oneField");
        assertBuildsEntryPath("two_field.one.two.oneProperty.one.two_field", h, "twoField.one.two.oneProperty.one.twoField");
        assertBuildsEntryPath("two_field.one.two.twoProperty", h, "twoField.one.two.twoProperty");
        assertBuildsEntryPath("two_field.one.two.twoProperty.one", h, "twoField.one.two.twoProperty.one");
        assertBuildsEntryPath("two_field.one.two.twoProperty.one.two", h, "twoField.one.two.twoProperty.one.two");
        assertBuildsEntryPath("two_field.one.two.twoProperty.one.two.key", h, "twoField.one.two.twoProperty.one.two.key");
        assertBuildsEntryPath("two_field.one.two.twoProperty.one.two.zero_field", h, "twoField.one.two.twoProperty.one.two.zeroField");
        assertBuildsEntryPath("two_field.one.two.twoProperty.one.two.one_field", h, "twoField.one.two.twoProperty.one.two.oneField");
        assertBuildsEntryPath("two_field.one.two.twoProperty.one.two.two_field", h, "twoField.one.two.twoProperty.one.two.twoField");
    }

    private <E> void assertBuildsEntryPath(String expected, Handle<E> handle, String fieldPath) {
        String[] fieldPaths = new String[]{fieldPath};
        assertArrayEquals(new String[]{expected}, handle.buildDataEntryPaths(fieldPaths));
    }

    @Test
    void convertsNullTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        assertConvertsNullTo(h, "booleanValue");
        assertConvertsNullTo(h, "boxedBooleanValue");
        assertConvertsNullTo(h, "charValue");
        assertConvertsNullTo(h, "boxedCharValue");
        assertConvertsNullTo(h, "byteValue");
        assertConvertsNullTo(h, "boxedByteValue");
        assertConvertsNullTo(h, "shortValue");
        assertConvertsNullTo(h, "boxedShortValue");
        assertConvertsNullTo(h, "intValue");
        assertConvertsNullTo(h, "boxedIntValue");
        assertConvertsNullTo(h, "longValue");
        assertConvertsNullTo(h, "boxedLongValue");
        assertConvertsNullTo(h, "floatValue");
        assertConvertsNullTo(h, "boxedFloatValue");
        assertConvertsNullTo(h, "doubleValue");
        assertConvertsNullTo(h, "boxedDoubleValue");
        assertConvertsNullTo(h, "stringValue");
        assertConvertsNullTo(h, "objectValue");
    }

    private <E> void assertConvertsNullTo(Handle<E> handle, String fieldName) {
        Map<String, Object> values = new HashMap<>();
        values.put(fieldName, null);
        Map<String, Object> data = handle.buildData(values);
        assertEquals(1, data.size());
        assertTrue(data.containsKey(fieldName));
        assertNull(data.get(fieldName));
    }

    @Test
    void convertsFieldValueTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        FieldValue value = mock(FieldValue.class);
        assertConvertsSameTo(h, "booleanValue", value);
        assertConvertsSameTo(h, "boxedBooleanValue", value);
        assertConvertsSameTo(h, "charValue", value);
        assertConvertsSameTo(h, "boxedCharValue", value);
        assertConvertsSameTo(h, "byteValue", value);
        assertConvertsSameTo(h, "boxedByteValue", value);
        assertConvertsSameTo(h, "shortValue", value);
        assertConvertsSameTo(h, "boxedShortValue", value);
        assertConvertsSameTo(h, "intValue", value);
        assertConvertsSameTo(h, "boxedIntValue", value);
        assertConvertsSameTo(h, "longValue", value);
        assertConvertsSameTo(h, "boxedLongValue", value);
        assertConvertsSameTo(h, "floatValue", value);
        assertConvertsSameTo(h, "boxedFloatValue", value);
        assertConvertsSameTo(h, "doubleValue", value);
        assertConvertsSameTo(h, "boxedDoubleValue", value);
        assertConvertsSameTo(h, "stringValue", value);
        assertConvertsSameTo(h, "objectValue", value);
    }

    @Test
    void convertsBooleanTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        boolean value = true;
        assertConvertsSameTo(h, "booleanValue", value);
        assertConvertsSameTo(h, "boxedBooleanValue", value);
        assertConvertsSameTo(h, "objectValue", value);
    }

    @Test
    void convertsBoxedBooleanTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        Boolean value = true;
        assertConvertsSameTo(h, "booleanValue", value);
        assertConvertsSameTo(h, "boxedBooleanValue", value);
        assertConvertsSameTo(h, "objectValue", value);
    }

    @Test
    void convertsCharTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        char value = 'c';
        String expected = "c";
        assertConvertsTo(expected, h, "charValue", value);
        assertConvertsTo(expected, h, "boxedCharValue", value);
        assertConvertsTo(expected, h, "stringValue", value);
        assertConvertsTo(expected, h, "objectValue", value);
    }

    @Test
    void convertsBoxedCharTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        Character value = 'c';
        String expected = "c";
        assertConvertsTo(expected, h, "charValue", value);
        assertConvertsTo(expected, h, "boxedCharValue", value);
        assertConvertsTo(expected, h, "stringValue", value);
        assertConvertsTo(expected, h, "objectValue", value);
    }

    @Test
    void convertsByteTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        byte value = 1;
        assertConvertsSameTo(h, "byteValue", value);
        assertConvertsSameTo(h, "boxedByteValue", value);
        assertConvertsSameTo(h, "shortValue", value);
        assertConvertsSameTo(h, "boxedShortValue", value);
        assertConvertsSameTo(h, "intValue", value);
        assertConvertsSameTo(h, "boxedIntValue", value);
        assertConvertsSameTo(h, "longValue", value);
        assertConvertsSameTo(h, "boxedLongValue", value);
        assertConvertsSameTo(h, "floatValue", value);
        assertConvertsSameTo(h, "boxedFloatValue", value);
        assertConvertsSameTo(h, "doubleValue", value);
        assertConvertsSameTo(h, "boxedDoubleValue", value);
        assertConvertsSameTo(h, "objectValue", value);
    }

    @Test
    void convertsBoxedByteTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        Byte value = 1;
        assertConvertsSameTo(h, "byteValue", value);
        assertConvertsSameTo(h, "boxedByteValue", value);
        assertConvertsSameTo(h, "shortValue", value);
        assertConvertsSameTo(h, "boxedShortValue", value);
        assertConvertsSameTo(h, "intValue", value);
        assertConvertsSameTo(h, "boxedIntValue", value);
        assertConvertsSameTo(h, "longValue", value);
        assertConvertsSameTo(h, "boxedLongValue", value);
        assertConvertsSameTo(h, "floatValue", value);
        assertConvertsSameTo(h, "boxedFloatValue", value);
        assertConvertsSameTo(h, "doubleValue", value);
        assertConvertsSameTo(h, "boxedDoubleValue", value);
        assertConvertsSameTo(h, "objectValue", value);
    }

    @Test
    void convertsShortTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        short value = 2;
        assertConvertsSameTo(h, "shortValue", value);
        assertConvertsSameTo(h, "boxedShortValue", value);
        assertConvertsSameTo(h, "intValue", value);
        assertConvertsSameTo(h, "boxedIntValue", value);
        assertConvertsSameTo(h, "longValue", value);
        assertConvertsSameTo(h, "boxedLongValue", value);
        assertConvertsSameTo(h, "floatValue", value);
        assertConvertsSameTo(h, "boxedFloatValue", value);
        assertConvertsSameTo(h, "doubleValue", value);
        assertConvertsSameTo(h, "boxedDoubleValue", value);
        assertConvertsSameTo(h, "objectValue", value);
    }

    @Test
    void convertsBoxedShortTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        Short value = 2;
        assertConvertsSameTo(h, "shortValue", value);
        assertConvertsSameTo(h, "boxedShortValue", value);
        assertConvertsSameTo(h, "intValue", value);
        assertConvertsSameTo(h, "boxedIntValue", value);
        assertConvertsSameTo(h, "longValue", value);
        assertConvertsSameTo(h, "boxedLongValue", value);
        assertConvertsSameTo(h, "floatValue", value);
        assertConvertsSameTo(h, "boxedFloatValue", value);
        assertConvertsSameTo(h, "doubleValue", value);
        assertConvertsSameTo(h, "boxedDoubleValue", value);
        assertConvertsSameTo(h, "objectValue", value);
    }

    @Test
    void convertsIntegerTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        int value = 3;
        assertConvertsSameTo(h, "intValue", value);
        assertConvertsSameTo(h, "boxedIntValue", value);
        assertConvertsSameTo(h, "longValue", value);
        assertConvertsSameTo(h, "boxedLongValue", value);
        assertConvertsSameTo(h, "floatValue", value);
        assertConvertsSameTo(h, "boxedFloatValue", value);
        assertConvertsSameTo(h, "doubleValue", value);
        assertConvertsSameTo(h, "boxedDoubleValue", value);
        assertConvertsSameTo(h, "objectValue", value);
    }

    @Test
    void convertsBoxedIntegerTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        Integer value = 3;
        assertConvertsSameTo(h, "intValue", value);
        assertConvertsSameTo(h, "boxedIntValue", value);
        assertConvertsSameTo(h, "longValue", value);
        assertConvertsSameTo(h, "boxedLongValue", value);
        assertConvertsSameTo(h, "floatValue", value);
        assertConvertsSameTo(h, "boxedFloatValue", value);
        assertConvertsSameTo(h, "doubleValue", value);
        assertConvertsSameTo(h, "boxedDoubleValue", value);
        assertConvertsSameTo(h, "objectValue", value);
    }

    @Test
    void convertsLongTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        long value = 4;
        assertConvertsSameTo(h, "longValue", value);
        assertConvertsSameTo(h, "boxedLongValue", value);
        assertConvertsSameTo(h, "doubleValue", value);
        assertConvertsSameTo(h, "boxedDoubleValue", value);
        assertConvertsSameTo(h, "objectValue", value);
    }

    @Test
    void convertsBoxedLongTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        Long value = 4L;
        assertConvertsSameTo(h, "longValue", value);
        assertConvertsSameTo(h, "boxedLongValue", value);
        assertConvertsSameTo(h, "doubleValue", value);
        assertConvertsSameTo(h, "boxedDoubleValue", value);
        assertConvertsSameTo(h, "objectValue", value);
    }

    @Test
    void convertsFloatTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        float value = 5.5F;
        assertConvertsSameTo(h, "intValue", value);
        assertConvertsSameTo(h, "boxedIntValue", value);
        assertConvertsSameTo(h, "longValue", value);
        assertConvertsSameTo(h, "boxedLongValue", value);
        assertConvertsSameTo(h, "floatValue", value);
        assertConvertsSameTo(h, "boxedFloatValue", value);
        assertConvertsSameTo(h, "doubleValue", value);
        assertConvertsSameTo(h, "boxedDoubleValue", value);
        assertConvertsSameTo(h, "objectValue", value);
    }

    @Test
    void convertsBoxedFloatTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        Float value = 5.5F;
        assertConvertsSameTo(h, "intValue", value);
        assertConvertsSameTo(h, "boxedIntValue", value);
        assertConvertsSameTo(h, "longValue", value);
        assertConvertsSameTo(h, "boxedLongValue", value);
        assertConvertsSameTo(h, "floatValue", value);
        assertConvertsSameTo(h, "boxedFloatValue", value);
        assertConvertsSameTo(h, "doubleValue", value);
        assertConvertsSameTo(h, "boxedDoubleValue", value);
        assertConvertsSameTo(h, "objectValue", value);
    }

    @Test
    void convertsDoubleTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        double value = 6.6;
        assertConvertsSameTo(h, "longValue", value);
        assertConvertsSameTo(h, "boxedLongValue", value);
        assertConvertsSameTo(h, "doubleValue", value);
        assertConvertsSameTo(h, "boxedDoubleValue", value);
        assertConvertsSameTo(h, "objectValue", value);
    }

    @Test
    void convertsBoxedDoubleTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        Double value = 6.6;
        assertConvertsSameTo(h, "longValue", value);
        assertConvertsSameTo(h, "boxedLongValue", value);
        assertConvertsSameTo(h, "doubleValue", value);
        assertConvertsSameTo(h, "boxedDoubleValue", value);
        assertConvertsSameTo(h, "objectValue", value);
    }

    @Test
    void convertsStringTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        String value = "s";
        assertConvertsSameTo(h, "stringValue", value);
        assertConvertsSameTo(h, "objectValue", value);
    }

    @Test
    void convertsPointTo() {
        Handle<FirestoreFields> h = newHandle(FirestoreFields.class);
        GeoPoint value = mock(GeoPoint.class);
        assertConvertsSameTo(h, "point", value);
        assertConvertsSameTo(h, "object", value);
    }

    @Test
    void convertsReferenceTo() {
        Handle<FirestoreFields> h = newHandle(FirestoreFields.class);
        DocumentReference value = mock(DocumentReference.class);
        assertConvertsSameTo(h, "reference", value);
        assertConvertsSameTo(h, "object", value);
    }

    @Test
    void convertsTimestampTo() {
        Handle<FirestoreFields> h = newHandle(FirestoreFields.class);
        Timestamp value = mock(Timestamp.class);
        assertConvertsSameTo(h, "timestamp", value);
        assertConvertsSameTo(h, "instant", value);
        assertConvertsSameTo(h, "object", value);
    }

    @Test
    void convertsInstantTo() {
        Handle<FirestoreFields> h = newHandle(FirestoreFields.class);
        assertConvertsInstantTo(h, "timestamp");
        assertConvertsInstantTo(h, "instant");
        assertConvertsInstantTo(h, "object");
    }

    private <E> void assertConvertsInstantTo(Handle<E> handle, String fieldName) {
        Map<String, Object> values = Map.of(fieldName, Instant.EPOCH);
        Map<String, Object> data;
        Timestamp timestamp = mock(Timestamp.class);
        try (MockedStatic<Timestamp> timestampStatic = mockStatic(Timestamp.class)) {
            timestampStatic.when(() -> Timestamp.ofTimeSecondsAndNanos(0, 0)).thenReturn(timestamp);
            data = handle.buildData(values);
        }
        assertEquals(1, data.size());
        assertSame(timestamp, data.get(fieldName));
    }

    @Test
    void convertsBlobTo() {
        Handle<FirestoreFields> h = newHandle(FirestoreFields.class);
        Blob value = mock(Blob.class);
        assertConvertsSameTo(h, "blob", value);
        assertConvertsSameTo(h, "stream", value);
        assertConvertsSameTo(h, "object", value);
    }

    @Test
    void convertsStreamTo() {
        Handle<FirestoreFields> h = newHandle(FirestoreFields.class);
        assertConvertsStreamTo(h, "blob");
        assertConvertsStreamTo(h, "stream");
        assertConvertsStreamTo(h, "object");
    }

    private <E> void assertConvertsStreamTo(Handle<E> handle, String fieldName) {
        InputStream stream = InputStream.nullInputStream();
        Map<String, Object> values = Map.of(fieldName, stream);
        Map<String, Object> data;
        Blob blob = mock(Blob.class);
        ByteString byteString = mock(ByteString.class);
        try (MockedStatic<ByteString> byteStringStatic = mockStatic(ByteString.class)) {
            byteStringStatic.when(() -> ByteString.readFrom(stream)).thenReturn(byteString);
            try (MockedStatic<Blob> blobStatic = mockStatic(Blob.class)) {
                blobStatic.when(() -> Blob.fromByteString(byteString)).thenReturn(blob);
                data = handle.buildData(values);
            }
        }
        assertEquals(1, data.size());
        assertSame(blob, data.get(fieldName));
    }

    @Test
    void convertsStringArrayTo() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        String[] value = new String[]{"s"};
        List<Object> expected = List.of("s");
        assertConvertsTo(expected, h, "stringArray", value);
        assertConvertsTo(expected, h, "objectArray", value);
        assertConvertsTo(expected, h, "stringList", value);
        assertConvertsTo(expected, h, "objectList", value);
        assertConvertsTo(expected, h, "rawList", value);
        assertConvertsTo(expected, h, "object", value);
    }

    @Test
    void convertsObjectArrayTo() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        Object[] value = new Object[]{new Object()};
        List<Object> expected = List.of(Map.of());
        assertConvertsTo(expected, h, "objectArray", value);
        assertConvertsTo(expected, h, "objectList", value);
        assertConvertsTo(expected, h, "rawList", value);
        assertConvertsTo(expected, h, "object", value);
    }

    @Test
    void convertsStringListTo() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        List<String> value = List.of("s");
        List<Object> expected = List.of("s");
        assertConvertsTo(expected, h, "stringArray", value);
        assertConvertsTo(expected, h, "objectArray", value);
        assertConvertsTo(expected, h, "stringList", value);
        assertConvertsTo(expected, h, "objectList", value);
        assertConvertsTo(expected, h, "rawList", value);
        assertConvertsTo(expected, h, "object", value);
    }

    @Test
    void convertsObjectListTo() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        List<Object> value = List.of(new Object());
        List<Object> expected = List.of(Map.of());
        assertConvertsTo(expected, h, "objectArray", value);
        assertConvertsTo(expected, h, "objectList", value);
        assertConvertsTo(expected, h, "rawList", value);
        assertConvertsTo(expected, h, "object", value);
    }

    @Test
    void convertsRawListTo() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        @SuppressWarnings("rawtypes")
        List value = List.of(new Object());
        List<Object> expected = List.of(Map.of());
        assertConvertsTo(expected, h, "objectArray", value);
        assertConvertsTo(expected, h, "objectList", value);
        assertConvertsTo(expected, h, "rawList", value);
        assertConvertsTo(expected, h, "object", value);
    }

    @Test
    void convertsStringStringMapTo() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        Map<String, String> value = Map.of("k", "v");
        Map<String, Object> expected = Map.of("k", "v");
        assertConvertsTo(expected, h, "stringStringMap", value);
        assertConvertsTo(expected, h, "stringObjectMap", value);
    }

    @Test
    void convertsStringObjectMapTo() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        Map<String, Object> value = Map.of("k", new Object());
        Map<String, Object> expected = Map.of("k", Map.of());
        assertConvertsTo(expected, h, "stringObjectMap", value);
    }

    @Test
    void convertsCustomTo() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        Custom value = new Custom();
        Map<String, Object> expected = Map.of("key", "value");
        assertConvertsTo(expected, h, "custom", value);
    }

    @Test
    void convertsEmailTo() {
        Handle<ConvertableFields> h = newHandle(ConvertableFields.class);
        Email value = new Email();
        value.setLogin("convertable");
        value.setDomain("email.com");
        String expected = "convertable@email.com";
        assertConvertsTo(expected, h, "email", value);
    }

    @Test
    void convertsAddressTo() {
        Handle<ConvertableFields> h = newHandle(ConvertableFields.class);
        Address value = new Address("Convertable Street", 0, "Convertable City");
        List<String> expected = List.of("Convertable City", "0", "Convertable Street");
        assertConvertsTo(expected, h, "address", value);
    }

    @Test
    void convertsBooleanWrapperTo() {
        Handle<ConvertableFields> h = newHandle(ConvertableFields.class);
        Wrapper<Boolean> value = new Wrapper<>(true);
        String expected = "true";
        assertConvertsTo(expected, h, "booleanWrapper", value);
    }

    @Test
    void convertsByteWrapperTo() {
        Handle<ConvertableFields> h = newHandle(ConvertableFields.class);
        Wrapper<Byte> value = new Wrapper<>((byte) 127);
        List<String> expected = List.of("1", "2", "7");
        assertConvertsTo(expected, h, "byteWrapper", value);
    }

    @Test
    void convertsCompositeNameTo() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        String value = "s";
        assertConvertsSameTo(h, "stringStringMap.name", value);
        assertConvertsSameTo(h, "stringObjectMap.name", value);
    }

    private <E> void assertConvertsSameTo(Handle<E> handle, String fieldName, Object value) {
        assertConvertsTo(value, handle, fieldName, value);
    }

    private <E> void assertConvertsTo(Object expected, Handle<E> handle, String fieldName, Object value) {
        Map<String, Object> values = Map.of(fieldName, value);
        Map<String, Object> data = handle.buildData(values);
        assertEquals(1, data.size());
        assertEquals(expected, data.get(fieldName));
    }

    @Test
    void convertsFieldPathsTo() {
        Handle<FieldNames> h = newHandle(FieldNames.class);
        FieldNames zeroField = new FieldNames();
        Map<String, FieldNames> oneField = Map.of("one", zeroField);
        Map<Integer, FieldNames> intField = Map.of(1, zeroField);
        Map<String, Map<Integer, FieldNames>> twoField = Map.of("two", intField);
        PropertyNames zeroProperty = new PropertyNames();
        Map<String, PropertyNames> oneProperty = Map.of("one", zeroProperty);
        Map<Integer, PropertyNames> intProperty = Map.of(1, zeroProperty);
        Map<String, Map<Integer, PropertyNames>> twoProperty = Map.of("two", intProperty);
        assertConvertsNameTo("zeroProperty", h, "zeroProperty", zeroProperty);
        assertConvertsNameTo("zeroProperty.key", h, "zeroProperty.key", "s");
        assertConvertsNameTo("zeroProperty.zero_map", h, "zeroProperty.zeroMap", zeroField);
        assertConvertsNameTo("zeroProperty.zero_map.name", h, "zeroProperty.zeroMap.name", "s");
        assertConvertsNameTo("zeroProperty.zero_field", h, "zeroProperty.zeroField", zeroField);
        assertConvertsNameTo("zeroProperty.zero_field.zeroProperty", h, "zeroProperty.zeroField.zeroProperty", zeroProperty);
        assertConvertsNameTo("zeroProperty.zero_field.oneProperty", h, "zeroProperty.zeroField.oneProperty", oneProperty);
        assertConvertsNameTo("zeroProperty.zero_field.twoProperty", h, "zeroProperty.zeroField.twoProperty", twoProperty);
        assertConvertsNameTo("zeroProperty.one_field", h, "zeroProperty.oneField", oneField);
        assertConvertsNameTo("zeroProperty.one_field.one", h, "zeroProperty.oneField.one", zeroField);
        assertConvertsNameTo("zeroProperty.one_field.one.zeroProperty", h, "zeroProperty.oneField.one.zeroProperty", zeroProperty);
        assertConvertsNameTo("zeroProperty.one_field.one.oneProperty", h, "zeroProperty.oneField.one.oneProperty", oneProperty);
        assertConvertsNameTo("zeroProperty.one_field.one.twoProperty", h, "zeroProperty.oneField.one.twoProperty", twoProperty);
        assertConvertsNameTo("zeroProperty.two_field", h, "zeroProperty.twoField", twoField);
        assertConvertsNameTo("zeroProperty.two_field.one", h, "zeroProperty.twoField.one", intField);
        assertConvertsNameTo("zeroProperty.two_field.one.two", h, "zeroProperty.twoField.one.two", zeroField);
        assertConvertsNameTo("zeroProperty.two_field.one.two.zeroProperty", h, "zeroProperty.twoField.one.two.zeroProperty", zeroProperty);
        assertConvertsNameTo("zeroProperty.two_field.one.two.oneProperty", h, "zeroProperty.twoField.one.two.oneProperty", oneProperty);
        assertConvertsNameTo("zeroProperty.two_field.one.two.twoProperty", h, "zeroProperty.twoField.one.two.twoProperty", twoProperty);
        assertConvertsNameTo("oneProperty", h, "oneProperty", oneProperty);
        assertConvertsNameTo("oneProperty.one", h, "oneProperty.one", zeroProperty);
        assertConvertsNameTo("oneProperty.one.key", h, "oneProperty.one.key", "s");
        assertConvertsNameTo("oneProperty.one.zero_map", h, "oneProperty.one.zeroMap", zeroField);
        assertConvertsNameTo("oneProperty.one.zero_map.name", h, "oneProperty.one.zeroMap.name", "s");
        assertConvertsNameTo("oneProperty.one.zero_field", h, "oneProperty.one.zeroField", zeroField);
        assertConvertsNameTo("oneProperty.one.zero_field.zeroProperty", h, "oneProperty.one.zeroField.zeroProperty", zeroProperty);
        assertConvertsNameTo("oneProperty.one.zero_field.oneProperty", h, "oneProperty.one.zeroField.oneProperty", oneProperty);
        assertConvertsNameTo("oneProperty.one.zero_field.twoProperty", h, "oneProperty.one.zeroField.twoProperty", twoProperty);
        assertConvertsNameTo("oneProperty.one.one_field", h, "oneProperty.one.oneField", oneField);
        assertConvertsNameTo("oneProperty.one.one_field.one", h, "oneProperty.one.oneField.one", zeroField);
        assertConvertsNameTo("oneProperty.one.one_field.one.zeroProperty", h, "oneProperty.one.oneField.one.zeroProperty", zeroProperty);
        assertConvertsNameTo("oneProperty.one.one_field.one.oneProperty", h, "oneProperty.one.oneField.one.oneProperty", oneProperty);
        assertConvertsNameTo("oneProperty.one.one_field.one.twoProperty", h, "oneProperty.one.oneField.one.twoProperty", twoProperty);
        assertConvertsNameTo("oneProperty.one.two_field", h, "oneProperty.one.twoField", twoField);
        assertConvertsNameTo("oneProperty.one.two_field.one", h, "oneProperty.one.twoField.one", intField);
        assertConvertsNameTo("oneProperty.one.two_field.one.two", h, "oneProperty.one.twoField.one.two", zeroField);
        assertConvertsNameTo("oneProperty.one.two_field.one.two.zeroProperty", h, "oneProperty.one.twoField.one.two.zeroProperty", zeroProperty);
        assertConvertsNameTo("oneProperty.one.two_field.one.two.oneProperty", h, "oneProperty.one.twoField.one.two.oneProperty", oneProperty);
        assertConvertsNameTo("oneProperty.one.two_field.one.two.twoProperty", h, "oneProperty.one.twoField.one.two.twoProperty", twoProperty);
        assertConvertsNameTo("twoProperty", h, "twoProperty", twoProperty);
        assertConvertsNameTo("twoProperty.one", h, "twoProperty.one", intProperty);
        assertConvertsNameTo("twoProperty.one.two", h, "twoProperty.one.two", zeroProperty);
        assertConvertsNameTo("twoProperty.one.two.key", h, "twoProperty.one.two.key", "s");
        assertConvertsNameTo("twoProperty.one.two.zero_map", h, "twoProperty.one.two.zeroMap", zeroField);
        assertConvertsNameTo("twoProperty.one.two.zero_map.name", h, "twoProperty.one.two.zeroMap.name", "s");
        assertConvertsNameTo("twoProperty.one.two.zero_field", h, "twoProperty.one.two.zeroField", zeroField);
        assertConvertsNameTo("twoProperty.one.two.zero_field.zeroProperty", h, "twoProperty.one.two.zeroField.zeroProperty", zeroProperty);
        assertConvertsNameTo("twoProperty.one.two.zero_field.oneProperty", h, "twoProperty.one.two.zeroField.oneProperty", oneProperty);
        assertConvertsNameTo("twoProperty.one.two.zero_field.twoProperty", h, "twoProperty.one.two.zeroField.twoProperty", twoProperty);
        assertConvertsNameTo("twoProperty.one.two.one_field", h, "twoProperty.one.two.oneField", oneField);
        assertConvertsNameTo("twoProperty.one.two.one_field.one", h, "twoProperty.one.two.oneField.one", zeroField);
        assertConvertsNameTo("twoProperty.one.two.one_field.one.zeroProperty", h, "twoProperty.one.two.oneField.one.zeroProperty", zeroProperty);
        assertConvertsNameTo("twoProperty.one.two.one_field.one.oneProperty", h, "twoProperty.one.two.oneField.one.oneProperty", oneProperty);
        assertConvertsNameTo("twoProperty.one.two.one_field.one.twoProperty", h, "twoProperty.one.two.oneField.one.twoProperty", twoProperty);
        assertConvertsNameTo("twoProperty.one.two.two_field", h, "twoProperty.one.two.twoField", twoField);
        assertConvertsNameTo("twoProperty.one.two.two_field.one", h, "twoProperty.one.two.twoField.one", intField);
        assertConvertsNameTo("twoProperty.one.two.two_field.one.two", h, "twoProperty.one.two.twoField.one.two", zeroField);
        assertConvertsNameTo("twoProperty.one.two.two_field.one.two.zeroProperty", h, "twoProperty.one.two.twoField.one.two.zeroProperty", zeroProperty);
        assertConvertsNameTo("twoProperty.one.two.two_field.one.two.oneProperty", h, "twoProperty.one.two.twoField.one.two.oneProperty", oneProperty);
        assertConvertsNameTo("twoProperty.one.two.two_field.one.two.twoProperty", h, "twoProperty.one.two.twoField.one.two.twoProperty", twoProperty);
    }

    @Test
    void convertsPropertyPathsTo() {
        Handle<PropertyNames> h = newHandle(PropertyNames.class);
        PropertyNames zeroProperty = new PropertyNames();
        Map<String, PropertyNames> oneProperty = Map.of("one", zeroProperty);
        Map<Integer, PropertyNames> intProperty = Map.of(1, zeroProperty);
        Map<String, Map<Integer, PropertyNames>> twoProperty = Map.of("two", intProperty);
        FieldNames zeroField = new FieldNames();
        Map<String, FieldNames> oneField = Map.of("one", zeroField);
        Map<Integer, FieldNames> intField = Map.of(1, zeroField);
        Map<String, Map<Integer, FieldNames>> twoField = Map.of("two", intField);
        assertConvertsNameTo("zero_field", h, "zeroField", zeroField);
        assertConvertsNameTo("zero_field.zeroMap", h, "zeroField.zeroMap", zeroProperty);
        assertConvertsNameTo("zero_field.zeroMap.name", h, "zeroField.zeroMap.name", "s");
        assertConvertsNameTo("zero_field.zeroProperty", h, "zeroField.zeroProperty", zeroProperty);
        assertConvertsNameTo("zero_field.zeroProperty.key", h, "zeroField.zeroProperty.key", "s");
        assertConvertsNameTo("zero_field.zeroProperty.zero_field", h, "zeroField.zeroProperty.zeroField", zeroField);
        assertConvertsNameTo("zero_field.zeroProperty.one_field", h, "zeroField.zeroProperty.oneField", oneField);
        assertConvertsNameTo("zero_field.zeroProperty.two_field", h, "zeroField.zeroProperty.twoField", twoField);
        assertConvertsNameTo("zero_field.oneProperty", h, "zeroField.oneProperty", oneProperty);
        assertConvertsNameTo("zero_field.oneProperty.one", h, "zeroField.oneProperty.one", zeroProperty);
        assertConvertsNameTo("zero_field.oneProperty.one.key", h, "zeroField.oneProperty.one.key", "s");
        assertConvertsNameTo("zero_field.oneProperty.one.zero_field", h, "zeroField.oneProperty.one.zeroField", zeroField);
        assertConvertsNameTo("zero_field.oneProperty.one.one_field", h, "zeroField.oneProperty.one.oneField", oneField);
        assertConvertsNameTo("zero_field.oneProperty.one.two_field", h, "zeroField.oneProperty.one.twoField", twoField);
        assertConvertsNameTo("zero_field.twoProperty", h, "zeroField.twoProperty", twoProperty);
        assertConvertsNameTo("zero_field.twoProperty.one", h, "zeroField.twoProperty.one", intProperty);
        assertConvertsNameTo("zero_field.twoProperty.one.two", h, "zeroField.twoProperty.one.two", zeroProperty);
        assertConvertsNameTo("zero_field.twoProperty.one.two.key", h, "zeroField.twoProperty.one.two.key", "s");
        assertConvertsNameTo("zero_field.twoProperty.one.two.zero_field", h, "zeroField.twoProperty.one.two.zeroField", zeroField);
        assertConvertsNameTo("zero_field.twoProperty.one.two.one_field", h, "zeroField.twoProperty.one.two.oneField", oneField);
        assertConvertsNameTo("zero_field.twoProperty.one.two.two_field", h, "zeroField.twoProperty.one.two.twoField", twoField);
        assertConvertsNameTo("one_field", h, "oneField", oneField);
        assertConvertsNameTo("one_field.one", h, "oneField.one", zeroField);
        assertConvertsNameTo("one_field.one.zeroMap", h, "oneField.one.zeroMap", zeroProperty);
        assertConvertsNameTo("one_field.one.zeroMap.name", h, "oneField.one.zeroMap.name", "s");
        assertConvertsNameTo("one_field.one.zeroProperty", h, "oneField.one.zeroProperty", zeroProperty);
        assertConvertsNameTo("one_field.one.zeroProperty.key", h, "oneField.one.zeroProperty.key", "s");
        assertConvertsNameTo("one_field.one.zeroProperty.zero_field", h, "oneField.one.zeroProperty.zeroField", zeroField);
        assertConvertsNameTo("one_field.one.zeroProperty.one_field", h, "oneField.one.zeroProperty.oneField", oneField);
        assertConvertsNameTo("one_field.one.zeroProperty.two_field", h, "oneField.one.zeroProperty.twoField", twoField);
        assertConvertsNameTo("one_field.one.oneProperty", h, "oneField.one.oneProperty", oneProperty);
        assertConvertsNameTo("one_field.one.oneProperty.one", h, "oneField.one.oneProperty.one", zeroProperty);
        assertConvertsNameTo("one_field.one.oneProperty.one.key", h, "oneField.one.oneProperty.one.key", "s");
        assertConvertsNameTo("one_field.one.oneProperty.one.zero_field", h, "oneField.one.oneProperty.one.zeroField", zeroField);
        assertConvertsNameTo("one_field.one.oneProperty.one.one_field", h, "oneField.one.oneProperty.one.oneField", oneField);
        assertConvertsNameTo("one_field.one.oneProperty.one.two_field", h, "oneField.one.oneProperty.one.twoField", twoField);
        assertConvertsNameTo("one_field.one.twoProperty", h, "oneField.one.twoProperty", twoProperty);
        assertConvertsNameTo("one_field.one.twoProperty.one", h, "oneField.one.twoProperty.one", intProperty);
        assertConvertsNameTo("one_field.one.twoProperty.one.two", h, "oneField.one.twoProperty.one.two", zeroProperty);
        assertConvertsNameTo("one_field.one.twoProperty.one.two.key", h, "oneField.one.twoProperty.one.two.key", "s");
        assertConvertsNameTo("one_field.one.twoProperty.one.two.zero_field", h, "oneField.one.twoProperty.one.two.zeroField", zeroField);
        assertConvertsNameTo("one_field.one.twoProperty.one.two.one_field", h, "oneField.one.twoProperty.one.two.oneField", oneField);
        assertConvertsNameTo("one_field.one.twoProperty.one.two.two_field", h, "oneField.one.twoProperty.one.two.twoField", twoField);
        assertConvertsNameTo("two_field", h, "twoField", twoField);
        assertConvertsNameTo("two_field.one", h, "twoField.one", intField);
        assertConvertsNameTo("two_field.one.two", h, "twoField.one.two", zeroField);
        assertConvertsNameTo("two_field.one.two.zeroMap", h, "twoField.one.two.zeroMap", zeroProperty);
        assertConvertsNameTo("two_field.one.two.zeroMap.name", h, "twoField.one.two.zeroMap.name", "s");
        assertConvertsNameTo("two_field.one.two.zeroProperty", h, "twoField.one.two.zeroProperty", zeroProperty);
        assertConvertsNameTo("two_field.one.two.zeroProperty.key", h, "twoField.one.two.zeroProperty.key", "s");
        assertConvertsNameTo("two_field.one.two.zeroProperty.zero_field", h, "twoField.one.two.zeroProperty.zeroField", zeroField);
        assertConvertsNameTo("two_field.one.two.zeroProperty.one_field", h, "twoField.one.two.zeroProperty.oneField", oneField);
        assertConvertsNameTo("two_field.one.two.zeroProperty.two_field", h, "twoField.one.two.zeroProperty.twoField", twoField);
        assertConvertsNameTo("two_field.one.two.oneProperty", h, "twoField.one.two.oneProperty", oneProperty);
        assertConvertsNameTo("two_field.one.two.oneProperty.one", h, "twoField.one.two.oneProperty.one", zeroProperty);
        assertConvertsNameTo("two_field.one.two.oneProperty.one.key", h, "twoField.one.two.oneProperty.one.key", "s");
        assertConvertsNameTo("two_field.one.two.oneProperty.one.zero_field", h, "twoField.one.two.oneProperty.one.zeroField", zeroField);
        assertConvertsNameTo("two_field.one.two.oneProperty.one.one_field", h, "twoField.one.two.oneProperty.one.oneField", oneField);
        assertConvertsNameTo("two_field.one.two.oneProperty.one.two_field", h, "twoField.one.two.oneProperty.one.twoField", twoField);
        assertConvertsNameTo("two_field.one.two.twoProperty", h, "twoField.one.two.twoProperty", twoProperty);
        assertConvertsNameTo("two_field.one.two.twoProperty.one", h, "twoField.one.two.twoProperty.one", intProperty);
        assertConvertsNameTo("two_field.one.two.twoProperty.one.two", h, "twoField.one.two.twoProperty.one.two", zeroProperty);
        assertConvertsNameTo("two_field.one.two.twoProperty.one.two.key", h, "twoField.one.two.twoProperty.one.two.key", "s");
        assertConvertsNameTo("two_field.one.two.twoProperty.one.two.zero_field", h, "twoField.one.two.twoProperty.one.two.zeroField", zeroField);
        assertConvertsNameTo("two_field.one.two.twoProperty.one.two.one_field", h, "twoField.one.two.twoProperty.one.two.oneField", oneField);
        assertConvertsNameTo("two_field.one.two.twoProperty.one.two.two_field", h, "twoField.one.two.twoProperty.one.two.twoField", twoField);
    }

    private <E> void assertConvertsNameTo(String expectedName, Handle<E> handle, String fieldName, Object value) {
        Map<String, Object> values = Map.of(fieldName, value);
        Map<String, Object> data = handle.buildData(values);
        assertEquals(1, data.size());
        assertTrue(data.containsKey(expectedName));
    }

    @Test
    void doesNotConvertBooleanTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        boolean value = true;
        assertDoesNotConvertTo(h, "charValue", value);
        assertDoesNotConvertTo(h, "boxedCharValue", value);
        assertDoesNotConvertTo(h, "byteValue", value);
        assertDoesNotConvertTo(h, "boxedByteValue", value);
        assertDoesNotConvertTo(h, "shortValue", value);
        assertDoesNotConvertTo(h, "boxedShortValue", value);
        assertDoesNotConvertTo(h, "intValue", value);
        assertDoesNotConvertTo(h, "boxedIntValue", value);
        assertDoesNotConvertTo(h, "longValue", value);
        assertDoesNotConvertTo(h, "boxedLongValue", value);
        assertDoesNotConvertTo(h, "floatValue", value);
        assertDoesNotConvertTo(h, "boxedFloatValue", value);
        assertDoesNotConvertTo(h, "doubleValue", value);
        assertDoesNotConvertTo(h, "boxedDoubleValue", value);
        assertDoesNotConvertTo(h, "stringValue", value);
    }

    @Test
    void doesNotConvertBoxedBooleanTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        Boolean value = true;
        assertDoesNotConvertTo(h, "charValue", value);
        assertDoesNotConvertTo(h, "boxedCharValue", value);
        assertDoesNotConvertTo(h, "byteValue", value);
        assertDoesNotConvertTo(h, "boxedByteValue", value);
        assertDoesNotConvertTo(h, "shortValue", value);
        assertDoesNotConvertTo(h, "boxedShortValue", value);
        assertDoesNotConvertTo(h, "intValue", value);
        assertDoesNotConvertTo(h, "boxedIntValue", value);
        assertDoesNotConvertTo(h, "longValue", value);
        assertDoesNotConvertTo(h, "boxedLongValue", value);
        assertDoesNotConvertTo(h, "floatValue", value);
        assertDoesNotConvertTo(h, "boxedFloatValue", value);
        assertDoesNotConvertTo(h, "doubleValue", value);
        assertDoesNotConvertTo(h, "boxedDoubleValue", value);
        assertDoesNotConvertTo(h, "stringValue", value);
    }

    @Test
    void doesNotConvertCharTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        char value = 'c';
        assertDoesNotConvertTo(h, "booleanValue", value);
        assertDoesNotConvertTo(h, "boxedBooleanValue", value);
        assertDoesNotConvertTo(h, "byteValue", value);
        assertDoesNotConvertTo(h, "boxedByteValue", value);
        assertDoesNotConvertTo(h, "shortValue", value);
        assertDoesNotConvertTo(h, "boxedShortValue", value);
        assertDoesNotConvertTo(h, "intValue", value);
        assertDoesNotConvertTo(h, "boxedIntValue", value);
        assertDoesNotConvertTo(h, "longValue", value);
        assertDoesNotConvertTo(h, "boxedLongValue", value);
        assertDoesNotConvertTo(h, "floatValue", value);
        assertDoesNotConvertTo(h, "boxedFloatValue", value);
        assertDoesNotConvertTo(h, "doubleValue", value);
        assertDoesNotConvertTo(h, "boxedDoubleValue", value);
    }

    @Test
    void doesNotConvertBoxedCharTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        Character value = 'c';
        assertDoesNotConvertTo(h, "booleanValue", value);
        assertDoesNotConvertTo(h, "boxedBooleanValue", value);
        assertDoesNotConvertTo(h, "byteValue", value);
        assertDoesNotConvertTo(h, "boxedByteValue", value);
        assertDoesNotConvertTo(h, "shortValue", value);
        assertDoesNotConvertTo(h, "boxedShortValue", value);
        assertDoesNotConvertTo(h, "intValue", value);
        assertDoesNotConvertTo(h, "boxedIntValue", value);
        assertDoesNotConvertTo(h, "longValue", value);
        assertDoesNotConvertTo(h, "boxedLongValue", value);
        assertDoesNotConvertTo(h, "floatValue", value);
        assertDoesNotConvertTo(h, "boxedFloatValue", value);
        assertDoesNotConvertTo(h, "doubleValue", value);
        assertDoesNotConvertTo(h, "boxedDoubleValue", value);
    }

    @Test
    void doesNotConvertByteTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        byte value = 1;
        assertDoesNotConvertTo(h, "booleanValue", value);
        assertDoesNotConvertTo(h, "boxedBooleanValue", value);
        assertDoesNotConvertTo(h, "charValue", value);
        assertDoesNotConvertTo(h, "boxedCharValue", value);
        assertDoesNotConvertTo(h, "stringValue", value);
    }

    @Test
    void doesNotConvertBoxedByteTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        Byte value = 1;
        assertDoesNotConvertTo(h, "booleanValue", value);
        assertDoesNotConvertTo(h, "boxedBooleanValue", value);
        assertDoesNotConvertTo(h, "charValue", value);
        assertDoesNotConvertTo(h, "boxedCharValue", value);
        assertDoesNotConvertTo(h, "stringValue", value);
    }

    @Test
    void doesNotConvertShortTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        short value = 2;
        assertDoesNotConvertTo(h, "booleanValue", value);
        assertDoesNotConvertTo(h, "boxedBooleanValue", value);
        assertDoesNotConvertTo(h, "charValue", value);
        assertDoesNotConvertTo(h, "boxedCharValue", value);
        assertDoesNotConvertTo(h, "byteValue", value);
        assertDoesNotConvertTo(h, "boxedByteValue", value);
        assertDoesNotConvertTo(h, "stringValue", value);
    }

    @Test
    void doesNotConvertBoxedShortTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        Short value = 2;
        assertDoesNotConvertTo(h, "booleanValue", value);
        assertDoesNotConvertTo(h, "boxedBooleanValue", value);
        assertDoesNotConvertTo(h, "charValue", value);
        assertDoesNotConvertTo(h, "boxedCharValue", value);
        assertDoesNotConvertTo(h, "byteValue", value);
        assertDoesNotConvertTo(h, "boxedByteValue", value);
        assertDoesNotConvertTo(h, "stringValue", value);
    }

    @Test
    void doesNotConvertIntTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        int value = 3;
        assertDoesNotConvertTo(h, "booleanValue", value);
        assertDoesNotConvertTo(h, "boxedBooleanValue", value);
        assertDoesNotConvertTo(h, "charValue", value);
        assertDoesNotConvertTo(h, "boxedCharValue", value);
        assertDoesNotConvertTo(h, "byteValue", value);
        assertDoesNotConvertTo(h, "boxedByteValue", value);
        assertDoesNotConvertTo(h, "shortValue", value);
        assertDoesNotConvertTo(h, "boxedShortValue", value);
        assertDoesNotConvertTo(h, "stringValue", value);
    }

    @Test
    void doesNotConvertBoxedIntTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        Integer value = 3;
        assertDoesNotConvertTo(h, "booleanValue", value);
        assertDoesNotConvertTo(h, "boxedBooleanValue", value);
        assertDoesNotConvertTo(h, "charValue", value);
        assertDoesNotConvertTo(h, "boxedCharValue", value);
        assertDoesNotConvertTo(h, "byteValue", value);
        assertDoesNotConvertTo(h, "boxedByteValue", value);
        assertDoesNotConvertTo(h, "shortValue", value);
        assertDoesNotConvertTo(h, "boxedShortValue", value);
        assertDoesNotConvertTo(h, "stringValue", value);
    }

    @Test
    void doesNotConvertLongTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        long value = 4;
        assertDoesNotConvertTo(h, "booleanValue", value);
        assertDoesNotConvertTo(h, "boxedBooleanValue", value);
        assertDoesNotConvertTo(h, "charValue", value);
        assertDoesNotConvertTo(h, "boxedCharValue", value);
        assertDoesNotConvertTo(h, "byteValue", value);
        assertDoesNotConvertTo(h, "boxedByteValue", value);
        assertDoesNotConvertTo(h, "shortValue", value);
        assertDoesNotConvertTo(h, "boxedShortValue", value);
        assertDoesNotConvertTo(h, "intValue", value);
        assertDoesNotConvertTo(h, "boxedIntValue", value);
        assertDoesNotConvertTo(h, "floatValue", value);
        assertDoesNotConvertTo(h, "boxedFloatValue", value);
        assertDoesNotConvertTo(h, "stringValue", value);
    }

    @Test
    void doesNotConvertBoxedLongTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        Long value = 4L;
        assertDoesNotConvertTo(h, "booleanValue", value);
        assertDoesNotConvertTo(h, "boxedBooleanValue", value);
        assertDoesNotConvertTo(h, "charValue", value);
        assertDoesNotConvertTo(h, "boxedCharValue", value);
        assertDoesNotConvertTo(h, "byteValue", value);
        assertDoesNotConvertTo(h, "boxedByteValue", value);
        assertDoesNotConvertTo(h, "shortValue", value);
        assertDoesNotConvertTo(h, "boxedShortValue", value);
        assertDoesNotConvertTo(h, "intValue", value);
        assertDoesNotConvertTo(h, "boxedIntValue", value);
        assertDoesNotConvertTo(h, "floatValue", value);
        assertDoesNotConvertTo(h, "boxedFloatValue", value);
        assertDoesNotConvertTo(h, "stringValue", value);
    }

    @Test
    void doesNotConvertFloatTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        float value = 5.5F;
        assertDoesNotConvertTo(h, "booleanValue", value);
        assertDoesNotConvertTo(h, "boxedBooleanValue", value);
        assertDoesNotConvertTo(h, "charValue", value);
        assertDoesNotConvertTo(h, "boxedCharValue", value);
        assertDoesNotConvertTo(h, "byteValue", value);
        assertDoesNotConvertTo(h, "boxedByteValue", value);
        assertDoesNotConvertTo(h, "shortValue", value);
        assertDoesNotConvertTo(h, "boxedShortValue", value);
        assertDoesNotConvertTo(h, "stringValue", value);
    }

    @Test
    void doesNotConvertBoxedFloatTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        Float value = 5.5F;
        assertDoesNotConvertTo(h, "booleanValue", value);
        assertDoesNotConvertTo(h, "boxedBooleanValue", value);
        assertDoesNotConvertTo(h, "charValue", value);
        assertDoesNotConvertTo(h, "boxedCharValue", value);
        assertDoesNotConvertTo(h, "byteValue", value);
        assertDoesNotConvertTo(h, "boxedByteValue", value);
        assertDoesNotConvertTo(h, "shortValue", value);
        assertDoesNotConvertTo(h, "boxedShortValue", value);
        assertDoesNotConvertTo(h, "stringValue", value);
    }

    @Test
    void doesNotConvertDoubleTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        double value = 6.6;
        assertDoesNotConvertTo(h, "booleanValue", value);
        assertDoesNotConvertTo(h, "boxedBooleanValue", value);
        assertDoesNotConvertTo(h, "charValue", value);
        assertDoesNotConvertTo(h, "boxedCharValue", value);
        assertDoesNotConvertTo(h, "byteValue", value);
        assertDoesNotConvertTo(h, "boxedByteValue", value);
        assertDoesNotConvertTo(h, "shortValue", value);
        assertDoesNotConvertTo(h, "boxedShortValue", value);
        assertDoesNotConvertTo(h, "intValue", value);
        assertDoesNotConvertTo(h, "boxedIntValue", value);
        assertDoesNotConvertTo(h, "floatValue", value);
        assertDoesNotConvertTo(h, "boxedFloatValue", value);
        assertDoesNotConvertTo(h, "stringValue", value);
    }

    @Test
    void doesNotConvertBoxedDoubleTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        Double value = 6.6;
        assertDoesNotConvertTo(h, "booleanValue", value);
        assertDoesNotConvertTo(h, "boxedBooleanValue", value);
        assertDoesNotConvertTo(h, "charValue", value);
        assertDoesNotConvertTo(h, "boxedCharValue", value);
        assertDoesNotConvertTo(h, "byteValue", value);
        assertDoesNotConvertTo(h, "boxedByteValue", value);
        assertDoesNotConvertTo(h, "shortValue", value);
        assertDoesNotConvertTo(h, "boxedShortValue", value);
        assertDoesNotConvertTo(h, "intValue", value);
        assertDoesNotConvertTo(h, "boxedIntValue", value);
        assertDoesNotConvertTo(h, "floatValue", value);
        assertDoesNotConvertTo(h, "boxedFloatValue", value);
        assertDoesNotConvertTo(h, "stringValue", value);
    }

    @Test
    void doesNotConvertStringTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        String value = "s";
        assertDoesNotConvertTo(h, "booleanValue", value);
        assertDoesNotConvertTo(h, "boxedBooleanValue", value);
        assertDoesNotConvertTo(h, "charValue", value);
        assertDoesNotConvertTo(h, "boxedCharValue", value);
        assertDoesNotConvertTo(h, "byteValue", value);
        assertDoesNotConvertTo(h, "boxedByteValue", value);
        assertDoesNotConvertTo(h, "shortValue", value);
        assertDoesNotConvertTo(h, "boxedShortValue", value);
        assertDoesNotConvertTo(h, "intValue", value);
        assertDoesNotConvertTo(h, "boxedIntValue", value);
        assertDoesNotConvertTo(h, "longValue", value);
        assertDoesNotConvertTo(h, "boxedLongValue", value);
        assertDoesNotConvertTo(h, "floatValue", value);
        assertDoesNotConvertTo(h, "boxedFloatValue", value);
        assertDoesNotConvertTo(h, "doubleValue", value);
        assertDoesNotConvertTo(h, "boxedDoubleValue", value);
    }

    @Test
    void doesNotConvertPointTo() {
        Handle<FirestoreFields> h = newHandle(FirestoreFields.class);
        GeoPoint value = mock(GeoPoint.class);
        assertDoesNotConvertTo(h, "reference", value);
        assertDoesNotConvertTo(h, "timestamp", value);
        assertDoesNotConvertTo(h, "instant", value);
        assertDoesNotConvertTo(h, "blob", value);
        assertDoesNotConvertTo(h, "stream", value);
    }

    @Test
    void doesNotConvertReferenceTo() {
        Handle<FirestoreFields> h = newHandle(FirestoreFields.class);
        DocumentReference value = mock(DocumentReference.class);
        assertDoesNotConvertTo(h, "point", value);
        assertDoesNotConvertTo(h, "timestamp", value);
        assertDoesNotConvertTo(h, "instant", value);
        assertDoesNotConvertTo(h, "blob", value);
        assertDoesNotConvertTo(h, "stream", value);
    }

    @Test
    void doesNotConvertTimestampTo() {
        Handle<FirestoreFields> h = newHandle(FirestoreFields.class);
        Timestamp value = mock(Timestamp.class);
        assertDoesNotConvertTo(h, "point", value);
        assertDoesNotConvertTo(h, "reference", value);
        assertDoesNotConvertTo(h, "blob", value);
        assertDoesNotConvertTo(h, "stream", value);
    }

    @Test
    void doesNotConvertInstantTo() {
        Handle<FirestoreFields> h = newHandle(FirestoreFields.class);
        Instant value = Instant.EPOCH;
        assertDoesNotConvertTo(h, "point", value);
        assertDoesNotConvertTo(h, "reference", value);
        assertDoesNotConvertTo(h, "blob", value);
        assertDoesNotConvertTo(h, "stream", value);
    }

    @Test
    void doesNotConvertBlobTo() {
        Handle<FirestoreFields> h = newHandle(FirestoreFields.class);
        Blob value = mock(Blob.class);
        assertDoesNotConvertTo(h, "point", value);
        assertDoesNotConvertTo(h, "reference", value);
        assertDoesNotConvertTo(h, "timestamp", value);
        assertDoesNotConvertTo(h, "instant", value);
    }

    @Test
    void doesNotConvertStreamTo() {
        Handle<FirestoreFields> h = newHandle(FirestoreFields.class);
        InputStream value = InputStream.nullInputStream();
        assertDoesNotConvertTo(h, "point", value);
        assertDoesNotConvertTo(h, "reference", value);
        assertDoesNotConvertTo(h, "timestamp", value);
        assertDoesNotConvertTo(h, "instant", value);
        assertDoesNotConvertStreamTo(h, "blob", value);
        assertDoesNotConvertStreamTo(h, "stream", value);
    }

    private <E> void assertDoesNotConvertStreamTo(Handle<E> handle, String fieldName, InputStream value) {
        Map<String, Object> values = Map.of(fieldName, value);
        Exception exception = assertThrows(UncheckedIOException.class, () -> {
            try (MockedStatic<ByteString> byteStringStatic = mockStatic(ByteString.class)) {
                byteStringStatic.when(() -> ByteString.readFrom(value)).thenThrow(IOException.class);
                handle.buildData(values);
            }
        });
        assertInstanceOf(IOException.class, exception.getCause());
    }

    @Test
    void doesNotConvertStringArrayTo() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        String[] value = new String[]{"s"};
        assertDoesNotConvertTo(h, "stringStringMap", value);
        assertDoesNotConvertTo(h, "stringObjectMap", value);
        assertDoesNotConvertTo(h, "listStringMap", value);
        assertDoesNotConvertTo(h, "objectStringMap", value);
        assertDoesNotConvertTo(h, "objectObjectMap", value);
        assertDoesNotConvertTo(h, "rawMap", value);
        assertDoesNotConvertTo(h, "custom", value);
    }

    @Test
    void doesNotConvertObjectArrayTo() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        Object[] value = new Object[]{new Object()};
        assertDoesNotConvertTo(h, "stringArray", value);
        assertDoesNotConvertTo(h, "stringList", value);
        assertDoesNotConvertTo(h, "stringStringMap", value);
        assertDoesNotConvertTo(h, "stringObjectMap", value);
        assertDoesNotConvertTo(h, "listStringMap", value);
        assertDoesNotConvertTo(h, "objectStringMap", value);
        assertDoesNotConvertTo(h, "objectObjectMap", value);
        assertDoesNotConvertTo(h, "rawMap", value);
        assertDoesNotConvertTo(h, "custom", value);
    }

    @Test
    void doesNotConvertStringListTo() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        List<String> value = List.of("s");
        assertDoesNotConvertTo(h, "stringStringMap", value);
        assertDoesNotConvertTo(h, "stringObjectMap", value);
        assertDoesNotConvertTo(h, "listStringMap", value);
        assertDoesNotConvertTo(h, "objectStringMap", value);
        assertDoesNotConvertTo(h, "objectObjectMap", value);
        assertDoesNotConvertTo(h, "rawMap", value);
        assertDoesNotConvertTo(h, "custom", value);
    }

    @Test
    void doesNotConvertObjectListTo() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        List<Object> value = List.of(new Object());
        assertDoesNotConvertTo(h, "stringArray", value);
        assertDoesNotConvertTo(h, "stringList", value);
        assertDoesNotConvertTo(h, "stringStringMap", value);
        assertDoesNotConvertTo(h, "stringObjectMap", value);
        assertDoesNotConvertTo(h, "listStringMap", value);
        assertDoesNotConvertTo(h, "objectStringMap", value);
        assertDoesNotConvertTo(h, "objectObjectMap", value);
        assertDoesNotConvertTo(h, "rawMap", value);
        assertDoesNotConvertTo(h, "custom", value);
    }

    @Test
    void doesNotConvertRawListTo() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        @SuppressWarnings("rawtypes")
        List value = List.of(new Object());
        assertDoesNotConvertTo(h, "stringArray", value);
        assertDoesNotConvertTo(h, "stringList", value);
        assertDoesNotConvertTo(h, "stringStringMap", value);
        assertDoesNotConvertTo(h, "stringObjectMap", value);
        assertDoesNotConvertTo(h, "listStringMap", value);
        assertDoesNotConvertTo(h, "objectStringMap", value);
        assertDoesNotConvertTo(h, "objectObjectMap", value);
        assertDoesNotConvertTo(h, "rawMap", value);
        assertDoesNotConvertTo(h, "custom", value);
    }

    @Test
    void doesNotConvertStringStringMapTo() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        Map<String, String> value = Map.of("k", "v");
        assertDoesNotConvertTo(h, "stringArray", value);
        assertDoesNotConvertTo(h, "objectArray", value);
        assertDoesNotConvertTo(h, "stringList", value);
        assertDoesNotConvertTo(h, "objectList", value);
        assertDoesNotConvertTo(h, "rawList", value);
        assertDoesNotConvertIllegalTo(h, "listStringMap", value);
        assertDoesNotConvertIllegalTo(h, "objectStringMap", value);
        assertDoesNotConvertIllegalTo(h, "objectObjectMap", value);
        assertDoesNotConvertTo(h, "rawMap", value);
        assertDoesNotConvertTo(h, "custom", value);
        assertDoesNotConvertTo(h, "object", value);
    }

    @Test
    void doesNotConvertStringObjectMapTo() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        Map<String, Object> value = Map.of("k", new Object());
        assertDoesNotConvertTo(h, "stringArray", value);
        assertDoesNotConvertTo(h, "objectArray", value);
        assertDoesNotConvertTo(h, "stringList", value);
        assertDoesNotConvertTo(h, "objectList", value);
        assertDoesNotConvertTo(h, "rawList", value);
        assertDoesNotConvertTo(h, "stringStringMap", value);
        assertDoesNotConvertIllegalTo(h, "listStringMap", value);
        assertDoesNotConvertIllegalTo(h, "objectStringMap", value);
        assertDoesNotConvertIllegalTo(h, "objectObjectMap", value);
        assertDoesNotConvertTo(h, "rawMap", value);
        assertDoesNotConvertTo(h, "custom", value);
        assertDoesNotConvertTo(h, "object", value);
    }

    @Test
    void doesNotConvertObjectStringMapTo() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        Map<Object, String> value = Map.of(new Object(), "v");
        assertDoesNotConvertTo(h, "stringArray", value);
        assertDoesNotConvertTo(h, "objectArray", value);
        assertDoesNotConvertTo(h, "stringList", value);
        assertDoesNotConvertTo(h, "objectList", value);
        assertDoesNotConvertTo(h, "rawList", value);
        assertDoesNotConvertIllegalTo(h, "stringStringMap", value);
        assertDoesNotConvertIllegalTo(h, "stringObjectMap", value);
        assertDoesNotConvertIllegalTo(h, "listStringMap", value);
        assertDoesNotConvertIllegalTo(h, "objectStringMap", value);
        assertDoesNotConvertIllegalTo(h, "objectObjectMap", value);
        assertDoesNotConvertTo(h, "rawMap", value);
        assertDoesNotConvertTo(h, "custom", value);
        assertDoesNotConvertTo(h, "object", value);
    }

    @Test
    void doesNotConvertObjectObjectMapTo() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        Map<Object, Object> value = Map.of(new Object(), new Object());
        assertDoesNotConvertTo(h, "stringArray", value);
        assertDoesNotConvertTo(h, "objectArray", value);
        assertDoesNotConvertTo(h, "stringList", value);
        assertDoesNotConvertTo(h, "objectList", value);
        assertDoesNotConvertTo(h, "rawList", value);
        assertDoesNotConvertIllegalTo(h, "stringStringMap", value);
        assertDoesNotConvertIllegalTo(h, "stringObjectMap", value);
        assertDoesNotConvertIllegalTo(h, "listStringMap", value);
        assertDoesNotConvertIllegalTo(h, "objectStringMap", value);
        assertDoesNotConvertIllegalTo(h, "objectObjectMap", value);
        assertDoesNotConvertTo(h, "rawMap", value);
        assertDoesNotConvertTo(h, "custom", value);
        assertDoesNotConvertTo(h, "object", value);
    }

    @Test
    void doesNotConvertRawMapTo() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        @SuppressWarnings("rawtypes")
        Map value = Map.of(new Object(), new Object());
        assertDoesNotConvertTo(h, "stringArray", value);
        assertDoesNotConvertTo(h, "objectArray", value);
        assertDoesNotConvertTo(h, "stringList", value);
        assertDoesNotConvertTo(h, "objectList", value);
        assertDoesNotConvertTo(h, "rawList", value);
        assertDoesNotConvertIllegalTo(h, "stringStringMap", value);
        assertDoesNotConvertIllegalTo(h, "stringObjectMap", value);
        assertDoesNotConvertIllegalTo(h, "listStringMap", value);
        assertDoesNotConvertIllegalTo(h, "objectStringMap", value);
        assertDoesNotConvertIllegalTo(h, "objectObjectMap", value);
        assertDoesNotConvertTo(h, "rawMap", value);
        assertDoesNotConvertTo(h, "custom", value);
        assertDoesNotConvertTo(h, "object", value);
    }

    @Test
    void doesNotConvertCustomTo() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        Custom value = new Custom();
        assertDoesNotConvertTo(h, "stringArray", value);
        assertDoesNotConvertTo(h, "objectArray", value);
        assertDoesNotConvertTo(h, "stringList", value);
        assertDoesNotConvertTo(h, "objectList", value);
        assertDoesNotConvertTo(h, "rawList", value);
        assertDoesNotConvertTo(h, "stringStringMap", value);
        assertDoesNotConvertTo(h, "stringObjectMap", value);
        assertDoesNotConvertTo(h, "listStringMap", value);
        assertDoesNotConvertTo(h, "objectStringMap", value);
        assertDoesNotConvertTo(h, "objectObjectMap", value);
        assertDoesNotConvertTo(h, "rawMap", value);
        assertDoesNotConvertTo(h, "object", value);
    }

    private <E> void assertDoesNotConvertTo(Handle<E> handle, String fieldName, Object value) {
        assertDoesNotConvertTo(ConversionException.class, handle, fieldName, value);
    }

    @Test
    void doesNotConvertArrayArrayTo() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        String[][] value = new String[][]{new String[]{"s"}};
        assertDoesNotConvertIllegalTo(h, "object", value);
    }

    @Test
    void doesNotConvertListArrayTo() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        @SuppressWarnings("rawtypes")
        List[] value = new List[]{List.of("s")};
        assertDoesNotConvertIllegalTo(h, "object", value);
    }

    @Test
    void doesNotConvertArrayListTo() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        List<String[]> value = new ArrayList<>();
        value.add(new String[]{"s"});
        assertDoesNotConvertIllegalTo(h, "object", value);
    }

    @Test
    void doesNotConvertListListTo() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        List<List<String>> value = List.of(List.of("s"));
        assertDoesNotConvertIllegalTo(h, "object", value);
    }

    @Test
    void doesNotConvertZeroCycleTo() {
        Recursive parent = new Recursive();
        parent.setValue(parent);
        assertDoesNotConvertCycleTo(parent);
    }

    @Test
    void doesNotConvertOneCycleTo() {
        Recursive parent = new Recursive();
        Recursive child = new Recursive();
        parent.setValue(child);
        child.setValue(parent);
        assertDoesNotConvertCycleTo(parent);
    }

    @Test
    void doesNotConvertTwoCycleTo() {
        Recursive parent = new Recursive();
        Recursive child = new Recursive();
        Recursive grandChild = new Recursive();
        parent.setValue(child);
        child.setValue(grandChild);
        grandChild.setValue(parent);
        assertDoesNotConvertCycleTo(parent);
    }

    private void assertDoesNotConvertCycleTo(Recursive value) {
        Handle<Recursive> h = newHandle(Recursive.class);
        assertDoesNotConvertIllegalTo(h, "value", value);
        Recursive[] array = new Recursive[]{value};
        assertDoesNotConvertIllegalTo(h, "array", array);
        List<Recursive[]> list = new ArrayList<>();
        list.add(array);
        assertDoesNotConvertIllegalTo(h, "list", list);
        Map<String, List<Recursive[]>> map = Map.of("key", list);
        assertDoesNotConvertIllegalTo(h, "map", map);
    }

    @Test
    void doesNotConvertSimpleNameTo() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        String value = "s";
        assertDoesNotConvertIllegalTo(h, "booleanValue.name", value);
        assertDoesNotConvertIllegalTo(h, "boxedBooleanValue.name", value);
        assertDoesNotConvertIllegalTo(h, "charValue.name", value);
        assertDoesNotConvertIllegalTo(h, "boxedCharValue.name", value);
        assertDoesNotConvertIllegalTo(h, "byteValue.name", value);
        assertDoesNotConvertIllegalTo(h, "boxedByteValue.name", value);
        assertDoesNotConvertIllegalTo(h, "shortValue.name", value);
        assertDoesNotConvertIllegalTo(h, "boxedShortValue.name", value);
        assertDoesNotConvertIllegalTo(h, "intValue.name", value);
        assertDoesNotConvertIllegalTo(h, "boxedIntValue.name", value);
        assertDoesNotConvertIllegalTo(h, "longValue.name", value);
        assertDoesNotConvertIllegalTo(h, "boxedLongValue.name", value);
        assertDoesNotConvertIllegalTo(h, "floatValue.name", value);
        assertDoesNotConvertIllegalTo(h, "boxedFloatValue.name", value);
        assertDoesNotConvertIllegalTo(h, "doubleValue.name", value);
        assertDoesNotConvertIllegalTo(h, "boxedDoubleValue.name", value);
        assertDoesNotConvertIllegalTo(h, "stringValue.name", value);
    }

    @Test
    void doesNotConvertFirestoreNameTo() {
        Handle<FirestoreFields> h = newHandle(FirestoreFields.class);
        String value = "s";
        assertDoesNotConvertIllegalTo(h, "point.name", value);
        assertDoesNotConvertIllegalTo(h, "reference.name", value);
        assertDoesNotConvertIllegalTo(h, "timestamp.name", value);
        assertDoesNotConvertIllegalTo(h, "instant.name", value);
        assertDoesNotConvertIllegalTo(h, "blob.name", value);
        assertDoesNotConvertIllegalTo(h, "stream.name", value);
    }

    @Test
    void doesNotConvertCompositeNameTo() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        String value = "s";
        assertDoesNotConvertIllegalTo(h, "stringArray.name", value);
        assertDoesNotConvertIllegalTo(h, "objectArray.name", value);
        assertDoesNotConvertIllegalTo(h, "stringList.name", value);
        assertDoesNotConvertIllegalTo(h, "objectList.name", value);
        assertDoesNotConvertIllegalTo(h, "rawList.name", value);
        assertDoesNotConvertIllegalTo(h, "listStringMap.name", value);
        assertDoesNotConvertIllegalTo(h, "objectStringMap.name", value);
        assertDoesNotConvertIllegalTo(h, "objectObjectMap.name", value);
        assertDoesNotConvertIllegalTo(h, "rawMap.name", value);
        assertDoesNotConvertIllegalTo(h, "custom.name", value);
    }

    @Test
    void doesNotConvertConvertableNameTo() {
        Handle<ConvertableFields> h = newHandle(ConvertableFields.class);
        assertDoesNotConvertIllegalTo(h, "email.login", "s");
        assertDoesNotConvertIllegalTo(h, "email.domain", "s");
        assertDoesNotConvertIllegalTo(h, "address.street", "s");
        assertDoesNotConvertIllegalTo(h, "address.number", 3);
        assertDoesNotConvertIllegalTo(h, "address.city", "s");
        assertDoesNotConvertIllegalTo(h, "booleanWrapper.value", true);
        assertDoesNotConvertIllegalTo(h, "byteWrapper.value", (byte) 1);
    }

    @Test
    void doesNotConvertFieldPathsTo() {
        Handle<FieldNames> h = newHandle(FieldNames.class);
        assertDoesNotConvertIllegalTo(h, "file", "s");
        assertDoesNotConvertIllegalTo(h, "zeroProperty.zeroField.file", "s");
        assertDoesNotConvertIllegalTo(h, "zeroProperty.oneField.one.file", "s");
        assertDoesNotConvertIllegalTo(h, "zeroProperty.twoField.one.two.file", "s");
        assertDoesNotConvertIllegalTo(h, "oneProperty.one.zeroField.file", "s");
        assertDoesNotConvertIllegalTo(h, "oneProperty.one.oneField.one.file", "s");
        assertDoesNotConvertIllegalTo(h, "oneProperty.one.twoField.one.two.file", "s");
        assertDoesNotConvertIllegalTo(h, "twoProperty.one.two.zeroField.file", "s");
        assertDoesNotConvertIllegalTo(h, "twoProperty.one.two.oneField.one.file", "s");
        assertDoesNotConvertIllegalTo(h, "twoProperty.one.two.twoField.one.two.file", "s");
    }

    @Test
    void doesNotConvertPropertyPathsTo() {
        Handle<PropertyNames> h = newHandle(PropertyNames.class);
        assertDoesNotConvertIllegalTo(h, "key", "s");
        assertDoesNotConvertIllegalTo(h, "zero_field.file", "s");
        assertDoesNotConvertIllegalTo(h, "one_field.one.file", "s");
        assertDoesNotConvertIllegalTo(h, "two_field.one.two.file", "s");
    }

    private <E> void assertDoesNotConvertIllegalTo(Handle<E> handle, String fieldName, Object value) {
        assertDoesNotConvertTo(IllegalArgumentException.class, handle, fieldName, value);
    }

    private <E> void assertDoesNotConvertTo(Class<? extends RuntimeException> exceptionType, Handle<E> handle, String fieldName, Object value) {
        Map<String, Object> values = Map.of(fieldName, value);
        assertThrows(exceptionType, () -> handle.buildData(values));
    }

    @Test
    void convertsNullFrom() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        assertConvertsNullFrom(h, "booleanValue");
        assertConvertsNullFrom(h, "boxedBooleanValue");
        assertConvertsNullFrom(h, "charValue");
        assertConvertsNullFrom(h, "boxedCharValue");
        assertConvertsNullFrom(h, "byteValue");
        assertConvertsNullFrom(h, "boxedByteValue");
        assertConvertsNullFrom(h, "shortValue");
        assertConvertsNullFrom(h, "boxedShortValue");
        assertConvertsNullFrom(h, "intValue");
        assertConvertsNullFrom(h, "boxedIntValue");
        assertConvertsNullFrom(h, "longValue");
        assertConvertsNullFrom(h, "boxedLongValue");
        assertConvertsNullFrom(h, "floatValue");
        assertConvertsNullFrom(h, "boxedFloatValue");
        assertConvertsNullFrom(h, "doubleValue");
        assertConvertsNullFrom(h, "boxedDoubleValue");
        assertConvertsNullFrom(h, "stringValue");
        assertConvertsNullFrom(h, "objectValue");
    }

    private <E> void assertConvertsNullFrom(Handle<E> handle, String fieldName) {
        Map<String, Object> data = new HashMap<>();
        data.put(fieldName, null);
        Map<String, Object> values = handle.buildValues(data);
        assertEquals(1, values.size());
        assertTrue(values.containsKey(fieldName));
        assertNull(values.get(fieldName));
    }

    @Test
    void convertsObjectFrom() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        assertConvertsObjectFrom(h, true);
        assertConvertsObjectFrom(h, 4L);
        assertConvertsObjectFrom(h, 6.6);
        assertConvertsObjectFrom(h, "s");
    }

    private <E> void assertConvertsObjectFrom(Handle<E> handle, Object value) {
        assertConvertsSameFrom(handle, "objectValue", value);
    }

    @Test
    void convertsBooleanFrom() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        boolean value = true;
        assertConvertsSameFrom(h, "booleanValue", value);
        assertConvertsSameFrom(h, "boxedBooleanValue", value);
    }

    @Test
    void convertsEmptyStringFrom() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        String value = "";
        char expected = '\0';
        assertConvertsFrom(expected, h, "charValue", value);
        assertConvertsFrom(expected, h, "boxedCharValue", value);
    }

    @Test
    void convertsLargeStringFrom() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        String value = "cc";
        char expected = 'c';
        assertConvertsFrom(expected, h, "charValue", value);
        assertConvertsFrom(expected, h, "boxedCharValue", value);
    }

    @Test
    void convertsLongFrom() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        long value = 4L;
        assertConvertsFrom((byte) 4, h, "byteValue", value);
        assertConvertsFrom((byte) 4, h, "boxedByteValue", value);
        assertConvertsFrom((short) 4, h, "shortValue", value);
        assertConvertsFrom((short) 4, h, "boxedShortValue", value);
        assertConvertsFrom(4, h, "intValue", value);
        assertConvertsFrom(4, h, "boxedIntValue", value);
        assertConvertsFrom(4L, h, "longValue", value);
        assertConvertsFrom(4L, h, "boxedLongValue", value);
        assertConvertsFrom(4.0F, h, "floatValue", value);
        assertConvertsFrom(4.0F, h, "boxedFloatValue", value);
        assertConvertsFrom(4.0, h, "doubleValue", value);
        assertConvertsFrom(4.0, h, "boxedDoubleValue", value);
    }

    @Test
    void convertsDoubleFrom() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        double value = 6.6;
        assertConvertsFrom((byte) 6, h, "byteValue", value);
        assertConvertsFrom((byte) 6, h, "boxedByteValue", value);
        assertConvertsFrom((short) 6, h, "shortValue", value);
        assertConvertsFrom((short) 6, h, "boxedShortValue", value);
        assertConvertsFrom(6, h, "intValue", value);
        assertConvertsFrom(6, h, "boxedIntValue", value);
        assertConvertsFrom(6L, h, "longValue", value);
        assertConvertsFrom(6L, h, "boxedLongValue", value);
        assertConvertsFrom(6.6F, h, "floatValue", value);
        assertConvertsFrom(6.6F, h, "boxedFloatValue", value);
        assertConvertsFrom(6.6, h, "doubleValue", value);
        assertConvertsFrom(6.6, h, "boxedDoubleValue", value);
    }

    @Test
    void convertsStringFrom() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        assertConvertsSameFrom(h, "stringValue", "s");
    }

    @Test
    void convertsPointFrom() {
        Handle<FirestoreFields> h = newHandle(FirestoreFields.class);
        assertConvertsSameFrom(h, "point", mock(GeoPoint.class));
    }

    @Test
    void convertsReferenceFrom() {
        Handle<FirestoreFields> h = newHandle(FirestoreFields.class);
        assertConvertsSameFrom(h, "reference", mock(DocumentReference.class));
    }

    @Test
    void convertsTimestampFrom() {
        Handle<FirestoreFields> h = newHandle(FirestoreFields.class);
        assertConvertsSameFrom(h, "timestamp", mock(Timestamp.class));
    }

    @Test
    void convertsInstantFrom() {
        Handle<FirestoreFields> h = newHandle(FirestoreFields.class);
        Timestamp timestamp = mock(Timestamp.class);
        when(timestamp.getSeconds()).thenReturn(0L);
        when(timestamp.getNanos()).thenReturn(0);
        assertConvertsFrom(Instant.EPOCH, h, "instant", timestamp);
    }

    @Test
    void convertsBlobFrom() {
        Handle<FirestoreFields> h = newHandle(FirestoreFields.class);
        assertConvertsSameFrom(h, "blob", mock(Blob.class));
    }

    @Test
    void convertsStreamFrom() {
        Handle<FirestoreFields> h = newHandle(FirestoreFields.class);
        InputStream stream = InputStream.nullInputStream();
        ByteString byteString = mock(ByteString.class);
        when(byteString.newInput()).thenReturn(stream);
        Blob blob = mock(Blob.class);
        when(blob.toByteString()).thenReturn(byteString);
        assertConvertsFrom(stream, h, "stream", blob);
    }

    @Test
    void convertsStringArrayFrom() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        List<String> value = List.of("s");
        String[] actual = assertInstanceOf(String[].class, convertFrom(h, "stringArray", value));
        assertArrayEquals(new String[]{"s"}, actual);
    }

    @Test
    void convertsObjectArrayFrom() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        List<String> value = List.of("s");
        Object[] actual = assertInstanceOf(Object[].class, convertFrom(h, "objectArray", value));
        assertArrayEquals(new Object[]{"s"}, actual);
    }

    @Test
    void convertsStringListFrom() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        List<String> value = List.of("s");
        List<String> expected = List.of("s");
        assertConvertsFrom(expected, h, "stringList", value);
    }

    @Test
    void convertsObjectListFrom() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        List<String> value = List.of("s");
        List<Object> expected = List.of("s");
        assertConvertsFrom(expected, h, "objectList", value);
    }

    @Test
    void convertsRawListFrom() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        List<String> value = List.of("s");
        @SuppressWarnings("rawtypes")
        List expected = List.of("s");
        assertConvertsFrom(expected, h, "rawList", value);
    }

    @Test
    void convertsStringStringMapFrom() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        Map<String, String> value = Map.of("k", "v");
        Map<String, String> expected = Map.of("k", "v");
        assertConvertsFrom(expected, h, "stringStringMap", value);
    }

    @Test
    void convertsStringObjectMapFrom() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        Map<String, String> value = Map.of("k", "v");
        Map<String, Object> expected = Map.of("k", "v");
        assertConvertsFrom(expected, h, "stringObjectMap", value);
    }

    @Test
    void convertsCustomFrom() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        Map<String, Object> value = Map.of("key", "value");
        Custom actual = assertInstanceOf(Custom.class, convertFrom(h, "custom", value));
        assertEquals("value", actual.getKey());
    }

    @Test
    void convertsEmailFrom() {
        Handle<ConvertableFields> h = newHandle(ConvertableFields.class);
        String value = "convertable@email.com";
        Email actual = assertInstanceOf(Email.class, convertFrom(h, "email", value));
        assertEquals("convertable", actual.getLogin());
        assertEquals("email.com", actual.getDomain());
    }

    @Test
    void convertsAddressFrom() {
        Handle<ConvertableFields> h = newHandle(ConvertableFields.class);
        List<String> value = List.of("Convertable Street", "0", "Convertable City");
        Address actual = assertInstanceOf(Address.class, convertFrom(h, "address", value));
        assertEquals("Convertable City", actual.getStreet());
        assertEquals(0, actual.getNumber());
        assertEquals("Convertable Street", actual.getCity());
    }

    @Test
    void convertsBooleanWrapperFrom() {
        Handle<ConvertableFields> h = newHandle(ConvertableFields.class);
        String value = "true";
        Wrapper<?> actual = assertInstanceOf(Wrapper.class, convertFrom(h, "booleanWrapper", value));
        assertEquals(true, actual.getValue());
    }

    @Test
    void convertsByteWrapperFrom() {
        Handle<ConvertableFields> h = newHandle(ConvertableFields.class);
        List<String> value = List.of("1", "2", "7");
        Wrapper<?> actual = assertInstanceOf(Wrapper.class, convertFrom(h, "byteWrapper", value));
        assertEquals((byte) 127, actual.getValue());
    }

    @Test
    void convertsCompositeNameFrom() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        String value = "s";
        assertConvertsSameFrom(h, "stringStringMap.name", value);
        assertConvertsSameFrom(h, "stringObjectMap.name", value);
    }

    private <E> void assertConvertsSameFrom(Handle<E> handle, String fieldName, Object value) {
        assertConvertsFrom(value, handle, fieldName, value);
    }

    private <E> void assertConvertsFrom(Object expected, Handle<E> handle, String fieldName, Object value) {
        assertEquals(expected, convertFrom(handle, fieldName, value));
    }

    private <E> Object convertFrom(Handle<E> handle, String fieldName, Object value) {
        Map<String, Object> data = Map.of(fieldName, value);
        Map<String, Object> values = handle.buildValues(data);
        assertEquals(1, values.size());
        return values.get(fieldName);
    }

    @Test
    void convertsFieldPathsFrom() {
        Handle<FieldNames> h = newHandle(FieldNames.class);
        Map<String, Object> zeroMap = Map.of();
        Map<String, Map<String, Object>> oneMap = Map.of("one", zeroMap);
        Map<String, Map<String, Object>> intMap = Map.of("1", zeroMap);
        Map<String, Map<String, Map<String, Object>>> twoMap = Map.of("two", intMap);
        assertConvertsNameFrom("file", h, "file", "s");
        assertConvertsNameFrom("zeroProperty", h, "zeroProperty", zeroMap);
        assertConvertsNameFrom("zeroProperty.key", h, "zeroProperty.key", "s");
        assertConvertsNameFrom("zeroProperty.zeroMap", h, "zeroProperty.zero_map", zeroMap);
        assertConvertsNameFrom("zeroProperty.zeroMap.name", h, "zeroProperty.zero_map.name", "s");
        assertConvertsNameFrom("zeroProperty.zeroField", h, "zeroProperty.zero_field", zeroMap);
        assertConvertsNameFrom("zeroProperty.zeroField.file", h, "zeroProperty.zero_field.file", "s");
        assertConvertsNameFrom("zeroProperty.zeroField.zeroProperty", h, "zeroProperty.zero_field.zeroProperty", zeroMap);
        assertConvertsNameFrom("zeroProperty.zeroField.oneProperty", h, "zeroProperty.zero_field.oneProperty", oneMap);
        assertConvertsNameFrom("zeroProperty.zeroField.twoProperty", h, "zeroProperty.zero_field.twoProperty", twoMap);
        assertConvertsNameFrom("zeroProperty.oneField", h, "zeroProperty.one_field", oneMap);
        assertConvertsNameFrom("zeroProperty.oneField.one", h, "zeroProperty.one_field.one", zeroMap);
        assertConvertsNameFrom("zeroProperty.oneField.one.file", h, "zeroProperty.one_field.one.file", "s");
        assertConvertsNameFrom("zeroProperty.oneField.one.zeroProperty", h, "zeroProperty.one_field.one.zeroProperty", zeroMap);
        assertConvertsNameFrom("zeroProperty.oneField.one.oneProperty", h, "zeroProperty.one_field.one.oneProperty", oneMap);
        assertConvertsNameFrom("zeroProperty.oneField.one.twoProperty", h, "zeroProperty.one_field.one.twoProperty", twoMap);
        assertConvertsNameFrom("zeroProperty.twoField", h, "zeroProperty.two_field", twoMap);
        assertConvertsNameFrom("zeroProperty.twoField.one", h, "zeroProperty.two_field.one", intMap);
        assertConvertsNameFrom("zeroProperty.twoField.one.two", h, "zeroProperty.two_field.one.two", zeroMap);
        assertConvertsNameFrom("zeroProperty.twoField.one.two.file", h, "zeroProperty.two_field.one.two.file", "s");
        assertConvertsNameFrom("zeroProperty.twoField.one.two.zeroProperty", h, "zeroProperty.two_field.one.two.zeroProperty", zeroMap);
        assertConvertsNameFrom("zeroProperty.twoField.one.two.oneProperty", h, "zeroProperty.two_field.one.two.oneProperty", oneMap);
        assertConvertsNameFrom("zeroProperty.twoField.one.two.twoProperty", h, "zeroProperty.two_field.one.two.twoProperty", twoMap);
        assertConvertsNameFrom("oneProperty", h, "oneProperty", oneMap);
        assertConvertsNameFrom("oneProperty.one", h, "oneProperty.one", zeroMap);
        assertConvertsNameFrom("oneProperty.one.key", h, "oneProperty.one.key", "s");
        assertConvertsNameFrom("oneProperty.one.zeroMap", h, "oneProperty.one.zero_map", zeroMap);
        assertConvertsNameFrom("oneProperty.one.zeroMap.name", h, "oneProperty.one.zero_map.name", "s");
        assertConvertsNameFrom("oneProperty.one.zeroField", h, "oneProperty.one.zero_field", zeroMap);
        assertConvertsNameFrom("oneProperty.one.zeroField.file", h, "oneProperty.one.zero_field.file", "s");
        assertConvertsNameFrom("oneProperty.one.zeroField.zeroProperty", h, "oneProperty.one.zero_field.zeroProperty", zeroMap);
        assertConvertsNameFrom("oneProperty.one.zeroField.oneProperty", h, "oneProperty.one.zero_field.oneProperty", oneMap);
        assertConvertsNameFrom("oneProperty.one.zeroField.twoProperty", h, "oneProperty.one.zero_field.twoProperty", twoMap);
        assertConvertsNameFrom("oneProperty.one.oneField", h, "oneProperty.one.one_field", oneMap);
        assertConvertsNameFrom("oneProperty.one.oneField.one", h, "oneProperty.one.one_field.one", zeroMap);
        assertConvertsNameFrom("oneProperty.one.oneField.one.file", h, "oneProperty.one.one_field.one.file", "s");
        assertConvertsNameFrom("oneProperty.one.oneField.one.zeroProperty", h, "oneProperty.one.one_field.one.zeroProperty", zeroMap);
        assertConvertsNameFrom("oneProperty.one.oneField.one.oneProperty", h, "oneProperty.one.one_field.one.oneProperty", oneMap);
        assertConvertsNameFrom("oneProperty.one.oneField.one.twoProperty", h, "oneProperty.one.one_field.one.twoProperty", twoMap);
        assertConvertsNameFrom("oneProperty.one.twoField", h, "oneProperty.one.two_field", twoMap);
        assertConvertsNameFrom("oneProperty.one.twoField.one", h, "oneProperty.one.two_field.one", intMap);
        assertConvertsNameFrom("oneProperty.one.twoField.one.two", h, "oneProperty.one.two_field.one.two", zeroMap);
        assertConvertsNameFrom("oneProperty.one.twoField.one.two.file", h, "oneProperty.one.two_field.one.two.file", "s");
        assertConvertsNameFrom("oneProperty.one.twoField.one.two.zeroProperty", h, "oneProperty.one.two_field.one.two.zeroProperty", zeroMap);
        assertConvertsNameFrom("oneProperty.one.twoField.one.two.oneProperty", h, "oneProperty.one.two_field.one.two.oneProperty", oneMap);
        assertConvertsNameFrom("oneProperty.one.twoField.one.two.twoProperty", h, "oneProperty.one.two_field.one.two.twoProperty", twoMap);
        assertConvertsNameFrom("twoProperty", h, "twoProperty", twoMap);
        assertConvertsNameFrom("twoProperty.one", h, "twoProperty.one", intMap);
        assertConvertsNameFrom("twoProperty.one.two", h, "twoProperty.one.two", zeroMap);
        assertConvertsNameFrom("twoProperty.one.two.key", h, "twoProperty.one.two.key", "s");
        assertConvertsNameFrom("twoProperty.one.two.zeroMap", h, "twoProperty.one.two.zero_map", zeroMap);
        assertConvertsNameFrom("twoProperty.one.two.zeroMap.name", h, "twoProperty.one.two.zero_map.name", "s");
        assertConvertsNameFrom("twoProperty.one.two.zeroField", h, "twoProperty.one.two.zero_field", zeroMap);
        assertConvertsNameFrom("twoProperty.one.two.zeroField.file", h, "twoProperty.one.two.zero_field.file", "s");
        assertConvertsNameFrom("twoProperty.one.two.zeroField.zeroProperty", h, "twoProperty.one.two.zero_field.zeroProperty", zeroMap);
        assertConvertsNameFrom("twoProperty.one.two.zeroField.oneProperty", h, "twoProperty.one.two.zero_field.oneProperty", oneMap);
        assertConvertsNameFrom("twoProperty.one.two.zeroField.twoProperty", h, "twoProperty.one.two.zero_field.twoProperty", twoMap);
        assertConvertsNameFrom("twoProperty.one.two.oneField", h, "twoProperty.one.two.one_field", oneMap);
        assertConvertsNameFrom("twoProperty.one.two.oneField.one", h, "twoProperty.one.two.one_field.one", zeroMap);
        assertConvertsNameFrom("twoProperty.one.two.oneField.one.file", h, "twoProperty.one.two.one_field.one.file", "s");
        assertConvertsNameFrom("twoProperty.one.two.oneField.one.zeroProperty", h, "twoProperty.one.two.one_field.one.zeroProperty", zeroMap);
        assertConvertsNameFrom("twoProperty.one.two.oneField.one.oneProperty", h, "twoProperty.one.two.one_field.one.oneProperty", oneMap);
        assertConvertsNameFrom("twoProperty.one.two.oneField.one.twoProperty", h, "twoProperty.one.two.one_field.one.twoProperty", twoMap);
        assertConvertsNameFrom("twoProperty.one.two.twoField", h, "twoProperty.one.two.two_field", twoMap);
        assertConvertsNameFrom("twoProperty.one.two.twoField.one", h, "twoProperty.one.two.two_field.one", intMap);
        assertConvertsNameFrom("twoProperty.one.two.twoField.one.two", h, "twoProperty.one.two.two_field.one.two", zeroMap);
        assertConvertsNameFrom("twoProperty.one.two.twoField.one.two.file", h, "twoProperty.one.two.two_field.one.two.file", "s");
        assertConvertsNameFrom("twoProperty.one.two.twoField.one.two.zeroProperty", h, "twoProperty.one.two.two_field.one.two.zeroProperty", zeroMap);
        assertConvertsNameFrom("twoProperty.one.two.twoField.one.two.oneProperty", h, "twoProperty.one.two.two_field.one.two.oneProperty", oneMap);
        assertConvertsNameFrom("twoProperty.one.two.twoField.one.two.twoProperty", h, "twoProperty.one.two.two_field.one.two.twoProperty", twoMap);
    }

    @Test
    void convertsPropertyPathsFrom() {
        Handle<PropertyNames> h = newHandle(PropertyNames.class);
        Map<String, Object> zeroMap = Map.of();
        Map<String, Map<String, Object>> oneMap = Map.of("one", zeroMap);
        Map<String, Map<String, Object>> intMap = Map.of("1", zeroMap);
        Map<String, Map<String, Map<String, Object>>> twoMap = Map.of("two", intMap);
        assertConvertsNameFrom("key", h, "key", "s");
        assertConvertsNameFrom("zeroField", h, "zero_field", zeroMap);
        assertConvertsNameFrom("zeroField.file", h, "zero_field.file", "s");
        assertConvertsNameFrom("zeroField.zeroMap", h, "zero_field.zeroMap", zeroMap);
        assertConvertsNameFrom("zeroField.zeroMap.name", h, "zero_field.zeroMap.name", "s");
        assertConvertsNameFrom("zeroField.zeroProperty", h, "zero_field.zeroProperty", zeroMap);
        assertConvertsNameFrom("zeroField.zeroProperty.key", h, "zero_field.zeroProperty.key", "s");
        assertConvertsNameFrom("zeroField.zeroProperty.zeroField", h, "zero_field.zeroProperty.zero_field", zeroMap);
        assertConvertsNameFrom("zeroField.zeroProperty.oneField", h, "zero_field.zeroProperty.one_field", oneMap);
        assertConvertsNameFrom("zeroField.zeroProperty.twoField", h, "zero_field.zeroProperty.two_field", twoMap);
        assertConvertsNameFrom("zeroField.oneProperty", h, "zero_field.oneProperty", oneMap);
        assertConvertsNameFrom("zeroField.oneProperty.one", h, "zero_field.oneProperty.one", zeroMap);
        assertConvertsNameFrom("zeroField.oneProperty.one.key", h, "zero_field.oneProperty.one.key", "s");
        assertConvertsNameFrom("zeroField.oneProperty.one.zeroField", h, "zero_field.oneProperty.one.zero_field", zeroMap);
        assertConvertsNameFrom("zeroField.oneProperty.one.oneField", h, "zero_field.oneProperty.one.one_field", oneMap);
        assertConvertsNameFrom("zeroField.oneProperty.one.twoField", h, "zero_field.oneProperty.one.two_field", twoMap);
        assertConvertsNameFrom("zeroField.twoProperty", h, "zero_field.twoProperty", twoMap);
        assertConvertsNameFrom("zeroField.twoProperty.one", h, "zero_field.twoProperty.one", intMap);
        assertConvertsNameFrom("zeroField.twoProperty.one.two", h, "zero_field.twoProperty.one.two", zeroMap);
        assertConvertsNameFrom("zeroField.twoProperty.one.two.key", h, "zero_field.twoProperty.one.two.key", "s");
        assertConvertsNameFrom("zeroField.twoProperty.one.two.zeroField", h, "zero_field.twoProperty.one.two.zero_field", zeroMap);
        assertConvertsNameFrom("zeroField.twoProperty.one.two.oneField", h, "zero_field.twoProperty.one.two.one_field", oneMap);
        assertConvertsNameFrom("zeroField.twoProperty.one.two.twoField", h, "zero_field.twoProperty.one.two.two_field", twoMap);
        assertConvertsNameFrom("oneField", h, "one_field", oneMap);
        assertConvertsNameFrom("oneField.one", h, "one_field.one", zeroMap);
        assertConvertsNameFrom("oneField.one.file", h, "one_field.one.file", "s");
        assertConvertsNameFrom("oneField.one.zeroMap", h, "one_field.one.zeroMap", zeroMap);
        assertConvertsNameFrom("oneField.one.zeroMap.name", h, "one_field.one.zeroMap.name", "s");
        assertConvertsNameFrom("oneField.one.zeroProperty", h, "one_field.one.zeroProperty", zeroMap);
        assertConvertsNameFrom("oneField.one.zeroProperty.key", h, "one_field.one.zeroProperty.key", "s");
        assertConvertsNameFrom("oneField.one.zeroProperty.zeroField", h, "one_field.one.zeroProperty.zero_field", zeroMap);
        assertConvertsNameFrom("oneField.one.zeroProperty.oneField", h, "one_field.one.zeroProperty.one_field", oneMap);
        assertConvertsNameFrom("oneField.one.zeroProperty.twoField", h, "one_field.one.zeroProperty.two_field", twoMap);
        assertConvertsNameFrom("oneField.one.oneProperty", h, "one_field.one.oneProperty", oneMap);
        assertConvertsNameFrom("oneField.one.oneProperty.one", h, "one_field.one.oneProperty.one", zeroMap);
        assertConvertsNameFrom("oneField.one.oneProperty.one.key", h, "one_field.one.oneProperty.one.key", "s");
        assertConvertsNameFrom("oneField.one.oneProperty.one.zeroField", h, "one_field.one.oneProperty.one.zero_field", zeroMap);
        assertConvertsNameFrom("oneField.one.oneProperty.one.oneField", h, "one_field.one.oneProperty.one.one_field", oneMap);
        assertConvertsNameFrom("oneField.one.oneProperty.one.twoField", h, "one_field.one.oneProperty.one.two_field", twoMap);
        assertConvertsNameFrom("oneField.one.twoProperty", h, "one_field.one.twoProperty", twoMap);
        assertConvertsNameFrom("oneField.one.twoProperty.one", h, "one_field.one.twoProperty.one", intMap);
        assertConvertsNameFrom("oneField.one.twoProperty.one.two", h, "one_field.one.twoProperty.one.two", zeroMap);
        assertConvertsNameFrom("oneField.one.twoProperty.one.two.key", h, "one_field.one.twoProperty.one.two.key", "s");
        assertConvertsNameFrom("oneField.one.twoProperty.one.two.zeroField", h, "one_field.one.twoProperty.one.two.zero_field", zeroMap);
        assertConvertsNameFrom("oneField.one.twoProperty.one.two.oneField", h, "one_field.one.twoProperty.one.two.one_field", oneMap);
        assertConvertsNameFrom("oneField.one.twoProperty.one.two.twoField", h, "one_field.one.twoProperty.one.two.two_field", twoMap);
        assertConvertsNameFrom("twoField", h, "two_field", twoMap);
        assertConvertsNameFrom("twoField.one", h, "two_field.one", intMap);
        assertConvertsNameFrom("twoField.one.two", h, "two_field.one.two", zeroMap);
        assertConvertsNameFrom("twoField.one.two.file", h, "two_field.one.two.file", "s");
        assertConvertsNameFrom("twoField.one.two.zeroMap", h, "two_field.one.two.zeroMap", zeroMap);
        assertConvertsNameFrom("twoField.one.two.zeroMap.name", h, "two_field.one.two.zeroMap.name", "s");
        assertConvertsNameFrom("twoField.one.two.zeroProperty", h, "two_field.one.two.zeroProperty", zeroMap);
        assertConvertsNameFrom("twoField.one.two.zeroProperty.key", h, "two_field.one.two.zeroProperty.key", "s");
        assertConvertsNameFrom("twoField.one.two.zeroProperty.zeroField", h, "two_field.one.two.zeroProperty.zero_field", zeroMap);
        assertConvertsNameFrom("twoField.one.two.zeroProperty.oneField", h, "two_field.one.two.zeroProperty.one_field", oneMap);
        assertConvertsNameFrom("twoField.one.two.zeroProperty.twoField", h, "two_field.one.two.zeroProperty.two_field", twoMap);
        assertConvertsNameFrom("twoField.one.two.oneProperty", h, "two_field.one.two.oneProperty", oneMap);
        assertConvertsNameFrom("twoField.one.two.oneProperty.one", h, "two_field.one.two.oneProperty.one", zeroMap);
        assertConvertsNameFrom("twoField.one.two.oneProperty.one.key", h, "two_field.one.two.oneProperty.one.key", "s");
        assertConvertsNameFrom("twoField.one.two.oneProperty.one.zeroField", h, "two_field.one.two.oneProperty.one.zero_field", zeroMap);
        assertConvertsNameFrom("twoField.one.two.oneProperty.one.oneField", h, "two_field.one.two.oneProperty.one.one_field", oneMap);
        assertConvertsNameFrom("twoField.one.two.oneProperty.one.twoField", h, "two_field.one.two.oneProperty.one.two_field", twoMap);
        assertConvertsNameFrom("twoField.one.two.twoProperty", h, "two_field.one.two.twoProperty", twoMap);
        assertConvertsNameFrom("twoField.one.two.twoProperty.one", h, "two_field.one.two.twoProperty.one", intMap);
        assertConvertsNameFrom("twoField.one.two.twoProperty.one.two", h, "two_field.one.two.twoProperty.one.two", zeroMap);
        assertConvertsNameFrom("twoField.one.two.twoProperty.one.two.key", h, "two_field.one.two.twoProperty.one.two.key", "s");
        assertConvertsNameFrom("twoField.one.two.twoProperty.one.two.zeroField", h, "two_field.one.two.twoProperty.one.two.zero_field", zeroMap);
        assertConvertsNameFrom("twoField.one.two.twoProperty.one.two.oneField", h, "two_field.one.two.twoProperty.one.two.one_field", oneMap);
        assertConvertsNameFrom("twoField.one.two.twoProperty.one.two.twoField", h, "two_field.one.two.twoProperty.one.two.two_field", twoMap);
    }

    private <E> void assertConvertsNameFrom(String expectedName, Handle<E> handle, String fieldName, Object value) {
        Map<String, Object> data = Map.of(fieldName, value);
        Map<String, Object> values = handle.buildValues(data);
        assertEquals(1, values.size());
        assertTrue(values.containsKey(expectedName));
    }

    @Test
    void doesNotConvertBooleanFrom() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        boolean value = true;
        assertDoesNotConvertFrom(h, "charValue", value);
        assertDoesNotConvertFrom(h, "boxedCharValue", value);
        assertDoesNotConvertFrom(h, "byteValue", value);
        assertDoesNotConvertFrom(h, "boxedByteValue", value);
        assertDoesNotConvertFrom(h, "shortValue", value);
        assertDoesNotConvertFrom(h, "boxedShortValue", value);
        assertDoesNotConvertFrom(h, "intValue", value);
        assertDoesNotConvertFrom(h, "boxedIntValue", value);
        assertDoesNotConvertFrom(h, "longValue", value);
        assertDoesNotConvertFrom(h, "boxedLongValue", value);
        assertDoesNotConvertFrom(h, "floatValue", value);
        assertDoesNotConvertFrom(h, "boxedFloatValue", value);
        assertDoesNotConvertFrom(h, "doubleValue", value);
        assertDoesNotConvertFrom(h, "boxedDoubleValue", value);
        assertDoesNotConvertFrom(h, "stringValue", value);
    }

    @Test
    void doesNotConvertLongFrom() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        long value = 4L;
        assertDoesNotConvertFrom(h, "booleanValue", value);
        assertDoesNotConvertFrom(h, "boxedBooleanValue", value);
        assertDoesNotConvertFrom(h, "charValue", value);
        assertDoesNotConvertFrom(h, "boxedCharValue", value);
        assertDoesNotConvertFrom(h, "stringValue", value);
    }

    @Test
    void doesNotConvertDoubleFrom() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        double value = 6.6;
        assertDoesNotConvertFrom(h, "booleanValue", value);
        assertDoesNotConvertFrom(h, "boxedBooleanValue", value);
        assertDoesNotConvertFrom(h, "charValue", value);
        assertDoesNotConvertFrom(h, "boxedCharValue", value);
        assertDoesNotConvertFrom(h, "stringValue", value);
    }

    @Test
    void doesNotConvertStringFrom() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        String value = "s";
        assertDoesNotConvertFrom(h, "booleanValue", value);
        assertDoesNotConvertFrom(h, "boxedBooleanValue", value);
        assertDoesNotConvertFrom(h, "byteValue", value);
        assertDoesNotConvertFrom(h, "boxedByteValue", value);
        assertDoesNotConvertFrom(h, "shortValue", value);
        assertDoesNotConvertFrom(h, "boxedShortValue", value);
        assertDoesNotConvertFrom(h, "intValue", value);
        assertDoesNotConvertFrom(h, "boxedIntValue", value);
        assertDoesNotConvertFrom(h, "longValue", value);
        assertDoesNotConvertFrom(h, "boxedLongValue", value);
        assertDoesNotConvertFrom(h, "floatValue", value);
        assertDoesNotConvertFrom(h, "boxedFloatValue", value);
        assertDoesNotConvertFrom(h, "doubleValue", value);
        assertDoesNotConvertFrom(h, "boxedDoubleValue", value);
    }

    @Test
    void doesNotConvertPointFrom() {
        Handle<FirestoreFields> h = newHandle(FirestoreFields.class);
        GeoPoint value = mock(GeoPoint.class);
        assertDoesNotConvertFrom(h, "reference", value);
        assertDoesNotConvertFrom(h, "timestamp", value);
        assertDoesNotConvertFrom(h, "instant", value);
        assertDoesNotConvertFrom(h, "blob", value);
        assertDoesNotConvertFrom(h, "stream", value);
    }

    @Test
    void doesNotConvertReferenceFrom() {
        Handle<FirestoreFields> h = newHandle(FirestoreFields.class);
        DocumentReference value = mock(DocumentReference.class);
        assertDoesNotConvertFrom(h, "point", value);
        assertDoesNotConvertFrom(h, "timestamp", value);
        assertDoesNotConvertFrom(h, "instant", value);
        assertDoesNotConvertFrom(h, "blob", value);
        assertDoesNotConvertFrom(h, "stream", value);
    }

    @Test
    void doesNotConvertTimestampFrom() {
        Handle<FirestoreFields> h = newHandle(FirestoreFields.class);
        Timestamp value = mock(Timestamp.class);
        assertDoesNotConvertFrom(h, "point", value);
        assertDoesNotConvertFrom(h, "reference", value);
        assertDoesNotConvertFrom(h, "blob", value);
        assertDoesNotConvertFrom(h, "stream", value);
    }

    @Test
    void doesNotConvertBlobFrom() {
        Handle<FirestoreFields> h = newHandle(FirestoreFields.class);
        Blob value = mock(Blob.class);
        assertDoesNotConvertFrom(h, "point", value);
        assertDoesNotConvertFrom(h, "reference", value);
        assertDoesNotConvertFrom(h, "timestamp", value);
        assertDoesNotConvertFrom(h, "instant", value);
    }

    @Test
    void doesNotConvertStringListFrom() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        List<String> value = List.of("s");
        assertDoesNotConvertFrom(h, "stringStringMap", value);
        assertDoesNotConvertFrom(h, "stringObjectMap", value);
        assertDoesNotConvertFrom(h, "listStringMap", value);
        assertDoesNotConvertFrom(h, "objectStringMap", value);
        assertDoesNotConvertFrom(h, "objectObjectMap", value);
        assertDoesNotConvertFrom(h, "rawMap", value);
        assertDoesNotConvertFrom(h, "custom", value);
    }

    @Test
    void doesNotConvertStringStringMapFrom() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        Map<String, String> value = Map.of("k", "v");
        assertDoesNotConvertFrom(h, "stringArray", value);
        assertDoesNotConvertFrom(h, "objectArray", value);
        assertDoesNotConvertFrom(h, "stringList", value);
        assertDoesNotConvertFrom(h, "objectList", value);
        assertDoesNotConvertFrom(h, "rawList", value);
        assertDoesNotConvertIllegalFrom(h, "listStringMap", value);
        assertDoesNotConvertIllegalFrom(h, "objectStringMap", value);
        assertDoesNotConvertIllegalFrom(h, "objectObjectMap", value);
        assertDoesNotConvertFrom(h, "rawMap", value);
    }

    private <E> void assertDoesNotConvertFrom(Handle<E> handle, String fieldName, Object value) {
        assertDoesNotConvertFrom(handle, fieldName, value, ConversionException.class);
    }

    @Test
    void doesNotConvertSimpleNameFrom() {
        Handle<SimpleFields> h = newHandle(SimpleFields.class);
        String value = "s";
        assertDoesNotConvertIllegalFrom(h, "booleanValue.name", value);
        assertDoesNotConvertIllegalFrom(h, "boxedBooleanValue.name", value);
        assertDoesNotConvertIllegalFrom(h, "charValue.name", value);
        assertDoesNotConvertIllegalFrom(h, "boxedCharValue.name", value);
        assertDoesNotConvertIllegalFrom(h, "byteValue.name", value);
        assertDoesNotConvertIllegalFrom(h, "boxedByteValue.name", value);
        assertDoesNotConvertIllegalFrom(h, "shortValue.name", value);
        assertDoesNotConvertIllegalFrom(h, "boxedShortValue.name", value);
        assertDoesNotConvertIllegalFrom(h, "intValue.name", value);
        assertDoesNotConvertIllegalFrom(h, "boxedIntValue.name", value);
        assertDoesNotConvertIllegalFrom(h, "longValue.name", value);
        assertDoesNotConvertIllegalFrom(h, "boxedLongValue.name", value);
        assertDoesNotConvertIllegalFrom(h, "floatValue.name", value);
        assertDoesNotConvertIllegalFrom(h, "boxedFloatValue.name", value);
        assertDoesNotConvertIllegalFrom(h, "doubleValue.name", value);
        assertDoesNotConvertIllegalFrom(h, "boxedDoubleValue.name", value);
        assertDoesNotConvertIllegalFrom(h, "stringValue.name", value);
    }

    @Test
    void doesNotConvertFirestoreNameFrom() {
        Handle<FirestoreFields> h = newHandle(FirestoreFields.class);
        String value = "s";
        assertDoesNotConvertIllegalFrom(h, "point.name", value);
        assertDoesNotConvertIllegalFrom(h, "reference.name", value);
        assertDoesNotConvertIllegalFrom(h, "timestamp.name", value);
        assertDoesNotConvertIllegalFrom(h, "instant.name", value);
        assertDoesNotConvertIllegalFrom(h, "blob.name", value);
        assertDoesNotConvertIllegalFrom(h, "stream.name", value);
    }

    @Test
    void doesNotConvertCompositeNameFrom() {
        Handle<CompositeFields> h = newHandle(CompositeFields.class);
        String value = "s";
        assertDoesNotConvertIllegalFrom(h, "stringArray.name", value);
        assertDoesNotConvertIllegalFrom(h, "objectArray.name", value);
        assertDoesNotConvertIllegalFrom(h, "stringList.name", value);
        assertDoesNotConvertIllegalFrom(h, "objectList.name", value);
        assertDoesNotConvertIllegalFrom(h, "rawList.name", value);
        assertDoesNotConvertIllegalFrom(h, "listStringMap.name", value);
        assertDoesNotConvertIllegalFrom(h, "objectStringMap.name", value);
        assertDoesNotConvertIllegalFrom(h, "objectObjectMap.name", value);
        assertDoesNotConvertIllegalFrom(h, "rawMap.name", value);
        assertDoesNotConvertIllegalFrom(h, "custom.name", value);
    }

    @Test
    void doesNotConvertConvertableNameFrom() {
        Handle<ConvertableFields> h = newHandle(ConvertableFields.class);
        assertDoesNotConvertIllegalFrom(h, "email.login", "s");
        assertDoesNotConvertIllegalFrom(h, "email.domain", "s");
        assertDoesNotConvertIllegalFrom(h, "address.street", "s");
        assertDoesNotConvertIllegalFrom(h, "address.number", 4L);
        assertDoesNotConvertIllegalFrom(h, "address.city", "s");
        assertDoesNotConvertIllegalFrom(h, "booleanWrapper.value", true);
        assertDoesNotConvertIllegalFrom(h, "byteWrapper.value", 4L);
    }

    private <E> void assertDoesNotConvertIllegalFrom(Handle<E> handle, String fieldName, Object value) {
        assertDoesNotConvertFrom(handle, fieldName, value, IllegalArgumentException.class);
    }

    private <E> void assertDoesNotConvertFrom(Handle<E> handle, String fieldName, Object value, Class<? extends RuntimeException> exceptionType) {
        Map<String, Object> data = Map.of(fieldName, value);
        assertThrows(exceptionType, () -> handle.buildValues(data));
    }

    private <E> Handle<E> newHandle(Class<E> type) {
        return new Handle<>(reflector, parserFactory, converterFactory, handleFactory, type);
    }
}
