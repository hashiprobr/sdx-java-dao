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
import com.google.cloud.firestore.AggregateQuery;
import com.google.cloud.firestore.AggregateQuerySnapshot;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.Query.Direction;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteBatch;
import com.google.cloud.storage.Bucket;

import br.pro.hashi.sdx.dao.DaoClient.Connection;
import br.pro.hashi.sdx.dao.annotation.Auto;
import br.pro.hashi.sdx.dao.annotation.File;
import br.pro.hashi.sdx.dao.annotation.Key;
import br.pro.hashi.sdx.dao.annotation.Web;
import br.pro.hashi.sdx.dao.exception.DataException;
import br.pro.hashi.sdx.dao.exception.FileException;
import br.pro.hashi.sdx.dao.reflection.Handle;

/**
 * Represents the data access object of an entity.
 *
 * @param <E> the entity type
 */
public final class Dao<E> {
	/**
	 * Gets a new data access object of the specified entity type from the first
	 * {@link DaoClient} created.
	 *
	 * @param <E>  the type
	 * @param type a {@link Class} representing {@code E}
	 * @return the object
	 * @throws NullPointerException  if the type is null
	 * @throws IllegalStateException if no client exists
	 */
	public static <E> Dao<E> of(Class<E> type) {
		return ClientFactory.getInstance().getFirst().get(type);
	}

	/**
	 * Gets a new data access object of the specified entity type from the
	 * {@link DaoClient} created for the specified project id.
	 *
	 * @param <E>       the type
	 * @param type      a {@link Class} representing {@code E}
	 * @param projectId the id
	 * @return the object
	 * @throws NullPointerException     if the type is null or the id is null
	 * @throws IllegalArgumentException if the id is blank or a client for the id
	 *                                  does not exist
	 */
	public static <E> Dao<E> of(Class<E> type, String projectId) {
		return ClientFactory.getInstance().getFromId(projectId).get(type);
	}

	private final DaoClient client;
	private final Handle<E> handle;

	Dao(DaoClient client, Handle<E> handle) {
		this.client = client;
		this.handle = handle;
	}

	/**
	 * <p>
	 * Creates the specified entity instance and returns its key.
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
	 * @throws FileException            if a Storage operation could not be
	 *                                  performed
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
		Map<String, Object> data = handle.buildCreateData(instance);
		try (Fao fao = new Fao(connection.bucket(), getFileNames(keyString))) {
			sync(document.create(data));
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
	 * @throws NullPointerException     if the instance list is null, if an instance
	 *                                  is null or if the key field is not an auto
	 *                                  field but a value is null
	 * @throws IllegalArgumentException if the instance list is empty or if the key
	 *                                  field is an auto field but a value is not
	 *                                  null
	 * @throws FileException            if a Storage operation could not be
	 *                                  performed
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
					Map<String, Object> data = handle.buildCreateData(instance);
					batch.create(document, data);
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

	private DocumentReference createDocument(CollectionReference collection, E instance) {
		if (handle.getKey(instance) != null) {
			throw new IllegalArgumentException("Key must be null");
		}
		return collection.document();
	}

	/**
	 * <p>
	 * Retrieves the entity instance identified by the specified key.
	 * </p>
	 * <p>
	 * If the instance does not exist, returns {@code null}.
	 * </p>
	 *
	 * @param key the key
	 * @return the instance
	 * @throws NullPointerException if the key is null
	 * @throws DataException        if the Firestore operation could not be
	 *                              performed
	 */
	public E retrieve(Object key) {
		String keyString = toString(key);
		DocumentReference document = getDocument(client.getFirestore(), keyString);
		DocumentSnapshot snapshot = sync(document.get());
		E instance;
		if (snapshot.exists()) {
			instance = handle.buildInstance(snapshot.getData());
			if (handle.hasAutoKey()) {
				handle.setAutoKey(instance, keyString);
			}
		} else {
			instance = null;
		}
		return instance;
	}

