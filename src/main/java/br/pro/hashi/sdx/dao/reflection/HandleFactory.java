package br.pro.hashi.sdx.dao.reflection;

import java.util.HashMap;
import java.util.Map;

public class HandleFactory {
	public static HandleFactory getInstance() {
		Reflector reflector = new Reflector();
		ConverterFactory factory = new ConverterFactory(reflector);
		return new HandleFactory(reflector, factory);
	}

	private final Reflector reflector;
	private final ConverterFactory factory;
	private final Map<Class<?>, Handle<?>> cache;

	HandleFactory(Reflector reflector, ConverterFactory factory) {
		this.reflector = reflector;
		this.factory = factory;
		this.cache = new HashMap<>();
	}

	public <E> Handle<E> get(Class<E> type) {
		@SuppressWarnings("unchecked")
		Handle<E> handle = (Handle<E>) cache.get(type);
		if (handle == null) {
			handle = new Handle<>(reflector, factory, type);
			cache.put(type, handle);
		}
		return handle;
	}
}
