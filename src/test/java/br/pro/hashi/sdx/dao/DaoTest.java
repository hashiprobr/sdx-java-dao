package br.pro.hashi.sdx.dao;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
import com.google.cloud.firestore.WriteResult;
import com.google.cloud.storage.Bucket;

import br.pro.hashi.sdx.dao.Dao.Construction;
import br.pro.hashi.sdx.dao.DaoClient.Connection;
import br.pro.hashi.sdx.dao.exception.DataException;
import br.pro.hashi.sdx.dao.mock.Entity;
import br.pro.hashi.sdx.dao.reflection.Handle;

class DaoTest {
	private List<String> keyStrings;
	private ApiFuture<WriteResult> future;
	private DocumentReference document;
	private CollectionReference collection;
	private ApiFuture<List<WriteResult>> batchFuture;
	private WriteBatch batch;
	private Firestore firestore;
	private Bucket bucket;
	private Connection connection;
	private DaoClient client;
	private Handle<Entity> handle;
	private Dao<Entity> d;
	private MockedConstruction<Fao> faoConstruction;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		keyStrings = new ArrayList<>();
		keyStrings.add("0");
		keyStrings.add("1");
		future = mock(ApiFuture.class);
		document = mock(DocumentReference.class);
		when(document.getId()).thenAnswer((invocation) -> keyStrings.remove(0));
		when(document.create(any(Map.class))).thenReturn(future);
		collection = mock(CollectionReference.class);
		when(collection.document()).thenReturn(document);
		when(collection.document(any(String.class))).thenReturn(document);
		batchFuture = mock(ApiFuture.class);
		batch = mock(WriteBatch.class);
		when(batch.commit()).thenReturn(batchFuture);
		firestore = mock(Firestore.class);
		when(firestore.collection("collection")).thenReturn(collection);
		when(firestore.batch()).thenReturn(batch);
		bucket = mock(Bucket.class);
		connection = new Connection(null, firestore, bucket);
		client = mock(DaoClient.class);
		when(client.getFirestore()).thenReturn(firestore);
		when(client.getBucket()).thenReturn(bucket);
		when(client.getConnection()).thenReturn(connection);
		handle = mock(Handle.class);
		when(handle.getCollectionName()).thenReturn("collection");
		when(handle.toData(any(Entity.class), any(boolean.class), any(boolean.class))).thenAnswer((invocation) -> {
			Entity instance = invocation.getArgument(0);
			boolean withFiles = invocation.getArgument(1);
			boolean withKey = invocation.getArgument(2);
			Map<String, Object> data = new HashMap<>();
			data.put("value", instance.getValue());
			if (withFiles) {
				data.put("file", "");
			}
			if (withKey) {
				data.put("key", handle.getKey(instance));
			}
			return data;
		});
		d = Construction.of(client, handle);
		faoConstruction = mockConstruction(Fao.class);
	}

	@AfterEach
	void tearDown() {
		faoConstruction.close();
	}

	@Test
	void createsDefault() {
		when(client.get(Entity.class)).thenReturn(d);
		ClientFactory clientFactory = mock(ClientFactory.class);
		when(clientFactory.getDefault()).thenReturn(client);
		try (MockedStatic<ClientFactory> clientFactoryStatic = mockStatic(ClientFactory.class)) {
			clientFactoryStatic.when(() -> ClientFactory.getInstance()).thenReturn(clientFactory);
			assertSame(d, Dao.of(Entity.class));
		}
	}

	@Test
	void createsFromId() {
		when(client.get(Entity.class)).thenReturn(d);
		ClientFactory clientFactory = mock(ClientFactory.class);
		when(clientFactory.getFromId("id")).thenReturn(client);
		try (MockedStatic<ClientFactory> clientFactoryStatic = mockStatic(ClientFactory.class)) {
			clientFactoryStatic.when(() -> ClientFactory.getInstance()).thenReturn(clientFactory);
			assertSame(d, Dao.of(Entity.class, "id"));
		}
	}

	@Test
	void creates() {
		mockAutoKey();
		mockFileFieldNames();
		mockBatchFutureReturn();
		Entity instance0 = newEntity(false, 0);
		Entity instance1 = newEntity(true, 1);
		List<Entity> instances = List.of(instance0, instance1);
		assertEquals(List.of("false", "true"), d.create(instances));
		verify(batch).create(document, Map.of("key", false, "value", 0, "file", ""));
		verify(batch).create(document, Map.of("key", true, "value", 1, "file", ""));
		verify(batch).commit();
		assertDoesNotThrow(() -> {
			verify(batchFuture).get();
		});
	}

	@Test
	void createsWithAutoKey() {
		mockAutoKey(true);
		mockFileFieldNames();
		mockBatchFutureReturn();
		Entity instance0 = newEntity(null, 0);
		Entity instance1 = newEntity(null, 1);
		List<Entity> instances = List.of(instance0, instance1);
		assertEquals(List.of("0", "1"), d.create(instances));
		verify(batch).create(document, Map.of("value", 0, "file", ""));
		verify(batch).create(document, Map.of("value", 1, "file", ""));
		verify(batch).commit();
		assertDoesNotThrow(() -> {
			verify(batchFuture).get();
		});
	}

	@Test
	void createsWithFileField() {
		mockAutoKey();
		mockFileFieldNames(Set.of("file"));
		mockFutureReturn();
		Entity instance0 = newEntity(false, 0);
		Entity instance1 = newEntity(true, 1);
		List<Entity> instances = List.of(instance0, instance1);
		assertEquals(List.of("false", "true"), d.create(instances));
		verify(document).create(Map.of("key", false, "value", 0, "file", ""));
		verify(document).create(Map.of("key", true, "value", 1, "file", ""));
		assertDoesNotThrow(() -> {
			verify(future, times(2)).get();
		});
	}

	@Test
	void createsWithAutoKeyAndFileField() {
		mockAutoKey(true);
		mockFileFieldNames(Set.of("file"));
		mockFutureReturn();
		Entity instance0 = newEntity(null, 0);
		Entity instance1 = newEntity(null, 1);
		List<Entity> instances = List.of(instance0, instance1);
		assertEquals(List.of("0", "1"), d.create(instances));
		verify(document).create(Map.of("value", 0, "file", ""));
		verify(document).create(Map.of("value", 1, "file", ""));
		assertDoesNotThrow(() -> {
			verify(future, times(2)).get();
		});
	}

	@Test
	void doesNotCreateIfInstanceIsNull() {
		assertThrows(NullPointerException.class, () -> {
			d.create((Entity) null);
		});
	}

	@Test
	void doesNotCreateIfListIsNull() {
		assertThrows(NullPointerException.class, () -> {
			d.create((List<Entity>) null);
		});
	}

	@Test
	void doesNotCreateIfListIsEmpty() {
		assertThrows(IllegalArgumentException.class, () -> {
			d.create(List.of());
		});
	}

	@Test
	void doesNotCreateIfKeyIsNull() {
		mockAutoKey();
		Entity instance = newEntity(null, 0);
		assertThrows(NullPointerException.class, () -> {
			d.create(instance);
		});
	}

	@Test
	void doesNotCreateWithAutoKeyIfKeyIsNotNull() {
		mockAutoKey(true);
		Entity instance = newEntity(false, 0);
		assertThrows(IllegalArgumentException.class, () -> {
			d.create(instance);
		});
	}

	@Test
	void doesNotCreateIfBatchFutureThrows() {
		mockAutoKey();
		mockFileFieldNames();
		Throwable cause = mockBatchFutureThrow();
		Entity instance0 = newEntity(false, 0);
		Entity instance1 = newEntity(true, 1);
		List<Entity> instances = List.of(instance0, instance1);
		Exception exception = assertThrows(DataException.class, () -> {
			d.create(instances);
		});
		assertSame(cause, exception.getCause());
	}

	@Test
	void doesNotCreateWithFileFieldIfFutureThrows() {
		mockAutoKey();
		mockFileFieldNames(Set.of("file"));
		Throwable cause = mockFutureThrow();
		Entity instance0 = newEntity(false, 0);
		Entity instance1 = newEntity(true, 1);
		List<Entity> instances = List.of(instance0, instance1);
		Exception exception = assertThrows(DataException.class, () -> {
			d.create(instances);
		});
		assertSame(cause, exception.getCause());
	}

	private void mockAutoKey() {
		mockAutoKey(false);
	}

	private void mockAutoKey(boolean autoKey) {
		when(handle.hasAutoKey()).thenReturn(autoKey);
	}

	private void mockFileFieldNames() {
		mockFileFieldNames(Set.of());
	}

	private void mockFileFieldNames(Set<String> fileFieldNames) {
		when(handle.getFileFieldNames()).thenReturn(fileFieldNames);
	}

	private void mockFutureReturn() {
		WriteResult result = mock(WriteResult.class);
		assertDoesNotThrow(() -> {
			when(future.get()).thenReturn(result);
		});
	}

	private Throwable mockFutureThrow() {
		Throwable cause = new Throwable();
		ExecutionException exception = new ExecutionException(cause);
		assertDoesNotThrow(() -> {
			when(future.get()).thenThrow(exception);
		});
		return cause;
	}

	private void mockBatchFutureReturn() {
		List<WriteResult> results = List.of();
		assertDoesNotThrow(() -> {
			when(batchFuture.get()).thenReturn(results);
		});
	}

	private Throwable mockBatchFutureThrow() {
		Throwable cause = new Throwable();
		ExecutionException exception = new ExecutionException(cause);
		assertDoesNotThrow(() -> {
			when(batchFuture.get()).thenThrow(exception);
		});
		return cause;
	}

	private Entity newEntity(Object key, int value) {
		Entity instance = new Entity(value);
		when(handle.getKey(instance)).thenReturn(key);
		return instance;
	}
}
