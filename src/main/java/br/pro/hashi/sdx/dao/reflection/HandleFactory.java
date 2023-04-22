package br.pro.hashi.sdx.dao.reflection;

import java.util.HashMap;
import java.util.Map;

public final class HandleFactory {
	private static final HandleFactory INSTANCE = new HandleFactory();

	public static HandleFactory getInstance() {
		return INSTANCE;
	}

	private final Map<Class<?>, Handle> cache;

	HandleFactory() {
		this.cache = new HashMap<>();
	}

	public synchronized Handle get(Class<?> type) {
		Handle handle = cache.get(type);
		if (handle == null) {
			handle = new Handle(type);
			cache.put(type, handle);
		}
		return handle;
	}
}
