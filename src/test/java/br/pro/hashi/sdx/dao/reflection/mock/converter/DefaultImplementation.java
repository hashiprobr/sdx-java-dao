package br.pro.hashi.sdx.dao.reflection.mock.converter;

import br.pro.hashi.sdx.dao.DaoConverter;

public class DefaultImplementation implements DaoConverter<Integer, Double> {
	@Override
	public Double to(Integer source) {
		return source.doubleValue();
	}

	@Override
	public Integer from(Double target) {
		return target.intValue();
	}
}
