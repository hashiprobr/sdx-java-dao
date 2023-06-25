package br.pro.hashi.sdx.dao.reflection;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Blob;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.GeoPoint;
import com.google.protobuf.ByteString;

import br.pro.hashi.sdx.dao.DaoConverter;
import br.pro.hashi.sdx.dao.annotation.Auto;
import br.pro.hashi.sdx.dao.annotation.Converted;
import br.pro.hashi.sdx.dao.annotation.File;
import br.pro.hashi.sdx.dao.annotation.Key;
import br.pro.hashi.sdx.dao.annotation.Renamed;
import br.pro.hashi.sdx.dao.annotation.Web;
import br.pro.hashi.sdx.dao.reflection.exception.AnnotationException;
import br.pro.hashi.sdx.dao.reflection.exception.ConversionException;
import br.pro.hashi.sdx.dao.reflection.exception.ReflectionException;

public class Handle<E> {
	private static final Map<Class<?>, Function<Long, ?>> LONG_FUNCTIONS;
	private static final Map<Class<?>, Function<Double, ?>> DOUBLE_FUNCTIONS;
	private static final Set<Class<?>> TEXT_TYPES;
	private static final Set<Class<?>> NUMBER_TYPES;
	private static final Set<Class<?>> BOOLEAN_COMPATIBLE_TYPES;
	private static final Set<Class<?>> CHARACTER_COMPATIBLE_TYPES;
	private static final Set<Class<?>> BYTE_COMPATIBLE_TYPES;
	private static final Set<Class<?>> SHORT_COMPATIBLE_TYPES;
	private static final Set<Class<?>> INTEGER_COMPATIBLE_TYPES;
	private static final Set<Class<?>> LONG_COMPATIBLE_TYPES;
	private static final Set<Class<?>> FLOAT_COMPATIBLE_TYPES;
	private static final Set<Class<?>> DOUBLE_COMPATIBLE_TYPES;
	private static final Set<Class<?>> STRING_COMPATIBLE_TYPES;
	private static final Set<Class<?>> TIMESTAMP_COMPATIBLE_TYPES;
	private static final Set<Class<?>> BLOB_COMPATIBLE_TYPES;

	private static final Set<Class<?>> BOOLEAN_TYPES = Set.of(
			boolean.class,
			Boolean.class);

	private static final Set<Class<?>> CHARACTER_TYPES = Set.of(
			char.class,
			Character.class);

	private static final Set<Class<?>> TIMESTAMP_TYPES = Set.of(
			Timestamp.class,
			Instant.class);

	private static final Set<Class<?>> BLOB_TYPES = Set.of(
			Blob.class,
			InputStream.class);

	private static final Set<Class<?>> POINT_COMPATIBLE_TYPES = Set.of(
			GeoPoint.class,
			Object.class);

	private static final Set<Class<?>> REFERENCE_COMPATIBLE_TYPES = Set.of(
			DocumentReference.class,
			Object.class);

	private static final Set<Class<?>> LIST_COMPATIBLE_TYPES = Set.of(
			List.class,
			Object.class);

	private static final Set<Class<?>> MAP_COMPATIBLE_TYPES = Set.of(
			Map.class,
			Object.class);

