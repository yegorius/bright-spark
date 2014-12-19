package spark.undertow;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.*;
import spark.exception.ExceptionHandlerImpl;
import spark.exception.ExceptionMapper;
import spark.route.HttpMethod;
import spark.route.RouteMatch;
import spark.route.SimpleRouteMatcher;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * @author Yegorius
 */
public class SparkHandler implements HttpHandler {
	private static final Logger log = LoggerFactory.getLogger(SparkHandler.class);
	private static final String ACCEPT_TYPE_MIME_HEADER = "Accept";
	private static final String HTTP_METHOD_OVERRIDE_HEADER = "X-HTTP-Method-Override";

	private SimpleRouteMatcher routeMatcher;

	// TODO: pass as param
	private final boolean multiPart = true;

	public SparkHandler(final SimpleRouteMatcher routeMatcher) {
		this.routeMatcher = routeMatcher;
	}

	@Override
	public void handleRequest(final HttpServerExchange exchange) throws Exception {
		String method = exchange.getRequestHeaders().getFirst(HTTP_METHOD_OVERRIDE_HEADER);
		if (method == null) {
			method = exchange.getRequestMethod().toString();
		}
		String httpMethodStr = method.toLowerCase();
		String uri = exchange.getRequestURI();
		String acceptType = exchange.getRequestHeaders().getFirst(ACCEPT_TYPE_MIME_HEADER);

		String bodyContent = null;

		log.debug("httpMethod: {}, uri: {}", httpMethodStr, uri);

		Request request = null;
		Response response = null;

		try {
			// BEFORE filters
			List<RouteMatch> matchSet = routeMatcher.findTargetsForRequestedRoute(HttpMethod.before, uri, acceptType);

			for (RouteMatch filterMatch : matchSet) {
				Object filterTarget = filterMatch.getTarget();
				if (filterTarget instanceof FilterImpl) {
					request = new UndertowRequest(filterMatch, exchange, multiPart);
					response = new UndertowResponse(exchange);

					FilterImpl filter = (FilterImpl) filterTarget;

					filter.handle(request, response);

					String bodyAfterFilter = Access.getBody(response);
					if (bodyAfterFilter != null) {
						bodyContent = bodyAfterFilter;
					}
				}
			}
			// BEFORE filters, END

			HttpMethod httpMethod = HttpMethod.valueOf(httpMethodStr);

			RouteMatch match = routeMatcher.findTargetForRequestedRoute(httpMethod, uri, acceptType);

			Object target = null;
			if (match != null) {
				target = match.getTarget();
			} else if (httpMethod == HttpMethod.head && bodyContent == null) {
				// See if get is mapped to provide default head mapping
				bodyContent = routeMatcher.findTargetForRequestedRoute(HttpMethod.get, uri, acceptType) != null ? "" : null;
			}

			if (target != null) {
				try {
					String result = null;
					if (target instanceof RouteImpl) {
						RouteImpl route = ((RouteImpl) target);
						request = new UndertowRequest(match, exchange, multiPart);
						response = new UndertowResponse(exchange);

						Object element = route.handle(request, response);

						result = route.render(element);
						// result = element.toString(); // TODO: Remove later when render fixed
					}
					if (result != null) {
						bodyContent = result;
					}
				} catch (HaltException hEx) { // NOSONAR
					throw hEx; // NOSONAR
				}
			}

			// AFTER filters
			matchSet = routeMatcher.findTargetsForRequestedRoute(HttpMethod.after, uri, acceptType);

			for (RouteMatch filterMatch : matchSet) {
				Object filterTarget = filterMatch.getTarget();
				if (filterTarget instanceof FilterImpl) {
					request = new UndertowRequest(match, exchange, multiPart);
					response = new UndertowResponse(exchange);

					FilterImpl filter = (FilterImpl) filterTarget;
					filter.handle(request, response);

					String bodyAfterFilter = Access.getBody(response);
					if (bodyAfterFilter != null) {
						bodyContent = bodyAfterFilter;
					}
				}
			}
			// AFTER filters, END

		} catch (HaltException hEx) {
			log.debug("halt performed");
			exchange.setResponseCode(hEx.getStatusCode());
			if (hEx.getBody() != null) {
				bodyContent = hEx.getBody();
			} else {
				bodyContent = "";
			}
		} catch (Exception e) {
			ExceptionHandlerImpl handler = ExceptionMapper.getInstance().getHandler(e);
			if (handler != null) {
				handler.handle(e, request, response);
				String bodyAfterFilter = Access.getBody(response);
				if (bodyAfterFilter != null) {
					bodyContent = bodyAfterFilter;
				}
			} else {
				log.error("", e);
				exchange.setResponseCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				bodyContent = INTERNAL_ERROR;
			}
		}

		if (bodyContent == null && (response != null && response.isRedirected())) {
			bodyContent = "";
		}

		boolean consumed = bodyContent != null;

		if (!consumed) {
			log.info("The requested route [{}] has not been mapped in Spark", uri);
			exchange.setResponseCode(404);
			bodyContent = String.format(NOT_FOUND);
			consumed = true;
		}

		if (consumed) {
			// Write body content
			if (!exchange.isComplete()) {
				if (!exchange.getResponseHeaders().contains(Headers.CONTENT_TYPE)) {
					exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, "text/html; charset=utf-8");
				}
				exchange.getResponseSender().send(bodyContent);
				exchange.endExchange();
			}
		}
	}

	private static final String NOT_FOUND = "<html><body><h2>404 Not found</h2></body></html>";
	private static final String INTERNAL_ERROR = "<html><body><h2>500 Internal Error</h2></body></html>";
}
