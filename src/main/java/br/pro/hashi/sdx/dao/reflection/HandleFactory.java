package br.pro.hashi.sdx.dao.reflection;

import java.util.HashMap;
import java.util.Map;

public class HandleFactory {
	private static final HandleFactory INSTANCE = new HandleFactory();

	public static HandleFactory getInstance() {
		return INSTANCE;
	}

	private final Map<Class<?>, Handle<?>> cache;

	HandleFactory() {
		this.cache = new HashMap<>();
	}

	public <E> Handle<E> get(Class<E> type) {
		@SuppressWarnings("unchecked")
		Handle<E> handle = (Handle<E>) cache.get(type);
		if (handle == null) {
			handle = Handle.newInstance(type);
			cache.put(type, handle);
		}
		return handle;
	}
}
