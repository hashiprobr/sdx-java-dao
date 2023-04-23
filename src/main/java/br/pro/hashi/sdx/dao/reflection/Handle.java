package br.pro.hashi.sdx.dao.reflection;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import br.pro.hashi.sdx.dao.DaoConverter;
import br.pro.hashi.sdx.dao.annotation.Auto;
import br.pro.hashi.sdx.dao.annotation.Converted;
import br.pro.hashi.sdx.dao.annotation.File;
import br.pro.hashi.sdx.dao.annotation.Key;
import br.pro.hashi.sdx.dao.annotation.Renamed;
import br.pro.hashi.sdx.dao.annotation.Web;
import br.pro.hashi.sdx.dao.reflection.exception.AnnotationException;
import br.pro.hashi.sdx.dao.reflection.exception.ReflectionException;

public class Handle {
	public static Handle of(Class<?> type) {
		return HandleFactory.getInstance().get(type);
	}

	private final Reflector reflector;
	private final ConverterFactory factory;
	private final Class<?> type;
	private final MethodHandle creator;
	private final String collectionName;
	private final Map<String, DaoConverter<?, ?>> converters;
	private final Map<String, MethodHandle> getters;
	private final Map<String, MethodHandle> setters;
	private final Map<String, String> propertyNames;
	private final Map<String, String> fieldTypeNames;
	private final Set<String> fieldNames;
	private final Set<String> fileFieldNames;
	private final Set<String> webFieldNames;
	private final String keyFieldName;
	private final boolean autoKey;

	Handle(Class<?> type) {
		this(Reflector.getInstance(), ConverterFactory.getInstance(), type);
	}

