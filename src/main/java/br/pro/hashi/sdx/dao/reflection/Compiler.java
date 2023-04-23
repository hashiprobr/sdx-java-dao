package br.pro.hashi.sdx.dao.reflection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import com.google.cloud.firestore.annotation.PropertyName;

import br.pro.hashi.sdx.dao.DaoConverter;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.StringMemberValue;

public final class Compiler {
	private static final Compiler INSTANCE = new Compiler();
	private static final ClassPool POOL = ClassPool.getDefault();
	private static final Lookup LOOKUP = MethodHandles.lookup();

	public static Compiler getInstance() {
		return INSTANCE;
	}

	private final Reflector reflector;
	private final ConverterFactory converterFactory;
	private final HandleFactory handleFactory;
	private final Map<Handle, Bytecode> cache;
	private final MethodHandle instanceGetter;
	private final String packageName;
	private final String superTypeName;
	private final String handleTypeName;
	private final String propertyAnnotationName;

	Compiler() {
		this(Reflector.getInstance(), ConverterFactory.getInstance(), HandleFactory.getInstance());
	}

	Compiler(Reflector reflector, ConverterFactory converterFactory, HandleFactory factory) {
		this.reflector = reflector;
		this.converterFactory = converterFactory;
		this.handleFactory = factory;
		this.cache = new HashMap<>();
		this.instanceGetter = unreflectInstanceGetter(Proxy.class);
		this.packageName = getClass().getPackageName();
		this.superTypeName = Proxy.class.getName();
		this.handleTypeName = Handle.class.getName();
		this.propertyAnnotationName = PropertyName.class.getName();
	}

	Reflector getReflector() {
		return reflector;
	}

	ConverterFactory getConverterFactory() {
		return converterFactory;
	}

	HandleFactory getHandleFactory() {
		return handleFactory;
	}

	public Class<?> getProxyType(Handle handle) {
		return get(handle).proxyType();
	}

	public <T> Object getProxy(Handle handle, T instance) {
		return reflector.invokeCreator(get(handle).creator(), handle, instance);
	}

	public <T> T getInstance(Object proxy) {
		return reflector.invokeGetter(instanceGetter, proxy);
	}

	private synchronized Bytecode get(Handle handle) {
		Bytecode bytecode = cache.get(handle);
		if (bytecode == null) {
			bytecode = compile(handle);
			cache.put(handle, bytecode);
		}
		return bytecode;
	}

	private Bytecode compile(Handle handle) {
		Class<?> type = handle.getType();
		String typeName = type.getName();
		CtClass ctType = getCtClass(typeName);

		String proxyTypeName = "%s.Proxy%x%x".formatted(packageName, System.nanoTime(), cache.size());
		CtClass ctSuperType = getCtClass(superTypeName);
		CtClass ctProxyType = POOL.makeClass(proxyTypeName, ctSuperType);
		ctProxyType.setModifiers(Modifier.PUBLIC);

		CtClass ctHandleType = getCtClass(handleTypeName);
		createAndAddCtHandleField(ctProxyType, ctHandleType);

		createAndAddCtConstructor(ctProxyType, null, """
				{
					handle = %s.of(%s.class);
					instance = handle.create();
				}""".formatted(handleTypeName, typeName));

		createAndAddCtConstructor(ctProxyType, new CtClass[] { ctHandleType, ctType }, """
				{
					handle = $1;
					instance = $2;
				}""");

		for (String fieldName : handle.getFieldNames()) {
			DaoConverter<?, ?> converter = handle.getConverter(fieldName);
			String propertyName = handle.getPropertyName(fieldName);
			String fieldTypeName = handle.getFieldTypeName(fieldName);

			CtClass ctFieldType;
			if (converter == null) {
				ctFieldType = getCtClass(fieldTypeName);
			} else {
				Class<?> targetType = (Class<?>) converterFactory.getTargetType(converter);
				ctFieldType = getCtClass(targetType.getName());
			}

			char prefix = Character.toUpperCase(fieldName.charAt(0));
			String suffix = "%c%s".formatted(prefix, fieldName.substring(1));

			String methodName, body;

			methodName = "get%s".formatted(suffix);
			body = "return ($r) handle.get(\"%s\", instance);".formatted(fieldName);
			createAndAddCtMethod(ctProxyType, ctFieldType, methodName, null, body, propertyName);

			methodName = "set%s".formatted(suffix);
			body = "handle.set(\"%s\", instance, ($w) $1);".formatted(fieldName);
			createAndAddCtMethod(ctProxyType, CtClass.voidType, methodName, new CtClass[] { ctFieldType }, body, propertyName);
		}

		Class<?> proxyType = toClass(ctProxyType);
		MethodHandle creator = reflector.getCreator(proxyType, Handle.class, type);
		return new Bytecode(proxyType, creator);
	}

	MethodHandle unreflectInstanceGetter(Class<?> type) {
		Field field;
		try {
			field = type.getDeclaredField("instance");
		} catch (NoSuchFieldException exception) {
			throw new AssertionError(exception);
		}
		field.setAccessible(true);
		return reflector.unreflectGetter(field);
	}

	CtClass getCtClass(String typeName) {
		CtClass ctType;
		try {
			ctType = POOL.get(typeName);
		} catch (NotFoundException exception) {
			throw new AssertionError(exception);
		}
		return ctType;
	}

	void createAndAddCtHandleField(CtClass ctType, CtClass ctHandleType) {
		CtField ctHandleField;
		try {
			ctHandleField = new CtField(ctHandleType, "handle", ctType);
		} catch (CannotCompileException exception) {
			throw new AssertionError(exception);
		}
		ctHandleField.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
		try {
			ctType.addField(ctHandleField);
		} catch (CannotCompileException exception) {
			throw new AssertionError(exception);
		}
	}

	void createAndAddCtConstructor(CtClass ctType, CtClass[] parameters, String body) {
		CtConstructor ctConstructor = new CtConstructor(parameters, ctType);
		try {
			ctConstructor.setBody(body);
			ctType.addConstructor(ctConstructor);
		} catch (CannotCompileException exception) {
			throw new AssertionError(exception);
		}
	}

	void createAndAddCtMethod(CtClass ctType, CtClass ctReturnType, String methodName, CtClass[] parameters, String body, String propertyName) {
		CtMethod ctMethod = new CtMethod(ctReturnType, methodName, parameters, ctType);
		try {
			ctMethod.setBody(body);
		} catch (CannotCompileException exception) {
			throw new AssertionError(exception);
		}
		if (propertyName != null) {
			MethodInfo info = ctMethod.getMethodInfo();
			ConstPool pool = info.getConstPool();
			AnnotationsAttribute attribute = new AnnotationsAttribute(pool, AnnotationsAttribute.visibleTag);
			Annotation annotation = new Annotation(propertyAnnotationName, pool);
			annotation.addMemberValue("value", new StringMemberValue(propertyName, pool));
			attribute.addAnnotation(annotation);
			info.addAttribute(attribute);
		}
		try {
			ctType.addMethod(ctMethod);
		} catch (CannotCompileException exception) {
			throw new AssertionError(exception);
		}
	}

	Class<?> toClass(CtClass ctType) {
		Class<?> type;
		try {
			type = ctType.toClass(LOOKUP);
		} catch (CannotCompileException exception) {
			throw new AssertionError(exception);
		}
		return type;
	}

	private record Bytecode(Class<?> proxyType, MethodHandle creator) {
	}
}
