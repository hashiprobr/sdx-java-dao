package br.pro.hashi.sdx.dao;

import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.WriteResult;

import br.pro.hashi.sdx.dao.reflection.Compiler;
import br.pro.hashi.sdx.dao.reflection.Handle;

public final class Dao<T> {
	public static <T> Dao<T> of(Class<T> type) {
		return ClientFactory.getInstance().get().get(type);
	}

	private final Compiler compiler;
	private final DaoClient client;
	private final Handle handle;
	private final String collectionName;

	Dao(DaoClient client, Handle handle) {
		this(Compiler.getInstance(), client, handle);
	}

	Dao(Compiler compiler, DaoClient client, Handle handle) {
		this.compiler = compiler;
		this.client = client;
		this.handle = handle;
		this.collectionName = handle.getCollectionName();
	}

	Compiler getCompiler() {
		return compiler;
	}

	public String create(T instance) {
		check(instance);
		String keyString;
		DocumentReference document;
		ApiFuture<WriteResult> future;
		if (handle.hasAutoKey()) {
			document = getCollection().document();
			keyString = document.getId();
			future = document.create(handle.toData(instance));
		} else {
			keyString = handle.getKeyString(instance);
			document = getDocument(keyString);
			future = document.create(compiler.getProxy(handle, instance));
		}
		await(future);
		return keyString;
	}

	public T retrieve(Object key) {
		String keyString = toString(key);
		DocumentReference reference = getDocument(keyString);
		DocumentSnapshot snapshot = await(reference.get());
		@SuppressWarnings("unchecked")
		T instance = (T) handle.toInstance(snapshot.getData());
		if (handle.hasAutoKey()) {
			handle.setAutoKey(instance, keyString);
		}
		return instance;
	}

	public void update(T instance) {
		check(instance);
		String keyString = handle.getKeyString(instance);
		update(keyString, handle.toData(instance));
	}

	public void update(Object key, Map<String, Object> values) {
		check(values);
		String keyString = key.toString();
		update(keyString, handle.toData(values));
	}

	private void update(String keyString, Map<String, Object> data) {
		DocumentReference reference = getDocument(keyString);
		await(reference.update(data));
	}

	public void delete(Object key) {
		String keyString = toString(key);
		DocumentReference reference = getDocument(keyString);
		await(reference.delete());
	}

	private String toString(Object key) {
		if (key == null) {
			throw new NullPointerException("Key cannot be null");
		}
		return key.toString();
	}

	private void check(T instance) {
		if (instance == null) {
			throw new NullPointerException("Instance cannot be null");
		}
	}

	private void check(Map<String, Object> values) {
		if (values == null) {
			throw new NullPointerException("Values map cannot be null");
		}
		if (values.isEmpty()) {
			throw new NullPointerException("Values map cannot be empty");
		}
	}

	private DocumentReference getDocument(String keyString) {
		return getCollection().document(keyString);
	}

	private CollectionReference getCollection() {
		return client.getFirestore().collection(collectionName);
	}

	private <V> V await(ApiFuture<V> future) {
		V result;
		try {
			result = future.get();
		} catch (ExecutionException exception) {
			throw new DaoException(exception.getCause());
		} catch (InterruptedException exception) {
			throw new DaoException(exception);
		}
		return result;
	}
}
