package br.pro.hashi.sdx.dao.reflection.example.handle.converter;

public class Address {
	private String street;
	private int number;
	private String city;

	Address() {
	}

	public Address(String street, int number, String city) {
		this.street = street;
		this.number = number;
		this.city = city;
	}

	public String getStreet() {
		return street;
	}

	public int getNumber() {
		return number;
	}

	public String getCity() {
		return city;
	}
}
