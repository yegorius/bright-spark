/*
 * Copyright 2011- Per Wendel
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package spark.undertow;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.server.session.SessionAttachmentHandler;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionManager;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.SparkServer;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;

/**
 * Spark server implementation
 *
 * @author Per Wendel
 * @author Yegorius
 */
public class UndertowSparkServer implements SparkServer {

	private static final Logger log = LoggerFactory.getLogger(UndertowSparkServer.class);
    private static final int SPARK_DEFAULT_PORT = 4567;
    private static final String NAME = "Spark";
	private final SessionManager sessionManager;
	private final SessionConfig sessionConfig;
	private final boolean mainIsBlocking;
	private HttpHandler mainHandler;
    private Undertow server;

	public UndertowSparkServer(HttpHandler mainHandler, SessionManager sessionManager, SessionConfig sessionConfig) {
		this(mainHandler, sessionManager, sessionConfig, true);
    }

	public UndertowSparkServer(HttpHandler mainHandler, SessionManager sessionManager, SessionConfig sessionConfig, boolean mainIsBlocking) {
		this.mainHandler = mainHandler;
		this.sessionManager = sessionManager;
		this.sessionConfig = sessionConfig;
		this.mainIsBlocking = mainIsBlocking;
	}

	public void ignite(String host, int port, String keystoreFile,
					   String keystorePassword, String truststoreFile,
					   String truststorePassword, String staticFilesFolder,
					   String externalFilesFolder) {
		if (port == 0) {
			try (ServerSocket s = new ServerSocket(0)) {
				port = s.getLocalPort();
			} catch (IOException e) {
				log.error("Could not get first available port (port set to 0), using default: {}", SPARK_DEFAULT_PORT);
				port = SPARK_DEFAULT_PORT;
			}
		}

		Undertow.Builder builder = Undertow.builder();

		if (keystoreFile == null) {
			builder.addHttpListener(port, host);
		} else {
			SSLContext sslContext = createSSLContext(keystoreFile, keystorePassword, truststoreFile, truststorePassword);
			if (sslContext == null) {
				log.error("Could not initialize SSL context");
				builder.addHttpListener(port, host);
			} else {
				builder.addHttpsListener(port, host, sslContext);
			}
		}

		// Handle static file routes
		if (staticFilesFolder != null || externalFilesFolder != null) {
			SparkResourceHandler resourceHandler = new SparkResourceHandler(mainHandler);
			resourceHandler.setStatic(getStaticResHandler(staticFilesFolder));
			resourceHandler.setExternal(getExternalResHandler(externalFilesFolder));
			mainHandler = resourceHandler;
		}

		mainHandler = new SessionAttachmentHandler(mainHandler, sessionManager, sessionConfig);

		if (mainIsBlocking) {
			mainHandler = new BlockingHandler(mainHandler);
		}

		builder.setHandler(mainHandler);

		server = builder.build();

		log.info("== {} has ignited ...", NAME);
		log.info(">> Listening on {}:{}", host, port);

		server.start();
	}

    public void stop() {
		log.info(">>> {} shutting down ...", NAME);
        try {
            if (server != null) server.stop();
        } catch (Exception e) {
			log.error("stop() failed", e);
            System.exit(100); // NOSONAR
        }
		log.info("done");
    }

    private static SSLContext createSSLContext(String keyStoreFile, String keyStorePassword,
											   String trustStoreFile, String trustStorePassword) {

        SslContextFactory sslContextFactory = new SslContextFactory(keyStoreFile);

        if (keyStorePassword != null)
			sslContextFactory.setKeyStorePassword(keyStorePassword);
        if (trustStoreFile != null)
			sslContextFactory.setTrustStorePath(trustStoreFile);
        if (trustStorePassword != null)
			sslContextFactory.setTrustStorePassword(trustStorePassword);

		try {
			sslContextFactory.start();
		} catch (Exception e) {
			log.error("Error initialising SSLContext", e);
			return null;
		}

		return sslContextFactory.getSslContext();
    }

    /**
     * Sets static file location if present
     */
    private static ResourceHandler getStaticResHandler(String staticFilesPath) {
        if (staticFilesPath != null) {
			ClassPathResourceManager resourceManager = new ClassPathResourceManager(ClassLoader.getSystemClassLoader());
			ResourceHandler resourceHandler = new ResourceHandler(resourceManager);
			resourceHandler.setWelcomeFiles("index.html");
			resourceHandler.setDirectoryListingEnabled(false);
			return resourceHandler;
        }
		return null;
	}

    /**
     * Sets external static file location if present
     */
    private static ResourceHandler getExternalResHandler(String externalFilesPath) {
        if (externalFilesPath != null) {
            try {
				FileResourceManager resourceManager = new FileResourceManager(new File(externalFilesPath), 16*1024);
				ResourceHandler resourceHandler = new ResourceHandler(resourceManager);
				resourceHandler.setWelcomeFiles("index.html");
				resourceHandler.setDirectoryListingEnabled(false);
				return resourceHandler;
            } catch (Exception exception) {
				log.error("Error during initialisation of external resource {}", externalFilesPath, exception);
            }
        }
		return null;
	}
}
