package br.pro.hashi.sdx.dao.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the type should use a specified collection name or the field
 * should use a specified property name.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.FIELD })
public @interface Renamed {
	/**
	 * The name of the collection or the property.
	 * 
	 * @return a string representing the name
	 */
	String value();
}