	/**
	 * <p>
	 * Updates the values of the specified entity instance.
	 * </p>
	 * <p>
	 * {@link File} fields are ignored and the {@link Key} field cannot be updated
	 * because it is used to identify the instance.
	 * </p>
	 *
	 * @param instance the instance
	 * @throws NullPointerException if the instance is null or the key value is null
	 * @throws DataException        if the Firestore operation could not be
	 *                              performed
	 */
	public void update(E instance) {
		check(instance);
		updateFromData(getKeyString(instance), handle.buildUpdateData(instance));
	}

	/**
	 * <p>
	 * Updates the specified values of the entity instance identified by the
	 * specified key.
	 * </p>
	 * <p>
	 * {@link File} fields and the {@link Key} field cannot be updated.
	 * </p>
	 *
	 * @param key    the key
	 * @param values the values
	 * @throws NullPointerException     if the value map is null or the key value is
	 *                                  null
	 * @throws IllegalArgumentException if the value map is empty
	 * @throws DataException            if the Firestore operation could not be
	 *                                  performed
	 */
	public void update(Object key, Map<String, Object> values) {
		check(values);
		updateFromData(toString(key), handle.buildData(values));
	}

	private void updateFromData(String keyString, Map<String, Object> data) {
		DocumentReference document = getDocument(client.getFirestore(), keyString);
		sync(document.update(data));
	}

	/**
	 * <p>
	 * Updates all values of the specified entity instances.
	 * </p>
	 * <p>
	 * {@link File} fields are ignored and the {@link Key} field cannot be updated
	 * because it is used to identify the instances.
	 * </p>
	 *
	 * @param instances the instances
	 * @throws NullPointerException     if the instance list is null, an instance is
	 *                                  null, or a key value is null
	 * @throws IllegalArgumentException if the instance list is empty
	 * @throws DataException            if the Firestore operation could not be
	 *                                  performed
	 */
	public void update(List<E> instances) {
		check(instances);
		Firestore firestore = client.getFirestore();
		runBatch(firestore, (batch) -> {
			for (E instance : instances) {
				check(instance);
				updateFromData(firestore, batch, getKeyString(instance), handle.buildUpdateData(instance));
			}
		});
	}

	/**
	 * <p>
	 * For each key-values pair of the specified map, updates the values of the
	 * entity instance identified by the key.
	 * </p>
	 * <p>
	 * {@link File} fields and the {@link Key} field cannot be updated.
	 * </p>
	 *
	 * @param map the map
	 * @throws NullPointerException     if the map is null, a value map is null, or
	 *                                  a key value is null
	 * @throws IllegalArgumentException if the map is empty or a value map is empty
	 * @throws DataException            if the Firestore operation could not be
	 *                                  performed
	 */
	public void update(Map<Object, Map<String, Object>> map) {
		if (map == null) {
			throw new NullPointerException("Map cannot be null");
		}
		if (map.isEmpty()) {
			throw new IllegalArgumentException("Map cannot be empty");
		}
		Firestore firestore = client.getFirestore();
		runBatch(firestore, (batch) -> {
			for (Object key : map.keySet()) {
				Map<String, Object> values = map.get(key);
				check(values);
				updateFromData(firestore, batch, toString(key), handle.buildData(values));
			}
		});
	}

	private void updateFromData(Firestore firestore, WriteBatch batch, String keyString, Map<String, Object> data) {
		DocumentReference document = getDocument(firestore, keyString);
		batch.update(document, data);
	}

	private void check(List<E> instances) {
		if (instances == null) {
			throw new NullPointerException("Instance list cannot be null");
		}
		if (instances.isEmpty()) {
			throw new IllegalArgumentException("Instance list cannot be empty");
		}
	}

	private void check(Map<String, Object> values) {
		if (values == null) {
			throw new NullPointerException("Value map cannot be null");
		}
		if (values.isEmpty()) {
			throw new IllegalArgumentException("Value map cannot be empty");
		}
	}

	private String getKeyString(E instance) {
		return toString(handle.getKey(instance));
	}

	/**
	 * <p>
	 * Deletes the entity instance identified by the specified key.
	 * </p>
	 * <p>
	 * If {@code E} has {@link File} fields, the files are deleted.
	 * </p>
	 * <p>
	 * A successful operation is guaranteed to be atomic, but a
	 * {@link DataException} can leave the entity in an inconsistent state that must
	 * be fixed with another call to this method.
	 * </p>
	 *
	 * @param key the key
	 * @throws NullPointerException if the key is null
	 * @throws FileException        if a Storage operation could not be performed
	 * @throws DataException        if the Firestore operation could not be
	 *                              performed
	 */
	public void delete(Object key) {
		String keyString = toString(key);
		Connection connection = client.getConnection();
		DocumentReference document = getDocument(connection.firestore(), keyString);
		delete(connection.bucket(), keyString, document);
	}

