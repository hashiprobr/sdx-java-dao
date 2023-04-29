package br.pro.hashi.sdx.dao.reflection.mock.handle;

import br.pro.hashi.sdx.dao.annotation.File;
import br.pro.hashi.sdx.dao.annotation.Key;
import br.pro.hashi.sdx.dao.annotation.Renamed;
import br.pro.hashi.sdx.dao.annotation.Web;

public class GrandChild extends Child {
	private @Key @Renamed("boolean_value") boolean booleanValue;
	public double doubleValue;
	private @Web @File(" \t\nimage/png \t\n") @Renamed("string_value") String stringValue;

	public GrandChild() {
		this.booleanValue = true;
		this.doubleValue = 3;
		this.stringValue = "g";
	}

	public boolean isBooleanValue() {
		return booleanValue;
	}

	public double getDoubleValue() {
		return doubleValue;
	}

	public String getStringValue() {
		return stringValue;
	}
}
