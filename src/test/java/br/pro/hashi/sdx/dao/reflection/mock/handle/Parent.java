package br.pro.hashi.sdx.dao.reflection.mock.handle;

import java.util.List;
import java.util.Map;

import br.pro.hashi.sdx.dao.annotation.Converted;
import br.pro.hashi.sdx.dao.annotation.Renamed;
import br.pro.hashi.sdx.dao.reflection.mock.converter.DefaultImplementation;

public class Parent extends GrandParent {
	public static Object staticValue = null;

	String file;
	private @Converted(DefaultImplementation.class) Integer key;
	private @Renamed("not_file_or_key") float notFileOrKey;

	public Parent[] array;
	public List<Map<Integer, Parent>> list;
	public Map<Integer, List<Parent>> map;

	public final Object finalValue;
	public transient Object transientValue;

	public Parent() {
		this.file = null;
		this.key = 0;
		this.notFileOrKey = 0;
		this.finalValue = null;
		this.transientValue = null;
	}

	public float getNotFileOrKey() {
		return notFileOrKey;
	}

	public void setNotFileOrKey(float notFileOrKey) {
		this.notFileOrKey = notFileOrKey;
	}
}
