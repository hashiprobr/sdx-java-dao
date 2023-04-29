package br.pro.hashi.sdx.dao;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.storage.Bucket;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.cloud.StorageClient;

import br.pro.hashi.sdx.dao.Dao.Construction;
import br.pro.hashi.sdx.dao.reflection.Handle;
import br.pro.hashi.sdx.dao.reflection.HandleFactory;

class DaoClientTest {
	private HandleFactory handleFactory;
	private FirebaseOptions options;
	private DaoClient c;
	private FirebaseApp firebase;
	private Firestore firestore;
	private Bucket bucket;
	private StorageClient storage;
	private MockedStatic<FirebaseApp> firebaseStatic;
	private MockedStatic<FirestoreClient> firestoreStatic;
	private MockedStatic<StorageClient> storageStatic;

	@BeforeEach
	void setUp() {
		handleFactory = mock(HandleFactory.class);
		doReturn(mock(Handle.class)).when(handleFactory).get(Object.class);
		options = mock(FirebaseOptions.class);
		c = new DaoClient(handleFactory, options, "id");
		firebase = mock(FirebaseApp.class);
		firestore = mock(Firestore.class);
		bucket = mock(Bucket.class);
		storage = mock(StorageClient.class);
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
	}

	@Test
	void createsFromId() {
		ClientFactory clientFactory = mock(ClientFactory.class);
		when(clientFactory.getFromId("id")).thenReturn(c);
		try (MockedStatic<ClientFactory> clientFactoryStatic = mockStatic(ClientFactory.class)) {
			clientFactoryStatic.when(() -> ClientFactory.getInstance()).thenReturn(clientFactory);
			assertSame(c, DaoClient.fromId("id"));
		}
	}

	@Test
	void createsFromCredentials() {
		ClientFactory clientFactory = mock(ClientFactory.class);
		when(clientFactory.getFromCredentials("mock.json")).thenReturn(c);
		try (MockedStatic<ClientFactory> clientFactoryStatic = mockStatic(ClientFactory.class)) {
			clientFactoryStatic.when(() -> ClientFactory.getInstance()).thenReturn(clientFactory);
			assertSame(c, DaoClient.fromCredentials("mock.json"));
		}
	}

	@Test
	void constructsWithDefaultHandleFactory() {
		DaoClient client;
		try (MockedStatic<HandleFactory> handleFactoryStatic = mockStatic(HandleFactory.class)) {
			handleFactoryStatic.when(() -> HandleFactory.getInstance()).thenReturn(handleFactory);
			client = new DaoClient(options, "id");
		}
		assertSame(handleFactory, client.getHandleFactory());
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
		try (MockedStatic<Construction> daoConstruction = mockStatic(Construction.class)) {
			daoConstruction.when(() -> Construction.of(eq(c), any())).thenReturn(mock(Dao.class));

			daoConstruction.verify(() -> Construction.of(any(), any()), times(0));

			Dao<Object> dao = c.get(Object.class);
			daoConstruction.verify(() -> Construction.of(eq(c), any()));

			assertSame(dao, c.get(Object.class));
			daoConstruction.verify(() -> Construction.of(any(), any()), times(1));
		}
	}
}
