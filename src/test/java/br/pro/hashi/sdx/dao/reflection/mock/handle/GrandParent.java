package br.pro.hashi.sdx.dao.reflection.mock.handle;

import java.util.List;
import java.util.Map;

import br.pro.hashi.sdx.dao.annotation.File;
import br.pro.hashi.sdx.dao.annotation.Key;

public class GrandParent {
	public static Object staticValue = null;

	private @File("image/png") String file;
	private @Key int key;
	boolean notFileOrKey;

	public Parent parent;
	public List<Parent> list;
	public Map<Integer, Parent> map;

	public final Object finalValue;
	public transient Object transientValue;

	public GrandParent() {
		this.file = null;
		this.key = 0;
		this.notFileOrKey = false;
		this.parent = null;
		this.list = null;
		this.map = null;
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

	public boolean isNotFileOrKey() {
		return notFileOrKey;
	}

	public void setNotFileOrKey(boolean notFileOrKey) {
		this.notFileOrKey = notFileOrKey;
	}
}
