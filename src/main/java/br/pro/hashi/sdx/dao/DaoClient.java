package br.pro.hashi.sdx.dao;

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

public final class DaoClient {
	public static DaoClient fromId(String projectId) {
		return ClientFactory.getInstance().getFromId(projectId);
	}

	public static DaoClient fromCredentials(String credentialsPath) {
		return ClientFactory.getInstance().getFromCredentials(credentialsPath);
	}

	private final Logger logger;
	private final HandleFactory factory;
	private final Map<Class<?>, Dao<?>> cache;
	private final FirebaseOptions options;
	private final String projectId;
	private FirebaseApp app;
	private Connection connection;

	DaoClient(FirebaseOptions options, String projectId) {
		this(HandleFactory.getInstance(), options, projectId);
	}

	DaoClient(HandleFactory factory, FirebaseOptions options, String projectId) {
		this.logger = LoggerFactory.getLogger(DaoClient.class);
		this.factory = factory;
		this.cache = new HashMap<>();
		this.options = options;
		this.projectId = projectId;
		this.app = null;
		this.connection = null;
	}

	HandleFactory getFactory() {
		return factory;
	}

	synchronized Firestore getFirestore() {
		doConnect();
		return connection.firestore();
	}

	synchronized Connection getConnection() {
		doConnect();
		return connection;
	}

	public synchronized <T> Dao<T> get(Class<T> type) {
		@SuppressWarnings("unchecked")
		Dao<T> dao = (Dao<T>) cache.get(type);
		if (dao == null) {
			Handle handle = factory.get(type);
			dao = new Dao<>(this, handle);
			cache.put(type, dao);
		}
		return dao;
	}

	public synchronized void connect() {
		doConnect();
	}

	public synchronized void disconnect() {
		if (app == null) {
			return;
		}
		logger.info("Disconnecting Firebase client from project %s...".formatted(projectId));
		app.delete();
		app = null;
		connection = null;
		logger.info("Firebase client disconnected from project %s".formatted(projectId));
	}

	private void doConnect() {
		if (app != null) {
			return;
		}
		logger.info("Connecting Firebase client to project %s...".formatted(projectId));
		app = FirebaseApp.initializeApp(options, projectId);
		Firestore firestore = FirestoreClient.getFirestore(app);
		String url = "%s.appspot.com".formatted(projectId);
		Bucket bucket = StorageClient.getInstance(app).bucket(url);
		connection = new Connection(firestore, bucket);
		logger.info("Firebase client connected to project %s".formatted(projectId));
	}

	record Connection(Firestore firestore, Bucket bucket) {
	}
}
