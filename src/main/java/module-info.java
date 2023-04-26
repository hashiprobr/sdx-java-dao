/**
 * Defines a simple DAO framework based on Google Cloud Firestore and Storage.
 */
module br.pro.hashi.sdx.dao {
	requires firebase.admin;
	requires google.cloud.firestore;
	requires google.cloud.storage;
	requires google.cloud.core;
	requires google.cloud.core.http;
	requires com.google.api.apicommon;
	requires com.google.auth;
	requires com.google.auth.oauth2;
	requires com.google.common;
	requires org.slf4j;

	exports br.pro.hashi.sdx.dao;
	exports br.pro.hashi.sdx.dao.annotation;
	exports br.pro.hashi.sdx.dao.exception;
}