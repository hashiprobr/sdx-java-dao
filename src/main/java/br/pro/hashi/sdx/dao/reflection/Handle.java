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

public class Handle<E> {
	private final Reflector reflector;
	private final ConverterFactory factory;
	private final MethodHandle creator;
	private final String collectionName;
	private final Map<String, DaoConverter<?, ?>> converters;
	private final Map<String, MethodHandle> getters;
	private final Map<String, MethodHandle> setters;
	private final Map<String, String> propertyNames;
	private final Map<String, String> contentTypes;
	private final Set<String> fieldNames;
	private final Set<String> webFieldNames;
	private final String keyFieldName;
	private final boolean autoKey;

	// TODO: Replace this class with a constructor if/when
	// Mockito can mock the construction of a generic type.
	static class Construction {
		static <E> Handle<E> of(Class<E> type) {
			return new Handle<>(Reflector.getInstance(), ConverterFactory.getInstance(), type);
		}

		private Construction() {
		}
	}

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
				throw new AnnotationException(typeName, "Class @Renamed value cannot be blank");
			}
		}

		Map<String, DaoConverter<?, ?>> converters = new HashMap<>();
		Map<String, MethodHandle> getters = new HashMap<>();
		Map<String, MethodHandle> setters = new HashMap<>();
		Map<String, String> propertyNames = new HashMap<>();
		Map<String, String> contentTypes = new HashMap<>();
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

						fieldNames.add(fieldName);

						Class<?> fieldType = field.getType();

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
							contentTypes.put(fieldName, fileAnnotation.value());
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
							if (contentTypes.containsKey(fieldName)) {
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
					}
				}
			}
		}

		if (keyFieldName == null) {
			throw new AnnotationException(typeName, "Must have a @Key field");
		}

		this.reflector = reflector;
		this.factory = factory;
		this.creator = creator;
		this.collectionName = collectionName;
		this.converters = converters;
		this.getters = getters;
		this.setters = setters;
		this.propertyNames = propertyNames;
		this.contentTypes = contentTypes;
		this.fieldNames = fieldNames;
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

	public String getCollectionName() {
		return collectionName;
	}

	public Set<String> getFieldNames() {
		return fieldNames;
	}

	public boolean hasAutoKey() {
		return autoKey;
	}

	public String getContentType(String fieldName) {
		return contentTypes.get(fieldName);
	}

	public boolean isFile(String fieldName) {
		return contentTypes.containsKey(fieldName);
	}

	public boolean isWeb(String fieldName) {
		return webFieldNames.contains(fieldName);
	}

	public boolean isKey(String fieldName) {
		return fieldName.equals(keyFieldName);
	}

	public Object getKey(Object instance) {
		return rawGet(keyFieldName, instance);
	}

	public void setKey(Object instance, String value) {
		rawSet(keyFieldName, instance, value);
	}

	public void setKey(Map<String, Object> values, String value) {
		values.put(keyFieldName, value);
	}

	private <V> V rawGet(String fieldName, Object instance) {
		return reflector.invokeGetter(getters.get(fieldName), instance);
	}

	private <V> void rawSet(String fieldName, Object instance, V value) {
		reflector.invokeSetter(setters.get(fieldName), instance, value);
	}

	public E toInstance(Map<String, Object> data) {
		E instance = reflector.invokeCreator(creator);
		for (String fieldName : fieldNames) {
			String propertyName = rename(fieldName);
			Object value = data.get(propertyName);
			if (value != null) {
				rawSet(fieldName, instance, convertFrom(fieldName, value));
			}
		}
		return instance;
	}

	public Map<String, Object> toValues(Map<String, Object> data) {
		Map<String, Object> values = new HashMap<>();
		for (String fieldName : fieldNames) {
			String propertyName = rename(fieldName);
			Object value = data.get(propertyName);
			if (value != null) {
				values.put(fieldName, convertFrom(fieldName, value));
			}
		}
		return values;
	}

	public Map<String, Object> toData(Object instance, boolean ignoreKey) {
		Map<String, Object> data = new HashMap<>();
		for (String fieldName : fieldNames) {
			if (!(ignoreKey && fieldName.equals(keyFieldName)) && contentTypes.get(fieldName) == null) {
				String propertyName = rename(fieldName);
				Object value = convertTo(fieldName, rawGet(fieldName, instance));
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
				if (contentTypes.get(fieldName) != null) {
					throw new IllegalArgumentException("@File fields can only be overwritten by uploadFile");
				}
				if (fieldName.equals(keyFieldName)) {
					throw new IllegalArgumentException("@Key field cannot be overwritten");
				}
				value = convertTo(fieldName, value);
				fieldName = rename(fieldName);
			} else {
				String prefix = fieldName.substring(0, index);
				String suffix = fieldName.substring(index);
				if (contentTypes.get(prefix) != null) {
					throw new IllegalArgumentException("@File fields can only be modified by uploadFile");
				}
				if (prefix.equals(keyFieldName)) {
					throw new IllegalArgumentException("@Key field cannot be modified");
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

	private <S, T> Object convertTo(String fieldName, S value) {
		@SuppressWarnings("unchecked")
		DaoConverter<S, T> converter = (DaoConverter<S, T>) converters.get(fieldName);
		if (converter == null) {
			return value;
		}
		return converter.to(value);
	}

	private <S, T> Object convertFrom(String fieldName, T value) {
		@SuppressWarnings("unchecked")
		DaoConverter<S, T> converter = (DaoConverter<S, T>) converters.get(fieldName);
		if (converter == null) {
			return value;
		}
		return converter.from(value);
	}
}
