package br.pro.hashi.sdx.dao.reflection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import br.pro.hashi.sdx.dao.DaoConverter;
import br.pro.hashi.sdx.dao.reflection.exception.ReflectionException;
import br.pro.hashi.sdx.dao.reflection.mock.converter.DefaultImplementation;
import br.pro.hashi.sdx.dao.reflection.mock.converter.FinalImplementationWithBoth;
import br.pro.hashi.sdx.dao.reflection.mock.converter.FinalImplementationWithDiamond;
import br.pro.hashi.sdx.dao.reflection.mock.converter.FinalImplementationWithNeither;
import br.pro.hashi.sdx.dao.reflection.mock.converter.FinalImplementationWithSource;
import br.pro.hashi.sdx.dao.reflection.mock.converter.FinalImplementationWithTarget;
import br.pro.hashi.sdx.dao.reflection.mock.converter.ImplementationWithBoth;
import br.pro.hashi.sdx.dao.reflection.mock.converter.ImplementationWithDiamond;
import br.pro.hashi.sdx.dao.reflection.mock.converter.ImplementationWithNeither;
import br.pro.hashi.sdx.dao.reflection.mock.converter.ImplementationWithSource;
import br.pro.hashi.sdx.dao.reflection.mock.converter.ImplementationWithTarget;
import br.pro.hashi.sdx.dao.reflection.mock.converter.ThrowerImplementation;

class ConverterFactoryTest {
	private static final Lookup LOOKUP = MethodHandles.lookup();

	private Reflector reflector;
	private ConverterFactory f;

	@BeforeEach
	void setUp() {
		reflector = mock(Reflector.class);
		when(reflector.getExternalCreator(any())).thenAnswer((invocation) -> {
			Class<? extends DaoConverter<?, ?>> type = invocation.getArgument(0);
			Constructor<? extends DaoConverter<?, ?>> constructor = type.getDeclaredConstructor();
			return LOOKUP.unreflectConstructor(constructor);
		});
		f = new ConverterFactory(reflector);
	}

	@Test
	void getsInstance() {
		assertInstanceOf(ConverterFactory.class, ConverterFactory.getInstance());
	}

	@Test
	void constructsWithDefaultReflector() {
		ConverterFactory factory;
		try (MockedStatic<Reflector> reflectorStatic = mockStatic(Reflector.class)) {
			reflectorStatic.when(() -> Reflector.getInstance()).thenReturn(reflector);
			factory = new ConverterFactory();
		}
		assertSame(reflector, factory.getReflector());
	}

	@Test
	void gets() {
		verify(reflector, times(0)).getExternalCreator(DefaultImplementation.class);

		DaoConverter<?, ?> converter = f.get(DefaultImplementation.class);
		verify(reflector).getExternalCreator(DefaultImplementation.class);

		assertSame(converter, f.get(DefaultImplementation.class));
		verify(reflector, times(1)).getExternalCreator(DefaultImplementation.class);
	}

	@Test
	void doesNotGet() {
		assertThrows(ReflectionException.class, () -> {
			f.get(ThrowerImplementation.class);
		});
	}

	@Test
	void getsSourceTypeFromFinalImplementationWithDiamond() {
		assertSourceTypeExists(new FinalImplementationWithDiamond());
	}

	@Test
	void getsSourceTypeFromFinalImplementationWithBoth() {
		assertSourceTypeExists(new FinalImplementationWithBoth());
	}

	@Test
	void getsSourceTypeFromFinalImplementationWithSource() {
		assertSourceTypeExists(new FinalImplementationWithSource());
	}

	@Test
	void getsSourceTypeFromFinalImplementationWithTarget() {
		assertSourceTypeExists(new FinalImplementationWithTarget());
	}

	@Test
	void getsSourceTypeFromFinalImplementationWithNeither() {
		assertSourceTypeExists(new FinalImplementationWithNeither());
	}

	@Test
	void getsSourceTypeFromImplementationWithDiamond() {
		assertSourceTypeExists(new ImplementationWithDiamond());
	}

	@Test
	void getsSourceTypeFromImplementationWithBoth() {
		assertSourceTypeExists(new ImplementationWithBoth());
	}

	@Test
	void getsSourceTypeFromImplementationWithSource() {
		assertSourceTypeExists(new ImplementationWithSource<>());
	}

	private <T, S extends T> void assertSourceTypeExists(DaoConverter<?, ?> converter) {
		assertEquals(Integer.class, f.getSourceType(converter));
	}

	@Test
	void doesNotGetSourceTypeFromImplementationWithTarget() {
		assertSourceTypeNotExists(new ImplementationWithTarget<>());
	}

	@Test
	void doesNotGetSourceTypeFromImplementationWithNeither() {
		assertSourceTypeNotExists(new ImplementationWithNeither<>());
	}

	private <T, S extends T> void assertSourceTypeNotExists(DaoConverter<?, ?> converter) {
		assertThrows(ReflectionException.class, () -> {
			f.getSourceType(converter);
		});
	}
}
