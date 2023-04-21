package br.pro.hashi.sdx.dao.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a collection or a property should use a specified name.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.FIELD })
public @interface Named {
	/**
	 * The name of the collection or the property.
	 * 
	 * @return a string representing the name
	 */
	String value();
}
