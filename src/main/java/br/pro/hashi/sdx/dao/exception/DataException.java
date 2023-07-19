package br.pro.hashi.sdx.dao.exception;

/**
 * Thrown to indicate that a Google Cloud Firestore operation could not be
 * performed.
 */
public class DataException extends DaoException {
	private static final long serialVersionUID = -3166598496952999214L;

	/**
	 * Constructs a new exception with the specified cause and a detail message of
	 * {@code (cause == null ? null : cause.toString())}.
	 *
	 * @param cause the cause
	 */
	public DataException(Throwable cause) {
		super(cause);
	}
}
