package br.pro.hashi.sdx.dao;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseOptions;

import br.pro.hashi.sdx.dao.reflection.HandleFactory;

class ClientFactory {
	private static final ClientFactory INSTANCE = newInstance();

	private static ClientFactory newInstance() {
		HandleFactory factory = HandleFactory.getInstance();
		return new ClientFactory(factory);
	}

	static ClientFactory getInstance() {
		return INSTANCE;
	}

	private final HandleFactory factory;
	private final Map<String, DaoClient> cache;

	ClientFactory(HandleFactory factory) {
		this.factory = factory;
		this.cache = new LinkedHashMap<>();
	}

	synchronized DaoClient getFirst() {
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
		DaoClient client = cache.get(projectId);
		if (client == null) {
			FirebaseOptions options = FirebaseOptions.builder()
					.setCredentials(credentials)
					.build();
			client = new DaoClient(factory, options, projectId);
			cache.put(projectId, client);
		}
		return client;
	}
}
