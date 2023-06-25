package br.pro.hashi.sdx.dao;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.storage.Bucket;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.cloud.StorageClient;

import br.pro.hashi.sdx.dao.reflection.Handle;
import br.pro.hashi.sdx.dao.reflection.HandleFactory;
import br.pro.hashi.sdx.dao.reflection.exception.AnnotationException;

class DaoClientTest {
	private AutoCloseable mocks;
	private @Mock ClientFactory clientFactory;
	private @Mock Handle<Object> handle;
	private @Mock HandleFactory handleFactory;
	private @Mock FirebaseOptions options;
	private DaoClient c;
	private @Mock FirebaseApp firebase;
	private @Mock Firestore firestore;
	private @Mock Bucket bucket;
	private @Mock StorageClient storage;
	private MockedStatic<FirebaseApp> firebaseStatic;
	private MockedStatic<FirestoreClient> firestoreStatic;
	private MockedStatic<StorageClient> storageStatic;

	@BeforeEach
	void setUp() {
		mocks = MockitoAnnotations.openMocks(this);

		when(handleFactory.get(Object.class)).thenReturn(handle);

		c = new DaoClient(handleFactory, options, "id");

		when(storage.bucket("id.appspot.com")).thenReturn(bucket);

		firebaseStatic = mockStatic(FirebaseApp.class);
		firebaseStatic.when(() -> FirebaseApp.initializeApp(options, "id")).thenReturn(firebase);

		firestoreStatic = mockStatic(FirestoreClient.class);
		firestoreStatic.when(() -> FirestoreClient.getFirestore(firebase)).thenReturn(firestore);

		storageStatic = mockStatic(StorageClient.class);
		storageStatic.when(() -> StorageClient.getInstance(firebase)).thenReturn(storage);
	}

	@AfterEach
	void tearDown() {
		storageStatic.close();
		firestoreStatic.close();
		firebaseStatic.close();
		assertDoesNotThrow(() -> {
			mocks.close();
		});
	}

	@Test
	void getsFromId() {
		when(clientFactory.getFromId("id")).thenReturn(c);
		try (MockedStatic<ClientFactory> factoryStatic = mockFactoryStatic()) {
			assertSame(c, DaoClient.fromId("id"));
		}
	}

	@Test
	void getsFromCredentials() {
		when(clientFactory.getFromCredentials("mock.json")).thenReturn(c);
		try (MockedStatic<ClientFactory> factoryStatic = mockFactoryStatic()) {
			assertSame(c, DaoClient.fromCredentials("mock.json"));
		}
	}

	private MockedStatic<ClientFactory> mockFactoryStatic() {
		MockedStatic<ClientFactory> factoryStatic = mockStatic(ClientFactory.class);
		factoryStatic.when(() -> ClientFactory.getInstance()).thenReturn(clientFactory);
		return factoryStatic;
	}

	@Test
	void getsFirestore() {
		c.connect();
		assertSame(firestore, c.getFirestore());
	}

	@Test
	void getsBucket() {
		c.connect();
		assertSame(bucket, c.getBucket());
	}

	@Test
	void doesNotGetConnection() {
		assertThrows(IllegalStateException.class, () -> {
			c.getConnection();
		});
	}

	@Test
	void connects() {
		firebaseStatic.verify(() -> FirebaseApp.initializeApp(any(), any()), times(0));
		firestoreStatic.verify(() -> FirestoreClient.getFirestore(any()), times(0));
		storageStatic.verify(() -> StorageClient.getInstance(any()), times(0));
		c.connect();
		firebaseStatic.verify(() -> FirebaseApp.initializeApp(options, "id"));
		firestoreStatic.verify(() -> FirestoreClient.getFirestore(firebase));
		storageStatic.verify(() -> StorageClient.getInstance(firebase));
		c.connect();
		firebaseStatic.verify(() -> FirebaseApp.initializeApp(any(), any()), times(1));
		firestoreStatic.verify(() -> FirestoreClient.getFirestore(any()), times(1));
		storageStatic.verify(() -> StorageClient.getInstance(any()), times(1));
	}

	@Test
	void disconnects() {
		c.connect();
		verify(firebase, times(0)).delete();
		c.disconnect();
		verify(firebase).delete();
		c.disconnect();
		verify(firebase, times(1)).delete();
	}

	@Test
	void gets() {
		when(handle.getKeyFieldName()).thenReturn("key");
		@SuppressWarnings("rawtypes")
		MockedConstruction<Dao> construction = mockConstruction(Dao.class);
		Dao<Object> handle = c.get(Object.class);
		construction.close();
		assertSame(handle, c.get(Object.class));
	}

	@Test
	void doesNotGetWithoutType() {
		when(handle.getKeyFieldName()).thenReturn("key");
		assertThrows(NullPointerException.class, () -> {
			c.get(null);
		});
	}

	@Test
	void doesNotGetWithoutKey() {
		when(handle.getKeyFieldName()).thenReturn(null);
		assertThrows(AnnotationException.class, () -> {
			c.get(Object.class);
		});
	}
}