	Handle(Reflector reflector, ConverterFactory factory, Class<?> type) {
		String typeName = type.getName();

		MethodHandle creator = reflector.getExternalCreator(type);
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
				throw new AnnotationException(typeName, "Class @Renamed value cannot be blank");
			}
		}

		Map<String, DaoConverter<?, ?>> converters = new HashMap<>();
		Map<String, MethodHandle> getters = new HashMap<>();
		Map<String, MethodHandle> setters = new HashMap<>();
		Map<String, String> propertyNames = new HashMap<>();
		Map<String, String> fieldTypeNames = new HashMap<>();
		Set<String> fieldNames = new HashSet<>();
		Set<String> fileFieldNames = new HashSet<>();
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

						Class<?> fieldType = field.getType();
						fieldTypeNames.put(fieldName, fieldType.getName());

						fieldNames.add(fieldName);

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
							fileFieldNames.add(fieldName);
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
							if (fileFieldNames.contains(fieldName)) {
								throw new AnnotationException(superType, "@Key field cannot be a @File field");
							}
							if (keyFieldName != null) {
								throw new AnnotationException(superType, "Cannot have multiple @Key fields");
							}
							keyFieldName = fieldName;
							if (autoAnnotation != null) {
								autoKey = true;
							}
						}
					}
				}
			}
		}

		if (keyFieldName == null) {
			throw new AnnotationException(typeName, "Must have a @Key field");
		}

		this.reflector = reflector;
		this.factory = factory;
		this.type = type;
		this.creator = creator;
		this.collectionName = collectionName;
		this.converters = converters;
		this.getters = getters;
		this.setters = setters;
		this.propertyNames = propertyNames;
		this.fieldTypeNames = fieldTypeNames;
		this.fieldNames = fieldNames;
		this.fileFieldNames = fileFieldNames;
		this.webFieldNames = webFieldNames;
		this.keyFieldName = keyFieldName;
		this.autoKey = autoKey;
	}

	Reflector getReflector() {
		return reflector;
	}

	ConverterFactory getFactory() {
		return factory;
	}

	Class<?> getType() {
		return type;
	}

	DaoConverter<?, ?> getConverter(String fieldName) {
		return converters.get(fieldName);
	}

	Set<String> getFieldNames() {
		return fieldNames;
	}

	String getPropertyName(String fieldName) {
		return propertyNames.get(fieldName);
	}

	String getFieldTypeName(String fieldName) {
		return fieldTypeNames.get(fieldName);
	}

	Object create() {
		return reflector.invokeCreator(creator);
	}

	Object get(String fieldName, Object instance) {
		Object value = rawGet(fieldName, instance);
		return convertTo(fieldName, value);
	}

	void set(String fieldName, Object instance, Object value) {
		value = convertFrom(fieldName, value);
		rawSet(fieldName, instance, value);
	}

	public String getCollectionName() {
		return collectionName;
	}

	public boolean hasAutoKey() {
		return autoKey;
	}

	public boolean isFile(String fieldName) {
		return fileFieldNames.contains(fieldName);
	}

	public boolean isWeb(String fieldName) {
		return webFieldNames.contains(fieldName);
	}

	public String getFile(String fieldName, Object instance) {
		return rawGet(fieldName, instance);
	}

	public void setFile(String fieldName, Object instance, String value) {
		rawSet(fieldName, instance, value);
	}

	public String getKeyString(Object instance) {
		return rawGet(keyFieldName, instance).toString();
	}

	public void setAutoKey(Object instance, String value) {
		rawSet(keyFieldName, instance, value);
	}

	private <T> T rawGet(String fieldName, Object instance) {
		return reflector.invokeGetter(getters.get(fieldName), instance);
	}

	private <T> void rawSet(String fieldName, Object instance, T value) {
		reflector.invokeSetter(setters.get(fieldName), instance, value);
	}

	public Object toInstance(Map<String, Object> data) {
		Object instance = create();
		for (String fieldName : fieldNames) {
			String propertyName = rename(fieldName);
			Object value = data.get(propertyName);
			if (value != null) {
				set(fieldName, instance, value);
			}
		}
		return instance;
	}

	public Map<String, Object> toData(Object instance) {
		Map<String, Object> data = new HashMap<>();
		for (String fieldName : fieldNames) {
			if (!fieldName.equals(keyFieldName)) {
				String propertyName = rename(fieldName);
				Object value = get(fieldName, instance);
				data.put(propertyName, value);
			}
		}
		return data;
	}

	public Map<String, Object> toData(Map<String, Object> values) {
		Map<String, Object> data = new HashMap<>();
		for (String fieldName : values.keySet()) {
			Object value = values.get(fieldName);
			fieldName = fieldName.strip();
			int index = fieldName.indexOf('.');
			if (index == -1) {
				if (fieldName.equals(keyFieldName)) {
					throw new IllegalArgumentException("Key field cannot be overwritten");
				}
				value = convertTo(fieldName, value);
				fieldName = rename(fieldName);
			} else {
				String prefix = fieldName.substring(0, index);
				String suffix = fieldName.substring(index);
				if (prefix.equals(keyFieldName)) {
					throw new IllegalArgumentException("Key field cannot be modified");
				}
				fieldName = "%s%s".formatted(rename(prefix), suffix);
			}
			data.put(fieldName, value);
		}
		return data;
	}

	private String rename(String fieldName) {
		String propertyName = propertyNames.get(fieldName);
		if (propertyName == null) {
			return fieldName;
		}
		return propertyName;
	}

	@SuppressWarnings("unchecked")
	private <S> Object convertTo(String fieldName, Object value) {
		DaoConverter<S, ?> converter = (DaoConverter<S, ?>) converters.get(fieldName);
		if (converter == null) {
			return value;
		}
		return converter.to((S) value);
	}

	@SuppressWarnings("unchecked")
	private <T> Object convertFrom(String fieldName, Object value) {
		DaoConverter<?, T> converter = (DaoConverter<?, T>) converters.get(fieldName);
		if (converter == null) {
			return value;
		}
		return converter.from((T) value);
	}
}
