package br.pro.hashi.sdx.dao.reflection.mock.handle;

import br.pro.hashi.sdx.dao.annotation.Auto;
import br.pro.hashi.sdx.dao.annotation.Key;

public class NonKeyAutoField {
	private @Key boolean booleanValue;
	private @Auto String stringValue;
}
