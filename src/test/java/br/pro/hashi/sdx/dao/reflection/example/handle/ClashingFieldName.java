package br.pro.hashi.sdx.dao.reflection.example.handle;

import br.pro.hashi.sdx.dao.annotation.Renamed;

public class ClashingFieldName {
	private @Renamed("doubleValue") int intValue;
	double doubleValue;
}