	static {
		Map<Class<?>, Function<Long, ?>> longFunctions = new HashMap<>();
		longFunctions.put(byte.class, (l) -> l.byteValue());
		longFunctions.put(Byte.class, (l) -> Byte.valueOf(l.byteValue()));
		longFunctions.put(short.class, (l) -> l.shortValue());
		longFunctions.put(Short.class, (l) -> Short.valueOf(l.shortValue()));
		longFunctions.put(int.class, (l) -> l.intValue());
		longFunctions.put(Integer.class, (l) -> Integer.valueOf(l.intValue()));
		longFunctions.put(long.class, (l) -> l.longValue());
		longFunctions.put(Long.class, (l) -> l);
		longFunctions.put(float.class, (l) -> l.floatValue());
		longFunctions.put(Float.class, (l) -> Float.valueOf(l.floatValue()));
		longFunctions.put(double.class, (l) -> l.doubleValue());
		longFunctions.put(Double.class, (l) -> Double.valueOf(l.doubleValue()));
		LONG_FUNCTIONS = Map.copyOf(longFunctions);

		Map<Class<?>, Function<Double, ?>> doubleFunctions = new HashMap<>();
		doubleFunctions.put(byte.class, (d) -> d.byteValue());
		doubleFunctions.put(Byte.class, (d) -> Byte.valueOf(d.byteValue()));
		doubleFunctions.put(short.class, (d) -> d.shortValue());
		doubleFunctions.put(Short.class, (d) -> Short.valueOf(d.shortValue()));
		doubleFunctions.put(int.class, (d) -> d.intValue());
		doubleFunctions.put(Integer.class, (d) -> Integer.valueOf(d.intValue()));
		doubleFunctions.put(long.class, (d) -> d.longValue());
		doubleFunctions.put(Long.class, (d) -> Long.valueOf(d.longValue()));
		doubleFunctions.put(float.class, (d) -> d.floatValue());
		doubleFunctions.put(Float.class, (d) -> Float.valueOf(d.floatValue()));
		doubleFunctions.put(double.class, (d) -> d.doubleValue());
		doubleFunctions.put(Double.class, (d) -> d);
		DOUBLE_FUNCTIONS = Map.copyOf(doubleFunctions);

		Set<Class<?>> booleanTypes = new HashSet<>(BOOLEAN_TYPES);
		booleanTypes.add(Object.class);
		BOOLEAN_COMPATIBLE_TYPES = Set.copyOf(booleanTypes);

		Set<Class<?>> textTypes = new HashSet<>(CHARACTER_TYPES);
		textTypes.add(String.class);
		TEXT_TYPES = Set.copyOf(textTypes);
		textTypes.add(Object.class);
		CHARACTER_COMPATIBLE_TYPES = Set.copyOf(textTypes);
		textTypes.remove(char.class);
		textTypes.remove(Character.class);
		STRING_COMPATIBLE_TYPES = Set.copyOf(textTypes);

		NUMBER_TYPES = Set.copyOf(LONG_FUNCTIONS.keySet());
		Set<Class<?>> numberTypes = new HashSet<>(DOUBLE_FUNCTIONS.keySet());
		numberTypes.add(Object.class);
		BYTE_COMPATIBLE_TYPES = Set.copyOf(numberTypes);
		numberTypes.remove(byte.class);
		numberTypes.remove(Byte.class);
		SHORT_COMPATIBLE_TYPES = Set.copyOf(numberTypes);
		numberTypes.remove(short.class);
		numberTypes.remove(Short.class);
		INTEGER_COMPATIBLE_TYPES = Set.copyOf(numberTypes);
		FLOAT_COMPATIBLE_TYPES = Set.copyOf(numberTypes);
		numberTypes.remove(int.class);
		numberTypes.remove(Integer.class);
		numberTypes.remove(float.class);
		numberTypes.remove(Float.class);
		LONG_COMPATIBLE_TYPES = Set.copyOf(numberTypes);
		DOUBLE_COMPATIBLE_TYPES = Set.copyOf(numberTypes);

		Set<Class<?>> timestampTypes = new HashSet<>(TIMESTAMP_TYPES);
		timestampTypes.add(Object.class);
		TIMESTAMP_COMPATIBLE_TYPES = Set.copyOf(timestampTypes);

		Set<Class<?>> blobTypes = new HashSet<>(BLOB_TYPES);
		blobTypes.add(Object.class);
		BLOB_COMPATIBLE_TYPES = Set.copyOf(blobTypes);
	}

	private final Reflector reflector;
	private final ParserFactory parserFactory;
	private final HandleFactory handleFactory;
	private final MethodHandle creator;
	private final String collectionName;
	private final Map<String, DaoConverter<?, ?>> converters;
	private final Map<String, MethodHandle> getters;
	private final Map<String, MethodHandle> setters;
	private final Map<String, Type> fieldTypes;
	private final Map<String, Type> targetTypes;
	private final Map<String, String> contentTypes;
	private final Map<String, String> propertyNames;
	private final Map<String, String> reverseNames;
	private final Set<String> webFieldNames;
	private final String keyFieldName;
	private final boolean autoKey;

