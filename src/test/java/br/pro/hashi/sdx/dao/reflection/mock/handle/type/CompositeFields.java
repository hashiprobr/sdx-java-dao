package br.pro.hashi.sdx.dao.reflection.mock.handle.type;

import java.util.List;
import java.util.Map;

public class CompositeFields {
	String[] stringArray;
	Object[] objectArray;
	List<String> stringList;
	List<Object> objectList;
	@SuppressWarnings("rawtypes")
	List rawList;
	Map<String, String> stringStringMap;
	Map<String, Object> stringObjectMap;
	Map<List<String>, String> listStringMap;
	Map<Object, String> objectStringMap;
	Map<Object, Object> objectObjectMap;
	@SuppressWarnings("rawtypes")
	Map rawMap;
	Custom custom;
	Object object;
}
