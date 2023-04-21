package br.pro.hashi.sdx.dao.reflection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import br.pro.hashi.sdx.dao.reflection.exception.ReflectionException;

final class Reflector {
	private static final Reflector INSTANCE = new Reflector();
	private static final Lookup LOOKUP = MethodHandles.lookup();

	static Reflector getInstance() {
		return INSTANCE;
	}

	Reflector() {
	}

	<T> MethodHandle getExternalCreator(Class<T> type) {
		String typeName = type.getName();
		if (Modifier.isAbstract(type.getModifiers())) {
			throw new ReflectionException("Class %s cannot be abstract".formatted(typeName));
		}
		Constructor<T> constructor;
		try {
			constructor = type.getDeclaredConstructor();
		} catch (NoSuchMethodException exception) {
			throw new ReflectionException("Class %s must have a no-args constructor (but not necessarily public)".formatted(typeName));
		}
		if (!Modifier.isPublic(constructor.getModifiers())) {
			constructor.setAccessible(true);
		}
		return unreflectConstructor(constructor);
	}

	<T> MethodHandle getCreator(Class<T> type, Class<?>... argTypes) {
		Constructor<T> constructor;
		try {
			constructor = type.getDeclaredConstructor(argTypes);
		} catch (NoSuchMethodException exception) {
			throw new AssertionError(exception);
		}
		return unreflectConstructor(constructor);
	}

	MethodHandle unreflectConstructor(Constructor<?> constructor) {
		MethodHandle creator;
		try {
			creator = LOOKUP.unreflectConstructor(constructor);
		} catch (IllegalAccessException exception) {
			throw new AssertionError(exception);
		}
		return creator;
	}

	Object invokeCreator(MethodHandle creator, Object... args) {
		Object instance;
		try {
			instance = creator.invokeWithArguments(args);
		} catch (Throwable throwable) {
			throw new AssertionError(throwable);
		}
		return instance;
	}

	MethodHandle unreflectGetter(Field field) {
		MethodHandle getter;
		try {
			getter = LOOKUP.unreflectGetter(field);
		} catch (IllegalAccessException exception) {
			throw new AssertionError(exception);
		}
		return getter;
	}

	MethodHandle unreflectSetter(Field field) {
		MethodHandle setter;
		try {
			setter = LOOKUP.unreflectSetter(field);
		} catch (IllegalAccessException exception) {
			throw new AssertionError(exception);
		}
		return setter;
	}

	<T> T invokeGetter(MethodHandle getter, Object instance) {
		T value;
		try {
			value = (T) getter.invoke(instance);
		} catch (Throwable throwable) {
			throw new AssertionError(throwable);
		}
		return value;
	}

	<T> void invokeSetter(MethodHandle setter, Object instance, T value) {
		try {
			setter.invoke(instance, value);
		} catch (Throwable throwable) {
			throw new AssertionError(throwable);
		}
	}
}
