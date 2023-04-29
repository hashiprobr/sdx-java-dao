package br.pro.hashi.sdx.dao.reflection.mock.handle;

import br.pro.hashi.sdx.dao.annotation.Auto;
import br.pro.hashi.sdx.dao.annotation.Converted;
import br.pro.hashi.sdx.dao.annotation.Key;
import br.pro.hashi.sdx.dao.annotation.Renamed;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.Address;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.AddressConverter;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.BooleanWrapperConverter;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.ByteWrapperConverter;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.Email;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.EmailConverter;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.Sheet;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.SheetConverter;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.Wrapper;

public class ConvertableFields {
	private @Auto @Key String key;
	private String value;
	private @Converted(EmailConverter.class) Email email;
	private @Converted(AddressConverter.class) Address address;
	private @Converted(SheetConverter.class) Sheet sheet;
	private @Renamed("boolean_wrapper") @Converted(BooleanWrapperConverter.class) Wrapper<Boolean> booleanWrapper;
	private @Renamed("byte_wrapper") @Converted(ByteWrapperConverter.class) Wrapper<Byte> byteWrapper;

	public ConvertableFields() {
		Email email = new Email();
		email.setLogin("convertable");
		email.setDomain("email.com");
		Sheet sheet = new Sheet();
		sheet.addRow("Street 0", 0, "City 0");
		sheet.addRow("Street 1", 1, "City 1");
		this.key = "key";
		this.value = "value";
		this.email = email;
		this.address = new Address("Convertable Street", 0, "Convertable City");
		this.sheet = sheet;
		this.booleanWrapper = new Wrapper<>(true);
		this.byteWrapper = new Wrapper<>((byte) 127);
	}

	public String getKey() {
		return key;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public Email getEmail() {
		return email;
	}

	public Address getAddress() {
		return address;
	}

	public Sheet getSheet() {
		return sheet;
	}

	public Wrapper<Boolean> getBooleanWrapper() {
		return booleanWrapper;
	}

	public Wrapper<Byte> getByteWrapper() {
		return byteWrapper;
	}
}
