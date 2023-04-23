package br.pro.hashi.sdx.dao.reflection.mock.handle;

import br.pro.hashi.sdx.dao.annotation.Auto;
import br.pro.hashi.sdx.dao.annotation.File;
import br.pro.hashi.sdx.dao.annotation.Key;
import br.pro.hashi.sdx.dao.annotation.Renamed;
import br.pro.hashi.sdx.dao.annotation.Web;

public class Parent {
	public static Object parentStaticValue = new Object();

	private @Auto @Key @Renamed("boolean_value") boolean booleanValue;
	int intValue;
	private @Web @File @Renamed("string_value") String stringValue;
	public final Object parentFinalValue;
	public transient Object parentTransientValue;

	public Parent() {
		this.booleanValue = true;
		this.intValue = 1;
		this.stringValue = "p";
		this.parentFinalValue = new Object();
		this.parentTransientValue = new Object();
	}

	public boolean isBooleanValue() {
		return booleanValue;
	}

	public int getIntValue() {
		return intValue;
	}

	public String getStringValue() {
		return stringValue;
	}
}
