package br.pro.hashi.sdx.dao.reflection.mock.handle;

import br.pro.hashi.sdx.dao.annotation.Converted;
import br.pro.hashi.sdx.dao.annotation.Key;
import br.pro.hashi.sdx.dao.reflection.mock.converter.DefaultImplementation;

public class ConvertedKeyField {
	private @Key @Converted(DefaultImplementation.class) Integer value;
}