	/**
	 * <p>
	 * Uploads the content of the specified {@link File} field of the specified
	 * entity and updates the field with the file link.
	 * </p>
	 * <p>
	 * If the field is a {@link Web} field, returns the automatically generated
	 * link. Otherwise, returns {@code ""}.
	 * </p>
	 * <p>
	 * A successful operation is guaranteed to be atomic, but a
	 * {@link DataException} can leave the entity in an inconsistent state that must
	 * be fixed with {@link #refreshFile(Object, String)}.
	 * </p>
	 *
	 * @param key       the entity key
	 * @param fieldName the field name
	 * @param stream    the file content
	 * @return the link
	 * @throws NullPointerException     if the key is null, the field name is null,
	 *                                  or the stream is null
	 * @throws IllegalArgumentException if the file field does not exist
	 * @throws FileException            if a Storage operation could not be
	 *                                  performed
	 * @throws DataException            if the Firestore operation could not be
	 *                                  performed
	 */
	public String uploadFile(Object key, String fieldName, InputStream stream) {
		if (stream == null) {
			throw new NullPointerException("Stream cannot be null");
		}
		check(fieldName);
		String keyString = toString(key);
		Connection connection = client.getConnection();
		String url;
		try (Fao fao = new Fao(connection.bucket(), getFileName(keyString, fieldName))) {
			url = fao.upload(stream, handle.getContentType(fieldName), handle.isWeb(fieldName));
			DocumentReference document = getDocument(connection.firestore(), keyString);
			sync(document.update(fieldName, url));
		}
		return url;
	}

	/**
	 * <p>
	 * Updates the specified {@link File} field of the specified entity with the
	 * file link.
	 * </p>
	 * <p>
	 * If the file exists and the field is a {@link Web} field, returns the
	 * automatically generated link. If the file exists and the field is not a
	 * {@link Web} field, returns {@code ""}. If the file does not exist, returns
	 * {@code null}.
	 * </p>
	 * <p>
	 * The operation is guaranteed to be atomic.
	 * </p>
	 *
	 * @param key       the entity key
	 * @param fieldName the field name
	 * @return the link
	 * @throws NullPointerException     if the key is null or the field name is null
	 * @throws IllegalArgumentException if the file field does not exist
	 * @throws FileException            if a Storage operation could not be
	 *                                  performed
	 * @throws DataException            if the Firestore operation could not be
	 *                                  performed
	 */
	public String refreshFile(Object key, String fieldName) {
		check(fieldName);
		String keyString = toString(key);
		Connection connection = client.getConnection();
		String url;
		try (Fao fao = new Fao(connection.bucket(), getFileName(keyString, fieldName))) {
			url = fao.refresh(handle.isWeb(fieldName));
			DocumentReference document = getDocument(connection.firestore(), keyString);
			sync(document.update(fieldName, url));
		}
		return url;
	}

	/**
	 * Obtains the content of the specified {@link File} field of the specified
	 * entity.
	 *
	 * @param key       the entity key
	 * @param fieldName the field name
	 * @return the file content
	 * @throws NullPointerException     if the key is null or the field name is null
	 * @throws IllegalArgumentException if the file field does not exist
	 * @throws FileException            if a Storage operation could not be
	 *                                  performed
	 */
	public DaoFile downloadFile(Object key, String fieldName) {
		check(fieldName);
		String keyString = toString(key);
		DaoFile file;
		try (Fao fao = new Fao(client.getBucket(), getFileName(keyString, fieldName))) {
			file = fao.download();
		}
		return file;
	}

