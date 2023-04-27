package br.pro.hashi.sdx.dao.reflection;

import java.util.HashMap;
import java.util.Map;

import br.pro.hashi.sdx.dao.reflection.Handle.Construction;

public final class HandleFactory {
	private static final HandleFactory INSTANCE = new HandleFactory();

	public static HandleFactory getInstance() {
		return INSTANCE;
	}

	private final ConverterFactory converterFactory;
	private final Map<Class<?>, Handle<?>> cache;

	HandleFactory() {
		this(ConverterFactory.getInstance());
	}

	HandleFactory(ConverterFactory converterFactory) {
		this.converterFactory = converterFactory;
		this.cache = new HashMap<>();
	}

	ConverterFactory getConverterFactory() {
		return converterFactory;
	}

	public <E> Handle<E> get(Class<E> type) {
		@SuppressWarnings("unchecked")
		Handle<E> handle = (Handle<E>) cache.get(type);
		if (handle == null) {
			handle = Construction.of(converterFactory, type);
			cache.put(type, handle);
		}
		return handle;
	}
}
