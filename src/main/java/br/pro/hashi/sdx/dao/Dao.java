package br.pro.hashi.sdx.dao;

import java.io.InputStream;
import java.nio.channels.ReadableByteChannel;
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
 * @param <T> stub
 */
public final class Dao<T> {
	/**
	 * Stub.
	 * 
	 * @param <T>  stub
	 * @param type stub
	 * @return stub
	 */
	public static <T> Dao<T> of(Class<T> type) {
		return ClientFactory.getInstance().get().get(type);
	}

	private final DaoClient client;
	private final Handle handle;
	private final String collectionName;

	Dao(DaoClient client, Handle handle) {
		this.client = client;
		this.handle = handle;
		this.collectionName = handle.getCollectionName();
	}

	/**
	 * Stub.
	 * 
	 * @param instance stub
	 * @return stub
	 */
	public String create(T instance) {
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
	public List<String> create(List<T> instances) {
		check(instances);
		Firestore firestore = client.getFirestore();
		CollectionReference collection = getCollection(firestore);
		List<String> keyStrings = new ArrayList<>();
		runBatch(firestore, (batch) -> {
			for (T instance : instances) {
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
	public T retrieve(Object key) {
		String keyString = toString(key);
		DocumentSnapshot document = await(getDocument(keyString).get());
		@SuppressWarnings("unchecked")
		T instance = (T) handle.toInstance(document.getData());
		if (handle.hasAutoKey()) {
			handle.setKey(instance, keyString);
		}
		return instance;
	}

	/**
	 * Stub.
	 * 
	 * @param instance stub
	 */
	public void update(T instance) {
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
	public void update(List<T> instances) {
		check(instances);
		runBatch((batch) -> {
			for (T instance : instances) {
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

	private DocumentReference createDocument(T instance) {
		return createDocument(getCollection(), instance);
	}

	private DocumentReference createDocument(CollectionReference collection, T instance) {
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

	private String getKeyString(T instance) {
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

	private void check(List<T> instances) {
		if (instances == null) {
			throw new NullPointerException("Instance list cannot be null");
		}
		if (instances.isEmpty()) {
			throw new IllegalArgumentException("Instance list cannot be empty");
		}
	}

	private void check(T instance) {
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
			String url = fao.upload(stream, fileName, handle.isWeb(fieldName));
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
	public File downloadFile(Object key, String fieldName) {
		check(fieldName);
		String fileName = getFileName(toString(key), fieldName);
		File file;
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
		return firestore.collection(collectionName);
	}

	private String getFileName(String keyString, String fieldName) {
		return "%s/%s/%s".formatted(collectionName, keyString, fieldName);
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
	public Collection instances() {
		return new Collection(getCollection());
	}

	/**
	 * Stub.
	 * 
	 * @param fieldNames stub
	 * @return stub
	 */
	public Selection select(String... fieldNames) {
		boolean includesFiles = false;
		boolean includesKey = false;
		for (String fieldName : fieldNames) {
			if (!handle.isFieldName(fieldName)) {
				throw new IllegalArgumentException("Field %s does not exist".formatted(fieldName));
			}
			if (handle.getContentType(fieldName) != null) {
				includesFiles = true;
			}
			if (handle.isKey(fieldName)) {
				includesKey = true;
			}
		}
		return new Selection(getCollection().select(fieldNames), fieldNames, includesFiles, includesKey);
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
		public List<T> retrieve() {
			QuerySnapshot snapshot = await(query.get());
			List<T> instances = new ArrayList<>();
			for (DocumentSnapshot document : snapshot) {
				@SuppressWarnings("unchecked")
				T instance = (T) handle.toInstance(document.getData());
				if (handle.hasAutoKey()) {
					handle.setKey(instance, document.getId());
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
		public void update(T instance) {
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
		private final boolean includesFiles;
		private final boolean includesKey;

		private Selection(Query query, String[] fieldNames, boolean includesFiles, boolean includesKey) {
			super(query);
			this.fieldNames = fieldNames;
			this.includesFiles = includesFiles;
			this.includesKey = includesKey;
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
					handle.setKey(values, document.getId());
				}
				valuesList.add(values);
			}
			return valuesList;
		}

		/**
		 * Stub.
		 * 
		 * @param values stub
		 */
		public void update(Object... values) {
			if (includesFiles) {
				throw new IllegalArgumentException("@File fields can only be updated by uploadFile");
			}
			if (includesKey) {
				throw new IllegalArgumentException("@Key field cannot be updated");
			}
			if (fieldNames.length != values.length) {
				throw new IllegalArgumentException("Cannot update %d fields with %d values".formatted(fieldNames.length, values.length));
			}
			Map<String, Object> map = new HashMap<>();
			for (int i = 0; i < fieldNames.length; i++) {
				map.put(fieldNames[i], values[i]);
			}
			runBatch((batch, document) -> {
				document.update(map);
			});
		}

		/**
		 * Stub.
		 */
		public void delete() {
			if (includesFiles) {
				throw new IllegalArgumentException("@File fields can only be deleted by removeFile");
			}
			if (includesKey) {
				throw new IllegalArgumentException("@Key field cannot be deleted");
			}
			Map<String, Object> map = new HashMap<>();
			for (int i = 0; i < fieldNames.length; i++) {
				map.put(fieldNames[i], FieldValue.delete());
			}
			runBatch((batch, document) -> {
				document.update(map);
			});
		}

		private void runBatch(BiConsumer<WriteBatch, DocumentReference> consumer) {
			QuerySnapshot snapshots = await(query.get());
			super.runBatch(snapshots, consumer);
		}
	}

	/**
	 * Stub.
	 * 
	 * @param channel       stub
	 * @param contentType   stub
	 * @param contentLength stub
	 */
	public static record File(ReadableByteChannel channel, String contentType, long contentLength) {
	}
}