	/**
	 * <p>
	 * Removes the content of the specified {@link File} field of the specified
	 * entity and updates the field with {@code null}.
	 * </p>
	 * <p>
	 * A successful operation is guaranteed to be atomic, but a
	 * {@link DataException} can leave the entity in an inconsistent state that must
	 * be fixed with {@link #refreshFile(Object, String)}.
	 * </p>
	 *
	 * @param key       the entity key
	 * @param fieldName the field name
	 * @throws NullPointerException     if the key is null or the field name is null
	 * @throws IllegalArgumentException if the file field does not exist
	 * @throws FileException            if a Storage operation could not be
	 *                                  performed
	 * @throws DataException            if the Firestore operation could not be
	 *                                  performed
	 */
	public void removeFile(Object key, String fieldName) {
		check(fieldName);
		String keyString = toString(key);
		Connection connection = client.getConnection();
		try (Fao fao = new Fao(connection.bucket(), getFileName(keyString, fieldName))) {
			fao.remove();
			DocumentReference document = getDocument(connection.firestore(), keyString);
			sync(document.update(fieldName, null));
		}
	}

	private void check(String fieldName) {
		if (fieldName == null) {
			throw new NullPointerException("Field name cannot be null");
		}
		if (!handle.getFileFieldNames().contains(fieldName)) {
			throw new IllegalArgumentException("@File field %s does not exist".formatted(fieldName));
		}
	}

	private String toString(Object key) {
		if (key == null) {
			throw new NullPointerException("Key cannot be null");
		}
		return key.toString();
	}

	private DocumentReference getDocument(Firestore firestore, String keyString) {
		return getCollection(firestore).document(keyString);
	}

	/**
	 * Creates a collection of entity instances.
	 *
	 * @return the collection
	 */
	public Collection collect() {
		return new Collection(client.getConnection());
	}

	/**
	 * Represents a query of collected instances.
	 */
	public final class Collection extends Filter<Collection> {
		private final Bucket bucket;

		private Collection(Connection connection) {
			super(getCollection(connection.firestore()));
			this.bucket = connection.bucket();
		}

		/**
		 * Retrieves the entity instances corresponding to the query.
		 *
		 * @return the instances
		 * @throws DataException if the Firestore operation could not be performed
		 */
		public List<E> retrieve() {
			QuerySnapshot snapshots = sync(query.get());
			List<E> instances = new ArrayList<>();
			for (DocumentSnapshot snapshot : snapshots) {
				E instance = handle.buildInstance(snapshot.getData());
				if (handle.hasAutoKey()) {
					handle.setAutoKey(instance, snapshot.getId());
				}
				instances.add(instance);
			}
			return instances;
		}

		/**
		 * <p>
		 * Updates the values of the entity instances corresponding to the query with
		 * the values of the specified instance.
		 * </p>
		 * <p>
		 * {@link File} fields and the {@link Key} field are ignored.
		 * </p>
		 *
		 * @param instance the instance
		 * @throws NullPointerException if the instance is null
		 * @throws DataException        if the Firestore operation could not be
		 *                              performed
		 */
		public void update(E instance) {
			check(instance);
			Map<String, Object> data = handle.buildUpdateData(instance);
			runBatch((batch, document) -> {
				batch.update(document, data);
			});
		}

		/**
		 * <p>
		 * Deletes the entity instances corresponding to the query.
		 * </p>
		 * <p>
		 * If {@code E} has {@link File} fields, simply calls {@link Dao#delete(Object)}
		 * for each instance key. Otherwise, performs a single batch operation.
		 * </p>
		 *
		 * @throws FileException if a Storage operation could not be performed
		 * @throws DataException if a Firestore operation could not be performed
		 */
		public void delete() {
			if (handle.getFileFieldNames().isEmpty()) {
				runBatch((batch, document) -> {
					batch.delete(document);
				});
			} else {
				Query writeQuery = getWriteQuery();
				QuerySnapshot snapshots = sync(writeQuery.get());
				for (DocumentSnapshot snapshot : snapshots) {
					Dao.this.delete(bucket, snapshot.getId(), snapshot.getReference());
				}
			}
		}

		@Override
		Query getWriteQuery() {
			return query.select(new String[] {});
		}

		@Override
		Collection self() {
			return this;
		}
	}

	private void check(E instance) {
		if (instance == null) {
			throw new NullPointerException("Instance cannot be null");
		}
	}

