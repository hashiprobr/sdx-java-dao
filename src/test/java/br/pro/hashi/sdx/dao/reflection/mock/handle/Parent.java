package br.pro.hashi.sdx.dao.reflection.mock.handle;

import br.pro.hashi.sdx.dao.annotation.Converted;
import br.pro.hashi.sdx.dao.annotation.Renamed;
import br.pro.hashi.sdx.dao.reflection.mock.converter.DefaultImplementation;

public class Parent extends GrandParent {
	public static Object staticValue = null;

	String file;
	private @Converted(DefaultImplementation.class) Integer key;
	private @Renamed("not_file_or_key") double notFileOrKey;

	public final Object finalValue;
	public transient Object transientValue;

	public Parent() {
		this.file = null;
		this.key = 0;
		this.notFileOrKey = 0;
		this.finalValue = null;
		this.transientValue = null;
	}

	public String getFile() {
		return file;
	}

	public void setFile(String file) {
		this.file = file;
	}

	public int getKey() {
		return key;
	}

	public void setKey(int key) {
		this.key = key;
	}

	public double getNotFileOrKey() {
		return notFileOrKey;
	}

	public void setNotFileOrKey(double notFileOrKey) {
		this.notFileOrKey = notFileOrKey;
	}
}