	Handle(Reflector reflector, ParserFactory parserFactory, ConverterFactory converterFactory, HandleFactory handleFactory, Class<E> type) {
		String typeName = type.getName();

		if (type.getTypeParameters().length > 0) {
			throw new ReflectionException("Class %s cannot be generic".formatted(typeName));
		}

		MethodHandle creator = reflector.getCreator(type, typeName);
		try {
			creator.invoke();
		} catch (Throwable throwable) {
			throw new ReflectionException(throwable);
		}

		Renamed typeNamedAnnotation = type.getDeclaredAnnotation(Renamed.class);
		String collectionName;
		if (typeNamedAnnotation == null) {
			String typeSimpleName = type.getSimpleName();
			if (typeSimpleName.endsWith("s")) {
				collectionName = typeSimpleName;
			} else {
				if (typeSimpleName.endsWith("y")) {
					collectionName = "%sies".formatted(typeSimpleName.substring(0, typeSimpleName.length() - 1));
				} else {
					collectionName = "%ss".formatted(typeSimpleName);
				}
			}
		} else {
			collectionName = typeNamedAnnotation.value().strip();
			if (collectionName.isEmpty()) {
				throw new AnnotationException(typeName, "Type @Renamed value cannot be blank");
			}
			if (collectionName.indexOf('.') != -1) {
				throw new AnnotationException(typeName, "Type @Renamed value cannot have dots");
			}
			if (collectionName.indexOf('/') != -1) {
				throw new AnnotationException(typeName, "Type @Renamed value cannot have slashes");
			}
		}

		Map<String, DaoConverter<?, ?>> converters = new HashMap<>();
		Map<String, MethodHandle> getters = new HashMap<>();
		Map<String, MethodHandle> setters = new HashMap<>();
		Map<String, Type> fieldTypes = new HashMap<>();
		Map<String, Type> targetTypes = new HashMap<>();
		Map<String, String> contentTypes = new HashMap<>();
		Map<String, String> propertyNames = new HashMap<>();
		Map<String, String> reverseNames = new HashMap<>();
		Set<String> webFieldNames = new HashSet<>();
		String keyFieldName = null;
		boolean autoKey = false;

		Set<String> fieldSet = fieldTypes.keySet();
		Collection<String> propertyCollection = propertyNames.values();

		for (Class<?> superType = type; !superType.equals(Object.class); superType = superType.getSuperclass()) {
			for (Field field : superType.getDeclaredFields()) {
				String fieldName = field.getName();
				if (propertyCollection.contains(fieldName)) {
					throw new AnnotationException(superType, "Field name cannot clash with field @Renamed values");
				}
				if (!fieldSet.contains(fieldName)) {
					int modifiers = field.getModifiers();
					if (!(Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || Modifier.isTransient(modifiers))) {
						if (!Modifier.isPublic(modifiers)) {
							field.setAccessible(true);
						}

						Type fieldType = field.getGenericType();
						if (fieldType.equals(FieldValue.class)) {
							throw new ReflectionException("Class %s cannot have a FieldValue field".formatted(superType.getName()));
						}

						Converted convertedAnnotation = field.getDeclaredAnnotation(Converted.class);
						if (convertedAnnotation != null) {
							DaoConverter<?, ?> converter = converterFactory.get(convertedAnnotation.value());
							Type sourceType = reflector.getSpecificType(converter, DaoConverter.class, 0);
							Type targetType = reflector.getSpecificType(converter, DaoConverter.class, 1);
							if (!fieldType.equals(sourceType)) {
								throw new AnnotationException(superType, "@Converted field type must be the converter source type");
							}
							converters.put(fieldName, converter);
							targetTypes.put(fieldName, targetType);
						}

						getters.put(fieldName, reflector.unreflectGetter(field));
						setters.put(fieldName, reflector.unreflectSetter(field));

						Renamed fieldRenamedAnnotation = field.getDeclaredAnnotation(Renamed.class);
						if (fieldRenamedAnnotation != null) {
							String propertyName = fieldRenamedAnnotation.value().strip();
							if (propertyName.isEmpty()) {
								throw new AnnotationException(superType, "Field @Renamed value cannot be blank");
							}
							if (propertyName.indexOf('.') != -1) {
								throw new AnnotationException(superType, "Field @Renamed value cannot have dots");
							}
							if (propertyName.indexOf('/') != -1) {
								throw new AnnotationException(superType, "Field @Renamed value cannot have slashes");
							}
							if (fieldSet.contains(propertyName)) {
								throw new AnnotationException(superType, "Field @Renamed value cannot clash with field names");
							}
							propertyNames.put(fieldName, propertyName);
							reverseNames.put(propertyName, fieldName);
						}

						File fileAnnotation = field.getDeclaredAnnotation(File.class);
						Web webAnnotation = field.getDeclaredAnnotation(Web.class);
						if (fileAnnotation == null) {
							if (webAnnotation != null) {
								throw new AnnotationException(superType, "@Web field must be a @File field");
							}
						} else {
							if (convertedAnnotation != null) {
								throw new AnnotationException(superType, "@File field cannot be a @Converted field");
							}
							if (!fieldType.equals(String.class)) {
								throw new AnnotationException(superType, "@File field must be a string");
							}
							contentTypes.put(fieldName, fileAnnotation.value().strip());
							if (webAnnotation != null) {
								webFieldNames.add(fieldName);
							}
						}

						Key keyAnnotation = field.getDeclaredAnnotation(Key.class);
						Auto autoAnnotation = field.getDeclaredAnnotation(Auto.class);
						if (keyAnnotation == null) {
							if (autoAnnotation != null) {
								throw new AnnotationException(superType, "@Auto field must be a @Key field");
							}
						} else {
							if (convertedAnnotation != null) {
								throw new AnnotationException(superType, "@Key field cannot be a @Converted field");
							}
							if (fileAnnotation != null) {
								throw new AnnotationException(superType, "@Key field cannot be a @File field");
							}
							if (keyFieldName != null) {
								throw new AnnotationException(superType, "Cannot have multiple @Key fields");
							}
							keyFieldName = fieldName;
							if (autoAnnotation != null) {
								if (!fieldType.equals(String.class)) {
									throw new AnnotationException(superType, "@Auto field must be a string");
								}
								autoKey = true;
							}
						}

						fieldTypes.put(fieldName, fieldType);
					}
				}
			}
		}

		this.reflector = reflector;
		this.parserFactory = parserFactory;
		this.handleFactory = handleFactory;
		this.creator = creator;
		this.collectionName = collectionName;
		this.converters = converters;
		this.getters = getters;
		this.setters = setters;
		this.fieldTypes = fieldTypes;
		this.targetTypes = targetTypes;
		this.contentTypes = contentTypes;
		this.propertyNames = propertyNames;
		this.reverseNames = reverseNames;
		this.webFieldNames = webFieldNames;
		this.keyFieldName = keyFieldName;
		this.autoKey = autoKey;
	}

