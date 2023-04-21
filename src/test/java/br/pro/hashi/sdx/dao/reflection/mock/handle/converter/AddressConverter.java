package br.pro.hashi.sdx.dao.reflection.mock.handle.converter;

import java.util.List;

import br.pro.hashi.sdx.dao.DaoConverter;

public class AddressConverter implements DaoConverter<Address, List<String>> {
	@Override
	public List<String> to(Address source) {
		return List.of(source.getCity(), Integer.toString(source.getNumber()), source.getStreet());
	}

	@Override
	public Address from(List<String> target) {
		return new Address(target.get(2), Integer.parseInt(target.get(1)), target.get(0));
	}
}
