package br.pro.hashi.sdx.dao;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import br.pro.hashi.sdx.dao.Dao.Construction;
import br.pro.hashi.sdx.dao.reflection.Handle;

class DaoTest {
	private DaoClient client;
	private Handle<?> handle;
	private Dao<?> d;

	@BeforeEach
	void setUp() {
		client = mock(DaoClient.class);
		handle = mock(Handle.class);
		d = Construction.of(client, handle);
	}

	@Test
	void creates() {
		doReturn(d).when(client).get(Object.class);
		ClientFactory clientFactory = mock(ClientFactory.class);
		when(clientFactory.get()).thenReturn(client);
		try (MockedStatic<ClientFactory> clientFactoryStatic = mockStatic(ClientFactory.class)) {
			clientFactoryStatic.when(() -> ClientFactory.getInstance()).thenReturn(clientFactory);
			assertSame(d, Dao.of(Object.class));
		}
	}

	@Test
	void createsFromId() {
		doReturn(d).when(client).get(Object.class);
		ClientFactory clientFactory = mock(ClientFactory.class);
		when(clientFactory.getFromId("id")).thenReturn(client);
		try (MockedStatic<ClientFactory> clientFactoryStatic = mockStatic(ClientFactory.class)) {
			clientFactoryStatic.when(() -> ClientFactory.getInstance()).thenReturn(clientFactory);
			assertSame(d, Dao.of(Object.class, "id"));
		}
	}
}
