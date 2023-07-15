package br.pro.hashi.sdx.dao;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.io.UncheckedIOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.FirebaseOptions.Builder;

class ClientFactoryTest {
	private AutoCloseable mocks;
	private ClientFactory f;
	private @Mock ServiceAccountCredentials credentials;
	private @Mock FirebaseOptions options;
	private @Mock Builder builder;
	private MockedStatic<ServiceAccountCredentials> credentialsStatic;
	private MockedStatic<FirebaseOptions> optionsStatic;
	private MockedConstruction<DaoClient> construction;

	@BeforeEach
	void setUp() {
		mocks = MockitoAnnotations.openMocks(this);

		f = new ClientFactory();

		when(credentials.getProjectId()).thenReturn("id");

		when(builder.setCredentials(credentials)).thenReturn(builder);
		when(builder.build()).thenReturn(options);

		credentialsStatic = mockStatic(ServiceAccountCredentials.class);
		credentialsStatic.when(() -> ServiceAccountCredentials.fromStream(any(InputStream.class))).thenReturn(credentials);

		optionsStatic = mockStatic(FirebaseOptions.class);
		optionsStatic.when(() -> FirebaseOptions.builder()).thenReturn(builder);

		construction = mockConstruction(DaoClient.class);
	}

	@AfterEach
	void tearDown() {
		construction.close();
		optionsStatic.close();
		credentialsStatic.close();
		assertDoesNotThrow(() -> {
			mocks.close();
		});
	}

	@Test
	void getsInstance() {
		assertInstanceOf(ClientFactory.class, ClientFactory.getInstance());
	}

	@Test
	void getsFirst() {
		String credentialsPath = getCredentialsPath();
		try (MockedStatic<DaoClient> clientStatic = mockClientStatic()) {
			DaoClient client = f.getFromCredentials(credentialsPath);
			assertSame(client, f.getFirst());
		}
	}

	@Test
	void getsFromId() {
		String credentialsPath = getCredentialsPath();
		try (MockedStatic<DaoClient> clientStatic = mockClientStatic()) {
			DaoClient client = f.getFromCredentials(credentialsPath);
			assertSame(client, f.getFromId("id"));
		}
	}

	@Test
	void getsFromCredentials() {
		String credentialsPath = getCredentialsPath();
		try (MockedStatic<DaoClient> clientStatic = mockClientStatic()) {
			DaoClient client = f.getFromCredentials(credentialsPath);
			assertSame(client, f.getFromCredentials(credentialsPath));
		}
	}

	private MockedStatic<DaoClient> mockClientStatic() {
		MockedStatic<DaoClient> clientStatic = mockStatic(DaoClient.class);
		clientStatic.when(() -> DaoClient.newInstance(options, "id")).thenReturn(mock(DaoClient.class));
		return clientStatic;
	}

	private String getCredentialsPath() {
		ClassLoader loader = getClass().getClassLoader();
		return loader.getResource("mock.json").getFile();
	}

	@Test
	void doesNotGetFirstFromEmptyCache() {
		assertThrows(IllegalStateException.class, () -> {
			f.getFirst();
		});
	}

	@Test
	void doesNotGetFromNullProjectId() {
		assertThrows(NullPointerException.class, () -> {
			f.getFromId(null);
		});
	}

	@Test
	void doesNotGetFromBlankProjectId() {
		assertThrows(IllegalArgumentException.class, () -> {
			f.getFromId(" \t\n");
		});
	}

	@Test
	void doesNotGetFromMissingProjectId() {
		assertThrows(IllegalArgumentException.class, () -> {
			f.getFromId("missing");
		});
	}

	@Test
	void doesNotGetFromNullCredentialsPath() {
		assertThrows(NullPointerException.class, () -> {
			f.getFromCredentials(null);
		});
	}

	@Test
	void doesNotGetFromBlankCredentialsPath() {
		assertThrows(IllegalArgumentException.class, () -> {
			f.getFromCredentials(" \t\n");
		});
	}

	@Test
	void doesNotGetFromMissingCredentialsPath() {
		assertThrows(UncheckedIOException.class, () -> {
			f.getFromCredentials("missing.json");
		});
	}
}
