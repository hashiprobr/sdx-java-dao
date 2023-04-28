package br.pro.hashi.sdx.dao;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteBatch;

import br.pro.hashi.sdx.dao.DaoClient.Connection;
import br.pro.hashi.sdx.dao.exception.DataException;
import br.pro.hashi.sdx.dao.reflection.Handle;

/**
 * Stub.
 *
 * @param <E> stub
 */
public final class Dao<E> {
	/**
	 * Stub.
	 * 
	 * @param <E>  stub
	 * @param type stub
	 * @return stub
	 */
	public static <E> Dao<E> of(Class<E> type) {
		return ClientFactory.getInstance().get().get(type);
	}

	private final DaoClient client;
	private final Handle<E> handle;

	Dao(DaoClient client, Handle<E> handle) {
		this.client = client;
		this.handle = handle;
	}

	/**
	 * Stub.
	 * 
	 * @param instance stub
	 * @return stub
	 */
	public String create(E instance) {
		check(instance);
		String keyString;
		DocumentReference document;
		if (handle.hasAutoKey()) {
			document = createDocument(instance);
			keyString = document.getId();
		} else {
			keyString = getKeyString(instance);
			document = getDocument(keyString);
		}
		await(document.create(handle.toData(instance, handle.hasAutoKey())));
		return keyString;
	}

	/**
	 * Stub.
	 * 
	 * @param instances stub
	 * @return stub
	 */
	public List<String> create(List<E> instances) {
		check(instances);
		Firestore firestore = client.getFirestore();
		CollectionReference collection = getCollection(firestore);
		List<String> keyStrings = new ArrayList<>();
		runBatch(firestore, (batch) -> {
			for (E instance : instances) {
				check(instance);
				String keyString;
				DocumentReference document;
				if (handle.hasAutoKey()) {
					document = createDocument(collection, instance);
					keyString = document.getId();
				} else {
					keyString = getKeyString(instance);
					document = getDocument(collection, keyString);
				}
				batch.create(document, handle.toData(instance, handle.hasAutoKey()));
				keyStrings.add(keyString);
			}
		});
		return keyStrings;
	}

	/**
	 * Stub.
	 * 
	 * @param key stub
	 * @return stub
	 */
	public E retrieve(Object key) {
		String keyString = toString(key);
		DocumentSnapshot document = await(getDocument(keyString).get());
		E instance = (E) handle.toInstance(document.getData());
		if (handle.hasAutoKey()) {
			handle.setAutoKey(instance, keyString);
		}
		return instance;
	}

	/**
	 * Stub.
	 * 
	 * @param instance stub
	 */
	public void update(E instance) {
		check(instance);
		doUpdate(handle.getKey(instance), handle.toData(instance, true));
	}

	/**
	 * Stub.
	 * 
	 * @param key    stub
	 * @param values stub
	 */
	public void update(Object key, Map<String, Object> values) {
		check(values);
		doUpdate(key, handle.toData(values));
	}

	private void doUpdate(Object key, Map<String, Object> data) {
		await(getDocument(key).update(data));
	}

	/**
	 * Stub.
	 * 
	 * @param instances stub
	 */
	public void update(List<E> instances) {
		check(instances);
		runBatch((batch) -> {
			for (E instance : instances) {
				check(instance);
				doUpdate(batch, handle.getKey(instance), handle.toData(instance, true));
			}
		});
	}

	/**
	 * Stub.
	 * 
	 * @param valuesMap stub
	 */
	public void update(Map<Object, Map<String, Object>> valuesMap) {
		if (valuesMap == null) {
			throw new NullPointerException("Values map cannot be null");
		}
		if (valuesMap.isEmpty()) {
			throw new IllegalArgumentException("Values map cannot be empty");
		}
		runBatch((batch) -> {
			for (Object key : valuesMap.keySet()) {
				Map<String, Object> values = valuesMap.get(key);
				check(values);
				doUpdate(batch, key, handle.toData(values));
			}
		});
	}

	private void doUpdate(WriteBatch batch, Object key, Map<String, Object> data) {
		batch.update(getDocument(key), data);
	}

	/**
	 * Stub.
	 * 
	 * @param key stub
	 */
	public void delete(Object key) {
		DocumentReference reference = getDocument(key);
		await(reference.delete());
	}

	private DocumentReference createDocument(E instance) {
		return createDocument(getCollection(), instance);
	}

