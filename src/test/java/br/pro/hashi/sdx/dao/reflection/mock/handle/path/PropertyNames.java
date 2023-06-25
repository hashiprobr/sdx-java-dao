package br.pro.hashi.sdx.dao.reflection.mock.handle.path;

import java.util.Map;

import br.pro.hashi.sdx.dao.annotation.Converted;
import br.pro.hashi.sdx.dao.annotation.Key;
import br.pro.hashi.sdx.dao.annotation.Renamed;

public class PropertyNames {
	private @Key String key;
	private @Renamed("zero_map") @Converted(FieldNamesConverter.class) FieldNames zeroMap;
	private @Renamed("zero_field") FieldNames zeroField;
	private @Renamed("one_field") Map<String, FieldNames> oneField;
	private @Renamed("two_field") Map<String, Map<Integer, FieldNames>> twoField;

	public PropertyNames() {
		this.key = null;
		this.zeroMap = null;
		this.zeroField = null;
		this.oneField = null;
		this.twoField = null;
	}
}
