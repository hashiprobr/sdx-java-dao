package br.pro.hashi.sdx.dao;

/**
 * <p>
 * A converter can convert objects of a source type to and from objects of a
 * target type.
 * </p>
 * <p>
 * The idea is that the source type is not supported by Google Cloud Firestore
 * but the target type is.
 * </p>
 * 
 * @param <S> the source type
 * @param <T> the target type
 */
public interface DaoConverter<S, T> {
	/**
	 * Converts an object of the source type to an object of the target type.
	 * 
	 * @param source an object of type {@code S}
	 * @return an object of type {@code T}
	 */
	T to(S source);

	/**
	 * Converts an object of the source type from an object of the target type.
	 * 
	 * @param target an object of type {@code T}
	 * @return an object of type {@code S}
	 */
	S from(T target);
}