	private DocumentReference createDocument(CollectionReference collection, E instance) {
		if (handle.getKey(instance) != null) {
			throw new IllegalArgumentException("Key must be null");
		}
		return collection.document();
	}

	private DocumentReference getDocument(Object key) {
		return getDocument(toString(key));
	}

	private DocumentReference getDocument(String keyString) {
		return getCollection().document(keyString);
	}

	private CollectionReference getCollection() {
		return getCollection(client.getFirestore());
	}

	private String getKeyString(E instance) {
		return toString(handle.getKey(instance));
	}

	private void check(Map<String, Object> values) {
		if (values == null) {
			throw new NullPointerException("Value map cannot be null");
		}
		if (values.isEmpty()) {
			throw new IllegalArgumentException("Value map cannot be empty");
		}
	}

	private void check(List<E> instances) {
		if (instances == null) {
			throw new NullPointerException("Instance list cannot be null");
		}
		if (instances.isEmpty()) {
			throw new IllegalArgumentException("Instance list cannot be empty");
		}
	}

	private void check(E instance) {
		if (instance == null) {
			throw new NullPointerException("Instance cannot be null");
		}
	}

	/**
	 * Stub.
	 * 
	 * @param key       stub
	 * @param fieldName stub
	 * @param stream    stub
	 */
	public void uploadFile(Object key, String fieldName, InputStream stream) {
		if (stream == null) {
			throw new NullPointerException("Stream cannot be null");
		}
		check(fieldName);
		String keyString = toString(key);
		String fileName = getFileName(keyString, fieldName);
		Connection connection = client.getConnection();
		try (Fao fao = new Fao(connection, fileName)) {
			String url = fao.upload(stream, handle.getContentType(fieldName), handle.isWeb(fieldName));
			DocumentReference document = getDocument(connection, keyString);
			await(document.update(fieldName, url));
		}
	}

	/**
	 * Stub.
	 * 
	 * @param key       stub
	 * @param fieldName stub
	 */
	public void refreshFile(Object key, String fieldName) {
		check(fieldName);
		String keyString = toString(key);
		String fileName = getFileName(keyString, fieldName);
		Connection connection = client.getConnection();
		try (Fao fao = new Fao(connection, fileName)) {
			String url = fao.refresh(handle.isWeb(fieldName));
			DocumentReference document = getDocument(connection, keyString);
			await(document.update(fieldName, url));
		}
	}

	/**
	 * Stub.
	 * 
	 * @param key       stub
	 * @param fieldName stub
	 * @return stub
	 */
	public DaoFile downloadFile(Object key, String fieldName) {
		check(fieldName);
		String fileName = getFileName(toString(key), fieldName);
		DaoFile file;
		try (Fao fao = new Fao(client.getBucket(), fileName)) {
			file = fao.download();
		}
		return file;
	}

	/**
	 * Stub.
	 * 
	 * @param key       stub
	 * @param fieldName stub
	 */
	public void removeFile(Object key, String fieldName) {
		check(fieldName);
		String keyString = toString(key);
		String fileName = getFileName(keyString, fieldName);
		Connection connection = client.getConnection();
		try (Fao fao = new Fao(connection, fileName)) {
			fao.remove();
			DocumentReference document = getDocument(connection, keyString);
			await(document.update(fieldName, null));
		}
	}

	private void runBatch(Consumer<WriteBatch> consumer) {
		runBatch(client.getFirestore(), consumer);
	}

	private void runBatch(Firestore firestore, Consumer<WriteBatch> consumer) {
		WriteBatch batch = firestore.batch();
		consumer.accept(batch);
		await(batch.commit());
	}

	private <V> V await(ApiFuture<V> future) {
		V result;
		try {
			result = future.get();
		} catch (ExecutionException exception) {
			throw new DataException(exception.getCause());
		} catch (InterruptedException exception) {
			throw new DataException(exception);
		}
		return result;
	}

	private DocumentReference getDocument(Connection connection, String keyString) {
		return getDocument(getCollection(connection.firestore()), keyString);
	}

	private DocumentReference getDocument(CollectionReference collection, String keyString) {
		return collection.document(keyString);
	}

	private CollectionReference getCollection(Firestore firestore) {
		return firestore.collection(handle.getCollectionName());
	}

	private String getFileName(String keyString, String fieldName) {
		return "%s/%s/%s".formatted(handle.getCollectionName(), keyString, fieldName);
	}

