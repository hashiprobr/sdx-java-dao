package br.pro.hashi.sdx.dao.reflection.mock.converter;

public class ImplementationWithTarget<A> implements InterfaceWithTarget<A> {
	@Override
	public Double to(A source) {
		return null;
	}

	@Override
	public A from(Double target) {
		return null;
	}
}
