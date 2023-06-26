package br.pro.hashi.sdx.dao.reflection.mock.handle;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import br.pro.hashi.sdx.dao.annotation.Converted;
import br.pro.hashi.sdx.dao.annotation.Renamed;
import br.pro.hashi.sdx.dao.reflection.mock.converter.DefaultImplementation;

public class Parent extends GrandParent {
	public static Object staticValue = null;

	private String file;
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
		this.array = null;
		this.list = null;
		this.map = null;
		this.finalValue = null;
		this.transientValue = null;
	}

	@Override
	public String getFile() {
		return file;
	}

	@Override
	public void setFile(String file) {
		this.file = file;
	}

	public Integer getBoxedKey() {
		return key;
	}

	public void setBoxedKey(Integer key) {
		this.key = key;
	}

	public float getNotFileOrKey() {
		return notFileOrKey;
	}

	public void setNotFileOrKey(float notFileOrKey) {
		this.notFileOrKey = notFileOrKey;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Parent)) {
			return false;
		}
		Parent p = (Parent) o;
		if (!Objects.equals(file, p.file)) {
			return false;
		}
		if (key != p.key) {
			return false;
		}
		if (notFileOrKey != p.notFileOrKey) {
			return false;
		}
		if (!Objects.equals(parent, p.parent)) {
			return false;
		}
		if (!Objects.equals(array, p.array)) {
			return false;
		}
		if (!Objects.equals(list, p.list)) {
			return false;
		}
		if (!Objects.equals(map, p.map)) {
			return false;
		}
		return true;
	}
}
