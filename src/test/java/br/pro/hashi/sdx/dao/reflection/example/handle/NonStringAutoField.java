package br.pro.hashi.sdx.dao.reflection.example.handle;

import br.pro.hashi.sdx.dao.annotation.Auto;
import br.pro.hashi.sdx.dao.annotation.Key;

public class NonStringAutoField {
	private @Auto @Key boolean value;
}