	public String getCollectionName() {
		return collectionName;
	}

	public Set<String> getFieldNames() {
		return fieldTypes.keySet();
	}

	public String getKeyFieldName() {
		return keyFieldName;
	}

	public boolean hasAutoKey() {
		return autoKey;
	}

	public Set<String> getFileFieldNames() {
		return contentTypes.keySet();
	}

	public String getContentType(String fieldName) {
		return contentTypes.get(fieldName);
	}

	public boolean isWeb(String fieldName) {
		return webFieldNames.contains(fieldName);
	}

	public boolean hasKey(String[] names) {
		for (String name : names) {
			if (name.equals(keyFieldName)) {
				return true;
			}
		}
		return false;
	}

	public <F> F getKey(E instance) {
		return get(keyFieldName, instance);
	}

	public void setAutoKey(E instance, String value) {
		set(keyFieldName, instance, value);
	}

	public void putAutoKey(Map<String, Object> values, String value) {
		values.put(keyFieldName, value);
	}

	public Map<String, Object> buildCreateData(E instance) {
		return buildData(instance, false);
	}

	public Map<String, Object> buildUpdateData(E instance) {
		return buildData(instance, true);
	}

	private Map<String, Object> buildData(E instance, boolean exists) {
		Set<Object> objectPath = new HashSet<>();
		objectPath.add(instance);
		return buildData(objectPath, instance, exists, true);
	}

	private Map<String, Object> buildData(Set<Object> objectPath, Object instance, boolean exists, boolean rooted) {
		Map<String, Object> data = new HashMap<>();
		for (String fieldName : fieldTypes.keySet()) {
			String propertyName = rename(fieldName);
			if (contentTypes.containsKey(fieldName)) {
				if (!exists) {
					data.put(propertyName, null);
				}
			} else {
				if (!(fieldName.equals(keyFieldName) && (exists || (rooted && autoKey)))) {
					Object value = get(fieldName, instance);
					if (value != null) {
						data.put(propertyName, convertTo(objectPath, fieldName, value));
					}
				}
			}
		}
		return data;
	}

	public Map<String, Object> buildData(Map<String, Object> values) {
		Set<Object> objectPath = new HashSet<>();
		objectPath.add(values);
		Map<String, Object> data = new HashMap<>();
		for (String fieldPath : values.keySet()) {
			if (fieldPath != null) {
				Object value = values.get(fieldPath);
				Entry entry = buildDataEntry(objectPath, fieldPath, value, true);
				data.put(entry.path(), entry.value());
			}
		}
		return data;
	}

	public E buildInstance(Map<String, Object> data) {
		E instance = reflector.invokeCreator(creator);
		for (String fieldName : fieldTypes.keySet()) {
			String propertyName = rename(fieldName);
			Object value = data.get(propertyName);
			set(fieldName, instance, convertFrom(fieldName, value));
		}
		return instance;
	}

	public Map<String, Object> buildValues(Map<String, Object> data) {
		Map<String, Object> values = new HashMap<>();
		for (String propertyPath : data.keySet()) {
			Object value = data.get(propertyPath);
			Entry entry = buildValuesEntry(propertyPath, value);
			values.put(entry.path(), entry.value());
		}
		return values;
	}

	public String[] buildDataEntryPaths(String[] fieldPaths) {
		String[] keys = new String[fieldPaths.length];
		for (int i = 0; i < fieldPaths.length; i++) {
			keys[i] = buildDataEntryPath(fieldPaths[i]);
		}
		return keys;
	}

