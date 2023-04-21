package br.pro.hashi.sdx.dao.reflection.mock.handle.converter;

import java.util.ArrayList;
import java.util.List;

import br.pro.hashi.sdx.dao.DaoConverter;

public class SheetConverter implements DaoConverter<Sheet, List<Address>> {
	@Override
	public List<Address> to(Sheet source) {
		List<Address> target = new ArrayList<>();
		for (List<String> row : source.getRows()) {
			target.add(new Address(row.get(0), Integer.parseInt(row.get(1)), row.get(2)));
		}
		return target;
	}

	@Override
	public Sheet from(List<Address> target) {
		Sheet sheet = new Sheet();
		for (Address address : target) {
			sheet.addRow(address.getStreet(), address.getNumber(), address.getCity());
		}
		return sheet;
	}
}
