package br.pro.hashi.sdx.dao.reflection;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import br.pro.hashi.sdx.dao.DaoConverter;
import br.pro.hashi.sdx.dao.reflection.exception.ReflectionException;
import br.pro.hashi.sdx.dao.reflection.mock.converter.DefaultImplementation;
import br.pro.hashi.sdx.dao.reflection.mock.converter.ThrowerImplementation;

class ConverterFactoryTest {
	private static final Lookup LOOKUP = MethodHandles.lookup();

	private AutoCloseable mocks;
	private @Mock Reflector reflector;
	private ConverterFactory f;

	@BeforeEach
	void setUp() {
		mocks = MockitoAnnotations.openMocks(this);

		when(reflector.getCreator(any(), any(String.class))).thenAnswer((invocation) -> {
			Class<? extends DaoConverter<?, ?>> type = invocation.getArgument(0);
			Constructor<? extends DaoConverter<?, ?>> constructor = type.getDeclaredConstructor();
			return LOOKUP.unreflectConstructor(constructor);
		});

		f = new ConverterFactory(reflector);
	}

	@AfterEach
	void tearDown() {
		assertDoesNotThrow(() -> {
			mocks.close();
		});
	}

	@Test
	void gets() {
		DaoConverter<?, ?> converter = f.get(DefaultImplementation.class);
		assertSame(converter, f.get(DefaultImplementation.class));
	}

	@Test
	void doesNotGetThrowerConverter() {
		assertThrows(ReflectionException.class, () -> {
			f.get(ThrowerImplementation.class);
		});
	}
}
