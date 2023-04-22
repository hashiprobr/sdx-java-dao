package br.pro.hashi.sdx.dao;

import br.pro.hashi.sdx.dao.reflection.Compiler;
import br.pro.hashi.sdx.dao.reflection.Handle;

public final class Dao<T> {
	public static <T> Dao<T> of(Class<T> type) {
		return ClientFactory.getInstance().get().get(type);
	}

	private final Compiler compiler;
	private final DaoClient client;
	private final Handle handle;

	Dao(DaoClient client, Handle handle) {
		this(Compiler.getInstance(), client, handle);
	}

	Dao(Compiler compiler, DaoClient client, Handle handle) {
		this.compiler = compiler;
		this.client = client;
		this.handle = handle;
	}
}
