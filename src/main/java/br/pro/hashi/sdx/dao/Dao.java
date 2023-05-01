package br.pro.hashi.sdx.dao;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import br.pro.hashi.sdx.dao.annotation.Auto;
import br.pro.hashi.sdx.dao.annotation.File;
import br.pro.hashi.sdx.dao.annotation.Key;
import br.pro.hashi.sdx.dao.exception.DataException;
import br.pro.hashi.sdx.dao.reflection.Handle;

/**
 * Represents the data access object of an entity.
 *
 * @param <E> the entity type
 */
public final class Dao<E> {
	/**
	 * Creates a new data access object of the specified entity type.
	 * 
	 * @param <E>  the type
	 * @param type a {@link Class} representing {@code E}
	 * @return the object
	 * @throws IllegalStateException if no client exists
	 */
	public static <E> Dao<E> of(Class<E> type) {
		return ClientFactory.getInstance().getDefault().get(type);
	}

	/**
	 * Creates a new data access object of the specified entity type from the
	 * specified project id.
	 * 
	 * @param <E>       the type
	 * @param type      a {@link Class} representing {@code E}
	 * @param projectId the id
	 * @return the object
	 * @throws NullPointerException     if the id is null
	 * @throws IllegalArgumentException if the id is blank or a client for the id
	 *                                  does not exist
	 */
	public static <E> Dao<E> of(Class<E> type, String projectId) {
		return ClientFactory.getInstance().getFromId(projectId).get(type);
	}

	private final DaoClient client;
	private final Handle<E> handle;

	// TODO: Replace this class with a constructor if/when
	// Mockito can mock the construction of a generic type.
	static class Construction {
		static <E> Dao<E> of(DaoClient factory, Handle<E> handle) {
			return new Dao<>(factory, handle);
		}

		private Construction() {
		}
	}

	Dao(DaoClient client, Handle<E> handle) {
		this.client = client;
		this.handle = handle;
	}

	/**
	 * <p>
	 * Creates the specified entity instance and returns its key.
	 * </p>
	 * <p>
	 * If {@code E} has {@link File} fields, the values are replaced by
	 * {@code null}.
	 * </p>
	 * <p>
	 * If the {@link Key} field of {@code E} is an {@link Auto} field, returns the
	 * automatically generated key. Otherwise, returns the result of
	 * {@link Object#toString()} for the key specified in the instance.
	 * </p>
	 * 
	 * @param instance the instance
	 * @return the key
	 * @throws NullPointerException     if the instance is null or if the key field
	 *                                  is not an auto field but the value is null
	 * @throws IllegalArgumentException if the key field is an auto field but the
	 *                                  value is not null
	 * @throws DataException            if the Firestore operation could not be
	 *                                  performed
	 */
	public String create(E instance) {
		check(instance);
		Connection connection = client.getConnection();
		CollectionReference collection = getCollection(connection.firestore());
		String keyString;
		DocumentReference document;
		if (handle.hasAutoKey()) {
			document = createDocument(collection, instance);
			keyString = document.getId();
		} else {
			keyString = getKeyString(instance);
			document = collection.document(keyString);
		}
		try (Fao fao = new Fao(connection.bucket(), getFileNames(keyString))) {
			await(document.create(handle.toData(instance, true, !handle.hasAutoKey())));
		}
		return keyString;
	}

