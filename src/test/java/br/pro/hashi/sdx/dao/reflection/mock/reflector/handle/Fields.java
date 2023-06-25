package br.pro.hashi.sdx.dao.reflection.mock.reflector.handle;

public class Fields {
	public boolean publicValue;
	protected boolean protectedValue;
	boolean packageValue;
	private boolean privateValue;

	public Fields() {
		this.publicValue = true;
		this.protectedValue = true;
		this.packageValue = true;
		this.privateValue = true;
	}

	public boolean isPublicValue() {
		return publicValue;
	}

	public boolean isProtectedValue() {
		return protectedValue;
	}

	public boolean isPackageValue() {
		return packageValue;
	}

	public boolean isPrivateValue() {
		return privateValue;
	}
}
