package br.pro.hashi.sdx.dao;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import br.pro.hashi.sdx.dao.mock.User;
import br.pro.hashi.sdx.dao.reflection.Handle;

class DaoTest {
	private DaoClient client;
	private Handle handle;
	private Dao<User> d;

	@BeforeEach
	void setUp() {
		client = mock(DaoClient.class);
		handle = mock(Handle.class);
		d = new Dao<>(client, handle);
	}

	@Test
	void creates() {
		doReturn(d).when(client).get(User.class);
		ClientFactory clientFactory = mock(ClientFactory.class);
		when(clientFactory.get()).thenReturn(client);
		try (MockedStatic<ClientFactory> factoryStatic = mockStatic(ClientFactory.class)) {
			factoryStatic.when(() -> ClientFactory.getInstance()).thenReturn(clientFactory);
			assertSame(d, Dao.of(User.class));
		}
	}
}
