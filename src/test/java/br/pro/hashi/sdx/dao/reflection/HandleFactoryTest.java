package br.pro.hashi.sdx.dao.reflection;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import br.pro.hashi.sdx.dao.reflection.Handle.Construction;

class HandleFactoryTest {
	private ConverterFactory converterFactory;
	private HandleFactory f;

	@BeforeEach
	void setUp() {
		converterFactory = mock(ConverterFactory.class);
		f = new HandleFactory(converterFactory);
	}

	@Test
	void getsInstance() {
		assertInstanceOf(HandleFactory.class, HandleFactory.getInstance());
	}

	@Test
	void constructsWithDefaultConverterFactory() {
		HandleFactory factory;
		try (MockedStatic<ConverterFactory> converterFactoryStatic = mockStatic(ConverterFactory.class)) {
			converterFactoryStatic.when(() -> ConverterFactory.getInstance()).thenReturn(converterFactory);
			factory = new HandleFactory();
		}
		assertSame(converterFactory, factory.getConverterFactory());
	}

	@Test
	void gets() {
		try (MockedStatic<Construction> handleConstruction = mockStatic(Construction.class)) {
			handleConstruction.when(() -> Construction.of(converterFactory, Object.class)).thenReturn(mock(Handle.class));

			handleConstruction.verify(() -> Construction.of(eq(converterFactory), any()), times(0));

			Handle<Object> handle = f.get(Object.class);
			handleConstruction.verify(() -> Construction.of(converterFactory, Object.class));

			assertSame(handle, f.get(Object.class));
			handleConstruction.verify(() -> Construction.of(eq(converterFactory), any()), times(1));
		}
	}
}
