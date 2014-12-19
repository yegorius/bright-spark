package spark.undertow;

import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.SessionCookieConfig;
import spark.SparkServer;
import spark.route.RouteMatcherFactory;

public final class NewSparkServerFactory {

	// TODO: pass as parameter
	public static final int MAX_SESSIONS = 100;

	private NewSparkServerFactory() {
    }

    public static SparkServer create() {
		SparkHandler mainHandler = new SparkHandler(RouteMatcherFactory.get());
		InMemorySessionManager sessionManager = new InMemorySessionManager("spark", MAX_SESSIONS);
		SessionCookieConfig sessionConfig = new SessionCookieConfig();
		return new UndertowSparkServer(mainHandler, sessionManager, sessionConfig);
    }

}
