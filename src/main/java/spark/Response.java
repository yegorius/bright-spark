package spark;

import javax.servlet.http.HttpServletResponse;

public interface Response {
	void status(int statusCode);

	void type(String contentType);

	void body(String body);

	String body();

	HttpServletResponse raw();

	void redirect(String location);

	void redirect(String location, int httpStatusCode);

	boolean isRedirected();

	void header(String header, String value);

	void cookie(String name, String value);

	void cookie(String name, String value, int maxAge);

	void cookie(String name, String value, int maxAge, boolean secured);

	void cookie(String path, String name, String value, int maxAge, boolean secured);

	void removeCookie(String name);
}
