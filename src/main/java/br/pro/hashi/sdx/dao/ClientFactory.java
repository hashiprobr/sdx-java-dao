package br.pro.hashi.sdx.dao;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseOptions;

final class ClientFactory {
	private static final ClientFactory INSTANCE = new ClientFactory();

	static ClientFactory getInstance() {
		return INSTANCE;
	}

	private final Map<String, DaoClient> cache;

	ClientFactory() {
		this.cache = new LinkedHashMap<>();
	}

	synchronized DaoClient get() {
		if (cache.isEmpty()) {
			throw new IllegalStateException("No client exists");
		}
		return cache.values().iterator().next();
	}

	synchronized DaoClient getFromId(String projectId) {
		if (projectId == null) {
			throw new NullPointerException("Project id cannot be null");
		}
		projectId = projectId.strip();
		if (projectId.isEmpty()) {
			throw new IllegalArgumentException("Project id cannot be blank");
		}
		DaoClient client = cache.get(projectId);
		if (client == null) {
			throw new IllegalArgumentException("Client for project %s does not exist".formatted(projectId));
		}
		return client;
	}

	synchronized DaoClient getFromCredentials(String credentialsPath) {
		if (credentialsPath == null) {
			throw new NullPointerException("Credentials path cannot be null");
		}
		credentialsPath = credentialsPath.strip();
		if (credentialsPath.isEmpty()) {
			throw new IllegalArgumentException("Credentials path cannot be blank");
		}
		ServiceAccountCredentials credentials;
		try {
			InputStream stream = new FileInputStream(credentialsPath);
			credentials = ServiceAccountCredentials.fromStream(stream);
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
		String projectId = credentials.getProjectId();
		DaoClient factory = cache.get(projectId);
		if (factory == null) {
			FirebaseOptions options = FirebaseOptions.builder()
					.setCredentials(credentials)
					.build();
			factory = new DaoClient(options, projectId);
			cache.put(projectId, factory);
		}
		return factory;
	}
}
