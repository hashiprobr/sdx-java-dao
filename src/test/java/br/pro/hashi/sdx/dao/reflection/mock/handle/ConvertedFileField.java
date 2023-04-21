package br.pro.hashi.sdx.dao.reflection.mock.handle;

import br.pro.hashi.sdx.dao.annotation.Converted;
import br.pro.hashi.sdx.dao.annotation.File;
import br.pro.hashi.sdx.dao.reflection.mock.converter.DefaultImplementation;

public class ConvertedFileField {
	private @File @Converted(DefaultImplementation.class) Integer value;
}
