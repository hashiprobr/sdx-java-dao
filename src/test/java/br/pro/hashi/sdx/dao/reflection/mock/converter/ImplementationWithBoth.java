package br.pro.hashi.sdx.dao.reflection.mock.converter;

public class ImplementationWithBoth implements InterfaceWithBoth {
	@Override
	public Double to(Integer source) {
		return null;
	}

	@Override
	public Integer from(Double target) {
		return null;
	}
}
