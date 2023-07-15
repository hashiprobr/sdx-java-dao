package br.pro.hashi.sdx.dao;

import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.storage.Bucket;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.cloud.StorageClient;

import br.pro.hashi.sdx.dao.reflection.Handle;
import br.pro.hashi.sdx.dao.reflection.HandleFactory;
import br.pro.hashi.sdx.dao.reflection.exception.AnnotationException;

/**
 * Creates data access objects from a Firebase project.
 */
public final class DaoClient {
	/**
	 * Gets a new client from the specified project id.
	 * 
	 * @param projectId the id
	 * @return the client
	 * @throws NullPointerException     if the id is null
	 * @throws IllegalArgumentException if the id is blank or a client for the id
	 *                                  does not exist
	 */
	public static DaoClient fromId(String projectId) {
		return ClientFactory.getInstance().getFromId(projectId);
	}

	/**
	 * Gets a new client from the specified credentials path.
	 * 
	 * @param credentialsPath the path
	 * @return the client
	 * @throws NullPointerException     if the path is null
	 * @throws IllegalArgumentException if the path is blank
	 * @throws UncheckedIOException     if the credentials cannot be read
	 */
	public static DaoClient fromCredentials(String credentialsPath) {
		return ClientFactory.getInstance().getFromCredentials(credentialsPath);
	}

	static DaoClient newInstance(FirebaseOptions options, String projectId) {
		HandleFactory factory = HandleFactory.getInstance();
		return new DaoClient(factory, options, projectId);
	}

	private final Logger logger;
	private final HandleFactory factory;
	private final Map<Class<?>, Dao<?>> cache;
	private final FirebaseOptions options;
	private final String projectId;
	private Connection connection;

	DaoClient(HandleFactory factory, FirebaseOptions options, String projectId) {
		this.logger = LoggerFactory.getLogger(DaoClient.class);
		this.factory = factory;
		this.cache = new HashMap<>();
		this.options = options;
		this.projectId = projectId;
		this.connection = null;
	}

	synchronized Firestore getFirestore() {
		return getConnection().firestore();
	}

	synchronized Bucket getBucket() {
		return getConnection().bucket();
	}

	synchronized Connection getConnection() {
		if (connection == null) {
			throw new IllegalStateException("Client is not connected");
		}
		return connection;
	}

	/**
	 * Connects to the project.
	 */
	public synchronized void connect() {
		if (connection != null) {
			return;
		}
		logger.info("Connecting client to project %s...".formatted(projectId));
		String bucketName = "%s.appspot.com".formatted(projectId);
		FirebaseApp firebase = FirebaseApp.initializeApp(options, projectId);
		Firestore firestore = FirestoreClient.getFirestore(firebase);
		Bucket bucket = StorageClient.getInstance(firebase).bucket(bucketName);
		connection = new Connection(firebase, firestore, bucket);
		logger.info("Client connected to project %s".formatted(projectId));
	}

	/**
	 * Disconnects from the project.
	 */
	public synchronized void disconnect() {
		if (connection == null) {
			return;
		}
		logger.info("Disconnecting client from project %s...".formatted(projectId));
		connection.firebase().delete();
		connection = null;
		logger.info("Client disconnected from project %s".formatted(projectId));
	}

	/**
	 * Obtains the data access object of the specified entity type.
	 * 
	 * @param <E>  the type
	 * @param type a {@link Class} representing {@code E}
	 * @return the object
	 * @throws NullPointerException if the type is null
	 */
	public <E> Dao<E> get(Class<E> type) {
		if (type == null) {
			throw new NullPointerException("Type cannot be null");
		}
		synchronized (cache) {
			@SuppressWarnings("unchecked")
			Dao<E> dao = (Dao<E>) cache.get(type);
			if (dao == null) {
				Handle<E> handle = factory.get(type);
				if (!handle.hasKey()) {
					throw new AnnotationException(type.getName(), "Must have a @Key field");
				}
				dao = new Dao<>(this, handle);
				cache.put(type, dao);
			}
			return dao;
		}
	}

	record Connection(FirebaseApp firebase, Firestore firestore, Bucket bucket) {
	}
}
