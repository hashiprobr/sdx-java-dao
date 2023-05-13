package br.pro.hashi.sdx.dao;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedConstruction.MockInitializer;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import com.google.api.core.ApiFuture;
import com.google.cloud.ReadChannel;
import com.google.cloud.firestore.AggregateQuery;
import com.google.cloud.firestore.AggregateQuerySnapshot;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query.Direction;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteBatch;
import com.google.cloud.firestore.WriteResult;
import com.google.cloud.storage.Bucket;
import com.google.firebase.FirebaseApp;

import br.pro.hashi.sdx.dao.DaoClient.Connection;
import br.pro.hashi.sdx.dao.exception.DataException;
import br.pro.hashi.sdx.dao.exception.FileException;
import br.pro.hashi.sdx.dao.mock.Entity;
import br.pro.hashi.sdx.dao.reflection.Handle;

class DaoTest {
	private AutoCloseable mocks;
	private @Mock ClientFactory clientFactory;
	private @Mock ApiFuture<DocumentSnapshot> readFuture;
	private @Mock ApiFuture<WriteResult> writeFuture;
	private @Mock DocumentReference document;
	private @Mock ApiFuture<QuerySnapshot> batchReadFuture;
	private @Mock CollectionReference collection;
	private @Mock ApiFuture<List<WriteResult>> batchWriteFuture;
	private @Mock WriteBatch batch;
	private @Mock FirebaseApp firebase;
	private @Mock Firestore firestore;
	private @Mock Bucket bucket;
	private @Mock DaoClient client;
	private @Mock Handle<Entity> handle;
	private Dao<Entity> d;
	private @Mock ApiFuture<AggregateQuerySnapshot> countFuture;

