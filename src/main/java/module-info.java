/**
 * Defines a simple DAO framework based on Google Cloud Firestore and Storage.
 */
module br.pro.hashi.sdx.dao {
	requires google.cloud.firestore;
	requires google.cloud.storage;
	requires org.slf4j;
	requires org.javassist;

	exports br.pro.hashi.sdx.dao;
	exports br.pro.hashi.sdx.dao.annotation;
}