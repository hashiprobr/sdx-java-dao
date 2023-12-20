package br.pro.hashi.sdx.dao.reflection.example.handle.type;

import br.pro.hashi.sdx.dao.annotation.Converted;
import br.pro.hashi.sdx.dao.reflection.example.handle.converter.Address;
import br.pro.hashi.sdx.dao.reflection.example.handle.converter.AddressConverter;
import br.pro.hashi.sdx.dao.reflection.example.handle.converter.BooleanWrapperConverter;
import br.pro.hashi.sdx.dao.reflection.example.handle.converter.ByteWrapperConverter;
import br.pro.hashi.sdx.dao.reflection.example.handle.converter.Email;
import br.pro.hashi.sdx.dao.reflection.example.handle.converter.EmailConverter;
import br.pro.hashi.sdx.dao.reflection.example.handle.converter.Wrapper;

public class ConvertableFields {
	private @Converted(EmailConverter.class) Email email;
	private @Converted(AddressConverter.class) Address address;
	private @Converted(BooleanWrapperConverter.class) Wrapper<Boolean> booleanWrapper;
	private @Converted(ByteWrapperConverter.class) Wrapper<Byte> byteWrapper;
}
