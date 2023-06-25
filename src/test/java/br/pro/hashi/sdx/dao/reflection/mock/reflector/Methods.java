package br.pro.hashi.sdx.dao.reflection.mock.reflector;

public class Methods {
	public void legal() {
		illegalPrivate();
	}

	protected void illegalProtected() {
	}

	void illegalPackage() {
	}

	private void illegalPrivate() {
	}
}
