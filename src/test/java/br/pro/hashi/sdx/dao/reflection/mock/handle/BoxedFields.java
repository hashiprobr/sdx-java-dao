package br.pro.hashi.sdx.dao.reflection.mock.handle;

import br.pro.hashi.sdx.dao.annotation.Key;

public class BoxedFields {
	private @Key Integer integerValue;
	private Float floatValue;

	public BoxedFields() {
		this.integerValue = 1;
		this.floatValue = 2F;
	}

	public Integer getIntegerValue() {
		return integerValue;
	}

	public Float getFloatValue() {
		return floatValue;
	}
}
