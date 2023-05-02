package br.pro.hashi.sdx.dao;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
import com.google.cloud.firestore.WriteResult;
import com.google.cloud.storage.Bucket;
import com.google.firebase.FirebaseApp;

import br.pro.hashi.sdx.dao.Dao.Construction;
import br.pro.hashi.sdx.dao.DaoClient.Connection;
import br.pro.hashi.sdx.dao.exception.DataException;
import br.pro.hashi.sdx.dao.mock.Entity;
import br.pro.hashi.sdx.dao.reflection.Handle;

class DaoTest {
	private List<String> keyStrings;
	private ApiFuture<DocumentSnapshot> readFuture;
	private ApiFuture<WriteResult> writeFuture;
	private DocumentReference document;
	private CollectionReference collection;
	private ApiFuture<List<WriteResult>> batchWriteFuture;
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
		readFuture = mock(ApiFuture.class);
		writeFuture = mock(ApiFuture.class);
		document = mock(DocumentReference.class);
		when(document.getId()).thenAnswer((invocation) -> keyStrings.remove(0));
		when(document.get()).thenReturn(readFuture);
		when(document.create(any(Map.class))).thenReturn(writeFuture);
		when(document.update(any(Map.class))).thenReturn(writeFuture);
		collection = mock(CollectionReference.class);
		when(collection.document()).thenReturn(document);
		when(collection.document(any(String.class))).thenReturn(document);
		batchWriteFuture = mock(ApiFuture.class);
		batch = mock(WriteBatch.class);
		when(batch.create(eq(document), any(Map.class))).thenReturn(batch);
		when(batch.commit()).thenReturn(batchWriteFuture);
		firestore = mock(Firestore.class);
		when(firestore.collection("collection")).thenReturn(collection);
		when(firestore.batch()).thenReturn(batch);
		bucket = mock(Bucket.class);
		connection = new Connection(mock(FirebaseApp.class), firestore, bucket);
		client = mock(DaoClient.class);
		when(client.getFirestore()).thenReturn(firestore);
		when(client.getBucket()).thenReturn(bucket);
		when(client.getConnection()).thenReturn(connection);
		handle = mock(Handle.class);
		when(handle.getCollectionName()).thenReturn("collection");
		when(handle.toInstance(any(Map.class))).thenAnswer((invocation) -> {
			Map<String, Object> data = invocation.getArgument(0);
			return new Entity((int) data.get("value"));
		});
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
		when(handle.toData(any(Map.class))).thenAnswer((invocation) -> {
			Map<String, Object> values = invocation.getArgument(0);
			return Map.of("value", values.get("value"));
		});
		d = Construction.of(client, handle);
		faoConstruction = mockConstruction(Fao.class);
	}

	@AfterEach
	void tearDown() {
		faoConstruction.close();
	}

	@Test
	void createsFirst() {
		when(client.get(Entity.class)).thenReturn(d);
		ClientFactory clientFactory = mock(ClientFactory.class);
		when(clientFactory.getFirst()).thenReturn(client);
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
		mockBatchWriteFutureReturn();
		List<Entity> instances = List.of(newEntity(false, 0), newEntity(true, 1));
		assertEquals(List.of("false", "true"), d.create(instances));
		verify(collection).document("false");
		verify(collection).document("true");
		verify(batch).create(document, Map.of("key", false, "value", 0, "file", ""));
		verify(batch).create(document, Map.of("key", true, "value", 1, "file", ""));
		verify(batch).commit();
		assertDoesNotThrow(() -> {
			verify(batchWriteFuture).get();
		});
	}

	@Test
	void createsWithAutoKey() {
		mockAutoKey(true);
		mockFileFieldNames();
		mockBatchWriteFutureReturn();
		List<Entity> instances = List.of(newEntity(null, 0), newEntity(null, 1));
		assertEquals(List.of("0", "1"), d.create(instances));
		verify(batch).create(document, Map.of("value", 0, "file", ""));
		verify(batch).create(document, Map.of("value", 1, "file", ""));
		verify(batch).commit();
		assertDoesNotThrow(() -> {
			verify(batchWriteFuture).get();
		});
	}

	@Test
	void createsWithFileField() {
		mockAutoKey();
		mockFileFieldNames(Set.of("file"));
		mockWriteFutureReturn();
		List<Entity> instances = List.of(newEntity(false, 0), newEntity(true, 1));
		assertEquals(List.of("false", "true"), d.create(instances));
		verify(collection).document("false");
		verify(collection).document("true");
		verify(document).create(Map.of("key", false, "value", 0, "file", ""));
		verify(document).create(Map.of("key", true, "value", 1, "file", ""));
		assertDoesNotThrow(() -> {
			verify(writeFuture, times(2)).get();
		});
	}

	@Test
	void createsWithAutoKeyAndFileField() {
		mockAutoKey(true);
		mockFileFieldNames(Set.of("file"));
		mockWriteFutureReturn();
		List<Entity> instances = List.of(newEntity(null, 0), newEntity(null, 1));
		assertEquals(List.of("0", "1"), d.create(instances));
		verify(document).create(Map.of("value", 0, "file", ""));
		verify(document).create(Map.of("value", 1, "file", ""));
		assertDoesNotThrow(() -> {
			verify(writeFuture, times(2)).get();
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
		Entity instance = newEntity(null, 1);
		assertThrows(NullPointerException.class, () -> {
			d.create(instance);
		});
	}

	@Test
	void doesNotCreateIfFirstIsNull() {
		mockAutoKey();
		mockFileFieldNames();
		List<Entity> instances = new ArrayList<>();
		instances.add(null);
		instances.add(newEntity(true, 1));
		assertThrows(NullPointerException.class, () -> {
			d.create(instances);
		});
	}

	@Test
	void doesNotCreateIfSecondIsNull() {
		mockAutoKey();
		mockFileFieldNames();
		List<Entity> instances = new ArrayList<>();
		instances.add(newEntity(true, 1));
		instances.add(null);
		assertThrows(NullPointerException.class, () -> {
			d.create(instances);
		});
	}

	@Test
	void doesNotCreateIfFirstKeyIsNull() {
		mockAutoKey();
		mockFileFieldNames();
		List<Entity> instances = List.of(newEntity(null, 0), newEntity(true, 1));
		assertThrows(NullPointerException.class, () -> {
			d.create(instances);
		});
	}

	@Test
	void doesNotCreateIfSecondKeyIsNull() {
		mockAutoKey();
		mockFileFieldNames();
		List<Entity> instances = List.of(newEntity(false, 0), newEntity(null, 1));
		assertThrows(NullPointerException.class, () -> {
			d.create(instances);
		});
	}

	@Test
	void doesNotCreateWithAutoKeyIfKeyIsNotNull() {
		mockAutoKey(true);
		Entity instance = newEntity(true, 1);
		assertThrows(IllegalArgumentException.class, () -> {
			d.create(instance);
		});
	}

	@Test
	void doesNotCreateWithAutoKeyIfFirstKeyIsNotNull() {
		mockAutoKey(true);
		mockFileFieldNames();
		List<Entity> instances = List.of(newEntity(false, 0), newEntity(null, 1));
		assertThrows(IllegalArgumentException.class, () -> {
			d.create(instances);
		});
	}

	@Test
	void doesNotCreateWithAutoKeyIfSecondKeyIsNotNull() {
		mockAutoKey(true);
		mockFileFieldNames();
		List<Entity> instances = List.of(newEntity(null, 0), newEntity(true, 1));
		assertThrows(IllegalArgumentException.class, () -> {
			d.create(instances);
		});
	}

	@Test
	void doesNotCreateIfBatchWriteFutureThrows() {
		mockAutoKey();
		mockFileFieldNames();
		Throwable cause = mockBatchWriteFutureThrow();
		List<Entity> instances = List.of(newEntity(false, 0), newEntity(true, 1));
		Exception exception = assertThrows(DataException.class, () -> {
			d.create(instances);
		});
		assertSame(cause, exception.getCause());
	}

	@Test
	void doesNotCreateWithFileFieldIfWriteFutureThrows() {
		mockAutoKey();
		mockFileFieldNames(Set.of("file"));
		Throwable cause = mockWriteFutureThrow();
		List<Entity> instances = List.of(newEntity(false, 0), newEntity(true, 1));
		Exception exception = assertThrows(DataException.class, () -> {
			d.create(instances);
		});
		assertSame(cause, exception.getCause());
	}

	private void mockFileFieldNames() {
		mockFileFieldNames(Set.of());
	}

	private void mockFileFieldNames(Set<String> fileFieldNames) {
		when(handle.getFileFieldNames()).thenReturn(fileFieldNames);
	}

	@Test
	void retrieves() {
		mockAutoKey();
		mockReadFutureReturn(1);
		Entity instance = d.retrieve(true);
		assertEquals(1, instance.getValue());
		verify(handle, times(0)).setAutoKey(any(), any());
	}

	@Test
	void retrievesWithAutoKey() {
		mockAutoKey(true);
		mockReadFutureReturn(1);
		Entity instance = d.retrieve(true);
		assertEquals(1, instance.getValue());
		verify(handle).setAutoKey(instance, "true");
	}

	@Test
	void doesNotRetrieveIfKeyIsNull() {
		assertThrows(NullPointerException.class, () -> {
			d.retrieve(null);
		});
	}

	@Test
	void doesNotRetrieveIfReadFutureThrows() {
		mockAutoKey();
		Throwable cause = mockReadFutureThrow();
		Exception exception = assertThrows(DataException.class, () -> {
			d.retrieve(false);
		});
		assertSame(cause, exception.getCause());
	}

	private void mockAutoKey() {
		mockAutoKey(false);
	}

	private void mockAutoKey(boolean autoKey) {
		when(handle.hasAutoKey()).thenReturn(autoKey);
	}

	private void mockReadFutureReturn(int value) {
		DocumentSnapshot snapshot = mock(DocumentSnapshot.class);
		when(snapshot.getData()).thenReturn(Map.of("value", value));
		assertDoesNotThrow(() -> {
			when(readFuture.get()).thenReturn(snapshot);
		});
	}

	private Throwable mockReadFutureThrow() {
		Throwable cause = new Throwable();
		ExecutionException exception = new ExecutionException(cause);
		assertDoesNotThrow(() -> {
			when(readFuture.get()).thenThrow(exception);
		});
		return cause;
	}

	@Test
	void updatesFromInstance() {
		mockWriteFutureReturn();
		Entity instance = newEntity(true, 1);
		d.update(instance);
		verify(collection).document("true");
		verify(document).update(Map.of("value", 1));
		assertDoesNotThrow(() -> {
			verify(writeFuture).get();
		});
	}

	@Test
	void doesNotUpdateFromNullInstance() {
		assertThrows(NullPointerException.class, () -> {
			d.update((Entity) null);
		});
	}

	@Test
	void doesNotUpdateFromInstanceWithNullKey() {
		Entity instance = newEntity(null, 1);
		assertThrows(NullPointerException.class, () -> {
			d.update(instance);
		});
	}

	@Test
	void updatesFromValues() {
		mockWriteFutureReturn();
		Map<String, Object> values = Map.of("value", 1);
		d.update(true, values);
		verify(collection).document("true");
		verify(document).update(Map.of("value", 1));
		assertDoesNotThrow(() -> {
			verify(writeFuture).get();
		});
	}

	@Test
	void doesNotUpdateFromNullValues() {
		assertThrows(NullPointerException.class, () -> {
			d.update(true, null);
		});
	}

	@Test
	void doesNotUpdateFromEmptyValues() {
		assertThrows(IllegalArgumentException.class, () -> {
			d.update(true, Map.of());
		});
	}

	@Test
	void doesNotUpdateFromValuesWithNullKey() {
		Map<String, Object> values = Map.of("value", 1);
		assertThrows(NullPointerException.class, () -> {
			d.update(null, values);
		});
	}

	@Test
	void updatesFromList() {
		mockBatchWriteFutureReturn();
		List<Entity> instances = List.of(newEntity(false, 0), newEntity(true, 1));
		d.update(instances);
		verify(collection).document("false");
		verify(collection).document("true");
		verify(batch).update(document, Map.of("value", 0));
		verify(batch).update(document, Map.of("value", 1));
		assertDoesNotThrow(() -> {
			verify(batchWriteFuture).get();
		});
	}

	@Test
	void doesNotUpdateFromNullList() {
		assertThrows(NullPointerException.class, () -> {
			d.update((List<Entity>) null);
		});
	}

	@Test
	void doesNotUpdateFromEmptyList() {
		assertThrows(IllegalArgumentException.class, () -> {
			d.update(List.of());
		});
	}

	@Test
	void doesNotUpdateFromNullListFirst() {
		List<Entity> instances = new ArrayList<>();
		instances.add(null);
		instances.add(newEntity(true, 1));
		assertThrows(NullPointerException.class, () -> {
			d.update(instances);
		});
	}

	@Test
	void doesNotUpdateFromNullListSecond() {
		List<Entity> instances = new ArrayList<>();
		instances.add(newEntity(true, 1));
		instances.add(null);
		assertThrows(NullPointerException.class, () -> {
			d.update(instances);
		});
	}

	@Test
	void doesNotUpdateFromListFirstWithNullKey() {
		List<Entity> instances = new ArrayList<>();
		instances.add(newEntity(null, 0));
		instances.add(newEntity(true, 1));
		assertThrows(NullPointerException.class, () -> {
			d.update(instances);
		});
	}

	@Test
	void doesNotUpdateFromListSecondWithNullKey() {
		List<Entity> instances = new ArrayList<>();
		instances.add(newEntity(false, 0));
		instances.add(newEntity(null, 1));
		assertThrows(NullPointerException.class, () -> {
			d.update(instances);
		});
	}

	@Test
	void updatesFromMap() {
		mockBatchWriteFutureReturn();
		Map<Object, Map<String, Object>> map = Map.of("false", Map.of("value", 0), "true", Map.of("value", 1));
		d.update(map);
		verify(collection).document("false");
		verify(collection).document("true");
		verify(batch).update(document, Map.of("value", 0));
		verify(batch).update(document, Map.of("value", 1));
		assertDoesNotThrow(() -> {
			verify(batchWriteFuture).get();
		});
	}

	@Test
	void doesNotUpdateFromNullMap() {
		assertThrows(NullPointerException.class, () -> {
			d.update((Map<Object, Map<String, Object>>) null);
		});
	}

	@Test
	void doesNotUpdateFromEmptyMap() {
		assertThrows(IllegalArgumentException.class, () -> {
			d.update(Map.of());
		});
	}

	@Test
	void doesNotUpdateFromNullMapFirst() {
		Map<Object, Map<String, Object>> map = new LinkedHashMap<>();
		map.put("false", null);
		map.put("true", Map.of("value", 1));
		assertThrows(NullPointerException.class, () -> {
			d.update(map);
		});
	}

	@Test
	void doesNotUpdateFromNullMapSecond() {
		Map<Object, Map<String, Object>> map = new LinkedHashMap<>();
		map.put("false", Map.of("value", 0));
		map.put("true", null);
		assertThrows(NullPointerException.class, () -> {
			d.update(map);
		});
	}

	@Test
	void doesNotUpdateFromEmptyMapFirst() {
		Map<Object, Map<String, Object>> map = new LinkedHashMap<>();
		map.put("false", Map.of());
		map.put("true", Map.of("value", 1));
		assertThrows(IllegalArgumentException.class, () -> {
			d.update(map);
		});
	}

	@Test
	void doesNotUpdateFromEmptyMapSecond() {
		Map<Object, Map<String, Object>> map = new LinkedHashMap<>();
		map.put("false", Map.of("value", 0));
		map.put("true", Map.of());
		assertThrows(IllegalArgumentException.class, () -> {
			d.update(map);
		});
	}

	@Test
	void doesNotUpdateFromMapFirstWithNullKey() {
		Map<Object, Map<String, Object>> map = new LinkedHashMap<>();
		map.put(null, Map.of("value", 0));
		map.put("true", Map.of("value", 1));
		assertThrows(NullPointerException.class, () -> {
			d.update(map);
		});
	}

	@Test
	void doesNotUpdateFromMapSecondWithNullKey() {
		Map<Object, Map<String, Object>> map = new LinkedHashMap<>();
		map.put("false", Map.of("value", 0));
		map.put(null, Map.of("value", 1));
		assertThrows(NullPointerException.class, () -> {
			d.update(map);
		});
	}

	private void mockWriteFutureReturn() {
		WriteResult result = mock(WriteResult.class);
		assertDoesNotThrow(() -> {
			when(writeFuture.get()).thenReturn(result);
		});
	}

	private Throwable mockWriteFutureThrow() {
		Throwable cause = new Throwable();
		ExecutionException exception = new ExecutionException(cause);
		assertDoesNotThrow(() -> {
			when(writeFuture.get()).thenThrow(exception);
		});
		return cause;
	}

	private void mockBatchWriteFutureReturn() {
		List<WriteResult> results = List.of();
		assertDoesNotThrow(() -> {
			when(batchWriteFuture.get()).thenReturn(results);
		});
	}

	private Throwable mockBatchWriteFutureThrow() {
		Throwable cause = new Throwable();
		ExecutionException exception = new ExecutionException(cause);
		assertDoesNotThrow(() -> {
			when(batchWriteFuture.get()).thenThrow(exception);
		});
		return cause;
	}

	private Entity newEntity(Object key, int value) {
		Entity instance = new Entity(value);
		when(handle.getKey(instance)).thenReturn(key);
		return instance;
	}
}
