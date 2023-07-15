package br.pro.hashi.sdx.dao.reflection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Stack;

import br.pro.hashi.sdx.dao.reflection.exception.ReflectionException;

class Reflector {
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

	<E, F> F invokeGetter(MethodHandle getter, E instance) {
		F value;
		try {
			value = (F) getter.invoke(instance);
		} catch (Throwable throwable) {
			throw new AssertionError(throwable);
		}
		return value;
	}

	<E, F> void invokeSetter(MethodHandle setter, E instance, F value) {
		try {
			setter.invoke(instance, value);
		} catch (Throwable throwable) {
			throw new AssertionError(throwable);
		}
	}

	public MethodHandle unreflect(Method method) {
		MethodHandle handle;
		try {
			handle = LOOKUP.unreflect(method);
		} catch (IllegalAccessException exception) {
			throw new AssertionError(exception);
		}
		return handle;
	}

	public <T, S extends T> Type getSpecificType(S object, Class<T> rootType, int rootIndex) {
		Class<?> type = object.getClass();
		TypeVariable<?>[] typeVariables;

		Stack<Node> stack = new Stack<>();
		stack.push(new Node(null, type));

		while (!stack.isEmpty()) {
			Node node = stack.peek();

			if (node.moveToNext()) {
				Class<?> superType = node.getSuperType();

				if (superType != null) {
					if (superType.equals(rootType)) {
						int index = rootIndex;

						while (node != null) {
							ParameterizedType genericSuperType = node.getGenericSuperType();
							Type[] types = genericSuperType.getActualTypeArguments();
							Type specificType = types[index];

							if (!(specificType instanceof TypeVariable)) {
								return specificType;
							}

							typeVariables = node.getTypeParameters();
							index = 0;
							while (!specificType.equals(typeVariables[index])) {
								index++;
							}

							node = node.getSubNode();
						}
					} else {
						stack.push(new Node(node, superType));
					}
				}
			} else {
				stack.pop();
			}
		}

		typeVariables = rootType.getTypeParameters();
		String typeVariableName = typeVariables[rootIndex].getName();
		throw new ReflectionException("Class %s must specify type %s of %s".formatted(type.getName(), typeVariableName, rootType.getName()));
	}

	private class Node {
		private final Node subNode;
		private final Class<?> type;
		private final Class<?>[] interfaces;
		private final Type[] genericInterfaces;
		private int index;

		private Node(Node subNode, Class<?> type) {
			this.subNode = subNode;
			this.type = type;
			this.interfaces = type.getInterfaces();
			this.genericInterfaces = type.getGenericInterfaces();
			this.index = -2;
			// superclass == -1
			// interfaces >= 0
		}

		private Node getSubNode() {
			return subNode;
		}

		private TypeVariable<?>[] getTypeParameters() {
			return type.getTypeParameters();
		}

		private boolean moveToNext() {
			index++;
			return index < interfaces.length;
		}

		private Class<?> getSuperType() {
			Class<?> superType;
			if (index == -1) {
				superType = type.getSuperclass();
			} else {
				superType = interfaces[index];
			}
			return superType;
		}

		private ParameterizedType getGenericSuperType() {
			Type genericType;
			if (index == -1) {
				genericType = type.getGenericSuperclass();
			} else {
				genericType = genericInterfaces[index];
			}
			return (ParameterizedType) genericType;
		}
	}
}
