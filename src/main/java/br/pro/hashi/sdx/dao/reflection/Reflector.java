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

	<E> MethodHandle getCreator(Class<E> type, String typeName) {
		if (Modifier.isAbstract(type.getModifiers())) {
			throw new ReflectionException("Class %s cannot be abstract".formatted(typeName));
		}
		Constructor<E> constructor;
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

	<E> MethodHandle unreflectConstructor(Constructor<E> constructor) {
		MethodHandle creator;
		try {
			creator = LOOKUP.unreflectConstructor(constructor);
		} catch (IllegalAccessException exception) {
			throw new AssertionError(exception);
		}
		return creator;
	}

	<E> E invokeCreator(MethodHandle creator) {
		E instance;
		try {
			instance = (E) creator.invoke();
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

	<V> V invokeGetter(MethodHandle getter, Object instance) {
		V value;
		try {
			value = (V) getter.invoke(instance);
		} catch (Throwable throwable) {
			throw new AssertionError(throwable);
		}
		return value;
	}

	<V> void invokeSetter(MethodHandle setter, Object instance, V value) {
		try {
			setter.invoke(instance, value);
		} catch (Throwable throwable) {
			throw new AssertionError(throwable);
		}
	}
}
