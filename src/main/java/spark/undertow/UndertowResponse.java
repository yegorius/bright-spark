package spark.undertow;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.util.CanonicalPathUtils;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Response;

import java.nio.charset.Charset;
import javax.servlet.http.HttpServletResponse;

/**
 * Author: Yegorius
 * Date:   19.12.14
 */
public class UndertowResponse implements Response {
	private static final Logger log = LoggerFactory.getLogger(UndertowResponse.class);

	private final HttpServerExchange exchange;
	private String body;
	private boolean redirected = false;

	private boolean charsetSet = false;
	private String contentType;
	private String charset;

	public UndertowResponse(HttpServerExchange exchange) {
		this.exchange = exchange;
	}

	@Override
	public void status(final int statusCode) {
		exchange.setResponseCode(statusCode);
	}

	// this code was copied and adapted from io.undertow.servlet.spec.HttpServletResponseImpl
	@Override
	public void type(final String type) {
		if (type == null || exchange.isResponseStarted()) return;
		contentType = type;
		int split = type.indexOf(";");
		if (split != -1) {
			int pos = type.indexOf("charset=");
			if (pos != -1) {
				int i = pos + "charset=".length();
				do {
					char c = type.charAt(i);
					if (c == ' ' || c == '\t' || c == ';') {
						break;
					}
					++i;
				} while (i < type.length());
				if (!exchange.isResponseStarted()) {
					charsetSet = true;
					//we only change the charset if the writer has not been retrieved yet
					this.charset = type.substring(pos + "charset=".length(), i);
					//it is valid for the charset to be enclosed in quotes
					if (this.charset.startsWith("\"") && this.charset.endsWith("\"") && this.charset.length() > 1) {
						this.charset = this.charset.substring(1, this.charset.length() - 1);
					}
				}
				int charsetStart = pos;
				while (type.charAt(--charsetStart) != ';' && charsetStart > 0) {
				}
				StringBuilder contentTypeBuilder = new StringBuilder();
				contentTypeBuilder.append(type.substring(0, charsetStart));
				if (i != type.length()) {
					contentTypeBuilder.append(type.substring(i));
				}
				contentType = contentTypeBuilder.toString();
			}
			//strip any trailing semicolon
			for (int i = contentType.length() - 1; i >= 0; --i) {
				char c = contentType.charAt(i);
				if (c == ' ' || c == '\t') {
					continue;
				}
				if (c == ';') {
					contentType = contentType.substring(0, i);
				}
				break;
			}
		}
		exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, getContentType());
	}

	@Override
	public void redirect(final String location) {
		redirected = true;
		log.debug("Redirecting ({} {} to {}", "Found", HttpServletResponse.SC_FOUND, location);

		if (exchange.isResponseStarted()) throw UndertowServletMessages.MESSAGES.responseAlreadyCommited();

		status(302);
		String realPath;
		if (location.contains("://")) {//absolute url
			exchange.getResponseHeaders().put(Headers.LOCATION, location);
		} else {
			if (location.startsWith("/")) {
				realPath = location;
			} else {
				String current = exchange.getRelativePath();
				int lastSlash = current.lastIndexOf("/");
				if (lastSlash != -1) {
					current = current.substring(0, lastSlash + 1);
				}
				realPath = CanonicalPathUtils.canonicalize(current + location);
			}
			String loc = exchange.getRequestScheme() + "://" + exchange.getHostAndPort() + realPath;
			exchange.getResponseHeaders().put(Headers.LOCATION, loc);
		}
	}

	@Override
	public void redirect(final String location, final int httpStatusCode) {
		redirected = true;
		log.debug("Redirecting ({} to {}", httpStatusCode, location);
		status(httpStatusCode);
		exchange.getResponseHeaders().put(Headers.LOCATION, location);
		exchange.getResponseHeaders().put(Headers.CONNECTION, "close");

		if (exchange.isResponseStarted()) throw UndertowServletMessages.MESSAGES.responseAlreadyCommited();

		type("text/html");
		body = String.format("<html><head><title>Error</title></head><body>%s</body></html>", StatusCodes.getReason(httpStatusCode));
	}

	// TODO: do we need this?
	private String getContentType() {
		if (contentType != null) {
			if (charsetSet) {
				return contentType + ";charset=" + getCharset();
			} else {
				return contentType;
			}
		}
		return null;
	}

	private String getCharset() {
		if (charset == null) return Charset.defaultCharset().name();
		return charset;
	}

	public void setCharset(String charset) {
		charsetSet = true;
		this.charset = charset;
		if (contentType != null) {
			exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, getContentType());
		}
	}
	// END HttpServletResponseImpl

	@Override
	public void body(final String body) {
		this.body = body;
	}

	@Override
	public String body() {
		return this.body;
	}

	@Override
	public HttpServletResponse raw() {
		return null;
	}

	@Override
	public boolean isRedirected() {
		return redirected;
	}

	@Override
	public void header(final String header, final String value) {
		exchange.getResponseHeaders().add(new HttpString(header), value);
	}

	@Override
	public void cookie(final String name, final String value) {
		cookie(name, value, -1, false);
	}

	@Override
	public void cookie(final String name, final String value, final int maxAge) {
		cookie(name, value, maxAge, false);
	}

	@Override
	public void cookie(final String name, final String value, final int maxAge, final boolean secured) {
		cookie("", name, value, maxAge, secured);
	}

	@Override
	public void cookie(final String path, final String name, final String value, final int maxAge, final boolean secured) {
		Cookie cookie = new CookieImpl(name, value);
		cookie.setMaxAge(maxAge);
		cookie.setSecure(secured);
		cookie.setPath(path);
		exchange.setResponseCookie(cookie);
	}

	@Override
	public void removeCookie(final String name) {
		Cookie cookie = new CookieImpl(name, "");
		cookie.setMaxAge(0);
		exchange.setResponseCookie(cookie);
	}
}
