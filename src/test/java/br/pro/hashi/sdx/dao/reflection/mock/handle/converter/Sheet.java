package br.pro.hashi.sdx.dao.reflection.mock.handle.converter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class Sheet {
	private List<List<String>> rows;

	public Sheet() {
		this.rows = new ArrayList<>();
	}

	public void addRow(String street, int number, String city) {
		rows.add(List.of(street, Integer.toString(number), city));
	}

	public List<String> getRow(int index) {
		return Collections.unmodifiableList(rows.get(index));
	}

	public Iterable<List<String>> getRows() {
		return new Iterable<>() {
			@Override
			public Iterator<List<String>> iterator() {
				return rows.iterator();
			}
		};
	}
}
