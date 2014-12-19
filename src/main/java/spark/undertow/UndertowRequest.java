package spark.undertow;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.form.*;
import io.undertow.server.session.SessionManager;
import io.undertow.servlet.spec.HttpSessionImpl;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.Sessions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.Pooled;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSourceChannel;
import spark.QueryParamsMap;
import spark.Request;
import spark.Session;
import spark.route.RouteMatch;
import spark.utils.IOUtils;
import spark.utils.SparkUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Yegorius
 */
public class UndertowRequest implements Request {
	private static final Logger log = LoggerFactory.getLogger(UndertowRequest.class);

	private HttpServerExchange exchange;
	private Map<String, Object> attributes;
	private HashSet<String> headers;
	private String body;
	private byte[] bodyAsBytes = null;
	private Map<String, String> params;
	private List<String> splat;
	private QueryParamsMap queryMap;
	private Map<String, String> cookies;
	private FormData parsedFormData;
	private boolean readStarted;
	private final FormParserFactory formParserFactory;
	private Session session;

	UndertowRequest(final RouteMatch routeMatch, final HttpServerExchange exchange, boolean multiPart) {
		this.exchange = exchange;

		List<String> requestList = SparkUtils.convertRouteToList(routeMatch.getRequestURI());
		List<String> matchedList = SparkUtils.convertRouteToList(routeMatch.getMatchUri());

		params = getParams(requestList, matchedList);
		splat = getSplat(requestList, matchedList);

		FormParserFactory.Builder builder = FormParserFactory.builder(false)
				.addParser(new FormEncodedDataDefinition());
		if (multiPart) {
			File temp = new File("/tmp/spark");
			if ((temp.exists() || temp.mkdirs()) && temp.canWrite()) {
				builder.addParser(new MultiPartParserDefinition(temp));
			}
			temp.deleteOnExit();
		}
		formParserFactory = builder.build();
	}

	private static Map<String, String> getParams(List<String> request, List<String> matched) {
		log.debug("get params");

		Map<String, String> params = new HashMap<>();

		for (int i = 0; (i < request.size()) && (i < matched.size()); i++) {
			String matchedPart = matched.get(i);
			if (SparkUtils.isParam(matchedPart)) {
				log.debug("matchedPart: "
						+ matchedPart
						+ " = "
						+ request.get(i));
				params.put(matchedPart.toLowerCase(), request.get(i));
			}
		}
		return Collections.unmodifiableMap(params);
	}

	private static List<String> getSplat(List<String> request, List<String> matched) {
		log.debug("get splat");

		int nbrOfRequestParts = request.size();
		int nbrOfMatchedParts = matched.size();

		boolean sameLength = (nbrOfRequestParts == nbrOfMatchedParts);

		List<String> splat = new ArrayList<>();

		for (int i = 0; (i < nbrOfRequestParts) && (i < nbrOfMatchedParts); i++) {
			String matchedPart = matched.get(i);

			if (SparkUtils.isSplat(matchedPart)) {

				StringBuilder splatParam = new StringBuilder(request.get(i));
				if (!sameLength && (i == (nbrOfMatchedParts - 1))) {
					for (int j = i + 1; j < nbrOfRequestParts; j++) {
						splatParam.append("/");
						splatParam.append(request.get(j));
					}
				}
				splat.add(splatParam.toString());
			}
		}
		return Collections.unmodifiableList(splat);
	}

	@Override
	public Map<String, String> params() {
		return Collections.unmodifiableMap(params);
	}

	@Override
	public String params(String param) {
		if (param == null) return null;

		if (param.startsWith(":")) {
			return params.get(param.toLowerCase()); // NOSONAR
		} else {
			return params.get(":" + param.toLowerCase()); // NOSONAR
		}
	}

	@Override
	public String[] splat() {
		return splat.toArray(new String[splat.size()]);
	}

	@Override
	public String requestMethod() {
		return exchange.getRequestMethod().toString();
	}

	@Override
	public String scheme() {
		return exchange.getRequestScheme();
	}

	@Override
	public String host() {
		return exchange.getHostName();
	}

	@Override
	public String userAgent() {
		return exchange.getRequestHeaders().getFirst(Headers.USER_AGENT);
	}

	@Override
	public int port() {
		return exchange.getHostPort();
	}

	@Override
	public String pathInfo() {
		return exchange.getRequestPath();
	}

	@Override
	public String servletPath() {
		return null;
	}

	@Override
	public String contextPath() {
		return null;
	}

	@Override
	public String url() {
		return exchange.getRequestURL();
	}

	@Override
	public String contentType() {
		return exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
	}

	@Override
	public String ip() {
		InetSocketAddress sourceAddress = exchange.getSourceAddress();
		if(sourceAddress == null) return "";
		InetAddress address = sourceAddress.getAddress();
		if(address == null) {
			//this is unresolved, so we just return the host name
			//not exactly spec, but if the name should be resolved then a PeerNameResolvingHandler should be used
			//and this is probably better than just returning null
			return sourceAddress.getHostString();
		}
		return address.getHostAddress();
	}

	@Override
	public String body() {
		if (body == null) readBody();
		return body;
	}

	@Override
	public byte[] bodyAsBytes() {
		if (bodyAsBytes == null) readBody();
		return bodyAsBytes;
	}