	public String buildDataEntryPath(String fieldPath) {
		int index = fieldPath.indexOf('.');
		if (index == -1) {
			getFieldType(fieldPath);
			return rename(fieldPath);
		}
		String fieldPrefix = fieldPath.substring(0, index);
		String fieldName = fieldPrefix;
		Type fieldType = getFieldType(fieldName);
		String propertyPrefix = rename(fieldName);
		String propertyName = propertyPrefix;
		String suffix = fieldPath.substring(index + 1);
		index = -1;
		do {
			PathType pathType = getPathType(fieldPrefix, fieldType);
			Class<?> rawType = pathType.raw();
			if (rawType.equals(Map.class)) {
				index = suffix.indexOf('.', index + 1);
				if (index != -1) {
					String base = suffix.substring(0, index);
					fieldPrefix = "%s.%s".formatted(fieldName, base);
					propertyPrefix = "%s.%s".formatted(propertyName, base);
				}
				fieldType = pathType.component();
			} else {
				Handle<?> handle = handleFactory.get(rawType);
				fieldPath = suffix.substring(index + 1);
				String entryPath = handle.buildDataEntryPath(fieldPath);
				return "%s.%s".formatted(propertyPrefix, entryPath);
			}
		} while (index != -1);
		return "%s.%s".formatted(propertyName, suffix);
	}

	private Entry buildDataEntry(Set<Object> objectPath, String fieldPath, Object value, boolean rooted) {
		String propertyPath;
		int index = fieldPath.indexOf('.');
		if (index == -1) {
			getFieldType(fieldPath);
			propertyPath = rename(fieldPath);
			return new Entry(propertyPath, convertTo(objectPath, fieldPath, value));
		}
		String fieldPrefix = fieldPath.substring(0, index);
		String fieldName = fieldPrefix;
		Type fieldType = getFieldType(fieldName);
		if (contentTypes.containsKey(fieldName)) {
			throw new IllegalArgumentException("@File fields cannot be directly updated");
		}
		if (rooted && fieldName.equals(keyFieldName)) {
			throw new IllegalArgumentException("Rooted @Key fields cannot be updated");
		}
		String propertyPrefix = rename(fieldName);
		String propertyName = propertyPrefix;
		String suffix = fieldPath.substring(index + 1);
		index = -1;
		do {
			PathType pathType = getPathType(fieldPrefix, fieldType);
			Class<?> rawType = pathType.raw();
			if (rawType.equals(Map.class)) {
				index = suffix.indexOf('.', index + 1);
				if (index != -1) {
					String base = suffix.substring(0, index);
					fieldPrefix = "%s.%s".formatted(fieldName, base);
					propertyPrefix = "%s.%s".formatted(propertyName, base);
				}
				fieldType = pathType.component();
			} else {
				Handle<?> handle = handleFactory.get(rawType);
				fieldPath = suffix.substring(index + 1);
				Entry entry = handle.buildDataEntry(objectPath, fieldPath, value, false);
				propertyPath = "%s.%s".formatted(propertyPrefix, entry.path());
				return new Entry(propertyPath, entry.value());
			}
		} while (index != -1);
		propertyPath = "%s.%s".formatted(propertyName, suffix);
		return new Entry(propertyPath, convertTo(objectPath, fieldType, value));
	}

	private Entry buildValuesEntry(String propertyPath, Object value) {
		String fieldPath;
		int index = propertyPath.indexOf('.');
		if (index == -1) {
			fieldPath = revert(propertyPath);
			getFieldType(fieldPath);
			return new Entry(fieldPath, convertFrom(fieldPath, value));
		}
		String propertyPrefix = propertyPath.substring(0, index);
		String propertyName = propertyPrefix;
		String suffix = propertyPath.substring(index + 1);
		String fieldPrefix = revert(propertyName);
		String fieldName = fieldPrefix;
		Type fieldType = getFieldType(fieldName);
		index = -1;
		do {
			PathType pathType = getPathType(fieldPrefix, fieldType);
			Class<?> rawType = pathType.raw();
			if (rawType.equals(Map.class)) {
				index = suffix.indexOf('.', index + 1);
				if (index != -1) {
					String base = suffix.substring(0, index);
					propertyPrefix = "%s.%s".formatted(propertyName, base);
					fieldPrefix = "%s.%s".formatted(fieldName, base);
				}
				fieldType = pathType.component();
			} else {
				Handle<?> handle = handleFactory.get(rawType);
				propertyPath = suffix.substring(index + 1);
				Entry entry = handle.buildValuesEntry(propertyPath, value);
				fieldPath = "%s.%s".formatted(fieldPrefix, entry.path());
				return new Entry(fieldPath, entry.value());
			}
		} while (index != -1);
		fieldPath = "%s.%s".formatted(fieldName, suffix);
		return new Entry(fieldPath, convertFrom(fieldType, value));
	}

	private record Entry(String path, Object value) {
	}

	private Type getFieldType(String fieldName) {
		Type fieldType = fieldTypes.get(fieldName);
		if (fieldType == null) {
			throw new IllegalArgumentException("Field '%s' does not exist".formatted(fieldName));
		}
		Type targetType = targetTypes.get(fieldName);
		if (targetType == null) {
			return fieldType;
		}
		return targetType;
	}

