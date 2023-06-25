package br.pro.hashi.sdx.dao.reflection.mock.reflector.parser;

public class UncheckedMethod {
	public static UncheckedMethod valueOf(String s) throws RuntimeException {
		throw new RuntimeException();
	}
}
