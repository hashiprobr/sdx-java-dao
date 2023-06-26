package br.pro.hashi.sdx.dao.reflection.mock.handle;

import br.pro.hashi.sdx.dao.annotation.File;

public class FileField {
	private @File String file;

	public FileField() {
		this.file = "value";
	}
}