	private PathType getPathType(String fieldPath, Type fieldType) {
		if (BOOLEAN_TYPES.contains(fieldType)) {
			throw new IllegalArgumentException("Field %s is supposed to be a boolean".formatted(fieldPath));
		}
		if (TEXT_TYPES.contains(fieldType)) {
			throw new IllegalArgumentException("Field %s is supposed to be a text".formatted(fieldPath));
		}
		if (NUMBER_TYPES.contains(fieldType)) {
			throw new IllegalArgumentException("Field %s is supposed to be a number".formatted(fieldPath));
		}
		if (fieldType.equals(GeoPoint.class)) {
			throw new IllegalArgumentException("Field %s is supposed to be a point".formatted(fieldPath));
		}
		if (fieldType.equals(DocumentReference.class)) {
			throw new IllegalArgumentException("Field %s is supposed to be a reference".formatted(fieldPath));
		}
		if (TIMESTAMP_TYPES.contains(fieldType)) {
			throw new IllegalArgumentException("Field %s is supposed to be a timestamp".formatted(fieldPath));
		}
		if (BLOB_TYPES.contains(fieldType)) {
			throw new IllegalArgumentException("Field %s is supposed to be a blob".formatted(fieldPath));
		}
		Class<?> rawType;
		Type[] componentTypes;
		if (fieldType instanceof ParameterizedType) {
			ParameterizedType genericType = (ParameterizedType) fieldType;
			rawType = (Class<?>) genericType.getRawType();
			componentTypes = genericType.getActualTypeArguments();
		} else {
			rawType = (Class<?>) fieldType;
			if (rawType.isArray()) {
				throw new IllegalArgumentException("Field %s is supposed to be an array".formatted(fieldPath));
			}
			componentTypes = null;
		}
		if (rawType.equals(List.class)) {
			throw new IllegalArgumentException("Field %s is supposed to be a list".formatted(fieldPath));
		}
		Type componentType;
		if (rawType.equals(Map.class)) {
			Class<?> keyType;
			if (componentTypes == null) {
				keyType = Object.class;
				componentType = Object.class;
			} else {
				keyType = downcast(componentTypes[0]);
				componentType = componentTypes[1];
			}
			parserFactory.get(keyType);
		} else {
			componentType = null;
		}
		return new PathType(rawType, componentType);
	}

	private record PathType(Class<?> raw, Type component) {
	}

	private <S> Object convertTo(Set<Object> objectPath, String fieldName, S source) {
		@SuppressWarnings("unchecked")
		DaoConverter<S, ?> converter = (DaoConverter<S, ?>) converters.get(fieldName);
		if (converter == null) {
			return convertTo(objectPath, fieldTypes.get(fieldName), source);
		}
		Object target = converter.to(source);
		return convertTo(objectPath, targetTypes.get(fieldName), target);
	}

