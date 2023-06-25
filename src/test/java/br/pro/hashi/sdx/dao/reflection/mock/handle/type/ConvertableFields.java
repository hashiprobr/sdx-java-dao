package br.pro.hashi.sdx.dao.reflection.mock.handle.type;

import br.pro.hashi.sdx.dao.annotation.Converted;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.Address;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.AddressConverter;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.BooleanWrapperConverter;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.ByteWrapperConverter;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.Email;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.EmailConverter;
import br.pro.hashi.sdx.dao.reflection.mock.handle.converter.Wrapper;

public class ConvertableFields {
	private @Converted(EmailConverter.class) Email email;
	private @Converted(AddressConverter.class) Address address;
	private @Converted(BooleanWrapperConverter.class) Wrapper<Boolean> booleanWrapper;
	private @Converted(ByteWrapperConverter.class) Wrapper<Byte> byteWrapper;
}
