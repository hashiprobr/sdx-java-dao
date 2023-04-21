package br.pro.hashi.sdx.dao.reflection.mock.converter;

public class ImplementationWithNeither<B, A> implements InterfaceWithNeither<B, A> {
	@Override
	public B to(A source) {
		return null;
	}

	@Override
	public A from(B target) {
		return null;
	}
}
