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
		when(reflector.getCreator(any(), any(String.class))).thenAnswer((invocation) -> {
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
		verify(reflector, times(0)).getCreator(any(), any());

		DaoConverter<?, ?> converter = f.get(DefaultImplementation.class);
		verify(reflector).getCreator(DefaultImplementation.class, DefaultImplementation.class.getName());

		assertSame(converter, f.get(DefaultImplementation.class));
		verify(reflector, times(1)).getCreator(any(), any());
	}

	@Test
	void doesNotGetThrower() {
		assertThrows(ReflectionException.class, () -> {
			f.get(ThrowerImplementation.class);
		});
	}

	@Test
	void getsBothTypesFromFinalImplementationWithDiamond() {
		FinalImplementationWithDiamond converter = new FinalImplementationWithDiamond();
		assertBothTypesExist(converter);
	}

	@Test
	void getsBothTypesFromFinalImplementationWithBoth() {
		FinalImplementationWithBoth converter = new FinalImplementationWithBoth();
		assertBothTypesExist(converter);
	}

	@Test
	void getsBothTypesFromFinalImplementationWithSource() {
		FinalImplementationWithSource converter = new FinalImplementationWithSource();
		assertBothTypesExist(converter);
	}

	@Test
	void getsBothTypesFromFinalImplementationWithTarget() {
		FinalImplementationWithTarget converter = new FinalImplementationWithTarget();
		assertBothTypesExist(converter);
	}

	@Test
	void getsBothTypesFromFinalImplementationWithNeither() {
		FinalImplementationWithNeither converter = new FinalImplementationWithNeither();
		assertBothTypesExist(converter);
	}

	@Test
	void getsBothTypesFromImplementationWithDiamond() {
		ImplementationWithDiamond converter = new ImplementationWithDiamond();
		assertBothTypesExist(converter);
	}

	@Test
	void getsBothTypesFromImplementationWithBoth() {
		ImplementationWithBoth converter = new ImplementationWithBoth();
		assertBothTypesExist(converter);
	}

	private void assertBothTypesExist(DaoConverter<?, ?> converter) {
		assertSourceTypeExists(converter);
		assertTargetTypeExists(converter);
	}

	@Test
	void getsSourceTypeFromImplementationWithSource() {
		ImplementationWithSource<Integer> converter = new ImplementationWithSource<>();
		assertSourceTypeExists(converter);
		assertTargetTypeNotExists(converter);
	}

	private void assertSourceTypeExists(DaoConverter<?, ?> converter) {
		assertEquals(Integer.class, f.getSourceType(converter));
	}

	@Test
	void getsTargetTypeFromImplementationWithTarget() {
		ImplementationWithTarget<Integer> converter = new ImplementationWithTarget<>();
		assertSourceTypeNotExists(converter);
		assertTargetTypeExists(converter);
	}

	private void assertTargetTypeExists(DaoConverter<?, ?> converter) {
		assertEquals(Double.class, f.getTargetType(converter));
	}

	@Test
	void getsNeitherTypeFromImplementationWithNeither() {
		ImplementationWithNeither<Integer, Double> converter = new ImplementationWithNeither<>();
		assertSourceTypeNotExists(converter);
		assertTargetTypeNotExists(converter);
	}

	private void assertSourceTypeNotExists(DaoConverter<?, ?> converter) {
		assertThrows(ReflectionException.class, () -> {
			f.getSourceType(converter);
		});
	}

	private void assertTargetTypeNotExists(DaoConverter<?, ?> converter) {
		assertThrows(ReflectionException.class, () -> {
			f.getTargetType(converter);
		});
	}
}
