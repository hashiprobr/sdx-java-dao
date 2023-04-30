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
				Blob lock = bucket.get(lockName);
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
		Blob blob = bucket.create(fileNames.get(0), stream)
				.toBuilder()
				.setContentType(contentType)
				.build()
				.update();
		String url;
		if (withLink) {
			blob.createAcl(ACL);
			url = blob.getMediaLink();
		} else {
			blob.deleteAcl(ENTITY);
			url = "";
		}
		return url;
	}

	String refresh(boolean withLink) {
		Blob blob = bucket.get(fileNames.get(0));
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
		Blob blob = bucket.get(fileNames.get(0));
		if (blob == null) {
			return null;
		}
		return new DaoFile(blob.reader(), blob.getContentType(), blob.getSize());
	}

	void remove() {
		remove(fileNames.get(0));
	}

	void clear() {
		for (String fileName : fileNames) {
			remove(fileName);
		}
	}

	private void remove(String fileName) {
		Blob blob = bucket.get(fileName);
		try {
			blob.delete();
		} catch (StorageException exception) {
			logger.warn("Could not remove %s".formatted(fileName), exception);
		}
	}
}
