package br.pro.hashi.sdx.dao;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.google.firebase.FirebaseOptions;

import br.pro.hashi.sdx.dao.reflection.HandleFactory;

class DaoClientTest {
	private HandleFactory factory;
	private FirebaseOptions options;
	private DaoClient c;

	@BeforeEach
	void setUp() {
		factory = mock(HandleFactory.class);
		options = mock(FirebaseOptions.class);
		c = new DaoClient(factory, options, "id");
	}

	@Test
	void createsFromId() {
		ClientFactory clientFactory = mock(ClientFactory.class);
		when(clientFactory.getFromId("id")).thenReturn(c);
		try (MockedStatic<ClientFactory> clientStatic = mockClientStatic(clientFactory)) {
			assertSame(c, DaoClient.fromId("id"));
		}
	}

	@Test
	void createsFromCredentials() {
		ClientFactory clientFactory = mock(ClientFactory.class);
		when(clientFactory.getFromCredentials("mock.json")).thenReturn(c);
		try (MockedStatic<ClientFactory> clientStatic = mockClientStatic(clientFactory)) {
			assertSame(c, DaoClient.fromCredentials("mock.json"));
		}
	}

	private MockedStatic<ClientFactory> mockClientStatic(ClientFactory clientFactory) {
		MockedStatic<ClientFactory> clientStatic = mockStatic(ClientFactory.class);
		clientStatic.when(() -> ClientFactory.getInstance()).thenReturn(clientFactory);
		return clientStatic;
	}

	@Test
	void constructsWithDefaultFactory() {
		DaoClient client;
		try (MockedStatic<HandleFactory> factoryStatic = mockStatic(HandleFactory.class)) {
			factoryStatic.when(() -> HandleFactory.getInstance()).thenReturn(factory);
			client = new DaoClient(options, "id");
		}
		assertSame(factory, client.getFactory());
	}
}
