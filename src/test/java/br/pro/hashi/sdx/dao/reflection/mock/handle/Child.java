package br.pro.hashi.sdx.dao.reflection.mock.handle;

import java.util.List;
import java.util.Map;

import br.pro.hashi.sdx.dao.annotation.Auto;
import br.pro.hashi.sdx.dao.annotation.File;
import br.pro.hashi.sdx.dao.annotation.Key;
import br.pro.hashi.sdx.dao.annotation.Renamed;
import br.pro.hashi.sdx.dao.annotation.Web;

@Renamed(" \t\nChildren \t\n")
public class Child extends Parent {
	public static Object staticValue = null;

	protected @Web @File String file;
	public @Auto @Key String key;

	public List<Map<Integer, Parent[]>> list;
	public Map<Integer, List<Map<Integer, Parent>>> map;

	public final Object finalValue;
	public transient Object transientValue;

	public Child() {
		this.file = null;
		this.key = null;
		this.finalValue = null;
		this.transientValue = null;
	}
}
