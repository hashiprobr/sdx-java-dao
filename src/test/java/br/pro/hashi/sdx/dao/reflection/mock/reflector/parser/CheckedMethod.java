package br.pro.hashi.sdx.dao.reflection.mock.reflector.parser;

public class CheckedMethod {
	public static CheckedMethod valueOf(String s) throws Exception {
		throw new Exception();
	}
}