	private Object convertTo(Set<Object> objectPath, Type fieldType, Object value) {
		if (value == null || value instanceof FieldValue) {
			return value;
		}
		if (objectPath.contains(value)) {
			throw new IllegalArgumentException("Cyclic references in object");
		}
		objectPath.add(value);
		try {
			Class<?> valueType = value.getClass();
			if (value instanceof Boolean) {
				if (BOOLEAN_COMPATIBLE_TYPES.contains(fieldType)) {
					return value;
				}
			} else if (value instanceof Character) {
				if (CHARACTER_COMPATIBLE_TYPES.contains(fieldType)) {
					return value.toString();
				}
			} else if (value instanceof Byte) {
				if (BYTE_COMPATIBLE_TYPES.contains(fieldType)) {
					return value;
				}
			} else if (value instanceof Short) {
				if (SHORT_COMPATIBLE_TYPES.contains(fieldType)) {
					return value;
				}
			} else if (value instanceof Integer) {
				if (INTEGER_COMPATIBLE_TYPES.contains(fieldType)) {
					return value;
				}
			} else if (value instanceof Long) {
				if (LONG_COMPATIBLE_TYPES.contains(fieldType)) {
					return value;
				}
			} else if (value instanceof Float) {
				if (FLOAT_COMPATIBLE_TYPES.contains(fieldType)) {
					return value;
				}
			} else if (value instanceof Double) {
				if (DOUBLE_COMPATIBLE_TYPES.contains(fieldType)) {
					return value;
				}
			} else if (value instanceof String) {
				if (STRING_COMPATIBLE_TYPES.contains(fieldType)) {
					return value;
				}
			} else if (value instanceof GeoPoint) {
				if (POINT_COMPATIBLE_TYPES.contains(fieldType)) {
					return value;
				}
			} else if (value instanceof DocumentReference) {
				if (REFERENCE_COMPATIBLE_TYPES.contains(fieldType)) {
					return value;
				}
			} else if (value instanceof Timestamp) {
				if (TIMESTAMP_COMPATIBLE_TYPES.contains(fieldType)) {
					return value;
				}
			} else if (value instanceof Instant) {
				if (TIMESTAMP_COMPATIBLE_TYPES.contains(fieldType)) {
					Instant instant = (Instant) value;
					return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
				}
			} else if (value instanceof Blob) {
				if (BLOB_COMPATIBLE_TYPES.contains(fieldType)) {
					return value;
				}
			} else if (value instanceof InputStream) {
				if (BLOB_COMPATIBLE_TYPES.contains(fieldType)) {
					InputStream stream = (InputStream) value;
					ByteString byteString;
					try {
						byteString = ByteString.readFrom(stream);
					} catch (IOException exception) {
						throw new UncheckedIOException(exception);
					}
					return Blob.fromByteString(byteString);
				}
			} else {
				Class<?> rawType;
				Type[] componentTypes;
				if (fieldType instanceof ParameterizedType) {
					ParameterizedType genericType = (ParameterizedType) fieldType;
					rawType = (Class<?>) genericType.getRawType();
					componentTypes = genericType.getActualTypeArguments();
				} else {
					rawType = (Class<?>) fieldType;
					componentTypes = null;
				}
				if (valueType.isArray()) {
					if (rawType.isArray()) {
						return convertArrayTo(objectPath, rawType.getComponentType(), value);
					}
					if (LIST_COMPATIBLE_TYPES.contains(rawType)) {
						if (componentTypes == null) {
							return convertArrayTo(objectPath, Object.class, value);
						}
						return convertArrayTo(objectPath, componentTypes[0], value);
					}
				} else if (value instanceof List) {
					if (rawType.isArray()) {
						return convertListTo(objectPath, rawType.getComponentType(), value);
					}
					if (LIST_COMPATIBLE_TYPES.contains(rawType)) {
						if (componentTypes == null) {
							return convertListTo(objectPath, Object.class, value);
						}
						return convertListTo(objectPath, componentTypes[0], value);
					}
				} else if (value instanceof Map) {
					if (MAP_COMPATIBLE_TYPES.contains(rawType)) {
						if (componentTypes != null) {
							return convertMapTo(objectPath, downcast(componentTypes[0]), componentTypes[1], value);
						}
					}
				} else {
					if (rawType.equals(valueType)) {
						return convertInstanceTo(objectPath, rawType, value);
					}
				}
			}
			throw new ConversionException("Cannot save as %s if declared as %s".formatted(valueType.getName(), fieldType.getTypeName()));
		} finally {
			objectPath.remove(value);
		}
	}

	private List<Object> convertArrayTo(Set<Object> objectPath, Type componentType, Object value) {
		int length = Array.getLength(value);
		List<Object> data = new ArrayList<>();
		for (int i = 0; i < length; i++) {
			Object component = convertTo(objectPath, componentType, Array.get(value, i));
			if (component instanceof List) {
				throw new IllegalArgumentException("Cannot save as array of arrays or array of lists");
			}
			data.add(component);
		}
		return data;
	}

	private List<Object> convertListTo(Set<Object> objectPath, Type componentType, Object value) {
		List<?> list = (List<?>) value;
		List<Object> data = new ArrayList<>();
		for (Object component : list) {
			component = convertTo(objectPath, componentType, component);
			if (component instanceof List) {
				throw new IllegalArgumentException("Cannot save as list of arrays or list of lists");
			}
			data.add(component);
		}
		return data;
	}

	private Map<String, Object> convertMapTo(Set<Object> objectPath, Class<?> keyType, Type componentType, Object value) {
		parserFactory.get(keyType);
		Map<?, ?> map = (Map<?, ?>) value;
		Map<String, Object> data = new HashMap<>();
		for (Object key : map.keySet()) {
			if (!keyType.isAssignableFrom(key.getClass())) {
				throw new IllegalArgumentException("Map key type is supposed to be %s".formatted(keyType.getName()));
			}
			String name = key.toString();
			Object component = map.get(key);
			data.put(name, convertTo(objectPath, componentType, component));
		}
		return data;
	}

	private Object convertInstanceTo(Set<Object> objectPath, Class<?> instanceType, Object value) {
		Handle<?> handle = handleFactory.get(instanceType);
		return handle.buildData(objectPath, value, false, false);
	}

	private <T> Object convertFrom(String fieldName, Object value) {
		@SuppressWarnings("unchecked")
		DaoConverter<?, T> converter = (DaoConverter<?, T>) converters.get(fieldName);
		if (converter == null) {
			return convertFrom(fieldTypes.get(fieldName), value);
		}
		@SuppressWarnings("unchecked")
		T target = (T) convertFrom(targetTypes.get(fieldName), value);
		return converter.from(target);
	}

