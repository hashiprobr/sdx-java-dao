package br.pro.hashi.sdx.dao;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Blob.BlobSourceOption;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Bucket.BlobTargetOption;
import com.google.cloud.storage.StorageException;

import br.pro.hashi.sdx.dao.exception.FileException;

class FaoTest {
	private Bucket bucket;
	private Fao f;

	@BeforeEach
	void setUp() {
		bucket = mock(Bucket.class);
	}

	@Test
	void constructsAndCloses() {
		when(bucket.get("file.lock")).thenReturn(null);
		Blob lock = mockNewLock("file");
		when(lock.delete()).thenReturn(true);
		f = newFao("file");
		verify(lock, times(0)).delete();
		f.close();
		verify(lock).delete();
	}

	@Test
	void constructsForTwoFilesAndCloses() {
		when(bucket.get("file0.lock")).thenReturn(null);
		Blob lock0 = mockNewLock("file0");
		when(lock0.delete()).thenReturn(true);
		when(bucket.get("file1.lock")).thenReturn(null);
		Blob lock1 = mockNewLock("file1");
		when(lock1.delete()).thenReturn(true);
		f = newFao(List.of("file0", "file1"));
		verify(lock0, times(0)).delete();
		verify(lock1, times(0)).delete();
		f.close();
		verify(lock0).delete();
		verify(lock1).delete();
	}

	@Test
	void constructsWithDeadLockAndCloses() {
		BlobSourceOption option = BlobSourceOption.generationMatch();
		Blob oldLock = mockOldLock("file", 0);
		when(oldLock.delete(option)).thenReturn(true);
		Blob newLock = mockNewLock("file");
		when(newLock.delete()).thenReturn(true);
		f = newFao("file");
		verify(oldLock).delete(option);
		verify(newLock, times(0)).delete();
		f.close();
		verify(newLock).delete();
	}

	@Test
	void constructsButDoesNotClose() {
		when(bucket.get("file.lock")).thenReturn(null);
		Blob lock = mockNewLock("file");
		when(lock.delete()).thenThrow(StorageException.class);
		f = newFao("file");
		verify(lock, times(0)).delete();
		f.close();
		verify(lock).delete();
	}

	@Test
	void constructsForTwoFilesButDoesNotCloseFirstLock() {
		when(bucket.get("file0.lock")).thenReturn(null);
		Blob lock0 = mockNewLock("file0");
		when(lock0.delete()).thenThrow(StorageException.class);
		when(bucket.get("file1.lock")).thenReturn(null);
		Blob lock1 = mockNewLock("file1");
		when(lock1.delete()).thenReturn(true);
		f = newFao(List.of("file0", "file1"));
		verify(lock0, times(0)).delete();
		verify(lock1, times(0)).delete();
		f.close();
		verify(lock0).delete();
		verify(lock1).delete();
	}

	@Test
	void constructsForTwoFilesButDoesNotCloseSecondLock() {
		when(bucket.get("file0.lock")).thenReturn(null);
		Blob lock0 = mockNewLock("file0");
		when(lock0.delete()).thenReturn(true);
		when(bucket.get("file1.lock")).thenReturn(null);
		Blob lock1 = mockNewLock("file1");
		when(lock1.delete()).thenThrow(StorageException.class);
		f = newFao(List.of("file0", "file1"));
		verify(lock0, times(0)).delete();
		verify(lock1, times(0)).delete();
		f.close();
		verify(lock0).delete();
		verify(lock1).delete();
	}

	@Test
	void doesNotConstructWithLiveLock() {
		Blob lock = mockOldLock("file", Instant.now().toEpochMilli());
		assertThrows(FileException.class, () -> {
			newFao("file");
		});
		verify(lock, times(0)).delete(any());
		verify(bucket, times(0)).create(any(), any(), (BlobTargetOption) any());
	}

	@Test
	void doesNotConstructWithReplacedLock() {
		BlobSourceOption option = BlobSourceOption.generationMatch();
		Blob lock = mockOldLock("file", 0);
		when(lock.delete(option)).thenThrow(StorageException.class);
		assertThrows(FileException.class, () -> {
			newFao("file");
		});
		verify(lock).delete(option);
		verify(bucket, times(0)).create(any(), any(), (BlobTargetOption) any());
	}

	@Test
	void doesNotConstructWithExistingLock() {
		when(bucket.get("file.lock")).thenReturn(null);
		BlobTargetOption option = BlobTargetOption.doesNotExist();
		when(bucket.create("file.lock", null, option)).thenThrow(StorageException.class);
		assertThrows(FileException.class, () -> {
			newFao("file");
		});
		verify(bucket).create("file.lock", null, option);
	}

	@Test
	void doesNotConstructForTwoFilesWithExistingFirstLock() {
		when(bucket.get("file0.lock")).thenReturn(null);
		BlobTargetOption option = BlobTargetOption.doesNotExist();
		when(bucket.create("file0.lock", null, option)).thenThrow(StorageException.class);
		assertThrows(FileException.class, () -> {
			newFao(List.of("file0", "file1"));
		});
		verify(bucket).create("file0.lock", null, option);
		verify(bucket, times(0)).get("file1.lock");
	}

	@Test
	void doesNotConstructForTwoFilesWithExistingSecondLock() {
		when(bucket.get("file0.lock")).thenReturn(null);
		Blob lock0 = mockNewLock("file0");
		when(lock0.delete()).thenReturn(true);
		when(bucket.get("file1.lock")).thenReturn(null);
		BlobTargetOption option = BlobTargetOption.doesNotExist();
		when(bucket.create("file1.lock", null, option)).thenThrow(StorageException.class);
		assertThrows(FileException.class, () -> {
			newFao(List.of("file0", "file1"));
		});
		verify(lock0).delete();
		verify(bucket).create("file1.lock", null, option);
	}

	private Blob mockOldLock(String fileName, long timestamp) {
		OffsetDateTime datetime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
		Blob lock = mock(Blob.class);
		when(lock.getCreateTimeOffsetDateTime()).thenReturn(datetime);
		when(bucket.get(getLockName(fileName))).thenReturn(lock);
		return lock;
	}

	private Blob mockNewLock(String fileName) {
		Blob lock = mock(Blob.class);
		when(bucket.create(getLockName(fileName), null, BlobTargetOption.doesNotExist())).thenReturn(lock);
		return lock;
	}

	private String getLockName(String fileName) {
		return "%s.lock".formatted(fileName);
	}

	private Fao newFao(String fileName) {
		return new Fao(bucket, fileName);
	}

	private Fao newFao(List<String> fileNames) {
		return new Fao(bucket, fileNames);
	}
}
