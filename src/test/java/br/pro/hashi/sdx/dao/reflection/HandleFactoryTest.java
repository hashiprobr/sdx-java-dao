package br.pro.hashi.sdx.dao.reflection;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mockConstruction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockitoAnnotations;

class HandleFactoryTest {
	private AutoCloseable mocks;
	private @Mock Reflector reflector;
	private @Mock ParserFactory parserFactory;
	private @Mock ConverterFactory converterFactory;
	private HandleFactory f;

	@BeforeEach
	void setUp() {
		mocks = MockitoAnnotations.openMocks(this);

		f = new HandleFactory(reflector, parserFactory, converterFactory);
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
		MockedConstruction<Handle> handleConstruction = mockConstruction(Handle.class);
		Handle<Object> handle = f.get(Object.class);
		handleConstruction.close();
		assertSame(handle, f.get(Object.class));
	}
}
