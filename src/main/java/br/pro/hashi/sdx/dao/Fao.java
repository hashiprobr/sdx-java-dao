package br.pro.hashi.sdx.dao;

import java.io.InputStream;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.storage.Acl;
import com.google.cloud.storage.Acl.Entity;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Blob.BlobSourceOption;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Bucket.BlobTargetOption;
import com.google.cloud.storage.StorageException;

import br.pro.hashi.sdx.dao.DaoClient.Connection;
import br.pro.hashi.sdx.dao.exception.FileException;

class Fao implements AutoCloseable {
	private static final Entity ENTITY = Acl.User.ofAllUsers();
	private static final Acl ACL = Acl.of(ENTITY, Acl.Role.READER);

	private final Logger logger;
	private final Bucket bucket;
	private final Blob lock;
	private final String fileName;

	Fao(Connection connection, String fileName) {
		this(connection.bucket(), fileName);
	}

	Fao(Bucket bucket, String fileName) {
		String lockName = "%s.lock".formatted(fileName);
		Blob lock = bucket.get(lockName);
		if (lock != null) {
			Instant instant = lock.getCreateTimeOffsetDateTime().toInstant();
			if (Instant.now().toEpochMilli() - instant.toEpochMilli() < 1800000) {
				throw new FileException("Could not acquire lock of file %s".formatted(fileName));
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
		this.logger = LoggerFactory.getLogger(Fao.class);
		this.bucket = bucket;
		this.lock = lock;
		this.fileName = fileName;
	}

	@Override
	public void close() {
		try {
			lock.delete();
		} catch (StorageException exception) {
			logger.warn("Could not release lock of file %s".formatted(fileName), exception);
		}
	}

	String upload(InputStream stream, String contentType, boolean publishLink) {
		Blob blob = bucket.create(fileName, stream)
				.toBuilder()
				.setContentType(contentType)
				.build()
				.update();
		String url;
		if (publishLink) {
			blob.createAcl(ACL);
			url = blob.getMediaLink();
		} else {
			blob.deleteAcl(ENTITY);
			url = "";
		}
		return url;
	}

	String refresh(boolean publishLink) {
		Blob blob = bucket.get(fileName);
		if (blob == null) {
			return null;
		}
		String url;
		if (publishLink) {
			url = blob.getMediaLink();
		} else {
			url = "";
		}
		return url;
	}

	DaoFile download() {
		Blob blob = bucket.get(fileName);
		if (blob == null) {
			return null;
		}
		return new DaoFile(blob.reader(), blob.getContentType(), blob.getSize());
	}

	void remove() {
		Blob blob = bucket.get(fileName);
		try {
			blob.delete();
		} catch (StorageException exception) {
			logger.warn("Could not remove file %s".formatted(fileName), exception);
		}
	}
}
