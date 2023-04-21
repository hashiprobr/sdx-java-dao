package br.pro.hashi.sdx.dao.reflection.mock.handle.converter;

import br.pro.hashi.sdx.dao.DaoConverter;

public class BooleanWrapperConverter implements DaoConverter<Wrapper<Boolean>, String> {
	@Override
	public String to(Wrapper<Boolean> source) {
		return Boolean.toString(source.getValue());
	}

	@Override
	public Wrapper<Boolean> from(String target) {
		return new Wrapper<>(Boolean.parseBoolean(target));
	}
}
