package br.pro.hashi.sdx.dao.reflection;

import java.util.HashMap;
import java.util.Map;

public class HandleFactory {
	public static HandleFactory getInstance() {
		Reflector reflector = new Reflector();
		ParserFactory parserFactory = new ParserFactory(reflector);
		ConverterFactory converterFactory = new ConverterFactory(reflector);
		return new HandleFactory(reflector, parserFactory, converterFactory);
	}

	private final Reflector reflector;
	private final ParserFactory parserFactory;
	private final ConverterFactory converterFactory;
	private final Map<Class<?>, Handle<?>> cache;

	HandleFactory(Reflector reflector, ParserFactory parserFactory, ConverterFactory converterFactory) {
		this.reflector = reflector;
		this.parserFactory = parserFactory;
		this.converterFactory = converterFactory;
		this.cache = new HashMap<>();
	}

	public <E> Handle<E> get(Class<E> type) {
		@SuppressWarnings("unchecked")
		Handle<E> handle = (Handle<E>) cache.get(type);
		if (handle == null) {
			handle = new Handle<>(reflector, parserFactory, converterFactory, this, type);
			cache.put(type, handle);
		}
		return handle;
	}
}
