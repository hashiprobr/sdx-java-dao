package br.pro.hashi.sdx.dao.reflection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockConstruction;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

class HandleFactoryTest {
	private HandleFactory f;

	@BeforeEach
	void setUp() {
		f = new HandleFactory();
	}

	@Test
	void getsInstance() {
		assertInstanceOf(HandleFactory.class, HandleFactory.getInstance());
	}

	@Test
	void gets() {
		try (MockedConstruction<Handle> handleConstruction = mockConstruction(Handle.class)) {
			List<Handle> constructed = handleConstruction.constructed();
			assertTrue(constructed.isEmpty());

			Handle handle = f.get(Object.class);
			assertEquals(1, constructed.size());
			assertEquals(handle, constructed.get(0));

			assertSame(handle, f.get(Object.class));
			assertEquals(1, constructed.size());
		}
	}
}
