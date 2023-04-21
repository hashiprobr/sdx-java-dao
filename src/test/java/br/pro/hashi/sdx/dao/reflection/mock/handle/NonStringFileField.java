package br.pro.hashi.sdx.dao.reflection.mock.handle;

import br.pro.hashi.sdx.dao.annotation.File;
import br.pro.hashi.sdx.dao.annotation.Key;

public class NonStringFileField {
	private @Key boolean booleanValue;
	private @File Object objectValue;
}
