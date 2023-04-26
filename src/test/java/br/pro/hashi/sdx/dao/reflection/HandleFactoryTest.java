package br.pro.hashi.sdx.dao.reflection;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import br.pro.hashi.sdx.dao.reflection.Handle.Construction;

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
		try (MockedStatic<Construction> handleConstruction = mockStatic(Construction.class)) {
			handleConstruction.when(() -> Construction.of(Object.class)).thenReturn(mock(Handle.class));
			handleConstruction.verify(() -> Construction.of(any()), times(0));

			Handle<?> handle = f.get(Object.class);
			handleConstruction.verify(() -> Construction.of(Object.class));

			assertSame(handle, f.get(Object.class));
			handleConstruction.verify(() -> Construction.of(any()), times(1));
		}
	}
}
