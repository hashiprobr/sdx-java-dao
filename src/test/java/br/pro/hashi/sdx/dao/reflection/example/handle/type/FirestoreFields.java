package br.pro.hashi.sdx.dao.reflection.example.handle.type;

import java.io.InputStream;
import java.time.Instant;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Blob;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.GeoPoint;

public class FirestoreFields {
	GeoPoint point;
	DocumentReference reference;
	Timestamp timestamp;
	Instant instant;
	Blob blob;
	InputStream stream;
	Object object;
}
