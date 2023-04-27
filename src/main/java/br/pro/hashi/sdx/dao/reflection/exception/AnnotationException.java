package br.pro.hashi.sdx.dao.reflection.exception;

public class AnnotationException extends ReflectionException {
	private static final long serialVersionUID = -6485469435080284273L;

	public AnnotationException(Class<?> type, String message) {
		this(type.getName(), message);
	}

	public AnnotationException(String typeName, String message) {
		super("%s: %s".formatted(typeName, message));
	}
}
