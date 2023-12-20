package br.pro.hashi.sdx.dao.reflection.example.handle;

import br.pro.hashi.sdx.dao.annotation.Converted;
import br.pro.hashi.sdx.dao.annotation.Key;
import br.pro.hashi.sdx.dao.reflection.example.converter.DefaultImplementation;

public class ConvertedKeyField {
	private @Key @Converted(DefaultImplementation.class) Integer value;
}
