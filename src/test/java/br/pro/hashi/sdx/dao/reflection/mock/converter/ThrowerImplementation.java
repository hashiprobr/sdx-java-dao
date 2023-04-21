package br.pro.hashi.sdx.dao.reflection.mock.converter;

import br.pro.hashi.sdx.dao.DaoConverter;

public class ThrowerImplementation implements DaoConverter<Integer, Double> {
	public ThrowerImplementation() throws Throwable {
		throw new Throwable();
	}

	@Override
	public Double to(Integer source) {
		return null;
	}

	@Override
	public Integer from(Double target) {
		return null;
	}
}
