package br.pro.hashi.sdx.dao.reflection.mock.handle;

import br.pro.hashi.sdx.dao.annotation.Converted;
import br.pro.hashi.sdx.dao.reflection.mock.converter.DefaultImplementation;

public class NonConvertableField {
	private @Converted(DefaultImplementation.class) boolean value;
}
