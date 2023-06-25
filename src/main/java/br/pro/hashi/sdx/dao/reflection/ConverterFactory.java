package br.pro.hashi.sdx.dao.reflection;

import java.lang.invoke.MethodHandle;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.pro.hashi.sdx.dao.DaoConverter;
import br.pro.hashi.sdx.dao.reflection.exception.ReflectionException;

class ConverterFactory {
	private final Logger logger;
	private final Reflector reflector;
	private final Map<Class<? extends DaoConverter<?, ?>>, DaoConverter<?, ?>> cache;

	ConverterFactory(Reflector reflector) {
		this.logger = LoggerFactory.getLogger(ConverterFactory.class);
		this.reflector = reflector;
		this.cache = new HashMap<>();
	}

	DaoConverter<?, ?> get(Class<? extends DaoConverter<?, ?>> type) {
		DaoConverter<?, ?> converter = cache.get(type);
		if (converter == null) {
			String typeName = type.getName();
			MethodHandle creator = reflector.getCreator(type, typeName);
			try {
				converter = (DaoConverter<?, ?>) creator.invoke();
			} catch (Throwable throwable) {
				throw new ReflectionException(throwable);
			}
			cache.put(type, converter);
			logger.info("Registered %s".formatted(typeName));
		}
		return converter;
	}
}