	@BeforeEach
	void setUp() {
		mocks = MockitoAnnotations.openMocks(this);

		List<String> autoKeys = new ArrayList<>();
		autoKeys.add("0");
		autoKeys.add("1");

		when(document.getId()).thenAnswer((invocation) -> {
			return autoKeys.remove(0);
		});
		when(document.get()).thenReturn(readFuture);
		when(document.create(any())).thenReturn(writeFuture);
		when(document.update(any())).thenReturn(writeFuture);
		when(document.delete()).thenReturn(writeFuture);

		when(collection.document()).thenReturn(document);
		when(collection.document(any(String.class))).thenReturn(document);
		when(collection.select(any(String[].class))).thenReturn(collection);
		when(collection.get()).thenReturn(batchReadFuture);

		when(batch.create(eq(document), any())).thenReturn(batch);
		when(batch.update(eq(document), any())).thenReturn(batch);
		when(batch.delete(document)).thenReturn(batch);
		when(batch.commit()).thenReturn(batchWriteFuture);

		when(firestore.collection("collection")).thenReturn(collection);
		when(firestore.batch()).thenReturn(batch);

		when(collection.getFirestore()).thenReturn(firestore);

		Connection connection = new Connection(firebase, firestore, bucket);

		when(client.getFirestore()).thenReturn(firestore);
		when(client.getBucket()).thenReturn(bucket);
		when(client.getConnection()).thenReturn(connection);

		when(handle.getCollectionName()).thenReturn("collection");
		when(handle.toInstance(any())).thenAnswer((invocation) -> {
			Map<String, Object> data = invocation.getArgument(0);
			return new Entity((int) data.get("value"));
		});
		when(handle.toValues(any())).thenAnswer((invocation) -> {
			Map<String, Object> data = invocation.getArgument(0);
			return Map.of("value", data.get("value"));
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
		when(handle.toData(any())).thenAnswer((invocation) -> {
			Map<String, Object> values = invocation.getArgument(0);
			return Map.of("value", values.get("value"));
		});
		when(handle.toAliases(any(String[].class))).thenAnswer((invocation) -> {
			return invocation.getArgument(0);
		});

		d = new Dao<>(client, handle);

		when(client.get(Entity.class)).thenReturn(d);
	}

	@AfterEach
	void tearDown() {
		assertDoesNotThrow(() -> {
			mocks.close();
		});
	}

	@Test
	void createsFirst() {
		when(clientFactory.getFirst()).thenReturn(client);
		try (MockedStatic<ClientFactory> factoryStatic = mockFactoryStatic()) {
			assertSame(d, Dao.of(Entity.class));
		}
	}

	@Test
	void createsFromId() {
		when(clientFactory.getFromId("id")).thenReturn(client);
		try (MockedStatic<ClientFactory> factoryStatic = mockFactoryStatic()) {
			assertSame(d, Dao.of(Entity.class, "id"));
		}
	}

	private MockedStatic<ClientFactory> mockFactoryStatic() {
		MockedStatic<ClientFactory> factoryStatic = mockStatic(ClientFactory.class);
		factoryStatic.when(() -> ClientFactory.getInstance()).thenReturn(clientFactory);
		return factoryStatic;
	}

	@Test
	void creates() {
		mockHasAutoKey();
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
		mockHasAutoKey(true);
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
		mockHasAutoKey();
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
		mockHasAutoKey(true);
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
	void doesNotCreateIfInstanceIsNull() {
		assertThrows(NullPointerException.class, () -> {
			d.create((Entity) null);
		});
	}

	@Test
	void doesNotCreateIfFirstKeyIsNull() {
		mockHasAutoKey();
		mockFileFieldNames();
		List<Entity> instances = List.of(newEntity(null, 0), newEntity(true, 1));
		assertThrows(NullPointerException.class, () -> {
			d.create(instances);
		});
	}

	@Test
	void doesNotCreateIfSecondKeyIsNull() {
		mockHasAutoKey();
		mockFileFieldNames();
		List<Entity> instances = List.of(newEntity(false, 0), newEntity(null, 1));
		assertThrows(NullPointerException.class, () -> {
			d.create(instances);
		});
	}

	@Test
	void doesNotCreateIfInstanceKeyIsNull() {
		mockHasAutoKey();
		Entity instance = newEntity(null, 1);
		assertThrows(NullPointerException.class, () -> {
			d.create(instance);
		});
	}

	@Test
	void doesNotCreateWithAutoKeyIfFirstKeyIsNotNull() {
		mockHasAutoKey(true);
		mockFileFieldNames();
		List<Entity> instances = List.of(newEntity(false, 0), newEntity(null, 1));
		assertThrows(IllegalArgumentException.class, () -> {
			d.create(instances);
		});
	}

	@Test
	void doesNotCreateWithAutoKeyIfSecondKeyIsNotNull() {
		mockHasAutoKey(true);
		mockFileFieldNames();
		List<Entity> instances = List.of(newEntity(null, 0), newEntity(true, 1));
		assertThrows(IllegalArgumentException.class, () -> {
			d.create(instances);
		});
	}

	@Test
	void doesNotCreateWithAutoKeyIfInstanceKeyIsNotNull() {
		mockHasAutoKey(true);
		Entity instance = newEntity(true, 1);
		assertThrows(IllegalArgumentException.class, () -> {
			d.create(instance);
		});
	}

	@Test
	void doesNotCreateIfBatchWriteFutureThrows() {
		mockHasAutoKey();
		mockFileFieldNames();
		Throwable cause = mockBatchWriteFutureThrow();
		List<Entity> instances = List.of(newEntity(false, 0), newEntity(true, 1));
		Exception exception = assertThrows(DataException.class, () -> {
			d.create(instances);
		});
		assertSame(cause, exception.getCause());
	}

	@Test
	void doesNotCreateIfWriteFutureThrows() {
		mockHasAutoKey();
		mockFileFieldNames();
		Throwable cause = mockWriteFutureThrow();
		Entity instance = newEntity(true, 1);
		Exception exception = assertThrows(DataException.class, () -> {
			try (MockedConstruction<Fao> faoConstruction = mockConstruction(Fao.class)) {
				d.create(instance);
			}
		});
		assertSame(cause, exception.getCause());
	}

	@Test
	void retrievesNull() {
		mockReadFutureReturn(false);
		assertNull(d.retrieve(true));
	}

	@Test
	void retrieves() {
		mockHasAutoKey();
		mockReadFutureReturn();
		Entity instance = d.retrieve(true);
		assertEquals(1, instance.getValue());
		verify(handle, times(0)).setAutoKey(any(), any());
	}

	@Test
	void retrievesWithAutoKey() {
		mockHasAutoKey(true);
		mockReadFutureReturn();
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
		mockHasAutoKey();
		Throwable cause = mockReadFutureThrow();
		Exception exception = assertThrows(DataException.class, () -> {
			d.retrieve(true);
		});
		assertSame(cause, exception.getCause());
	}

	private void mockReadFutureReturn() {
		mockReadFutureReturn(true);
	}

	private void mockReadFutureReturn(boolean exists) {
		DocumentSnapshot snapshot = mock(DocumentSnapshot.class);
		when(snapshot.exists()).thenReturn(exists);
		if (exists) {
			when(snapshot.getData()).thenReturn(Map.of("value", 1));
		}
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
		verify(batch).commit();
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
		verify(batch).commit();
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
			assertEquals(List.of("collection/true/file0", "collection/true/file1"), arguments.get(1));
		};
		try (MockedConstruction<Fao> faoConstruction = mockConstruction(Fao.class, initializer)) {
			d.delete(true);
			fao = faoConstruction.constructed().get(0);
		}
		verify(fao).remove();
		verify(collection).document("true");
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
		assertThrows(FileException.class, () -> {
			try (MockedConstruction<Fao> faoConstruction = mockConstruction(Fao.class, initializer)) {
				d.delete(true);
			}
		});
	}

	@Test
	void doesNotDeleteIfWriteFutureThrows() {
		mockFileFieldNames();
		Throwable cause = mockWriteFutureThrow();
		Exception exception = assertThrows(DataException.class, () -> {
			try (MockedConstruction<Fao> faoConstruction = mockConstruction(Fao.class)) {
				d.delete(true);
			}
		});
		assertSame(cause, exception.getCause());
	}

	@Test
	void uploadsFile() {
		mockFaoFileFieldNames();
		InputStream stream = InputStream.nullInputStream();
		mockWriteFutureReturn();
		String url;
		try (MockedConstruction<Fao> faoConstruction = mockUploadFaoConstruction(stream)) {
			url = d.uploadFile(true, "file", stream);
		}
		assertEquals("url", url);
		verify(collection).document("true");
		verify(document).update("file", "url");
		assertDoesNotThrow(() -> {
			verify(writeFuture).get();
		});
	}

	@Test
	void doesNotUploadFileIfStreamIsNull() {
		assertThrows(NullPointerException.class, () -> {
			d.uploadFile(true, "file", null);
		});
	}

	@Test
	void doesNotUploadFileIfFieldNameIsNull() {
		InputStream stream = InputStream.nullInputStream();
		assertThrows(NullPointerException.class, () -> {
			d.uploadFile(true, null, stream);
		});
	}

	@Test
	void doesNotUploadFileIfFieldDoesNotExist() {
		mockFileFieldNames();
		InputStream stream = InputStream.nullInputStream();
		assertThrows(IllegalArgumentException.class, () -> {
			d.uploadFile(true, "file", stream);
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
		assertThrows(FileException.class, () -> {
			try (MockedConstruction<Fao> faoConstruction = mockConstruction(Fao.class, initializer)) {
				d.uploadFile(true, "file", stream);
			}
		});
	}

	@Test
	void doesNotUploadFileIfWriteFutureThrows() {
		mockFaoFileFieldNames();
		InputStream stream = InputStream.nullInputStream();
		Throwable cause = mockWriteFutureThrow();
		Exception exception = assertThrows(DataException.class, () -> {
			try (MockedConstruction<Fao> faoConstruction = mockUploadFaoConstruction(stream)) {
				d.uploadFile(true, "file", stream);
			}
		});
		assertSame(cause, exception.getCause());
	}

	private MockedConstruction<Fao> mockUploadFaoConstruction(InputStream stream) {
		when(document.update("file", "url")).thenReturn(writeFuture);
		MockInitializer<Fao> initializer = (mock, context) -> {
			List<?> arguments = context.arguments();
			assertEquals(bucket, arguments.get(0));
			assertEquals("collection/true/file", arguments.get(1));
			when(mock.upload(stream, "application/octet-stream", true)).thenReturn("url");
		};
		return mockConstruction(Fao.class, initializer);
	}

	@Test
	void refreshesFile() {
		mockFaoFileFieldNames();
		mockWriteFutureReturn();
		String url;
		try (MockedConstruction<Fao> faoConstruction = mockRefreshFaoConstruction()) {
			url = d.refreshFile(true, "file");
		}
		assertEquals("url", url);
		verify(collection).document("true");
		verify(document).update("file", "url");
		assertDoesNotThrow(() -> {
			verify(writeFuture).get();
		});
	}

	@Test
	void doesNotRefreshFileIfFieldNameIsNull() {
		assertThrows(NullPointerException.class, () -> {
			d.refreshFile(true, null);
		});
	}

	@Test
	void doesNotRefreshFileIfFieldDoesNotExist() {
		mockFileFieldNames();
		assertThrows(IllegalArgumentException.class, () -> {
			d.refreshFile(true, "file");
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
		assertThrows(FileException.class, () -> {
			try (MockedConstruction<Fao> faoConstruction = mockConstruction(Fao.class, initializer)) {
				d.refreshFile(true, "file");
			}
		});
	}

	@Test
	void doesNotRefreshFileIfWriteFutureThrows() {
		mockFaoFileFieldNames();
		Throwable cause = mockWriteFutureThrow();
		Exception exception = assertThrows(DataException.class, () -> {
			try (MockedConstruction<Fao> faoConstruction = mockRefreshFaoConstruction()) {
				d.refreshFile(true, "file");
			}
		});
		assertSame(cause, exception.getCause());
	}

	private MockedConstruction<Fao> mockRefreshFaoConstruction() {
		when(document.update("file", "url")).thenReturn(writeFuture);
		MockInitializer<Fao> initializer = (mock, context) -> {
			List<?> arguments = context.arguments();
			assertEquals(bucket, arguments.get(0));
			assertEquals("collection/true/file", arguments.get(1));
			when(mock.refresh(true)).thenReturn("url");
		};
		return mockConstruction(Fao.class, initializer);
	}

	@Test
	void downloadsFile() {
		mockFaoFileFieldNames();
		ReadChannel channel = mock(ReadChannel.class);
		mockWriteFutureReturn();
		DaoFile file;
		try (MockedConstruction<Fao> faoConstruction = mockDownloadFaoConstruction(channel)) {
			file = d.downloadFile(true, "file");
		}
		assertSame(channel, file.getChannel());
		assertEquals("application/octet-stream", file.getContentType());
		assertEquals(1, file.getContentLength());
	}

	@Test
	void doesNotDownloadFileIfFieldNameIsNull() {
		assertThrows(NullPointerException.class, () -> {
			d.downloadFile(true, null);
		});
	}

	@Test
	void doesNotDownloadFileIfFieldDoesNotExist() {
		mockFileFieldNames();
		assertThrows(IllegalArgumentException.class, () -> {
			d.downloadFile(true, "file");
		});
	}

	@Test
	void doesNotDownloadFileIfKeyIsNull() {
		mockFaoFileFieldNames();
		assertThrows(NullPointerException.class, () -> {
			d.downloadFile(null, "file");
		});
	}

	@Test
	void doesNotDownloadFileIfFaoThrows() {
		mockFaoFileFieldNames();
		MockInitializer<Fao> initializer = (mock, context) -> {
			when(mock.download()).thenThrow(FileException.class);
		};
		assertThrows(FileException.class, () -> {
			try (MockedConstruction<Fao> faoConstruction = mockConstruction(Fao.class, initializer)) {
				d.downloadFile(true, "file");
			}
		});
	}

	private MockedConstruction<Fao> mockDownloadFaoConstruction(ReadableByteChannel channel) {
		MockInitializer<Fao> initializer = (mock, context) -> {
			List<?> arguments = context.arguments();
			assertEquals(bucket, arguments.get(0));
			assertEquals("collection/true/file", arguments.get(1));
			when(mock.download()).thenReturn(new DaoFile(channel, "application/octet-stream", 1));
		};
		return mockConstruction(Fao.class, initializer);
	}

	@Test
	void removesFile() {
		mockFaoFileFieldNames();
		mockWriteFutureReturn();
		Fao fao;
		try (MockedConstruction<Fao> faoConstruction = mockRemoveFaoConstruction()) {
			d.removeFile(true, "file");
			fao = faoConstruction.constructed().get(0);
		}
		verify(fao).remove();
		verify(collection).document("true");
		verify(document).update("file", null);
		assertDoesNotThrow(() -> {
			verify(writeFuture).get();
		});
	}

	@Test
	void doesNotRemoveFileIfFieldNameIsNull() {
		assertThrows(NullPointerException.class, () -> {
			d.removeFile(true, null);
		});
	}

	@Test
	void doesNotRemoveFileIfFieldDoesNotExist() {
		mockFileFieldNames();
		assertThrows(IllegalArgumentException.class, () -> {
			d.removeFile(true, "file");
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
		assertThrows(FileException.class, () -> {
			try (MockedConstruction<Fao> faoConstruction = mockConstruction(Fao.class, initializer)) {
				d.removeFile(true, "file");
			}
		});
	}

	@Test
	void doesNotRemoveFileIfWriteFutureThrows() {
		mockFaoFileFieldNames();
		Throwable cause = mockWriteFutureThrow();
		Exception exception = assertThrows(DataException.class, () -> {
			try (MockedConstruction<Fao> faoConstruction = mockRemoveFaoConstruction()) {
				d.removeFile(true, "file");
			}
		});
		assertSame(cause, exception.getCause());
	}

	private MockedConstruction<Fao> mockRemoveFaoConstruction() {
		when(document.update("file", null)).thenReturn(writeFuture);
		MockInitializer<Fao> initializer = (mock, context) -> {
			List<?> arguments = context.arguments();
			assertEquals(bucket, arguments.get(0));
			assertEquals("collection/true/file", arguments.get(1));
		};
		return mockConstruction(Fao.class, initializer);
	}

	private void mockFaoFileFieldNames() {
		when(handle.getContentType("file")).thenReturn("application/octet-stream");
		when(handle.isWeb("file")).thenReturn(true);
		mockFileFieldNames(List.of("file"));
	}

	@Test
	void doesNotSelectNullNames() {
		assertThrows(NullPointerException.class, () -> {
			d.select((String[]) null);
		});
	}

	@Test
	void doesNotSelectEmptyNames() {
		assertThrows(IllegalArgumentException.class, () -> {
			d.select(new String[] {});
		});
	}

	@Test
	void collectionRetrieves() {
		Dao<Entity>.Collection c = d.collect();
		mockBatchReadFutureReturn();
		mockHasAutoKey();
		List<Entity> instances = c.retrieve();
		Entity instance0 = instances.get(0);
		Entity instance1 = instances.get(1);
		assertEquals(0, instance0.getValue());
		assertEquals(1, instance1.getValue());
		verify(handle, times(0)).setAutoKey(any(), any());
	}

	@Test
	void collectionRetrievesWithAutoKey() {
		Dao<Entity>.Collection c = d.collect();
		mockBatchReadFutureReturn();
		mockHasAutoKey(true);
		List<Entity> instances = c.retrieve();
		Entity instance0 = instances.get(0);
		Entity instance1 = instances.get(1);
		assertEquals(0, instance0.getValue());
		assertEquals(1, instance1.getValue());
		verify(handle).setAutoKey(instance0, "0");
		verify(handle).setAutoKey(instance1, "1");
	}

	@Test
	void collectionDoesNotRetrieveIfBatchReadFutureThrows() {
		Dao<Entity>.Collection c = d.collect();
		Throwable cause = mockBatchReadFutureThrow();
		Exception exception = assertThrows(DataException.class, () -> {
			c.retrieve();
		});
		assertSame(cause, exception.getCause());
	}

	@Test
	void collectionUpdates() {
		Dao<Entity>.Collection c = d.collect();
		mockBatchReadFutureReturn();
		mockBatchWriteFutureReturn();
		Entity instance = new Entity(1);
		c.update(instance);
		verify(batch, times(2)).update(document, Map.of("value", 1));
		verify(batch).commit();
		assertDoesNotThrow(() -> {
			verify(batchWriteFuture).get();
		});
	}

	@Test
	void collectionDoesNotUpdateIfInstanceIsNull() {
		Dao<Entity>.Collection c = d.collect();
		assertThrows(NullPointerException.class, () -> {
			c.update(null);
		});
	}

	@Test
	void collectionDoesNotUpdateIfBatchReadFutureThrows() {
		Dao<Entity>.Collection c = d.collect();
		Throwable cause = mockBatchReadFutureThrow();
		Entity instance = new Entity(1);
		Exception exception = assertThrows(DataException.class, () -> {
			c.update(instance);
		});
		assertSame(cause, exception.getCause());
	}

	@Test
	void collectionDoesNotUpdateIfBatchWriteFutureThrows() {
		Dao<Entity>.Collection c = d.collect();
		mockBatchReadFutureReturn();
		Throwable cause = mockBatchWriteFutureThrow();
		Entity instance = new Entity(1);
		Exception exception = assertThrows(DataException.class, () -> {
			c.update(instance);
		});
		assertSame(cause, exception.getCause());
	}

	@Test
	void collectionDeletes() {
		Dao<Entity>.Collection c = d.collect();
		mockFileFieldNames();
		mockBatchReadFutureReturn();
		mockBatchWriteFutureReturn();
		c.delete();
		verify(batch, times(2)).delete(document);
		verify(batch).commit();
		assertDoesNotThrow(() -> {
			verify(batchWriteFuture).get();
		});
	}

	@Test
	void collectionDeletesWithFileFieldNames() {
		Dao<Entity>.Collection c = d.collect();
		mockFileFieldNames(List.of("file"));
		mockBatchReadFutureReturn();
		mockWriteFutureReturn();
		Fao fao0;
		Fao fao1;
		try (MockedConstruction<Fao> faoConstruction = mockConstruction(Fao.class)) {
			c.delete();
			fao0 = faoConstruction.constructed().get(0);
			fao1 = faoConstruction.constructed().get(1);
		}
		verify(fao0).remove();
		verify(fao1).remove();
		verify(document, times(2)).delete();
		assertDoesNotThrow(() -> {
			verify(writeFuture, times(2)).get();
		});
	}

	@Test
	void collectionDoesNotDeleteIfBatchReadFutureThrows() {
		Dao<Entity>.Collection c = d.collect();
		mockFileFieldNames();
		Throwable cause = mockBatchReadFutureThrow();
		Exception exception = assertThrows(DataException.class, () -> {
			c.delete();
		});
		assertSame(cause, exception.getCause());
	}

	@Test
	void collectionDoesNotDeleteIfBatchWriteFutureThrows() {
		Dao<Entity>.Collection c = d.collect();
		mockFileFieldNames();
		mockBatchReadFutureReturn();
		Throwable cause = mockBatchWriteFutureThrow();
		Exception exception = assertThrows(DataException.class, () -> {
			c.delete();
		});
		assertSame(cause, exception.getCause());
	}

	@Test
	void collectionDoesNotDeleteWithFileFieldNamesIfBatchReadFutureThrows() {
		Dao<Entity>.Collection c = d.collect();
		mockFileFieldNames(List.of("file"));
		Throwable cause = mockBatchReadFutureThrow();
		Exception exception = assertThrows(DataException.class, () -> {
			c.delete();
		});
		assertSame(cause, exception.getCause());
	}

	@Test
	void collectionDoesNotDeleteWithFileFieldNamesIfFaoThrows() {
		Dao<Entity>.Collection c = d.collect();
		mockFileFieldNames(List.of("file"));
		mockBatchReadFutureReturn();
		MockInitializer<Fao> initializer = (mock, context) -> {
			doThrow(FileException.class).when(mock).remove();
		};
		assertThrows(FileException.class, () -> {
			try (MockedConstruction<Fao> faoConstruction = mockConstruction(Fao.class, initializer)) {
				c.delete();
			}
		});
	}

	@Test
	void collectionDoesNotDeleteWithFileFieldNamesIfWriteFutureThrows() {
		Dao<Entity>.Collection c = d.collect();
		mockFileFieldNames(List.of("file"));
		mockBatchReadFutureReturn();
		Throwable cause = mockWriteFutureThrow();
		Exception exception = assertThrows(DataException.class, () -> {
			try (MockedConstruction<Fao> faoConstruction = mockConstruction(Fao.class)) {
				c.delete();
			}
		});
		assertSame(cause, exception.getCause());
	}

	private void mockFileFieldNames() {
		mockFileFieldNames(List.of());
	}

	private void mockFileFieldNames(List<String> fileFieldNames) {
		when(handle.getFileFieldNames()).thenReturn(new LinkedHashSet<>(fileFieldNames));
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
	void selectionRetrieves() {
		mockHasKey();
		Dao<Entity>.Selection s = d.select("value");
		mockBatchReadFutureReturn();
		mockHasAutoKey();
		List<Map<String, Object>> list = s.retrieve();
		Map<String, Object> values0 = list.get(0);
		Map<String, Object> values1 = list.get(1);
		assertEquals(0, values0.get("value"));
		assertEquals(1, values1.get("value"));
		verify(handle, times(0)).putAutoKey(any(), any());
	}

	@Test
	void selectionRetrievesWithKey() {
		mockHasKey(true);
		Dao<Entity>.Selection s = d.select("value");
		mockBatchReadFutureReturn();
		mockHasAutoKey();
		List<Map<String, Object>> list = s.retrieve();
		Map<String, Object> values0 = list.get(0);
		Map<String, Object> values1 = list.get(1);
		assertEquals(0, values0.get("value"));
		assertEquals(1, values1.get("value"));
		verify(handle, times(0)).putAutoKey(any(), any());
	}

	@Test
	void selectionRetrievesWithAutoKey() {
		mockHasKey();
		Dao<Entity>.Selection s = d.select("value");
		mockBatchReadFutureReturn();
		mockHasAutoKey(true);
		List<Map<String, Object>> list = s.retrieve();
		Map<String, Object> values0 = list.get(0);
		Map<String, Object> values1 = list.get(1);
		assertEquals(0, values0.get("value"));
		assertEquals(1, values1.get("value"));
		verify(handle, times(0)).putAutoKey(any(), any());
	}

	@Test
	void selectionRetrievesWithKeyAndAutoKey() {
		mockHasKey(true);
		Dao<Entity>.Selection s = d.select("value");
		mockBatchReadFutureReturn();
		mockHasAutoKey(true);
		List<Map<String, Object>> list = s.retrieve();
		Map<String, Object> values0 = list.get(0);
		Map<String, Object> values1 = list.get(1);
		assertEquals(0, values0.get("value"));
		assertEquals(1, values1.get("value"));
		verify(handle).putAutoKey(values0, "0");
		verify(handle).putAutoKey(values1, "1");
	}

	@Test
	void selectionDoesNotRetrieveIfBatchReadFutureThrows() {
		Dao<Entity>.Selection s = d.select("value");
		Throwable cause = mockBatchReadFutureThrow();
		Exception exception = assertThrows(DataException.class, () -> {
			s.retrieve();
		});
		assertSame(cause, exception.getCause());
	}

	@Test
	void selectionUpdates() {
		Dao<Entity>.Selection s = d.select("value");
		mockBatchReadFutureReturn();
		mockBatchWriteFutureReturn();
		s.update(1);
		verify(batch, times(2)).update(document, Map.of("value", 1));
		verify(batch).commit();
		assertDoesNotThrow(() -> {
			verify(batchWriteFuture).get();
		});
	}

	@Test
	void selectionDoesNotUpdateIfLengthsAreDifferent() {
		Dao<Entity>.Selection s = d.select("value");
		assertThrows(IllegalArgumentException.class, () -> {
			s.update();
		});
	}

	@Test
	void selectionDoesNotUpdateIfBatchReadFutureThrows() {
		Dao<Entity>.Selection s = d.select("value");
		Throwable cause = mockBatchReadFutureThrow();
		Exception exception = assertThrows(DataException.class, () -> {
			s.update(1);
		});
		assertSame(cause, exception.getCause());
	}

	@Test
	void selectionDoesNotUpdateIfBatchWriteFutureThrows() {
		Dao<Entity>.Selection s = d.select("value");
		mockBatchReadFutureReturn();
		Throwable cause = mockBatchWriteFutureThrow();
		Exception exception = assertThrows(DataException.class, () -> {
			s.update(1);
		});
		assertSame(cause, exception.getCause());
	}

	@Test
	void selectionDeletes() {
		Dao<Entity>.Selection s = d.select("value");
		mockBatchReadFutureReturn();
		mockBatchWriteFutureReturn();
		s.delete();
		verify(batch, times(2)).update(document, Map.of("value", FieldValue.delete()));
		verify(batch).commit();
		assertDoesNotThrow(() -> {
			verify(batchWriteFuture).get();
		});
	}

	@Test
	void selectionDoesNotDeleteIfBatchReadFutureThrows() {
		Dao<Entity>.Selection s = d.select("value");
		Throwable cause = mockBatchReadFutureThrow();
		Exception exception = assertThrows(DataException.class, () -> {
			s.delete();
		});
		assertSame(cause, exception.getCause());
	}

	@Test
	void selectionDoesNotDeleteIfBatchWriteFutureThrows() {
		Dao<Entity>.Selection s = d.select("value");
		mockBatchReadFutureReturn();
		Throwable cause = mockBatchWriteFutureThrow();
		Exception exception = assertThrows(DataException.class, () -> {
			s.delete();
		});
		assertSame(cause, exception.getCause());
	}

	private void mockHasAutoKey() {
		mockHasAutoKey(false);
	}

	private void mockHasAutoKey(boolean hasAutoKey) {
		when(handle.hasAutoKey()).thenReturn(hasAutoKey);
	}

	private void mockHasKey() {
		mockHasKey(false);
	}

	private void mockHasKey(boolean hasKey) {
		when(handle.hasKey(any(String[].class))).thenReturn(hasKey);
	}

	private void mockBatchReadFutureReturn() {
		QueryDocumentSnapshot snapshot0 = mock(QueryDocumentSnapshot.class);
		when(snapshot0.getId()).thenReturn("0");
		when(snapshot0.getData()).thenReturn(Map.of("value", 0));
		when(snapshot0.getReference()).thenReturn(document);
		QueryDocumentSnapshot snapshot1 = mock(QueryDocumentSnapshot.class);
		when(snapshot1.getId()).thenReturn("1");
		when(snapshot1.getData()).thenReturn(Map.of("value", 1));
		when(snapshot1.getReference()).thenReturn(document);
		List<QueryDocumentSnapshot> iterable = List.of(snapshot0, snapshot1);
		QuerySnapshot snapshots = mock(QuerySnapshot.class);
		when(snapshots.iterator()).thenReturn(iterable.iterator());
		assertDoesNotThrow(() -> {
			when(batchReadFuture.get()).thenReturn(snapshots);
		});
	}

	private Throwable mockBatchReadFutureThrow() {
		Throwable cause = new Throwable();
		ExecutionException exception = new ExecutionException(cause);
		assertDoesNotThrow(() -> {
			when(batchReadFuture.get()).thenThrow(exception);
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

	@Test
	void filtersWhereEqualTo() {
		mockAlias();
		when(collection.whereEqualTo("alias", 1)).thenReturn(collection);
		Dao<Entity>.Collection c = d.collect();
		assertSame(c, c.whereEqualTo("name", 1));
		Dao<Entity>.Selection s = d.select("value");
		assertSame(s, s.whereEqualTo("name", 1));
		verify(collection, times(2)).whereEqualTo("alias", 1);
	}

	@Test
	void filtersWhereNotEqualTo() {
		mockAlias();
		when(collection.whereNotEqualTo("alias", 1)).thenReturn(collection);
		Dao<Entity>.Collection c = d.collect();
		assertSame(c, c.whereNotEqualTo("name", 1));
		Dao<Entity>.Selection s = d.select("value");
		assertSame(s, s.whereNotEqualTo("name", 1));
		verify(collection, times(2)).whereNotEqualTo("alias", 1);
	}

	@Test
	void filtersWhereLessThan() {
		mockAlias();
		when(collection.whereLessThan("alias", 1)).thenReturn(collection);
		Dao<Entity>.Collection c = d.collect();
		assertSame(c, c.whereLessThan("name", 1));
		Dao<Entity>.Selection s = d.select("value");
		assertSame(s, s.whereLessThan("name", 1));
		verify(collection, times(2)).whereLessThan("alias", 1);
	}

	@Test
	void filtersWhereLessThanOrEqualTo() {
		mockAlias();
		when(collection.whereLessThanOrEqualTo("alias", 1)).thenReturn(collection);
		Dao<Entity>.Collection c = d.collect();
		assertSame(c, c.whereLessThanOrEqualTo("name", 1));
		Dao<Entity>.Selection s = d.select("value");
		assertSame(s, s.whereLessThanOrEqualTo("name", 1));
		verify(collection, times(2)).whereLessThanOrEqualTo("alias", 1);
	}

	@Test
	void filtersWhereGreaterThan() {
		mockAlias();
		when(collection.whereGreaterThan("alias", 1)).thenReturn(collection);
		Dao<Entity>.Collection c = d.collect();
		assertSame(c, c.whereGreaterThan("name", 1));
		Dao<Entity>.Selection s = d.select("value");
		assertSame(s, s.whereGreaterThan("name", 1));
		verify(collection, times(2)).whereGreaterThan("alias", 1);
	}

	@Test
	void filtersWhereGreaterThanOrEqualTo() {
		mockAlias();
		when(collection.whereGreaterThanOrEqualTo("alias", 1)).thenReturn(collection);
		Dao<Entity>.Collection c = d.collect();
		assertSame(c, c.whereGreaterThanOrEqualTo("name", 1));
		Dao<Entity>.Selection s = d.select("value");
		assertSame(s, s.whereGreaterThanOrEqualTo("name", 1));
		verify(collection, times(2)).whereGreaterThanOrEqualTo("alias", 1);
	}

	@Test
	void filtersWhereArrayContains() {
		mockAlias();
		when(collection.whereArrayContains("alias", 1)).thenReturn(collection);
		Dao<Entity>.Collection c = d.collect();
		assertSame(c, c.whereArrayContains("name", 1));
		Dao<Entity>.Selection s = d.select("value");
		assertSame(s, s.whereArrayContains("name", 1));
		verify(collection, times(2)).whereArrayContains("alias", 1);
	}

	@Test
	void filtersWhereArrayContainsAny() {
		mockAlias();
		when(collection.whereArrayContainsAny("alias", List.of(1))).thenReturn(collection);
		Dao<Entity>.Collection c = d.collect();
		assertSame(c, c.whereArrayContainsAny("name", List.of(1)));
		Dao<Entity>.Selection s = d.select("value");
		assertSame(s, s.whereArrayContainsAny("name", List.of(1)));
		verify(collection, times(2)).whereArrayContainsAny("alias", List.of(1));
	}

	@Test
	void filtersWhereIn() {
		mockAlias();
		when(collection.whereIn("alias", List.of(1))).thenReturn(collection);
		Dao<Entity>.Collection c = d.collect();
		assertSame(c, c.whereIn("name", List.of(1)));
		Dao<Entity>.Selection s = d.select("value");
		assertSame(s, s.whereIn("name", List.of(1)));
		verify(collection, times(2)).whereIn("alias", List.of(1));
	}

	@Test
	void filtersWhereNotIn() {
		mockAlias();
		when(collection.whereNotIn("alias", List.of(1))).thenReturn(collection);
		Dao<Entity>.Collection c = d.collect();
		assertSame(c, c.whereNotIn("name", List.of(1)));
		Dao<Entity>.Selection s = d.select("value");
		assertSame(s, s.whereNotIn("name", List.of(1)));
		verify(collection, times(2)).whereNotIn("alias", List.of(1));
	}

	@Test
	void ordersByAscending() {
		mockAlias();
		when(collection.orderBy("alias", Direction.ASCENDING)).thenReturn(collection);
		Dao<Entity>.Collection c = d.collect();
		assertSame(c, c.orderByAscending("name"));
		Dao<Entity>.Selection s = d.select("value");
		assertSame(s, s.orderByAscending("name"));
		verify(collection, times(2)).orderBy("alias", Direction.ASCENDING);
	}

	@Test
	void ordersByDescending() {
		mockAlias();
		when(collection.orderBy("alias", Direction.DESCENDING)).thenReturn(collection);
		Dao<Entity>.Collection c = d.collect();
		assertSame(c, c.orderByDescending("name"));
		Dao<Entity>.Selection s = d.select("value");
		assertSame(s, s.orderByDescending("name"));
		verify(collection, times(2)).orderBy("alias", Direction.DESCENDING);
	}

	private void mockAlias() {
		when(handle.toAlias("name")).thenReturn("alias");
	}

	@Test
	void offsets() {
		when(collection.offset(1)).thenReturn(collection);
		Dao<Entity>.Collection c = d.collect();
		assertSame(c, c.offset(1));
		Dao<Entity>.Selection s = d.select("value");
		assertSame(s, s.offset(1));
		verify(collection, times(2)).offset(1);
	}

	@Test
	void limits() {
		when(collection.limit(1)).thenReturn(collection);
		Dao<Entity>.Collection c = d.collect();
		assertSame(c, c.limit(1));
		Dao<Entity>.Selection s = d.select("value");
		assertSame(s, s.limit(1));
		verify(collection, times(2)).limit(1);
	}

	@Test
	void limitsToLast() {
		when(collection.limitToLast(1)).thenReturn(collection);
		Dao<Entity>.Collection c = d.collect();
		assertSame(c, c.limitToLast(1));
		Dao<Entity>.Selection s = d.select("value");
		assertSame(s, s.limitToLast(1));
		verify(collection, times(2)).limitToLast(1);
	}

	@Test
	void startsAt() {
		when(collection.startAt(1)).thenReturn(collection);
		Dao<Entity>.Collection c = d.collect();
		assertSame(c, c.startAt(1));
		Dao<Entity>.Selection s = d.select("value");
		assertSame(s, s.startAt(1));
		verify(collection, times(2)).startAt(1);
	}

	@Test
	void startsAfter() {
		when(collection.startAfter(1)).thenReturn(collection);
		Dao<Entity>.Collection c = d.collect();
		assertSame(c, c.startAfter(1));
		Dao<Entity>.Selection s = d.select("value");
		assertSame(s, s.startAfter(1));
		verify(collection, times(2)).startAfter(1);
	}

	@Test
	void endsBefore() {
		when(collection.endBefore(1)).thenReturn(collection);
		Dao<Entity>.Collection c = d.collect();
		assertSame(c, c.endBefore(1));
		Dao<Entity>.Selection s = d.select("value");
		assertSame(s, s.endBefore(1));
		verify(collection, times(2)).endBefore(1);
	}

	@Test
	void endsAt() {
		when(collection.endAt(1)).thenReturn(collection);
		Dao<Entity>.Collection c = d.collect();
		assertSame(c, c.endAt(1));
		Dao<Entity>.Selection s = d.select("value");
		assertSame(s, s.endAt(1));
		verify(collection, times(2)).endAt(1);
	}

	@Test
	void counts() {
		mockAggregates();
		AggregateQuerySnapshot aggregate = mock(AggregateQuerySnapshot.class);
		when(aggregate.getCount()).thenReturn(1L);
		assertDoesNotThrow(() -> {
			when(countFuture.get()).thenReturn(aggregate);
		});
		assertEquals(1, d.collect().count());
		assertEquals(1, d.select("value").count());
	}

	@Test
	void doesNotCountIfAggregatesThrows() {
		mockAggregates();
		Throwable cause = new Throwable();
		ExecutionException executionException = new ExecutionException(cause);
		assertDoesNotThrow(() -> {
			when(countFuture.get()).thenThrow(executionException);
		});
		Exception exception;
		exception = assertThrows(DataException.class, () -> {
			d.collect().count();
		});
		assertSame(cause, exception.getCause());
		exception = assertThrows(DataException.class, () -> {
			d.select("value").count();
		});
		assertSame(cause, exception.getCause());
	}

	private void mockAggregates() {
		AggregateQuery aggregates = mock(AggregateQuery.class);
		when(aggregates.get()).thenReturn(countFuture);
		when(collection.count()).thenReturn(aggregates);
	}

	@Test
	void doesNotSyncInterruptedFuture() {
		InterruptedException cause = new InterruptedException();
		assertDoesNotThrow(() -> {
			when(readFuture.get()).thenThrow(cause);
		});
		Exception exception = assertThrows(DataException.class, () -> {
			d.sync(readFuture);
		});
		assertSame(cause, exception.getCause());
	}
}
