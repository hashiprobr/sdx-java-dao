package br.pro.hashi.sdx.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Acl;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Blob.BlobSourceOption;
import com.google.cloud.storage.Blob.Builder;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Bucket.BlobTargetOption;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageBatch;
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
		Blob lock = mockAvailableCloseableLock("file");
		f = newFao("file");
		verify(lock, times(0)).delete();
		f.close();
		verify(lock).delete();
	}

	@Test
	void constructsForTwoFilesAndCloses() {
		Blob lock0 = mockAvailableCloseableLock("file0");
		Blob lock1 = mockAvailableCloseableLock("file1");
		f = newFao(List.of("file0", "file1"));
		verify(lock0, times(0)).delete();
		verify(lock1, times(0)).delete();
		f.close();
		verify(lock0).delete();
		verify(lock1).delete();
	}

	@Test
	void constructsWithDeadLockAndCloses() {
		String lockName = getLockName("file");
		BlobSourceOption option = BlobSourceOption.generationMatch();
		Blob oldLock = mockOldLock(lockName, 0);
		when(oldLock.delete(option)).thenReturn(true);
		Blob newLock = mockCloseableLock(lockName);
		f = newFao("file");
		verify(oldLock).delete(option);
		verify(newLock, times(0)).delete();
		f.close();
		verify(newLock).delete();
	}

	@Test
	void constructsButDoesNotClose() {
		Blob lock = mockAvailableUncloseableLock("file");
		f = newFao("file");
		verify(lock, times(0)).delete();
		f.close();
		verify(lock).delete();
	}

	@Test
	void constructsForTwoFilesButDoesNotCloseFirst() {
		Blob lock0 = mockAvailableUncloseableLock("file0");
		Blob lock1 = mockAvailableCloseableLock("file1");
		f = newFao(List.of("file0", "file1"));
		verify(lock0, times(0)).delete();
		verify(lock1, times(0)).delete();
		f.close();
		verify(lock0).delete();
		verify(lock1).delete();
	}

	@Test
	void constructsForTwoFilesButDoesNotCloseSecond() {
		Blob lock0 = mockAvailableCloseableLock("file0");
		Blob lock1 = mockAvailableUncloseableLock("file1");
		f = newFao(List.of("file0", "file1"));
		verify(lock0, times(0)).delete();
		verify(lock1, times(0)).delete();
		f.close();
		verify(lock0).delete();
		verify(lock1).delete();
	}

	private Blob mockAvailableUncloseableLock(String fileName) {
		String lockName = getLockName(fileName);
		when(bucket.get(lockName)).thenReturn(null);
		Blob lock = mockNewLock(lockName);
		when(lock.delete()).thenThrow(StorageException.class);
		when(lock.getName()).thenReturn(fileName);
		return lock;
	}

	@Test
	void doesNotConstructIfBucketDoesNotGet() {
		when(bucket.get(getLockName("file"))).thenThrow(StorageException.class);
		assertThrows(FileException.class, () -> {
			newFao("file");
		});
		verify(bucket, times(0)).create(any(), any(), (BlobTargetOption) any());
	}

	@Test
	void doesNotConstructWithLiveLock() {
		Blob lock = mockOldLock(getLockName("file"), Instant.now().toEpochMilli());
		assertThrows(FileException.class, () -> {
			newFao("file");
		});
		verify(lock, times(0)).delete(any());
		verify(bucket, times(0)).create(any(), any(), (BlobTargetOption) any());
	}

	@Test
	void doesNotConstructWithReplacedLock() {
		BlobSourceOption option = BlobSourceOption.generationMatch();
		Blob lock = mockOldLock(getLockName("file"), 0);
		when(lock.delete(option)).thenThrow(StorageException.class);
		assertThrows(FileException.class, () -> {
			newFao("file");
		});
		verify(lock).delete(option);
		verify(bucket, times(0)).create(any(), any(), (BlobTargetOption) any());
	}

	private Blob mockOldLock(String lockName, long timestamp) {
		OffsetDateTime datetime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
		Blob lock = mock(Blob.class);
		when(lock.getCreateTimeOffsetDateTime()).thenReturn(datetime);
		when(bucket.get(lockName)).thenReturn(lock);
		return lock;
	}

	@Test
	void doesNotConstructWithExistingLock() {
		BlobTargetOption option = mockExistingLock("file");
		assertThrows(FileException.class, () -> {
			newFao("file");
		});
		verify(bucket).create("file.lock", null, option);
	}

	@Test
	void doesNotConstructForTwoFilesWithExistingFirstLock() {
		BlobTargetOption option = mockExistingLock("file0");
		assertThrows(FileException.class, () -> {
			newFao(List.of("file0", "file1"));
		});
		verify(bucket).create("file0.lock", null, option);
		verify(bucket, times(0)).get("file1.lock");
	}

	@Test
	void doesNotConstructForTwoFilesWithExistingSecondLock() {
		Blob lock0 = mockAvailableCloseableLock("file0");
		BlobTargetOption option = mockExistingLock("file1");
		assertThrows(FileException.class, () -> {
			newFao(List.of("file0", "file1"));
		});
		verify(lock0).delete();
		verify(bucket).create("file1.lock", null, option);
	}

	private BlobTargetOption mockExistingLock(String fileName) {
		String lockName = getLockName(fileName);
		BlobTargetOption option = BlobTargetOption.doesNotExist();
		when(bucket.get(lockName)).thenReturn(null);
		when(bucket.create(lockName, null, option)).thenThrow(StorageException.class);
		return option;
	}

	@Test
	void uploads() {
		f = newFao();
		Blob blob = mockNewBlob();
		assertEquals("", upload(false));
		verify(blob).deleteAcl(Acl.User.ofAllUsers());
	}

	@Test
	void uploadsWithLink() {
		f = newFao();
		Blob blob = mockNewBlob();
		when(blob.getMediaLink()).thenReturn("url");
		assertEquals("url", upload(true));
		verify(blob).createAcl(Acl.of(Acl.User.ofAllUsers(), Acl.Role.READER));
	}

	@Test
	void doesNotUploadIfBucketDoesNotCreate() {
		f = newFao();
		when(bucket.create(eq("file"), any(InputStream.class))).thenThrow(StorageException.class);
		assertThrows(FileException.class, () -> {
			upload(false);
		});
	}

	@Test
	void doesNotUploadIfBlobDoesNotDeleteAcl() {
		f = newFao();
		Blob blob = mockNewBlob();
		when(blob.deleteAcl(Acl.User.ofAllUsers())).thenThrow(StorageException.class);
		assertThrows(FileException.class, () -> {
			upload(false);
		});
	}

	@Test
	void doesNotUploadWithLinkIfBlobDoesNotCreateAcl() {
		f = newFao();
		Blob blob = mockNewBlob();
		when(blob.createAcl(Acl.of(Acl.User.ofAllUsers(), Acl.Role.READER))).thenThrow(StorageException.class);
		assertThrows(FileException.class, () -> {
			upload(true);
		});
	}

	private Blob mockNewBlob() {
		Blob blob = mock(Blob.class);
		Builder builder = mock(Builder.class);
		when(builder.setContentType("application/octet-stream")).thenReturn(builder);
		when(builder.build()).thenReturn(blob);
		when(blob.toBuilder()).thenReturn(builder);
		when(blob.update()).thenReturn(blob);
		when(bucket.create(eq("file"), any(InputStream.class))).thenReturn(blob);
		return blob;
	}

	private String upload(boolean withLink) {
		return f.upload(InputStream.nullInputStream(), "application/octet-stream", withLink);
	}

	@Test
	void ignoresRefresh() {
		f = newFao();
		when(bucket.get("file")).thenReturn(null);
		assertNull(f.refresh(false));
	}

	@Test
	void refreshes() {
		f = newFao();
		mockOldBlob();
		assertEquals("", f.refresh(false));
	}

	@Test
	void refreshesWithLink() {
		f = newFao();
		Blob blob = mockOldBlob();
		when(blob.getMediaLink()).thenReturn("url");
		assertEquals("url", f.refresh(true));
	}

	@Test
	void ignoresDownload() {
		f = newFao();
		when(bucket.get("file")).thenReturn(null);
		assertNull(f.download());
	}

	@Test
	void downloads() {
		f = newFao();
		ReadChannel channel = mock(ReadChannel.class);
		Blob blob = mockOldBlob();
		when(blob.reader()).thenReturn(channel);
		when(blob.getContentType()).thenReturn("application/octet-stream");
		when(blob.getSize()).thenReturn(1L);
		DaoFile file = f.download();
		assertSame(channel, file.getChannel());
		assertEquals("application/octet-stream", file.getContentType());
		assertEquals(1, file.getContentLength());
	}

	private Blob mockOldBlob() {
		Blob blob = mock(Blob.class);
		when(bucket.get("file")).thenReturn(blob);
		return blob;
	}

	@Test
	void doesNotGetIfBucketDoesNotGet() {
		f = newFao();
		when(bucket.get("file")).thenThrow(StorageException.class);
		assertThrows(FileException.class, () -> {
			f.get();
		});
	}

	@Test
	void removes() {
		f = newFao();
		StorageBatch batch = mockBatch();
		f.remove();
		verify(batch).delete("bucket", "file");
		verify(batch).submit();
	}

	@Test
	void removesTwoFiles() {
		mockAvailableLock("file0");
		mockAvailableLock("file1");
		f = newFao(List.of("file0", "file1"));
		StorageBatch batch = mockBatch();
		f.remove();
		verify(batch).delete("bucket", "file0");
		verify(batch).delete("bucket", "file1");
		verify(batch).submit();
	}

	@Test
	void doesNotRemoveIfBatchDoesNotSubmit() {
		f = newFao();
		StorageBatch batch = mockBatch();
		doThrow(StorageException.class).when(batch).submit();
		assertThrows(FileException.class, () -> {
			f.remove();
		});
	}

	private StorageBatch mockBatch() {
		StorageBatch batch = mock(StorageBatch.class);
		Storage storage = mock(Storage.class);
		when(storage.batch()).thenReturn(batch);
		when(bucket.getStorage()).thenReturn(storage);
		when(bucket.getName()).thenReturn("bucket");
		return batch;
	}

	private Fao newFao() {
		mockAvailableLock("file");
		Fao fao = newFao("file");
		return fao;
	}

	private Fao newFao(String fileName) {
		return new Fao(bucket, fileName);
	}

	private Fao newFao(List<String> fileNames) {
		return new Fao(bucket, fileNames);
	}

	private void mockAvailableLock(String fileName) {
		String lockName = getLockName(fileName);
		when(bucket.get(lockName)).thenReturn(null);
		mockNewLock(lockName);
	}

	private Blob mockAvailableCloseableLock(String fileName) {
		String lockName = getLockName(fileName);
		when(bucket.get(lockName)).thenReturn(null);
		return mockCloseableLock(lockName);
	}

	private Blob mockCloseableLock(String lockName) {
		Blob lock = mockNewLock(lockName);
		when(lock.delete()).thenReturn(true);
		return lock;
	}

	private Blob mockNewLock(String lockName) {
		Blob lock = mock(Blob.class);
		when(bucket.create(lockName, null, BlobTargetOption.doesNotExist())).thenReturn(lock);
		return lock;
	}

	private String getLockName(String fileName) {
		return "%s.lock".formatted(fileName);
	}
}
