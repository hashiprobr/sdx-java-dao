package br.pro.hashi.sdx.dao.reflection.mock.handle;

import br.pro.hashi.sdx.dao.annotation.Renamed;

public class ClashingFieldName {
	private @Renamed("doubleValue") int intValue;
	double doubleValue;
}
