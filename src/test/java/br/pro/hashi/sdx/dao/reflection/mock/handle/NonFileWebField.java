package br.pro.hashi.sdx.dao.reflection.mock.handle;

import br.pro.hashi.sdx.dao.annotation.Key;
import br.pro.hashi.sdx.dao.annotation.Web;

public class NonFileWebField {
	private @Key boolean booleanValue;
	private @Web String stringValue;
}
