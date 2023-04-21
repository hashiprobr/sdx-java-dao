package br.pro.hashi.sdx.dao.reflection.mock.converter;

public class ImplementationWithDiamond implements InterfaceWithDiamond {
	@Override
	public Double to(Integer source) {
		return null;
	}

	@Override
	public Integer from(Double target) {
		return null;
	}
}
