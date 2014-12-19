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
package spark.webserver;

import java.io.IOException;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Response;

/**
 * Provides functionality for modifying the response
 *
 * @author Per Wendel
 */
public class ServletResponse implements Response {

    /**
     * The logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger(ServletResponse.class);

    private HttpServletResponse response;
    private String body;
	private boolean redirected;

	protected ServletResponse() {
        // Used by wrapper
    }

    ServletResponse(HttpServletResponse response) {
        this.response = response;
    }


    /**
     * Sets the status code for the
     *
     * @param statusCode the status code
     */
    @Override
    public void status(int statusCode) {
        response.setStatus(statusCode);
    }

    /**
     * Sets the content type for the response
     *
     * @param contentType the content type
     */
    @Override
    public void type(String contentType) {
        response.setContentType(contentType);
    }

    /**
     * Sets the body
     *
     * @param body the body
     */
    @Override
    public void body(String body) {
        this.body = body;
    }

    /**
     * returns the body
     *
     * @return the body
     */
    @Override
    public String body() {
        return this.body;
    }

    /**
     * @return the raw response object handed in by Jetty
     */
    @Override
    public HttpServletResponse raw() {
        return response;
    }

    /**
     * Trigger a browser redirect
     *
     * @param location Where to redirect
     */
    @Override
	public void redirect(String location) {
		redirected = true;
        if (LOG.isDebugEnabled()) {
            LOG.debug("Redirecting ({} {} to {}", "Found", HttpServletResponse.SC_FOUND, location);
        }
        try {
            response.sendRedirect(location);
        } catch (IOException ioException) {
            LOG.warn("Redirect failure", ioException);
        }
    }

    /**
     * Trigger a browser redirect with specific http 3XX status code.
     *
     * @param location       Where to redirect permanently
     * @param httpStatusCode the http status code
     */
    @Override
	public void redirect(String location, int httpStatusCode) {
		redirected = true;
        if (LOG.isDebugEnabled()) {
            LOG.debug("Redirecting ({} to {}", httpStatusCode, location);
        }
        response.setStatus(httpStatusCode);
        response.setHeader("Location", location);
        response.setHeader("Connection", "close");
        try {
            response.sendError(httpStatusCode);
        } catch (IOException e) {
            LOG.warn("Exception when trying to redirect permanently", e);
        }
    }

	@Override
	public boolean isRedirected() {
		return redirected;
	}

	/**
     * Adds/Sets a response header
     *
     * @param header the header
     * @param value  the value
     */
    @Override
    public void header(String header, String value) {
        response.addHeader(header, value);
    }

    /**
     * Adds not persistent cookie to the response.
     * Can be invoked multiple times to insert more than one cookie.
     *
     * @param name  name of the cookie
     * @param value value of the cookie
     */
    @Override
    public void cookie(String name, String value) {
        cookie(name, value, -1, false);
    }

    /**
     * Adds cookie to the response. Can be invoked multiple times to insert more than one cookie.
     *
     * @param name   name of the cookie
     * @param value  value of the cookie
     * @param maxAge max age of the cookie in seconds (negative for the not persistent cookie,
     *               zero - deletes the cookie)
     */
    @Override
    public void cookie(String name, String value, int maxAge) {
        cookie(name, value, maxAge, false);
    }

    /**
     * Adds cookie to the response. Can be invoked multiple times to insert more than one cookie.
     *
     * @param name    name of the cookie
     * @param value   value of the cookie
     * @param maxAge  max age of the cookie in seconds (negative for the not persistent cookie, zero - deletes the cookie)
     * @param secured if true : cookie will be secured
     *                zero - deletes the cookie)
     */
    @Override
    public void cookie(String name, String value, int maxAge, boolean secured) {
        cookie("", name, value, maxAge, secured);
    }

    /**
     * Adds cookie to the response. Can be invoked multiple times to insert more than one cookie.
     *
     * @param path    path of the cookie
     * @param name    name of the cookie
     * @param value   value of the cookie
     * @param maxAge  max age of the cookie in seconds (negative for the not persistent cookie, zero - deletes the cookie)
     * @param secured if true : cookie will be secured
     *                zero - deletes the cookie)
     */
    @Override
    public void cookie(String path, String name, String value, int maxAge, boolean secured) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath(path);
        cookie.setMaxAge(maxAge);
        cookie.setSecure(secured);
        response.addCookie(cookie);
    }

    /**
     * Removes the cookie.
     *
     * @param name name of the cookie
     */
    @Override
    public void removeCookie(String name) {
        Cookie cookie = new Cookie(name, "");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
