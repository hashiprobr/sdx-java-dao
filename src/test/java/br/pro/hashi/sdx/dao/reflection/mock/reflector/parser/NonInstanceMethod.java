package br.pro.hashi.sdx.dao.reflection.mock.reflector.parser;

public class NonInstanceMethod {
	public static Object valueOf(String s) {
		return new NonInstanceMethod();
	}
}
