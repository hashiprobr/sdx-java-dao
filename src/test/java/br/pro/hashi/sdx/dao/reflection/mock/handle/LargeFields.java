package br.pro.hashi.sdx.dao.reflection.mock.handle;

import br.pro.hashi.sdx.dao.annotation.Key;

public class LargeFields {
	private @Key long longValue;
	private double doubleValue;

	public LargeFields() {
		this.longValue = 1;
		this.doubleValue = 2;
	}

	public long getLongValue() {
		return longValue;
	}

	public double getDoubleValue() {
		return doubleValue;
	}
}
