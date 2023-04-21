package br.pro.hashi.sdx.dao.reflection.mock.handle.converter;

import br.pro.hashi.sdx.dao.DaoConverter;

public class EmailConverter implements DaoConverter<Email, String> {
	@Override
	public String to(Email source) {
		return "%s@%s".formatted(source.getLogin(), source.getDomain());
	}

	@Override
	public Email from(String target) {
		String[] items = target.split("@");
		Email email = new Email();
		email.setLogin(items[0]);
		email.setDomain(items[1]);
		return email;
	}
}
