package spark.undertow;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.Resource;
import io.undertow.server.handlers.resource.ResourceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author Yegorius
 */
public class SparkResourceHandler implements HttpHandler {
	private static final Logger log = LoggerFactory.getLogger(SparkResourceHandler.class);

	private HttpHandler defaultHandler;
	private ResourceHandler staticResourceHandler;
	private ResourceHandler externalResourceHandler;

	public SparkResourceHandler(final HttpHandler defaultHandler) {
		this.defaultHandler = defaultHandler;
	}

	@Override
	public void handleRequest(final HttpServerExchange exchange) throws Exception {
		if (externalResourceHandler != null) {
			Resource resource = null;
			try {
				resource = externalResourceHandler.getResourceManager().getResource(exchange.getRelativePath());
			} catch (IOException e) {
				// TODO: resource not found? throw 404?
				//log.error("IO error", e);
			}
			if (resource != null) {
				externalResourceHandler.handleRequest(exchange);
				return;
			}
		}

		if (staticResourceHandler != null) {
			Resource resource = null;
			try {
				resource = staticResourceHandler.getResourceManager().getResource(exchange.getRelativePath());
			} catch (IOException e) {
				// TODO: resource not found? throw 404?
				//log.error("IO error", e);
			}
			if (resource != null) {
				staticResourceHandler.handleRequest(exchange);
				return;
			}
		}

		// else
		if (!exchange.isComplete()) {
			defaultHandler.handleRequest(exchange);
		}
	}

	public SparkResourceHandler setExternal(final ResourceHandler resourceHandler) {
		this.externalResourceHandler = resourceHandler;
		return this;
	}

	public SparkResourceHandler setStatic(final ResourceHandler resourceHandler) {
		this.staticResourceHandler = resourceHandler;
		return this;
	}
}
