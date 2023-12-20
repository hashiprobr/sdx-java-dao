package br.pro.hashi.sdx.dao.reflection.example.handle;

import br.pro.hashi.sdx.dao.annotation.Converted;
import br.pro.hashi.sdx.dao.reflection.example.converter.DefaultImplementation;

public class NonConvertableField {
	private @Converted(DefaultImplementation.class) boolean value;
}
