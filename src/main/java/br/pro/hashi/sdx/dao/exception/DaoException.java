package br.pro.hashi.sdx.dao.exception;

/**
 * Thrown to indicate that a Google Cloud Firestore or Storage operation could
 * not be performed.
 */
public abstract class DaoException extends RuntimeException {
	private static final long serialVersionUID = -7520252328860960831L;

	DaoException(String message) {
		super(message);
	}

	DaoException(Throwable cause) {
		super(cause);
	}
}