	private void readBody() {
		try {
			if (exchange.isRequestChannelAvailable()) {
				StreamSourceChannel requestChannel = exchange.getRequestChannel();
				Pooled<ByteBuffer> pooledBuffer = exchange.getConnection().getBufferPool().allocate();
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				try {
					ByteBuffer buffer = pooledBuffer.getResource();
					int read = Channels.readBlocking(requestChannel, buffer);
					while (read > 0) {
						buffer.flip();
						os.write(buffer.array());
						buffer.clear();
						read = Channels.readBlocking(requestChannel, buffer);
					}
				} catch (IOException e) {
					log.error("Error reading from channel", e);
					pooledBuffer.free();
					return;
				}
				bodyAsBytes = os.toByteArray();
				body = IOUtils.toString(new ByteArrayInputStream(bodyAsBytes));
				pooledBuffer.free();
			}
		} catch (Exception e) {
			log.warn("Exception when reading body", e);
		}
	}

	@Override
	public int contentLength() {
		return (int) exchange.getRequestContentLength();
	}

	@Override
	public String queryParams(final String queryParam) {
		Deque<String> param = exchange.getQueryParameters().get(queryParam);
		if (param == null) {
			if (exchange.getRequestMethod().equals(Methods.POST)) {
				final FormData parsedFormData = parseFormData();
				if (parsedFormData != null) {
					FormData.FormValue res = parsedFormData.getFirst(queryParam);
					if (res == null || res.isFile()) return null;
					else return res.getValue();
				}
			}
			return null;
		}
		return param.getFirst();
	}

	private FormData parseFormData() {
		if (parsedFormData == null) {
			if (readStarted) return null;
			readStarted = true;

			final FormDataParser parser = formParserFactory.createParser(exchange);
			if (parser == null) return null;
			try {
				return parsedFormData = parser.parseBlocking();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return parsedFormData;
	}

	@Override
	public Set<String> queryParams() {
		return exchange.getQueryParameters().keySet();
	}

	@Override
	public String headers(final String header) {
		return exchange.getRequestHeaders().getFirst(header);
	}

	@Override
	public Set<String> headers() {
		if (headers == null) {
			Collection<HttpString> headerNames = exchange.getRequestHeaders().getHeaderNames();
			headers = new HashSet<>(headerNames.size());
			headers.addAll(headerNames.stream().map(HttpString::toString).collect(Collectors.toList()));
		}
		return headers;
	}

	@Override
	public String queryString() {
		return exchange.getQueryString().isEmpty() ? null : exchange.getQueryString();
	}

	@Override
	public void attribute(final String attribute, final Object value) {
		if (attributes == null) attributes = new HashMap<>();
		attributes.put(attribute, value);
	}

	@Override
	public Object attribute(final String attribute) {
		if (attributes == null) return null;
		return attributes.get(attribute);
	}

	@Override
	public Set<String> attributes() {
		if (attributes == null) return null;
		return attributes.keySet();
	}

	@Override
	public HttpServletRequest raw() {
		return null;
	}

	@Override
	public QueryParamsMap queryMap() {
		if (queryMap == null) queryMap = new QueryParamsMap(exchange);
		return queryMap;
	}

	@Override
	public QueryParamsMap queryMap(final String key) {
		return queryMap().get(key);
	}

	@Override
	public Session session() {
		if (session == null) {
			session = adaptSession(Sessions.getOrCreateSession(exchange), true);
		}
		return session;
	}

	@Override
	public Session session(final boolean create) {
		if (session == null) {
			io.undertow.server.session.Session undertowSession = Sessions.getSession(exchange);
			if (undertowSession != null) return adaptSession(undertowSession, false);
			if (create) return adaptSession(Sessions.getOrCreateSession(exchange), true);
		}
		return session;
	}

	private Session adaptSession(io.undertow.server.session.Session undertowSession, boolean isNew) {
		return new Session(HttpSessionImpl.forSession(undertowSession, null, isNew));
	}

	@Override
	public Map<String, String> cookies() {
		if (cookies != null) return cookies;

		Map<String, Cookie> tmpCookies = exchange.getRequestCookies();
		if (tmpCookies.isEmpty()) return null;

		cookies = new HashMap<>(tmpCookies.size());

		for (Map.Entry<String, Cookie> entry : tmpCookies.entrySet()) {
			cookies.put(entry.getKey(), entry.getValue().getValue());
		}

		return cookies;
	}

	@Override
	public String cookie(final String name) {
		if (cookies != null) return cookies.get(name);
		Cookie cookie = exchange.getRequestCookies().get(name);
		if (cookie == null) return null;
		else return cookie.getValue();
	}

	@Override
	public String uri() {
		//we need the non-decoded string, which means we need to use exchange.getRequestURI()
		if (exchange.isHostIncludedInRequestURI()) {
			//we need to strip out the host part
			String uri = exchange.getRequestURI();
			int slashes = 0;
			for (int i = 0; i < uri.length(); ++i)
				if (uri.charAt(i) == '/' && ++slashes == 3)
					return uri.substring(i);
			return "/";
		} else
			return exchange.getRequestURI();
	}

	@Override
	public String protocol() {
		return exchange.getProtocol().toString();
	}

	public void changeMatch(RouteMatch match) {
		List<String> requestList = SparkUtils.convertRouteToList(match.getRequestURI());
		List<String> matchedList = SparkUtils.convertRouteToList(match.getMatchUri());

		params = getParams(requestList, matchedList);
		splat = getSplat(requestList, matchedList);
	}
}
