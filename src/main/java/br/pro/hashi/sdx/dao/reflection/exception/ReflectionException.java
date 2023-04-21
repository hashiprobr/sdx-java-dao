package br.pro.hashi.sdx.dao.reflection.exception;

public class ReflectionException extends RuntimeException {
	private static final long serialVersionUID = -6858521464400897508L;

	public ReflectionException(String message) {
		super(message);
	}

	public ReflectionException(Throwable cause) {
		super(cause);
	}
}