	private void delete(Bucket bucket, String keyString, DocumentReference document) {
		try (Fao fao = new Fao(bucket, getFileNames(keyString))) {
			fao.remove();
			sync(document.delete());
		}
	}

	private List<String> getFileNames(String keyString) {
		return handle.getFileFieldNames()
				.stream()
				.map((fieldName) -> getFileName(keyString, fieldName))
				.toList();
	}

	private String getFileName(String keyString, String fieldName) {
		return "%s/%s/%s".formatted(handle.getCollectionName(), keyString, fieldName);
	}

	/**
	 * Creates a selection of entity fields with the specified names.
	 *
	 * @param names the names
	 * @return the selection
	 * @throws NullPointerException     if the name array is null
	 * @throws IllegalArgumentException if the name array is empty
	 */
	public Selection select(String... names) {
		if (names == null) {
			throw new NullPointerException("Name array cannot be null");
		}
		if (names.length == 0) {
			throw new IllegalArgumentException("Name array cannot be empty");
		}
		return new Selection(client.getFirestore(), names);
	}

	/**
	 * Represents a query of selected fields.
	 */
	public final class Selection extends Filter<Selection> {
		private final String[] names;
		private final boolean hasKey;

		private Selection(Firestore firestore, String[] names) {
			super(getCollection(firestore).select(handle.buildDataEntryPaths(names)));
			this.names = names;
			this.hasKey = handle.containsKey(names);
		}

		/**
		 * Retrieves a list of value maps corresponding to the query.
		 *
		 * @return the list
		 * @throws DataException if the Firestore operation could not be performed
		 */
		public List<Map<String, Object>> retrieve() {
			QuerySnapshot snapshots = sync(query.get());
			List<Map<String, Object>> list = new ArrayList<>();
			for (DocumentSnapshot snapshot : snapshots) {
				Map<String, Object> values = handle.buildValues(snapshot.getData());
				if (hasKey && handle.hasAutoKey()) {
					handle.putAutoKey(values, snapshot.getId());
				}
				list.add(values);
			}
			return list;
		}

		/**
		 * <p>
		 * Updates the specified values of the entity instances corresponding to the
		 * query.
		 * </p>
		 * <p>
		 * {@link File} fields and the {@link Key} field cannot be updated.
		 * </p>
		 *
		 * @param fieldValues the values
		 * @throws IllegalArgumentException if the number of selected fields and the
		 *                                  number of specified values are different
		 * @throws DataException            if the Firestore operation could not be
		 *                                  performed
		 */
		public void update(Object... fieldValues) {
			if (names.length != fieldValues.length) {
				throw new IllegalArgumentException("Cannot update %d fields with %d values".formatted(names.length, fieldValues.length));
			}
			Map<String, Object> values = new HashMap<>();
			for (int i = 0; i < names.length; i++) {
				values.put(names[i], fieldValues[i]);
			}
			Map<String, Object> data = handle.buildData(values);
			runBatch((batch, document) -> {
				batch.update(document, data);
			});
		}

		/**
		 * <p>
		 * Deletes the selected fields of the entity instances corresponding to the
		 * query.
		 * </p>
		 * <p>
		 * {@link File} fields and the {@link Key} field cannot be deleted.
		 * </p>
		 *
		 * @throws DataException if the Firestore operation could not be performed
		 */
		public void delete() {
			Map<String, Object> values = new HashMap<>();
			for (int i = 0; i < names.length; i++) {
				values.put(names[i], FieldValue.delete());
			}
			Map<String, Object> data = handle.buildData(values);
			runBatch((batch, document) -> {
				batch.update(document, data);
			});
		}

		@Override
		Query getWriteQuery() {
			return query;
		}

		@Override
		Selection self() {
			return this;
		}
	}

	private CollectionReference getCollection(Firestore firestore) {
		return firestore.collection(handle.getCollectionName());
	}

	/**
	 * Base class for queries.
	 *
	 * @param <F> the subclass
	 */
	public abstract sealed class Filter<F extends Filter<F>> permits Collection, Selection {
		Query query;

		private Filter(Query query) {
			this.query = query;
		}

