package br.pro.hashi.sdx.dao;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.storage.Acl;
import com.google.cloud.storage.Acl.Entity;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Blob.BlobSourceOption;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Bucket.BlobTargetOption;
import com.google.cloud.storage.StorageBatch;
import com.google.cloud.storage.StorageException;

import br.pro.hashi.sdx.dao.exception.FileException;

class Fao implements AutoCloseable {
	private static final Entity ENTITY = Acl.User.ofAllUsers();
	private static final Acl ACL = Acl.of(ENTITY, Acl.Role.READER);

	private final Logger logger;
	private final Bucket bucket;
	private final List<Blob> locks;
	private final List<String> fileNames;

	Fao(Bucket bucket, String fileName) {
		this(bucket, List.of(fileName));
	}

	Fao(Bucket bucket, List<String> fileNames) {
		List<Blob> locks = new ArrayList<>();
		try {
			for (String fileName : fileNames) {
				String lockName = "%s.lock".formatted(fileName);
				Blob lock;
				try {
					lock = bucket.get(lockName);
				} catch (StorageException exception) {
					throw new FileException(exception);
				}
				if (lock != null) {
					Instant instant = lock.getCreateTimeOffsetDateTime().toInstant();
					if (Instant.now().toEpochMilli() - instant.toEpochMilli() < 1800000) {
						throw new FileException("Could not acquire %s".formatted(lockName));
					}
					BlobSourceOption option = BlobSourceOption.generationMatch();
					try {
						lock.delete(option);
					} catch (StorageException exception) {
						throw new FileException(exception);
					}
				}
				BlobTargetOption option = BlobTargetOption.doesNotExist();
				try {
					lock = bucket.create(lockName, null, option);
				} catch (StorageException exception) {
					throw new FileException(exception);
				}
				locks.add(lock);
			}
		} catch (FileException exception) {
			release(locks);
			throw exception;
		}
		this.logger = LoggerFactory.getLogger(Fao.class);
		this.bucket = bucket;
		this.locks = locks;
		this.fileNames = fileNames;
	}

	@Override
	public void close() {
		release(locks);
	}

	private void release(List<Blob> locks) {
		for (Blob lock : locks) {
			try {
				lock.delete();
			} catch (StorageException exception) {
				logger.warn("Could not release %s".formatted(lock.getName()), exception);
			}
		}
	}

	String upload(InputStream stream, String contentType, boolean withLink) {
		String fileName = fileNames.get(0);
		Blob blob;
		try {
			blob = bucket.create(fileName, stream)
					.toBuilder()
					.setContentType(contentType)
					.build()
					.update();
		} catch (StorageException exception) {
			throw new FileException(exception);
		}
		String url;
		if (withLink) {
			try {
				blob.createAcl(ACL);
			} catch (StorageException exception) {
				throw new FileException(exception);
			}
			url = blob.getMediaLink();
		} else {
			try {
				blob.deleteAcl(ENTITY);
			} catch (StorageException exception) {
				throw new FileException(exception);
			}
			url = "";
		}
		return url;
	}

	String refresh(boolean withLink) {
		Blob blob = get();
		if (blob == null) {
			return null;
		}
		String url;
		if (withLink) {
			url = blob.getMediaLink();
		} else {
			url = "";
		}
		return url;
	}

	DaoFile download() {
		Blob blob = get();
		if (blob == null) {
			return null;
		}
		return new DaoFile(blob.reader(), blob.getContentType(), blob.getSize());
	}

	Blob get() {
		String fileName = fileNames.get(0);
		Blob blob;
		try {
			blob = bucket.get(fileName);
		} catch (StorageException exception) {
			throw new FileException(exception);
		}
		return blob;
	}

	void remove() {
		if (!fileNames.isEmpty()) {
			StorageBatch batch = bucket.getStorage().batch();
			String bucketName = bucket.getName();
			for (String fileName : fileNames) {
				batch.delete(bucketName, fileName);
			}
			try {
				batch.submit();
			} catch (StorageException exception) {
				throw new FileException(exception);
			}
		}
	}
}
