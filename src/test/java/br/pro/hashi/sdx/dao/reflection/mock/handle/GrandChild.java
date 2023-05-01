package br.pro.hashi.sdx.dao.reflection.mock.handle;

import br.pro.hashi.sdx.dao.annotation.File;
import br.pro.hashi.sdx.dao.annotation.Key;
import br.pro.hashi.sdx.dao.annotation.Renamed;
import br.pro.hashi.sdx.dao.annotation.Web;

public class GrandChild extends Child {
	private @Key @Renamed("boolean_value") boolean booleanValue;
	public float floatValue;
	private @Web @File(" \t\nimage/png \t\n") @Renamed("string_value") String stringValue;

	public GrandChild() {
		this.booleanValue = true;
		this.floatValue = 3;
		this.stringValue = "g";
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
