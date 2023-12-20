package br.pro.hashi.sdx.dao.reflection.example.handle.type;

import java.util.List;
import java.util.Map;

public class Recursive {
	Recursive value;
	Recursive[] array;
	List<Recursive[]> list;
	Map<String, List<Recursive[]>> map;

	public Recursive() {
		this.value = null;
		this.array = null;
		this.list = null;
		this.map = null;
	}

	public void setValue(Recursive value) {
		this.value = value;
	}
}
