package br.pro.hashi.sdx.dao.reflection.mock.handle;

import br.pro.hashi.sdx.dao.annotation.Key;

public class SmallFields {
	private @Key byte byteValue;
	private short shortValue;

	public SmallFields() {
		this.byteValue = 1;
		this.shortValue = 2;
	}

	public byte getByteValue() {
		return byteValue;
	}

	public short getShortValue() {
		return shortValue;
	}
}
