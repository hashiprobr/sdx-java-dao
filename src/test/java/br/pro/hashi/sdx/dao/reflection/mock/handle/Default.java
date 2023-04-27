package br.pro.hashi.sdx.dao.reflection.mock.handle;

import br.pro.hashi.sdx.dao.annotation.File;
import br.pro.hashi.sdx.dao.annotation.Key;

public class Default {
	private @Key boolean booleanValue;
	private @File String stringValue;

	public Default() {
		this.booleanValue = true;
		this.stringValue = "d";
	}
}