	/**
	 * <p>
	 * Creates the specified entity instances and returns their keys.
	 * </p>
	 * <p>
	 * If {@code E} has {@link File} fields, simply calls {@code Dao.create(E)} for
	 * each instance. Otherwise, performs a single batch operation.
	 * </p>
	 * <p>
	 * If the {@link Key} field of {@code E} is an {@link Auto} field, returns the
	 * automatically generated keys. Otherwise, returns the result of
	 * {@link Object#toString()} for the keys specified in the instances.
	 * </p>
	 * 
	 * @param instances the instances
	 * @return the keys
	 * @throws NullPointerException     if an instance is null or if the key field
	 *                                  is not an auto field but a value is null
	 * @throws IllegalArgumentException if there are no instances or if the key
	 *                                  field is an auto field but a value is not
	 *                                  null
	 * @throws DataException            if a Firestore operation could not be
	 *                                  performed
	 */
	public List<String> create(List<E> instances) {
		check(instances);
		List<String> keyStrings = new ArrayList<>();
		if (handle.getFileFieldNames().isEmpty()) {
			Firestore firestore = client.getFirestore();
			CollectionReference collection = getCollection(firestore);
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
						document = collection.document(keyString);
					}
					batch.create(document, handle.toData(instance, true, !handle.hasAutoKey()));
					keyStrings.add(keyString);
				}
			});
		} else {
			for (E instance : instances) {
				keyStrings.add(create(instance));
			}
		}
		return keyStrings;
	}

	private void check(E instance) {
		if (instance == null) {
			throw new NullPointerException("Instance cannot be null");
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

	private String getKeyString(E instance) {
		return toString(handle.getKey(instance));
	}

	private List<String> getFileNames(String keyString) {
		return handle.getFileFieldNames()
				.stream()
				.map((fieldName) -> getFileName(keyString, fieldName))
				.toList();
	}

	private CollectionReference getCollection(Firestore firestore) {
		return firestore.collection(handle.getCollectionName());
	}

	private DocumentReference createDocument(CollectionReference collection, E instance) {
		if (handle.getKey(instance) != null) {
			throw new IllegalArgumentException("Key must be null");
		}
		return collection.document();
	}

	private void runBatch(Firestore firestore, Consumer<WriteBatch> consumer) {
		WriteBatch batch = firestore.batch();
		consumer.accept(batch);
		await(batch.commit());
	}

	/**
	 * Stub.
	 * 
	 * @param key       stub
	 * @param fieldName stub
	 * @param stream    stub
	 * @return stub
	 */
	public String uploadFile(Object key, String fieldName, InputStream stream) {
		if (stream == null) {
			throw new NullPointerException("Stream cannot be null");
		}
		check(fieldName);
		String keyString = toString(key);
		Connection connection = client.getConnection();
		String fileName = getFileName(keyString, fieldName);
		String url;
		try (Fao fao = new Fao(connection.bucket(), fileName)) {
			url = fao.upload(stream, handle.getContentType(fieldName), handle.isWeb(fieldName));
			DocumentReference document = getDocument(connection, keyString);
			await(document.update(fieldName, url));
		}
		return url;
	}

	/**
	 * Stub.
	 * 
	 * @param key       stub
	 * @param fieldName stub
	 * @return
	 */
	public String refreshFile(Object key, String fieldName) {
		check(fieldName);
		String keyString = toString(key);
		Connection connection = client.getConnection();
		String fileName = getFileName(keyString, fieldName);
		String url;
		try (Fao fao = new Fao(connection.bucket(), fileName)) {
			url = fao.refresh(handle.isWeb(fieldName));
			DocumentReference document = getDocument(connection, keyString);
			await(document.update(fieldName, url));
		}
		return url;
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
		Connection connection = client.getConnection();
		String fileName = getFileName(keyString, fieldName);
		try (Fao fao = new Fao(connection.bucket(), fileName)) {
			fao.remove();
			DocumentReference document = getDocument(connection, keyString);
			await(document.update(fieldName, null));
		}
	}

	private void check(String fieldName) {
		if (fieldName == null) {
			throw new NullPointerException("Field name cannot be null");
		}
	}

	private String toString(Object key) {
		if (key == null) {
			throw new NullPointerException("Key cannot be null");
		}
		return key.toString();
	}

	private String getFileName(String keyString, String fieldName) {
		return "%s/%s/%s".formatted(handle.getCollectionName(), keyString, fieldName);
	}

	private DocumentReference getDocument(Connection connection, String keyString) {
		return getCollection(connection.firestore()).document(keyString);
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
		doUpdate(handle.getKey(instance), handle.toData(instance, false, true));
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
				doUpdate(batch, handle.getKey(instance), handle.toData(instance, false, true));
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
		Set<String> fileFieldNames = handle.getFileFieldNames();
		Connection connection = client.getConnection();
		if (fileFieldNames.isEmpty()) {
			delete(connection, toString(key));
		} else {
			delete(connection, key, fileFieldNames);
		}
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

	private void check(Map<String, Object> values) {
		if (values == null) {
			throw new NullPointerException("Value map cannot be null");
		}
		if (values.isEmpty()) {
			throw new IllegalArgumentException("Value map cannot be empty");
		}
	}

	private void delete(Connection connection, Object key, Set<String> fieldNames) {
		String keyString = toString(key);
		List<String> fileNames = fieldNames
				.stream()
				.map((fieldName) -> getFileName(keyString, fieldName))
				.toList();
		try (Fao fao = new Fao(connection.bucket(), fileNames)) {
			fao.remove();
			delete(connection, keyString);
		}
	}

	private void delete(Connection connection, String keyString) {
		DocumentReference document = getDocument(connection, keyString);
		await(document.delete());
	}

	private void runBatch(Consumer<WriteBatch> consumer) {
		runBatch(client.getFirestore(), consumer);
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
			Map<String, Object> data = handle.toData(instance, false, true);
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
