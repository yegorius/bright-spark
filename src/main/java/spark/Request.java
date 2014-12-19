package spark;

import spark.route.RouteMatch;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;

public interface Request {

	void changeMatch(RouteMatch match);

	Map<String, String> params();

	String params(String param);

	String[] splat();

	String requestMethod();

	String scheme();

	String host();

	String userAgent();

	int port();

	String pathInfo();

	String servletPath();

	String contextPath();

	String url();

	String contentType();

	String ip();

	String body();

	byte[] bodyAsBytes();

	int contentLength();

	String queryParams(String queryParam);

	String headers(String header);

	Set<String> queryParams();

	Set<String> headers();

	String queryString();

	void attribute(String attribute, Object value);

	Object attribute(String attribute);

	Set<String> attributes();

	HttpServletRequest raw();

	QueryParamsMap queryMap();

	QueryParamsMap queryMap(String key);

	Session session();

	Session session(boolean create);

	Map<String, String> cookies();

	String cookie(String name);

	String uri();

	String protocol();
}
