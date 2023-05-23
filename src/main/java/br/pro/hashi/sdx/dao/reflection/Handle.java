package br.pro.hashi.sdx.dao.reflection;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Blob;
import com.google.cloud.firestore.FieldValue;

import br.pro.hashi.sdx.dao.DaoConverter;
import br.pro.hashi.sdx.dao.annotation.Auto;
import br.pro.hashi.sdx.dao.annotation.Converted;
import br.pro.hashi.sdx.dao.annotation.File;
import br.pro.hashi.sdx.dao.annotation.Key;
import br.pro.hashi.sdx.dao.annotation.Renamed;
import br.pro.hashi.sdx.dao.annotation.Web;
import br.pro.hashi.sdx.dao.reflection.exception.AnnotationException;
import br.pro.hashi.sdx.dao.reflection.exception.ReflectionException;

public class Handle<E> {
	private static final Set<Class<?>> PRIMITIVE_TYPES = Set.of(
			boolean.class,
			short.class,
			int.class,
			long.class,
			float.class,
			double.class,
			Boolean.class,
			Short.class,
			Integer.class,
			Long.class,
			Float.class,
			Double.class,
			String.class);

	private final Reflector reflector;
	private final MethodHandle creator;
	private final String collectionName;
	private final Map<String, DaoConverter<?, ?>> converters;
	private final Map<String, MethodHandle> getters;
	private final Map<String, MethodHandle> setters;
	private final Map<String, Type> fieldTypes;
	private final Map<String, String> contentTypes;
	private final Map<String, String> propertyNames;
	private final Set<String> fieldNames;
	private final Set<String> webFieldNames;
	private final String keyFieldName;
	private final boolean autoKey;

	Handle(Reflector reflector, ConverterFactory factory, Class<E> type) {
		String typeName = type.getName();

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
		}

		Map<String, DaoConverter<?, ?>> converters = new HashMap<>();
		Map<String, MethodHandle> getters = new HashMap<>();
		Map<String, MethodHandle> setters = new HashMap<>();
		Map<String, Type> fieldTypes = new HashMap<>();
		Map<String, String> contentTypes = new HashMap<>();
		Map<String, String> propertyNames = new HashMap<>();
		Set<String> fieldNames = new HashSet<>();
		Set<String> webFieldNames = new HashSet<>();
		String keyFieldName = null;
		boolean autoKey = false;

		for (Class<?> superType = type; !superType.equals(Object.class); superType = superType.getSuperclass()) {
			for (Field field : superType.getDeclaredFields()) {
				String fieldName = field.getName();
				if (!fieldNames.contains(fieldName)) {
					int modifiers = field.getModifiers();
					if (!(Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || Modifier.isTransient(modifiers))) {
						if (!Modifier.isPublic(modifiers)) {
							field.setAccessible(true);
						}

						Converted convertedAnnotation = field.getDeclaredAnnotation(Converted.class);
						if (convertedAnnotation != null) {
							DaoConverter<?, ?> converter = factory.get(convertedAnnotation.value());
							if (!field.getGenericType().equals(factory.getSourceType(converter))) {
								throw new AnnotationException(superType, "@Converted field type must be the converter source type");
							}
							converters.put(fieldName, converter);
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
							propertyNames.put(fieldName, propertyName);
						}

						Type fieldType = field.getGenericType();

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
						fieldNames.add(fieldName);
					}
				}
			}
		}

		if (keyFieldName == null) {
			throw new AnnotationException(typeName, "Must have a @Key field");
		}

