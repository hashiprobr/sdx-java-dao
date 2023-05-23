package br.pro.hashi.sdx.dao.reflection.mock.handle;

import br.pro.hashi.sdx.dao.annotation.Key;

public class BoxedFields {
	private @Key Character characterValue;
	private Short shortValue;
	private Integer integerValue;
	private Long longValue;
	private Float floatValue;
	private Double doubleValue;

	public BoxedFields() {
		this.characterValue = 1;
		this.shortValue = 2;
		this.integerValue = 3;
		this.longValue = 4L;
		this.floatValue = 5F;
		this.doubleValue = 6.0;
	}

	public Character getCharacterValue() {
		return characterValue;
	}

	public Short getShortValue() {
		return shortValue;
	}

	public Integer getIntegerValue() {
		return integerValue;
	}

	public Long getLongValue() {
		return longValue;
	}

	public Float getFloatValue() {
		return floatValue;
	}

	public Double getDoubleValue() {
		return doubleValue;
	}
}
