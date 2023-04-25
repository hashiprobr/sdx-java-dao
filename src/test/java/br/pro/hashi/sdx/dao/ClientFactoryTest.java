package br.pro.hashi.sdx.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.FirebaseOptions.Builder;

class ClientFactoryTest {
	private ClientFactory f;
	private ServiceAccountCredentials credentials;
	private FirebaseOptions options;
	private Builder builder;
	private MockedStatic<ServiceAccountCredentials> credentialsStatic;
	private MockedStatic<FirebaseOptions> optionsStatic;
	private MockedConstruction<DaoClient> factoryConstruction;

	@BeforeEach
	void setUp() {
		f = new ClientFactory();
		credentials = mock(ServiceAccountCredentials.class);
		when(credentials.getProjectId()).thenReturn("id");
		options = mock(FirebaseOptions.class);
		builder = mock(Builder.class);
		when(builder.setCredentials(credentials)).thenReturn(builder);
		when(builder.build()).thenReturn(options);
		credentialsStatic = mockStatic(ServiceAccountCredentials.class);
		credentialsStatic.when(() -> ServiceAccountCredentials.fromStream(any(InputStream.class))).thenReturn(credentials);
		optionsStatic = mockStatic(FirebaseOptions.class);
		optionsStatic.when(() -> FirebaseOptions.builder()).thenReturn(builder);
		factoryConstruction = mockConstruction(DaoClient.class);
	}

	@AfterEach
	void tearDown() {
		factoryConstruction.close();
		optionsStatic.close();
		credentialsStatic.close();
	}

	@Test
	void getsInstance() {
		assertInstanceOf(ClientFactory.class, ClientFactory.getInstance());
	}

	@Test
	void gets() {
		String credentialsPath = mockCredentials();

		List<DaoClient> constructed = factoryConstruction.constructed();
		assertTrue(constructed.isEmpty());

		DaoClient factory = f.getFromCredentials(credentialsPath);
		assertEquals(1, constructed.size());
		assertEquals(factory, constructed.get(0));

		assertSame(factory, f.get());
		assertEquals(1, constructed.size());
	}

	@Test
	void getsFromId() {
		String credentialsPath = mockCredentials();

		List<DaoClient> constructed = factoryConstruction.constructed();
		assertTrue(constructed.isEmpty());

		DaoClient factory = f.getFromCredentials(credentialsPath);
		assertEquals(1, constructed.size());
		assertEquals(factory, constructed.get(0));

		assertSame(factory, f.getFromId("id"));
		assertEquals(1, constructed.size());
	}

	@Test
	void getsFromCredentials() {
		String credentialsPath = mockCredentials();

		List<DaoClient> constructed = factoryConstruction.constructed();
		assertTrue(constructed.isEmpty());

		DaoClient factory = f.getFromCredentials(credentialsPath);
		assertEquals(1, constructed.size());
		assertEquals(factory, constructed.get(0));

		assertSame(factory, f.getFromCredentials(credentialsPath));
		assertEquals(1, constructed.size());
	}

	private String mockCredentials() {
		ClassLoader loader = getClass().getClassLoader();
		File file = new File(loader.getResource("mock.json").getFile());
		return file.getAbsolutePath();
	}

	@Test
	void doesNotGet() {
		assertThrows(IllegalStateException.class, () -> {
			f.get();
		});
	}

	@Test
	void doesNotGetFromNullId() {
		assertThrows(NullPointerException.class, () -> {
			f.getFromId(null);
		});
	}

	@Test
	void doesNotGetFromBlankId() {
		assertThrows(IllegalArgumentException.class, () -> {
			f.getFromId(" \t\n");
		});
	}

	@Test
	void doesNotGetFromMissingId() {
		assertThrows(IllegalArgumentException.class, () -> {
			f.getFromId("missing");
		});
	}

	@Test
	void doesNotGetFromNullCredentials() {
		assertThrows(NullPointerException.class, () -> {
			f.getFromCredentials(null);
		});
	}

	@Test
	void doesNotGetFromBlankCredentials() {
		assertThrows(IllegalArgumentException.class, () -> {
			f.getFromCredentials(" \t\n");
		});
	}

	@Test
	void doesNotGetFromMissingCredentials() {
		assertThrows(UncheckedIOException.class, () -> {
			f.getFromCredentials("missing.json");
		});
	}
}