		this.reflector = reflector;
		this.creator = creator;
		this.collectionName = collectionName;
		this.converters = converters;
		this.getters = getters;
		this.setters = setters;
		this.fieldTypes = fieldTypes;
		this.contentTypes = contentTypes;
		this.propertyNames = propertyNames;
		this.fieldNames = fieldNames;
		this.webFieldNames = webFieldNames;
		this.keyFieldName = keyFieldName;
		this.autoKey = autoKey;
	}

	public String getCollectionName() {
		return collectionName;
	}

	public Set<String> getFieldNames() {
		return fieldNames;
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

	public E toInstance(Map<String, Object> data) {
		E instance = reflector.invokeCreator(creator);
		for (String fieldName : fieldNames) {
			String propertyName = rename(fieldName);
			Object value = data.get(propertyName);
			if (value != null) {
				set(fieldName, instance, convertFrom(fieldName, value));
			}
		}
		return instance;
	}

	public Map<String, Object> toValues(Map<String, Object> data) {
		Map<String, List<String>> suffixMap = new HashMap<>();
		for (String alias : data.keySet()) {
			int index = alias.indexOf('.');
			if (index != -1 && data.get(alias) != null) {
				String propertyName = alias.substring(0, index);
				List<String> suffixList = suffixMap.get(propertyName);
				if (suffixList == null) {
					suffixList = new ArrayList<>();
					suffixMap.put(propertyName, suffixList);
				}
				suffixList.add(alias.substring(index));
			}
		}
		Map<String, Object> values = new HashMap<>();
		for (String fieldName : fieldNames) {
			String propertyName = rename(fieldName);
			Object value = data.get(propertyName);
			if (value != null) {
				values.put(fieldName, convertFrom(fieldName, value));
			}
			List<String> suffixList = suffixMap.get(propertyName);
			if (suffixList != null) {
				for (String suffix : suffixList) {
					value = data.get("%s%s".formatted(propertyName, suffix));
					values.put("%s%s".formatted(fieldName, suffix), value);
				}
			}
		}
		return values;
	}

	public Map<String, Object> toData(E instance, boolean withFiles, boolean withKey) {
		Map<String, Object> data = new HashMap<>();
		for (String fieldName : fieldNames) {
			String propertyName = rename(fieldName);
			if (contentTypes.containsKey(fieldName)) {
				if (withFiles) {
					data.put(propertyName, null);
				}
			} else {
				if (!fieldName.equals(keyFieldName) || withKey) {
					Object value = get(fieldName, instance);
					if (value != null) {
						data.put(propertyName, convertTo(fieldName, value));
					}
				}
			}
		}
		return data;
	}

	public Map<String, Object> toData(Map<String, Object> values) {
		Map<String, Object> data = new HashMap<>();
		for (String name : values.keySet()) {
			if (name != null) {
				Object value = values.get(name);
				if (value != null) {
					name = name.strip();
					int index = name.indexOf('.');
					if (index == -1) {
						if (fieldNames.contains(name)) {
							if (contentTypes.containsKey(name)) {
								throw new IllegalArgumentException("@File fields cannot be overwritten");
							}
							if (name.equals(keyFieldName)) {
								throw new IllegalArgumentException("@Key field cannot be overwritten");
							}
							if (!(value instanceof FieldValue)) {
								value = convertTo(name, value);
							}
							data.put(rename(name), value);
						}
					} else {
						String prefix = name.substring(0, index);
						if (fieldNames.contains(prefix)) {
							if (contentTypes.containsKey(prefix)) {
								throw new IllegalArgumentException("@File fields cannot be modified");
							}
							if (prefix.equals(keyFieldName)) {
								throw new IllegalArgumentException("@Key field cannot be modified");
							}
							String suffix = name.substring(index);
							data.put("%s%s".formatted(rename(prefix), suffix), value);
						}
					}
				}
			}
		}
		return data;
	}

	public String[] toAliases(String[] names) {
		String[] aliases = new String[names.length];
		for (int i = 0; i < names.length; i++) {
			aliases[i] = toAlias(names[i]);
		}
		return aliases;
	}

	public String toAlias(String name) {
		String alias;
		if (name == null) {
			alias = name;
		} else {
			name = name.strip();
			int index = name.indexOf('.');
			if (index == -1) {
				alias = rename(name);
			} else {
				String prefix = name.substring(0, index);
				String suffix = name.substring(index);
				alias = "%s%s".formatted(rename(prefix), suffix);
			}
		}
		return alias;
	}

	private <S> Object convertTo(String fieldName, S value) {
		@SuppressWarnings("unchecked")
		DaoConverter<S, ?> converter = (DaoConverter<S, ?>) converters.get(fieldName);
		if (converter == null) {
			Type fieldType = fieldTypes.get(fieldName);
			return convertTo(fieldType, value);
		}
		return converter.to(value);
	}

	private <S> Object convertTo(Type fieldType, S value) {
		if (value instanceof byte[]) {
			return Blob.fromBytes((byte[]) value);
		}
		if (value instanceof Byte) {
			return Blob.fromBytes((new byte[] { (byte) value }));
		}
		if (value instanceof Byte[]) {
			Byte[] boxedBytes = (Byte[]) value;
			byte[] bytes = new byte[boxedBytes.length];
			for (int i = 0; i < boxedBytes.length; i++) {
				bytes[i] = boxedBytes[i];
			}
			return Blob.fromBytes(bytes);
		}
		if (value instanceof Character) {
			return (int) value;
		}
		if (value instanceof Instant) {
			Instant i = (Instant) value;
			return Timestamp.ofTimeSecondsAndNanos(i.getEpochSecond(), i.getNano());
		}
		if (value instanceof ZonedDateTime) {
			ZonedDateTime dt = (ZonedDateTime) value;
			return Timestamp.ofTimeSecondsAndNanos(dt.toEpochSecond(), dt.getNano());
		}
		if (value instanceof OffsetDateTime) {
			OffsetDateTime dt = (OffsetDateTime) value;
			return Timestamp.ofTimeSecondsAndNanos(dt.toEpochSecond(), dt.getNano());
		}
		if (value instanceof OffsetTime) {
			OffsetTime t = (OffsetTime) value;
			return Timestamp.ofTimeSecondsAndNanos(t.toEpochSecond(LocalDate.EPOCH), t.getNano());
		}
		if (value instanceof LocalDateTime) {
			LocalDateTime dt = (LocalDateTime) value;
			return Timestamp.ofTimeSecondsAndNanos(dt.toEpochSecond(ZoneOffset.UTC), dt.getNano());
		}
		if (value instanceof LocalDate) {
			LocalDate d = (LocalDate) value;
			return Timestamp.ofTimeSecondsAndNanos(d.toEpochSecond(LocalTime.MIN, ZoneOffset.UTC), 0);
		}
		if (value instanceof LocalTime) {
			LocalTime t = (LocalTime) value;
			return Timestamp.ofTimeSecondsAndNanos(t.toEpochSecond(LocalDate.EPOCH, ZoneOffset.UTC), t.getNano());
		}
		if (fieldType instanceof ParameterizedType) {
			ParameterizedType genericType = (ParameterizedType) fieldType;
			Type[] componentTypes = genericType.getActualTypeArguments();
			if (value instanceof List) {
				List<?> list = (List<?>) value;
				List<Object> values = new ArrayList<>();
				for (Object component : list) {
					values.add(convertTo(componentTypes[0], component));
				}
				return values;
			}
			if (value instanceof Map && componentTypes[0].equals(String.class)) {
				Map<?, ?> map = (Map<?, ?>) value;
				Map<Object, Object> values = new HashMap<>();
				for (Object name : map.keySet()) {
					values.put(name, convertTo(componentTypes[1], map.get(name)));
				}
				return values;
			}
		} else {
			Class<?> rawType = (Class<?>) fieldType;
			if (rawType.isArray()) {
				Class<?> componentType = rawType.componentType();
				List<Object> values = new ArrayList<>();
				for (int i = 0; i < Array.getLength(value); i++) {
					values.add(convertTo(componentType, Array.get(value, i)));
				}
				return values;
			}
			if (PRIMITIVE_TYPES.contains(rawType)) {
				return value;
			}
		}
		return value;
	}

	private <T> Object convertFrom(String fieldName, T value) {
		@SuppressWarnings("unchecked")
		DaoConverter<?, T> converter = (DaoConverter<?, T>) converters.get(fieldName);
		if (converter == null) {
			Type fieldType = fieldTypes.get(fieldName);
			return convertFrom(fieldType, value);
		}
		return converter.from(value);
	}

	private <T> Object convertFrom(Type fieldType, T value) {
		if (value instanceof Blob) {
			Blob b = (Blob) value;
			if (fieldType.equals(byte.class) || fieldType.equals(Byte.class)) {
				byte[] bytes = b.toBytes();
				return bytes[0];
			}
			if (fieldType.equals(byte[].class)) {
				return b.toBytes();
			}
			if (fieldType.equals(Byte[].class)) {
				byte[] bytes = b.toBytes();
				Byte[] boxedBytes = new Byte[bytes.length];
				for (int i = 0; i < bytes.length; i++) {
					boxedBytes[i] = bytes[i];
				}
				return boxedBytes;
			}
		}
		if (value instanceof Long) {
			Long l = (Long) value;
			if (fieldType.equals(char.class) || fieldType.equals(Character.class)) {
				char[] chars = Character.toChars(l.intValue());
				return chars[0];
			}
			if (fieldType.equals(short.class) || fieldType.equals(Short.class)) {
				return l.shortValue();
			}
			if (fieldType.equals(int.class) || fieldType.equals(Integer.class)) {
				return l.intValue();
			}
		}
		if (value instanceof Double) {
			Double d = (Double) value;
			if (fieldType.equals(float.class) || fieldType.equals(Float.class)) {
				return d.floatValue();
			}
		}
		if (value instanceof Timestamp) {
			Timestamp t = (Timestamp) value;
			Instant i = Instant.ofEpochSecond(t.getSeconds(), t.getNanos());
			if (fieldType.equals(Instant.class)) {
				return i;
			}
			if (fieldType.equals(ZonedDateTime.class)) {
				return ZonedDateTime.ofInstant(i, ZoneId.systemDefault());
			}
			if (fieldType.equals(OffsetDateTime.class)) {
				return OffsetDateTime.ofInstant(i, ZoneId.systemDefault());
			}
			if (fieldType.equals(OffsetTime.class)) {
				return OffsetTime.ofInstant(i, ZoneId.systemDefault());
			}
			if (fieldType.equals(LocalDateTime.class)) {
				return LocalDateTime.ofInstant(i, ZoneOffset.UTC);
			}
			if (fieldType.equals(LocalDate.class)) {
				return LocalDate.ofInstant(i, ZoneOffset.UTC);
			}
			if (fieldType.equals(LocalTime.class)) {
				return LocalTime.ofInstant(i, ZoneOffset.UTC);
			}
		}
		if (value instanceof List) {
			List<?> list = (List<?>) value;
			if (fieldType instanceof ParameterizedType) {
				ParameterizedType genericType = (ParameterizedType) fieldType;
				if (genericType.getRawType().equals(List.class)) {
					Type[] componentTypes = genericType.getActualTypeArguments();
					List<Object> values = new ArrayList<>();
					for (Object component : list) {
						values.add(convertFrom(componentTypes[0], component));
					}
					return values;
				}
			} else {
				Class<?> rawType = (Class<?>) fieldType;
				if (rawType.isArray()) {
					Class<?> componentType = rawType.componentType();
					Object values = Array.newInstance(componentType, list.size());
					int index = 0;
					for (Object component : list) {
						Array.set(values, index, convertFrom(componentType, component));
						index++;
					}
					return values;
				}
			}
		}
		if (value instanceof Map) {
			Map<?, ?> map = (Map<?, ?>) value;
			if (fieldType instanceof ParameterizedType) {
				ParameterizedType genericType = (ParameterizedType) fieldType;
				if (genericType.getRawType().equals(Map.class)) {
					Type[] componentTypes = genericType.getActualTypeArguments();
					Map<Object, Object> values = new HashMap<>();
					for (Object name : map.keySet()) {
						values.put(name, convertFrom(componentTypes[1], map.get(name)));
					}
					return values;
				}
			}
		}
		return value;
	}

	private <F> F get(String fieldName, E instance) {
		return reflector.invokeGetter(getters.get(fieldName), instance);
	}

	private <F> void set(String fieldName, E instance, F value) {
		reflector.invokeSetter(setters.get(fieldName), instance, value);
	}

	private String rename(String fieldName) {
		String propertyName = propertyNames.get(fieldName);
		if (propertyName == null) {
			return fieldName;
		}
		return propertyName;
	}
}
