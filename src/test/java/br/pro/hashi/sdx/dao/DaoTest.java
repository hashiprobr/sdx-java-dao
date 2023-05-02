package br.pro.hashi.sdx.dao;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedConstruction.MockInitializer;
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
import br.pro.hashi.sdx.dao.exception.FileException;
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
		when(document.delete()).thenReturn(writeFuture);
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
	}

	@Test
	void createsFirst() {
		try (MockedConstruction<Fao> faoConstruction = mockConstruction(Fao.class)) {
			when(client.get(Entity.class)).thenReturn(d);
			ClientFactory clientFactory = mock(ClientFactory.class);
			when(clientFactory.getFirst()).thenReturn(client);
			try (MockedStatic<ClientFactory> clientFactoryStatic = mockStatic(ClientFactory.class)) {
				clientFactoryStatic.when(() -> ClientFactory.getInstance()).thenReturn(clientFactory);
				assertSame(d, Dao.of(Entity.class));
			}
		}
	}

	@Test
	void createsFromId() {
		try (MockedConstruction<Fao> faoConstruction = mockConstruction(Fao.class)) {
			when(client.get(Entity.class)).thenReturn(d);
			ClientFactory clientFactory = mock(ClientFactory.class);
			when(clientFactory.getFromId("id")).thenReturn(client);
			try (MockedStatic<ClientFactory> clientFactoryStatic = mockStatic(ClientFactory.class)) {
				clientFactoryStatic.when(() -> ClientFactory.getInstance()).thenReturn(clientFactory);
				assertSame(d, Dao.of(Entity.class, "id"));
			}
		}
	}

	@Test
	void creates() {
		mockAutoKey();
		mockFileFieldNames();
		mockBatchWriteFutureReturn();
		assertEquals(List.of("false", "true"), d.create(List.of(newEntity(false, 0), newEntity(true, 1))));
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
		assertEquals(List.of("0", "1"), d.create(List.of(newEntity(null, 0), newEntity(null, 1))));
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
		mockFileFieldNames(List.of("file0", "file1"));
		mockWriteFutureReturn();
		List<Entity> instances = List.of(newEntity(false, 0), newEntity(true, 1));
		List<String> allFileNames = new ArrayList<>();
		List<String> keyStrings;
		try (MockedConstruction<Fao> faoConstruction = mockFaoConstruction(allFileNames)) {
			keyStrings = d.create(instances);
		}
		assertEquals(List.of("false", "true"), keyStrings);
		assertEquals(List.of(
				"collection/false/file0",
				"collection/false/file1",
				"collection/true/file0",
				"collection/true/file1"), allFileNames);
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
		mockFileFieldNames(List.of("file0", "file1"));
		mockWriteFutureReturn();
		List<Entity> instances = List.of(newEntity(null, 0), newEntity(null, 1));
		List<String> allFileNames = new ArrayList<>();
		List<String> keyStrings;
		try (MockedConstruction<Fao> faoConstruction = mockFaoConstruction(allFileNames)) {
			keyStrings = d.create(instances);
		}
		assertEquals(List.of("0", "1"), keyStrings);
		assertEquals(List.of(
				"collection/0/file0",
				"collection/0/file1",
				"collection/1/file0",
				"collection/1/file1"), allFileNames);
		verify(document).create(Map.of("value", 0, "file", ""));
		verify(document).create(Map.of("value", 1, "file", ""));
		assertDoesNotThrow(() -> {
			verify(writeFuture, times(2)).get();
		});
	}

	private MockedConstruction<Fao> mockFaoConstruction(List<String> allFileNames) {
		MockInitializer<Fao> initializer = (mock, context) -> {
			List<?> arguments = context.arguments();
			assertEquals(bucket, arguments.get(0));
			@SuppressWarnings("unchecked")
			List<String> fileNames = (List<String>) arguments.get(1);
			allFileNames.addAll(fileNames);
		};
		return mockConstruction(Fao.class, initializer);
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
	void doesNotCreateIfFirstIsNull() {
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
		mockFileFieldNames();
		List<Entity> instances = new ArrayList<>();
		instances.add(newEntity(true, 1));
		instances.add(null);
		assertThrows(NullPointerException.class, () -> {
			d.create(instances);
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
	void doesNotCreateIfWriteFutureThrows() {
		mockAutoKey();
		Throwable cause = mockWriteFutureThrow();
		Entity instance = newEntity(true, 1);
		Exception exception;
		try (MockedConstruction<Fao> faoConstruction = mockConstruction(Fao.class)) {
			exception = assertThrows(DataException.class, () -> {
				d.create(instance);
			});
		}
		assertSame(cause, exception.getCause());
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
	void doesNotUpdateFromInstanceIfWriteFutureThrows() {
		Throwable cause = mockWriteFutureThrow();
		Entity instance = newEntity(true, 1);
		Exception exception = assertThrows(DataException.class, () -> {
			d.update(instance);
		});
		assertSame(cause, exception.getCause());
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
	void doesNotUpdateFromValuesIfWriteFutureThrows() {
		Throwable cause = mockWriteFutureThrow();
		Map<String, Object> values = Map.of("value", 1);
		Exception exception = assertThrows(DataException.class, () -> {
			d.update(true, values);
		});
		assertSame(cause, exception.getCause());
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
	void doesNotUpdateFromListIfBatchWriteFutureThrows() {
		Throwable cause = mockBatchWriteFutureThrow();
		List<Entity> instances = List.of(newEntity(false, 0), newEntity(true, 1));
		Exception exception = assertThrows(DataException.class, () -> {
			d.update(instances);
		});
		assertSame(cause, exception.getCause());
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

	@Test
	void doesNotUpdateFromMapIfBatchWriteFutureThrows() {
		Throwable cause = mockBatchWriteFutureThrow();
		Map<Object, Map<String, Object>> map = Map.of("false", Map.of("value", 0), "true", Map.of("value", 1));
		Exception exception = assertThrows(DataException.class, () -> {
			d.update(map);
		});
		assertSame(cause, exception.getCause());
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

	@Test
	void deletes() {
		mockFileFieldNames(List.of("file0", "file1"));
		mockWriteFutureReturn();
		Fao fao;
		MockInitializer<Fao> initializer = (mock, context) -> {
			List<?> arguments = context.arguments();
			assertEquals(bucket, arguments.get(0));
			assertEquals(List.of("collection/1/file0", "collection/1/file1"), arguments.get(1));
		};
		try (MockedConstruction<Fao> faoConstruction = mockConstruction(Fao.class, initializer)) {
			d.delete(1);
			fao = faoConstruction.constructed().get(0);
		}
		verify(fao).remove();
		verify(collection).document("1");
		verify(document).delete();
		assertDoesNotThrow(() -> {
			verify(writeFuture).get();
		});
	}

	@Test
	void doesNotDeleteIfKeyIsNull() {
		assertThrows(NullPointerException.class, () -> {
			d.delete(null);
		});
	}

	@Test
	void doesNotDeleteIfFaoThrows() {
		mockFileFieldNames();
		MockInitializer<Fao> initializer = (mock, context) -> {
			doThrow(FileException.class).when(mock).remove();
		};
		try (MockedConstruction<Fao> faoConstruction = mockConstruction(Fao.class, initializer)) {
			assertThrows(FileException.class, () -> {
				d.delete(1);
			});
		}
	}

	@Test
	void doesNotDeleteIfWriteFutureThrows() {
		mockFileFieldNames();
		Throwable cause = mockWriteFutureThrow();
		Exception exception;
		try (MockedConstruction<Fao> faoConstruction = mockConstruction(Fao.class)) {
			exception = assertThrows(DataException.class, () -> {
				d.delete(1);
			});
		}
		assertSame(cause, exception.getCause());
	}

	@Test
	void uploadsFile() {
		mockFaoFileFieldNames();
		InputStream stream = InputStream.nullInputStream();
		mockWriteFutureReturn();
		String url;
		Fao fao;
		try (MockedConstruction<Fao> faoConstruction = mockUploadFaoConstruction(stream)) {
			url = d.uploadFile(1, "file", stream);
			fao = faoConstruction.constructed().get(0);
		}
		assertEquals("url", url);
		verify(fao).upload(stream, "application/octet-stream", true);
		verify(collection).document("1");
		verify(document).update("file", "url");
		assertDoesNotThrow(() -> {
			verify(writeFuture).get();
		});
	}

	@Test
	void doesNotUploadFileIfStreamIsNull() {
		assertThrows(NullPointerException.class, () -> {
			d.uploadFile(1, "file", null);
		});
	}

	@Test
	void doesNotUploadFileIfFieldNameIsNull() {
		InputStream stream = InputStream.nullInputStream();
		assertThrows(NullPointerException.class, () -> {
			d.uploadFile(1, null, stream);
		});
	}

	@Test
	void doesNotUploadFileIfFieldDoesNotExist() {
		mockFileFieldNames();
		InputStream stream = InputStream.nullInputStream();
		assertThrows(IllegalArgumentException.class, () -> {
			d.uploadFile(1, "file", stream);
		});
	}

	@Test
	void doesNotUploadFileIfKeyIsNull() {
		mockFaoFileFieldNames();
		InputStream stream = InputStream.nullInputStream();
		assertThrows(NullPointerException.class, () -> {
			d.uploadFile(null, "file", stream);
		});
	}

	@Test
	void doesNotUploadFileIfFaoThrows() {
		mockFaoFileFieldNames();
		InputStream stream = InputStream.nullInputStream();
		MockInitializer<Fao> initializer = (mock, context) -> {
			when(mock.upload(stream, "application/octet-stream", true)).thenThrow(FileException.class);
		};
		try (MockedConstruction<Fao> faoConstruction = mockConstruction(Fao.class, initializer)) {
			assertThrows(FileException.class, () -> {
				d.uploadFile(1, "file", stream);
			});
		}
	}

	@Test
	void doesNotUploadFileIfWriteFutureThrows() {
		mockFaoFileFieldNames();
		InputStream stream = InputStream.nullInputStream();
		Throwable cause = mockWriteFutureThrow();
		Exception exception;
		try (MockedConstruction<Fao> faoConstruction = mockUploadFaoConstruction(stream)) {
			exception = assertThrows(DataException.class, () -> {
				d.uploadFile(1, "file", stream);
			});
		}
		assertSame(cause, exception.getCause());
	}

	private MockedConstruction<Fao> mockUploadFaoConstruction(InputStream stream) {
		when(document.update("file", "url")).thenReturn(writeFuture);
		MockInitializer<Fao> initializer = (mock, context) -> {
			List<?> arguments = context.arguments();
			assertEquals(bucket, arguments.get(0));
			assertEquals("collection/1/file", arguments.get(1));
			when(mock.upload(stream, "application/octet-stream", true)).thenReturn("url");
		};
		return mockConstruction(Fao.class, initializer);
	}

	@Test
	void refreshesFile() {
		mockFaoFileFieldNames();
		mockWriteFutureReturn();
		String url;
		Fao fao;
		try (MockedConstruction<Fao> faoConstruction = mockRefreshFaoConstruction()) {
			url = d.refreshFile(1, "file");
			fao = faoConstruction.constructed().get(0);
		}
		assertEquals("url", url);
		verify(fao).refresh(true);
		verify(collection).document("1");
		verify(document).update("file", "url");
		assertDoesNotThrow(() -> {
			verify(writeFuture).get();
		});
	}

	@Test
	void doesNotRefreshFileIfFieldNameIsNull() {
		assertThrows(NullPointerException.class, () -> {
			d.refreshFile(1, null);
		});
	}

	@Test
	void doesNotRefreshFileIfFieldDoesNotExist() {
		mockFileFieldNames();
		assertThrows(IllegalArgumentException.class, () -> {
			d.refreshFile(1, "file");
		});
	}

	@Test
	void doesNotRefreshFileIfKeyIsNull() {
		mockFaoFileFieldNames();
		assertThrows(NullPointerException.class, () -> {
			d.refreshFile(null, "file");
		});
	}

	@Test
	void doesNotRefreshFileIfFaoThrows() {
		mockFaoFileFieldNames();
		MockInitializer<Fao> initializer = (mock, context) -> {
			when(mock.refresh(true)).thenThrow(FileException.class);
		};
		try (MockedConstruction<Fao> faoConstruction = mockConstruction(Fao.class, initializer)) {
			assertThrows(FileException.class, () -> {
				d.refreshFile(1, "file");
			});
		}
	}

	@Test
	void doesNotRefreshFileIfWriteFutureThrows() {
		mockFaoFileFieldNames();
		Throwable cause = mockWriteFutureThrow();
		Exception exception;
		try (MockedConstruction<Fao> faoConstruction = mockRefreshFaoConstruction()) {
			exception = assertThrows(DataException.class, () -> {
				d.refreshFile(1, "file");
			});
		}
		assertSame(cause, exception.getCause());
	}

	private MockedConstruction<Fao> mockRefreshFaoConstruction() {
		when(document.update("file", "url")).thenReturn(writeFuture);
		MockInitializer<Fao> initializer = (mock, context) -> {
			List<?> arguments = context.arguments();
			assertEquals(bucket, arguments.get(0));
			assertEquals("collection/1/file", arguments.get(1));
			when(mock.refresh(true)).thenReturn("url");
		};
		return mockConstruction(Fao.class, initializer);
	}

	@Test
	void downloadsFile() {
	}

	@Test
	void doesNotDownloadFileIfFieldNameIsNull() {
	}

	@Test
	void doesNotDownloadFileIfFieldDoesNotExist() {
	}

	@Test
	void doesNotDownloadFileIfKeyIsNull() {
	}

	@Test
	void doesNotDownloadFileIfFaoThrows() {
	}

	@Test
	void removesFile() {
		mockFaoFileFieldNames();
		mockWriteFutureReturn();
		Fao fao;
		try (MockedConstruction<Fao> faoConstruction = mockRemoveFaoConstruction()) {
			d.removeFile(1, "file");
			fao = faoConstruction.constructed().get(0);
		}
		verify(fao).remove();
		verify(collection).document("1");
		verify(document).update("file", null);
		assertDoesNotThrow(() -> {
			verify(writeFuture).get();
		});
	}

	@Test
	void doesNotRemoveFileIfFieldNameIsNull() {
		assertThrows(NullPointerException.class, () -> {
			d.removeFile(1, null);
		});
	}

	@Test
	void doesNotRemoveFileIfFieldDoesNotExist() {
		mockFileFieldNames();
		assertThrows(IllegalArgumentException.class, () -> {
			d.removeFile(1, "file");
		});
	}

	@Test
	void doesNotRemoveFileIfKeyIsNull() {
		mockFaoFileFieldNames();
		assertThrows(NullPointerException.class, () -> {
			d.removeFile(null, "file");
		});
	}

	@Test
	void doesNotRemoveFileIfFaoThrows() {
		mockFaoFileFieldNames();
		MockInitializer<Fao> initializer = (mock, context) -> {
			doThrow(FileException.class).when(mock).remove();
		};
		try (MockedConstruction<Fao> faoConstruction = mockConstruction(Fao.class, initializer)) {
			assertThrows(FileException.class, () -> {
				d.removeFile(1, "file");
			});
		}
	}

	@Test
	void doesNotRemoveFileIfWriteFutureThrows() {
		mockFaoFileFieldNames();
		Throwable cause = mockWriteFutureThrow();
		Exception exception;
		try (MockedConstruction<Fao> faoConstruction = mockRemoveFaoConstruction()) {
			exception = assertThrows(DataException.class, () -> {
				d.removeFile(1, "file");
			});
		}
		assertSame(cause, exception.getCause());
	}

	private MockedConstruction<Fao> mockRemoveFaoConstruction() {
		when(document.update("file", null)).thenReturn(writeFuture);
		MockInitializer<Fao> initializer = (mock, context) -> {
			List<?> arguments = context.arguments();
			assertEquals(bucket, arguments.get(0));
			assertEquals("collection/1/file", arguments.get(1));
		};
		return mockConstruction(Fao.class, initializer);
	}

	private void mockFaoFileFieldNames() {
		when(handle.getContentType("file")).thenReturn("application/octet-stream");
		when(handle.isWeb("file")).thenReturn(true);
		mockFileFieldNames(List.of("file"));
	}

	private void mockFileFieldNames() {
		mockFileFieldNames(List.of());
	}

	private void mockFileFieldNames(List<String> fileFieldNames) {
		when(handle.getFileFieldNames()).thenReturn(new LinkedHashSet<>(fileFieldNames));
	}

	@Test
	void collectionRetrieves() {
	}

	@Test
	void collectionRetrievesWithAutoKey() {
	}

	@Test
	void collectionDoesNotRetrieveIfWriteFutureThrows() {
	}

	@Test
	void collectionUpdates() {
	}

	@Test
	void collectionDoesNotUpdateIfInstanceIsNull() {
	}

	@Test
	void collectionDoesNotUpdateIfWriteFutureThrows() {
	}

	@Test
	void collectionDeletes() {
	}

	@Test
	void collectionDoesNotDeleteIfFaoThrows() {
	}

	@Test
	void collectionDoesNotDeleteIfWriteFutureThrows() {
	}

	@Test
	void selectionRetrieves() {
	}

	@Test
	void selectionRetrievesWithAutoKey() {
	}

	@Test
	void selectionDoesNotRetrieveIfWriteFutureThrows() {
	}

	@Test
	void selectionUpdates() {
	}

	@Test
	void selectionDoesNotUpdateIfLengthsAreDifferent() {
	}

	@Test
	void selectionDoesNotUpdateIfWriteFutureThrows() {
	}

	@Test
	void selectionDeletes() {
	}

	@Test
	void selectionDoesNotDeleteIfWriteFutureThrows() {
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

	@Test
	void doesNotSync() {
		assertDoesNotThrow(() -> {
			when(readFuture.get()).thenThrow(InterruptedException.class);
		});
		assertThrows(AssertionError.class, () -> {
			d.sync(readFuture);
		});
	}
}
