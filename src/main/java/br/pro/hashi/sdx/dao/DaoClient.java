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

import br.pro.hashi.sdx.dao.Dao.Construction;
import br.pro.hashi.sdx.dao.reflection.Handle;
import br.pro.hashi.sdx.dao.reflection.HandleFactory;

/**
 * Creates data access objects from a Firebase project.
 */
public final class DaoClient {
	/**
	 * Creates a new client from the specified project id.
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
	 * Creates a new client from the specified credentials path.
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

	private final Logger logger;
	private final HandleFactory handleFactory;
	private final Map<Class<?>, Dao<?>> cache;
	private final FirebaseOptions options;
	private final String projectId;
	private Connection connection;

	DaoClient(FirebaseOptions options, String projectId) {
		this(HandleFactory.getInstance(), options, projectId);
	}

	DaoClient(HandleFactory factory, FirebaseOptions options, String projectId) {
		this.logger = LoggerFactory.getLogger(DaoClient.class);
		this.handleFactory = factory;
		this.cache = new HashMap<>();
		this.options = options;
		this.projectId = projectId;
		this.connection = null;
	}

	HandleFactory getHandleFactory() {
		return handleFactory;
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
	 * Obtains the data access object of an entity.
	 * 
	 * @param <E>  the entity type
	 * @param type a {@link Class} representing {@code E}
	 * @return the object
	 */
	public <E> Dao<E> get(Class<E> type) {
		synchronized (cache) {
			@SuppressWarnings("unchecked")
			Dao<E> dao = (Dao<E>) cache.get(type);
			if (dao == null) {
				Handle<E> handle = handleFactory.get(type);
				dao = Construction.of(this, handle);
				cache.put(type, dao);
			}
			return dao;
		}
	}

	record Connection(FirebaseApp firebase, Firestore firestore, Bucket bucket) {
	}
}
