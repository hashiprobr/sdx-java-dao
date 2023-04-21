package br.pro.hashi.sdx.dao.reflection.mock.converter;

public class ImplementationWithSource<B> implements InterfaceWithSource<B> {
	@Override
	public B to(Integer source) {
		return null;
	}

	@Override
	public Integer from(B target) {
		return null;
	}
}
