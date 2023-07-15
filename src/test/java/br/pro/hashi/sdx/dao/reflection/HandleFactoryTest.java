package br.pro.hashi.sdx.dao.reflection;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

class HandleFactoryTest {
	private AutoCloseable mocks;
	private HandleFactory f;

	@BeforeEach
	void setUp() {
		mocks = MockitoAnnotations.openMocks(this);

		f = new HandleFactory();
	}

	@AfterEach
	void tearDown() {
		assertDoesNotThrow(() -> {
			mocks.close();
		});
	}

	@Test
	void getsInstance() {
		assertInstanceOf(HandleFactory.class, HandleFactory.getInstance());
	}

	@Test
	void gets() {
		@SuppressWarnings("rawtypes")
		MockedStatic<Handle> handleStatic = mockStatic(Handle.class);
		handleStatic.when(() -> Handle.newInstance(Object.class)).thenReturn(mock(Handle.class));
		Handle<Object> handle = f.get(Object.class);
		assertSame(handle, f.get(Object.class));
		handleStatic.close();
	}
}