	private String toString(Object key) {
		if (key == null) {
			throw new NullPointerException("Key cannot be null");
		}
		return key.toString();
	}

	private void check(String fieldName) {
		if (fieldName == null) {
			throw new NullPointerException("Field name cannot be null");
		}
	}

	/**
	 * Stub.
	 * 
	 * @return stub
	 */
	public Collection collect() {
		return new Collection(getCollection());
	}

	/**
	 * Stub.
	 * 
	 * @param fieldNames stub
	 * @return stub
	 */
	public Selection select(String... fieldNames) {
		return new Selection(getCollection().select(fieldNames), fieldNames);
	}

	/**
	 * Stub.
	 */
	public sealed class Filter permits Collection, Selection {
		final Query query;

		private Filter(Query query) {
			this.query = query;
		}

		private void runBatch(QuerySnapshot snapshots, BiConsumer<WriteBatch, DocumentReference> consumer) {
			Dao.this.runBatch(query.getFirestore(), (batch) -> {
				for (DocumentSnapshot snapshot : snapshots) {
					consumer.accept(batch, snapshot.getReference());
				}
			});
		}
	}

	/**
	 * Stub.
	 */
	public final class Collection extends Filter {
		private Collection(CollectionReference reference) {
			super(reference);
		}

		/**
		 * Stub.
		 * 
		 * @return stub
		 */
		public List<E> retrieve() {
			QuerySnapshot snapshot = await(query.get());
			List<E> instances = new ArrayList<>();
			for (DocumentSnapshot document : snapshot) {
				E instance = handle.toInstance(document.getData());
				if (handle.hasAutoKey()) {
					handle.setAutoKey(instance, document.getId());
				}
				instances.add(instance);
			}
			return instances;
		}

		/**
		 * Stub.
		 * 
		 * @param instance stub
		 */
		public void update(E instance) {
			check(instance);
			Map<String, Object> data = handle.toData(instance, true);
			runBatch((batch, document) -> {
				batch.update(document, data);
			});
		}

		/**
		 * Stub.
		 */
		public void delete() {
			runBatch((batch, document) -> {
				batch.delete(document);
			});
		}

		private void runBatch(BiConsumer<WriteBatch, DocumentReference> consumer) {
			QuerySnapshot snapshots = await(query.select(new String[] {}).get());
			super.runBatch(snapshots, consumer);
		}
	}

	/**
	 * Stub.
	 */
	public final class Selection extends Filter {
		private final String[] fieldNames;

		private Selection(Query query, String[] fieldNames) {
			super(query);
			this.fieldNames = fieldNames;
		}

		/**
		 * Stub.
		 * 
		 * @return stub
		 */
		public List<Map<String, Object>> retrieve() {
			QuerySnapshot snapshot = await(query.get());
			List<Map<String, Object>> valuesList = new ArrayList<>();
			for (DocumentSnapshot document : snapshot) {
				Map<String, Object> values = handle.toValues(document.getData());
				if (handle.hasAutoKey()) {
					handle.putAutoKey(values, document.getId());
				}
				valuesList.add(values);
			}
			return valuesList;
		}

		/**
		 * Stub.
		 * 
		 * @param fieldValues stub
		 */
		public void update(Object... fieldValues) {
			if (fieldNames.length != fieldValues.length) {
				throw new IllegalArgumentException("Cannot update %d fields with %d values".formatted(fieldNames.length, fieldValues.length));
			}
			Map<String, Object> values = new HashMap<>();
			for (int i = 0; i < fieldNames.length; i++) {
				values.put(fieldNames[i], fieldValues[i]);
			}
			Map<String, Object> data = handle.toData(values);
			runBatch((batch, document) -> {
				batch.update(document, data);
			});
		}

		/**
		 * Stub.
		 */
		public void delete() {
			Map<String, Object> values = new HashMap<>();
			for (int i = 0; i < fieldNames.length; i++) {
				values.put(fieldNames[i], FieldValue.delete());
			}
			Map<String, Object> data = handle.toData(values);
			runBatch((batch, document) -> {
				batch.update(document, data);
			});
		}

		private void runBatch(BiConsumer<WriteBatch, DocumentReference> consumer) {
			QuerySnapshot snapshots = await(query.get());
			super.runBatch(snapshots, consumer);
		}
	}
}
