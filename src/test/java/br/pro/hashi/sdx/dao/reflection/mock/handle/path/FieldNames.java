package br.pro.hashi.sdx.dao.reflection.mock.handle.path;

import java.util.Map;

import br.pro.hashi.sdx.dao.annotation.Converted;
import br.pro.hashi.sdx.dao.annotation.File;

public class FieldNames {
	private @File String file;
	private @Converted(PropertyNamesConverter.class) PropertyNames zeroMap;
	PropertyNames zeroProperty;
	Map<String, PropertyNames> oneProperty;
	Map<String, Map<Integer, PropertyNames>> twoProperty;

	public FieldNames() {
		this.file = null;
		this.zeroMap = null;
		this.zeroProperty = null;
		this.oneProperty = null;
		this.twoProperty = null;
	}
}
