package br.pro.hashi.sdx.dao.reflection.mock.handle;

import br.pro.hashi.sdx.dao.annotation.Renamed;

public class ClashingPropertyName {
	int intValue;
	private @Renamed("intValue") double doubleValue;
}
