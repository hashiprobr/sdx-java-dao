package br.pro.hashi.sdx.dao.reflection;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import br.pro.hashi.sdx.dao.DaoConverter;
import br.pro.hashi.sdx.dao.reflection.exception.ReflectionException;

final class ConverterFactory {
	private static final ConverterFactory INSTANCE = new ConverterFactory();

	static ConverterFactory getInstance() {
		return INSTANCE;
	}

	private final Reflector reflector;
	private final Map<Class<? extends DaoConverter<?, ?>>, DaoConverter<?, ?>> cache;

	ConverterFactory() {
		this(Reflector.getInstance());
	}

	ConverterFactory(Reflector reflector) {
		this.reflector = reflector;
		this.cache = new HashMap<>();
	}

	Reflector getReflector() {
		return reflector;
	}

	synchronized DaoConverter<?, ?> get(Class<? extends DaoConverter<?, ?>> type) {
		DaoConverter<?, ?> converter = cache.get(type);
		if (converter == null) {
			MethodHandle creator = reflector.getExternalCreator(type);
			try {
				converter = (DaoConverter<?, ?>) creator.invoke();
			} catch (Throwable throwable) {
				throw new ReflectionException(throwable);
			}
			cache.put(type, converter);
		}
		return converter;
	}

	Type getSourceType(DaoConverter<?, ?> converter) {
		Class<?> type = converter.getClass();
		TypeVariable<?>[] typeVariables;

		Stack<Node> stack = new Stack<>();
		stack.push(new Node(null, type));

		while (!stack.isEmpty()) {
			Node node = stack.peek();

			if (node.moveToNext()) {
				Class<?> superType = node.getSuperType();

				if (superType != null) {
					if (superType.equals(DaoConverter.class)) {
						int index = 0;

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

		typeVariables = DaoConverter.class.getTypeParameters();
		String typeVariableName = typeVariables[0].getName();
		throw new ReflectionException("Class %s must specify type %s".formatted(type.getName(), typeVariableName));
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