		/**
		 * Considers the entity instances where the value of the field with the
		 * specified name is equal to the specified value.
		 *
		 * @param name       the name
		 * @param fieldValue the value
		 * @return this filter, for chaining
		 */
		public F whereEqualTo(String name, Object fieldValue) {
			query = query.whereEqualTo(handle.buildDataEntryPath(name), fieldValue);
			return self();
		}

		/**
		 * Considers the entity instances where the value of the field with the
		 * specified name is not equal to the specified value.
		 *
		 * @param name       the name
		 * @param fieldValue the value
		 * @return this filter, for chaining
		 */
		public F whereNotEqualTo(String name, Object fieldValue) {
			query = query.whereNotEqualTo(handle.buildDataEntryPath(name), fieldValue);
			return self();
		}

		/**
		 * Considers the entity instances where the value of the field with the
		 * specified name is less than the specified value.
		 *
		 * @param name       the name
		 * @param fieldValue the value
		 * @return this filter, for chaining
		 */
		public F whereLessThan(String name, Object fieldValue) {
			query = query.whereLessThan(handle.buildDataEntryPath(name), fieldValue);
			return self();
		}

		/**
		 * Considers the entity instances where the value of the field with the
		 * specified name is less than or equal to the specified value.
		 *
		 * @param name       the name
		 * @param fieldValue the value
		 * @return this filter, for chaining
		 */
		public F whereLessThanOrEqualTo(String name, Object fieldValue) {
			query = query.whereLessThanOrEqualTo(handle.buildDataEntryPath(name), fieldValue);
			return self();
		}

		/**
		 * Considers the entity instances where the value of the field with the
		 * specified name is greater than the specified value.
		 *
		 * @param name       the name
		 * @param fieldValue the value
		 * @return this filter, for chaining
		 */
		public F whereGreaterThan(String name, Object fieldValue) {
			query = query.whereGreaterThan(handle.buildDataEntryPath(name), fieldValue);
			return self();
		}

		/**
		 * Considers the entity instances where the value of the field with the
		 * specified name is greater than or equal to the specified value.
		 *
		 * @param name       the name
		 * @param fieldValue the value
		 * @return this filter, for chaining
		 */
		public F whereGreaterThanOrEqualTo(String name, Object fieldValue) {
			query = query.whereGreaterThanOrEqualTo(handle.buildDataEntryPath(name), fieldValue);
			return self();
		}

		/**
		 * Considers the entity instances where the value of the field with the
		 * specified name is represented by an array and contains the specified value.
		 *
		 * @param name       the name
		 * @param fieldValue the value
		 * @return this filter, for chaining
		 */
		public F whereArrayContains(String name, Object fieldValue) {
			query = query.whereArrayContains(handle.buildDataEntryPath(name), fieldValue);
			return self();
		}

		/**
		 * Considers the entity instances where the value of the field with the
		 * specified name is represented by an array and contains at least one of the
		 * specified values.
		 *
		 * @param name        the name
		 * @param fieldValues the values
		 * @return this filter, for chaining
		 */
		public F whereArrayContainsAny(String name, List<?> fieldValues) {
			query = query.whereArrayContainsAny(handle.buildDataEntryPath(name), fieldValues);
			return self();
		}

		/**
		 * Considers the entity instances where the value of the field with the
		 * specified name is equal to one of the specified values.
		 *
		 * @param name        the name
		 * @param fieldValues the values
		 * @return this filter, for chaining
		 */
		public F whereIn(String name, List<?> fieldValues) {
			query = query.whereIn(handle.buildDataEntryPath(name), fieldValues);
			return self();
		}

		/**
		 * Considers the entity instances where the value of the field with the
		 * specified name is not equal to any of the specified values.
		 *
		 * @param name        the name
		 * @param fieldValues the values
		 * @return this filter, for chaining
		 */
		public F whereNotIn(String name, List<?> fieldValues) {
			query = query.whereNotIn(handle.buildDataEntryPath(name), fieldValues);
			return self();
		}

		/**
		 * Orders the entity instances by ascending values of the field with the
		 * specified name.
		 *
		 * @param name the name
		 * @return this filter, for chaining
		 */
		public F orderByAscending(String name) {
			query = query.orderBy(handle.buildDataEntryPath(name), Direction.ASCENDING);
			return self();
		}

