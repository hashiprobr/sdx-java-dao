package br.pro.hashi.sdx.dao.reflection.mock.handle;

import br.pro.hashi.sdx.dao.annotation.Key;

public class BoxedFields {
	private @Key Byte byteValue;
	private Short shortValue;
	private Integer integerValue;
	private Float floatValue;

	public BoxedFields() {
		this.byteValue = 1;
		this.shortValue = 2;
		this.integerValue = 3;
		this.floatValue = 4F;
	}

	public Byte getByteValue() {
		return byteValue;
	}

	public Short getShortValue() {
		return shortValue;
	}

	public Integer getIntegerValue() {
		return integerValue;
	}

	public Float getFloatValue() {
		return floatValue;
	}
}
