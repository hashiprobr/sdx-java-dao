package br.pro.hashi.sdx.dao.exception;

/**
 * Thrown to indicate that...
 */
public class FileException extends DaoException {
	private static final long serialVersionUID = -3539105707086782325L;

	/**
	 * Constructs a {@code FileException} with the specified detail message.
	 * 
	 * @param message the detail message
	 */
	public FileException(String message) {
		super(message);
	}

	/**
	 * Constructs a new exception with the specified cause and a detail message of
	 * {@code (cause == null ? null : cause.toString())}.
	 * 
	 * @param cause the cause
	 */
	public FileException(Throwable cause) {
		super(cause);
	}
}