		/**
		 * Orders the entity instances by descending values of the field with the
		 * specified name.
		 *
		 * @param name the name
		 * @return this filter, for chaining
		 */
		public F orderByDescending(String name) {
			query = query.orderBy(handle.buildDataEntryPath(name), Direction.DESCENDING);
			return self();
		}

		/**
		 * Ignores the first <em>n</em> entity instances.
		 *
		 * @param offset the value of <em>n</em>
		 * @return this filter, for chaining
		 */
		public F offset(int offset) {
			query = query.offset(offset);
			return self();
		}

		/**
		 * Considers the first <em>n</em> entity instances, not counting the offset if
		 * specified.
		 *
		 * @param limit the value of <em>n</em>
		 * @return this filter, for chaining
		 */
		public F limit(int limit) {
			query = query.limit(limit);
			return self();
		}

		/**
		 * Considers the last <em>n</em> entity instances, but only if an order has been
		 * specified.
		 *
		 * @param limit the value of <em>n</em>
		 * @return this filter, for chaining
		 * @throws IllegalStateException if an order has not been specified
		 */
		public F limitToLast(int limit) {
			query = query.limitToLast(limit);
			return self();
		}

		/**
		 * Considers the entity instances that start at the specified values, relative
		 * to the order specified by {@link #orderByAscending(String)} and
		 * {@link #orderByDescending(String)}. The order of the values must match the
		 * order of the calls to {@link #orderByAscending(String)} and
		 * {@link #orderByDescending(String)}.
		 *
		 * @param fieldValues the values
		 * @return this filter, for chaining
		 */
		public F startAt(Object... fieldValues) {
			query = query.startAt(fieldValues);
			return self();
		}

		/**
		 * Considers the entity instances that start after the specified values,
		 * relative to the order specified by {@link #orderByAscending(String)} and
		 * {@link #orderByDescending(String)}. The order of the values must match the
		 * order of the calls to {@link #orderByAscending(String)} and
		 * {@link #orderByDescending(String)}.
		 *
		 * @param fieldValues the values
		 * @return this filter, for chaining
		 */
		public F startAfter(Object... fieldValues) {
			query = query.startAfter(fieldValues);
			return self();
		}

		/**
		 * Considers the entity instances that end before the specified values, relative
		 * to the order specified by {@link #orderByAscending(String)} and
		 * {@link #orderByDescending(String)}. The order of the values must match the
		 * order of the calls to {@link #orderByAscending(String)} and
		 * {@link #orderByDescending(String)}.
		 *
		 * @param fieldValues the values
		 * @return this filter, for chaining
		 */
		public F endBefore(Object... fieldValues) {
			query = query.endBefore(fieldValues);
			return self();
		}

		/**
		 * Considers the entity instances that end at the specified values, relative to
		 * the order specified by {@link #orderByAscending(String)} and
		 * {@link #orderByDescending(String)}. The order of the values must match the
		 * order of the calls to {@link #orderByAscending(String)} and
		 * {@link #orderByDescending(String)}.
		 *
		 * @param fieldValues the values
		 * @return this filter, for chaining
		 */
		public F endAt(Object... fieldValues) {
			query = query.endAt(fieldValues);
			return self();
		}

		/**
		 * Counts the number of entity instances corresponding to the query.
		 *
		 * @return the number
		 * @throws DataException if the Firestore operation could not be performed
		 */
		public long count() {
			AggregateQuery aggregates = query.count();
			AggregateQuerySnapshot aggregate = sync(aggregates.get());
			return aggregate.getCount();
		}

		void runBatch(BiConsumer<WriteBatch, DocumentReference> consumer) {
			Query writeQuery = getWriteQuery();
			QuerySnapshot snapshots = sync(writeQuery.get());
			Dao.this.runBatch(query.getFirestore(), (batch) -> {
				for (DocumentSnapshot snapshot : snapshots) {
					consumer.accept(batch, snapshot.getReference());
				}
			});
		}

		abstract Query getWriteQuery();

		abstract F self();
	}

	private void runBatch(Firestore firestore, Consumer<WriteBatch> consumer) {
		WriteBatch batch = firestore.batch();
		consumer.accept(batch);
		sync(batch.commit());
	}

	<V> V sync(ApiFuture<V> future) {
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
}