	private Object convertFrom(Type fieldType, Object value) {
		if (value == null || fieldType.equals(Object.class)) {
			return value;
		}
		Class<?> valueType = value.getClass();
		if (BOOLEAN_TYPES.contains(fieldType)) {
			if (value instanceof Boolean) {
				return value;
			}
		} else if (CHARACTER_TYPES.contains(fieldType)) {
			if (value instanceof String) {
				String s = (String) value;
				if (s.isEmpty()) {
					return '\0';
				}
				return s.charAt(0);
			}
		} else if (NUMBER_TYPES.contains(fieldType)) {
			if (value instanceof Long) {
				return LONG_FUNCTIONS.get(fieldType).apply((Long) value);
			}
			if (value instanceof Double) {
				return DOUBLE_FUNCTIONS.get(fieldType).apply((Double) value);
			}
		} else if (fieldType.equals(String.class)) {
			if (value instanceof String) {
				return value;
			}
		} else if (fieldType.equals(GeoPoint.class)) {
			if (value instanceof GeoPoint) {
				return value;
			}
		} else if (fieldType.equals(DocumentReference.class)) {
			if (value instanceof DocumentReference) {
				return value;
			}
		} else if (TIMESTAMP_TYPES.contains(fieldType)) {
			if (value instanceof Timestamp) {
				Timestamp timestamp = (Timestamp) value;
				if (fieldType.equals(Instant.class)) {
					return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
				}
				return timestamp;
			}
		} else if (BLOB_TYPES.contains(fieldType)) {
			if (value instanceof Blob) {
				Blob blob = (Blob) value;
				if (fieldType.equals(InputStream.class)) {
					return blob.toByteString().newInput();
				}
				return blob;
			}
		} else {
			Class<?> rawType;
			Type[] componentTypes;
			if (fieldType instanceof ParameterizedType) {
				ParameterizedType genericType = (ParameterizedType) fieldType;
				rawType = (Class<?>) genericType.getRawType();
				componentTypes = genericType.getActualTypeArguments();
			} else {
				rawType = (Class<?>) fieldType;
				componentTypes = null;
			}
			if (rawType.isArray()) {
				if (value instanceof List) {
					return convertArrayFrom(rawType.getComponentType(), value);
				}
			} else if (rawType.equals(List.class)) {
				if (value instanceof List) {
					if (componentTypes == null) {
						return convertListFrom(Object.class, value);
					}
					return convertListFrom(componentTypes[0], value);
				}
			} else if (rawType.equals(Map.class)) {
				if (value instanceof Map) {
					if (componentTypes != null) {
						return convertMapFrom(downcast(componentTypes[0]), componentTypes[1], value);
					}
				}
			} else {
				if (value instanceof Map) {
					return convertInstanceFrom(rawType, value);
				}
			}
		}
		throw new ConversionException("Cannot load from %s if declared as %s".formatted(valueType.getName(), fieldType.getTypeName()));
	}

	private Object convertArrayFrom(Class<?> componentType, Object value) {
		List<?> data = (List<?>) value;
		Object array = Array.newInstance(componentType, data.size());
		int i = 0;
		for (Object component : data) {
			Array.set(array, i, convertFrom(componentType, component));
			i++;
		}
		return array;
	}

	private List<Object> convertListFrom(Type componentType, Object value) {
		List<?> data = (List<?>) value;
		List<Object> list = new ArrayList<>();
		for (Object component : data) {
			list.add(convertFrom(componentType, component));
		}
		return list;
	}

	private <K> Map<K, Object> convertMapFrom(Class<K> keyType, Type componentType, Object value) {
		Function<String, K> parser = parserFactory.get(keyType);
		Map<?, ?> data = (Map<?, ?>) value;
		Map<K, Object> map = new HashMap<>();
		for (Object name : data.keySet()) {
			K key = parser.apply((String) name);
			Object component = data.get(name);
			map.put(key, convertFrom(componentType, component));
		}
		return map;
	}

	private Object convertInstanceFrom(Class<?> instanceType, Object value) {
		Map<?, ?> data = (Map<?, ?>) value;
		Map<String, Object> map = new HashMap<>();
		for (Object name : data.keySet()) {
			String key = (String) name;
			Object component = data.get(name);
			map.put(key, component);
		}
		Handle<?> handle = handleFactory.get(instanceType);
		return handle.buildInstance(map);
	}

	private <F> F get(String fieldName, Object instance) {
		return reflector.invokeGetter(getters.get(fieldName), instance);
	}

	private <F> void set(String fieldName, Object instance, F value) {
		reflector.invokeSetter(setters.get(fieldName), instance, value);
	}

	private String rename(String fieldName) {
		String propertyName = propertyNames.get(fieldName);
		if (propertyName == null) {
			return fieldName;
		}
		return propertyName;
	}

	private String revert(String propertyName) {
		String fieldName = reverseNames.get(propertyName);
		if (fieldName == null) {
			return propertyName;
		}
		return fieldName;
	}

	private Class<?> downcast(Type keyType) {
		if (keyType instanceof ParameterizedType) {
			ParameterizedType genericType = (ParameterizedType) keyType;
			return (Class<?>) genericType.getRawType();
		}
		return (Class<?>) keyType;
	}
}
