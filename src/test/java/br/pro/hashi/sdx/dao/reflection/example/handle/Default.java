package br.pro.hashi.sdx.dao.reflection.example.handle;

import br.pro.hashi.sdx.dao.annotation.File;

public class Default {
	private @File String file;

	public Default() {
		this.file = "value";
	}
}
