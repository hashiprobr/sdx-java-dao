package br.pro.hashi.sdx.dao.reflection.mock.handle;

import br.pro.hashi.sdx.dao.annotation.Key;
import br.pro.hashi.sdx.dao.annotation.Renamed;

@Renamed(" \t\nChildren \t\n")
public class Child extends Parent {
	public static Object childStaticValue = new Object();

	protected boolean booleanValue;
	public @Key float floatValue;
	private String stringValue;
	public final Object childFinalValue;
	public transient Object childTransientValue;

	public Child() {
		this.booleanValue = false;
		this.floatValue = 2;
		this.stringValue = "c";
		this.childFinalValue = new Object();
		this.childTransientValue = new Object();
	}

	public boolean isBooleanValue() {
		return booleanValue;
	}

	public float getFloatValue() {
		return floatValue;
	}

	public String getStringValue() {
		return stringValue;
	}
}
