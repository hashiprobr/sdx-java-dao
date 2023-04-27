package br.pro.hashi.sdx.dao.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import br.pro.hashi.sdx.dao.DaoConverter;

/**
 * Indicates that the field should be converted.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Converted {
	/**
	 * The type of the converter.
	 * 
	 * @return a subtype of {@link DaoConverter}
	 */
	Class<? extends DaoConverter<?, ?>> value();
}
