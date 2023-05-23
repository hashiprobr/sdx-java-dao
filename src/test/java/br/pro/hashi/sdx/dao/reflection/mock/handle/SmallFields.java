package br.pro.hashi.sdx.dao.reflection.mock.handle;

import br.pro.hashi.sdx.dao.annotation.Key;

public class SmallFields {
	private @Key char charValue;
	private short shortValue;

	public SmallFields() {
		this.charValue = 1;
		this.shortValue = 2;
	}

	public char getCharValue() {
		return charValue;
	}

	public short getShortValue() {
		return shortValue;
	}
}
