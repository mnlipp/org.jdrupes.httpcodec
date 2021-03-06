/*
 * This file is part of the JDrupes non-blocking HTTP Codec
 * Copyright (C) 2016, 2017  Michael N. Lipp
 *
 * This program is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 3 of the License, or 
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public 
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along 
 * with this program; if not, see <http://www.gnu.org/licenses/>.
 */

package org.jdrupes.httpcodec.protocols.http;

import org.jdrupes.httpcodec.ProtocolException;

import static org.jdrupes.httpcodec.protocols.http.HttpConstants.*;

/**
 * Represents a violation of the HTTP protocol. This kind of exception
 * is thrown by the HTTP codecs when a problem is detected while encoding
 * or decoding a message.
 */
public class HttpProtocolException extends ProtocolException {

	private static final long serialVersionUID = 1L;

	private HttpProtocol httpProtocol;
	private int statusCode;
	private String reasonPhrase;

	/**
	 * Creates a new exception with the given values.
	 * 
	 * @param httpProtocol the HTTP version
	 * @param statusCode the status code
	 * @param reasonPhrase the reason phrase
	 */
	public HttpProtocolException(HttpProtocol httpProtocol, int statusCode,
	        String reasonPhrase) {
		super(String.format("%03d %s", statusCode, reasonPhrase));
		this.httpProtocol = httpProtocol;
		this.statusCode = statusCode;
		this.reasonPhrase = reasonPhrase;
	}

	/**
	 * Creates a new exception with the standard reason phrase.
	 * 
	 * @param httpProtocol the HTTP version
	 * @param status the status
	 */
	public HttpProtocolException(HttpProtocol httpProtocol, HttpStatus status) {
		super(String.format("%03d %s", status.statusCode(),
				status.reasonPhrase()));
		this.httpProtocol = httpProtocol;
		this.statusCode = status.statusCode();
		this.reasonPhrase = status.reasonPhrase();
	}
	
	/**
	 * Returns the HTTP version.
	 * 
	 * @return the HTTP Version
	 */
	public HttpProtocol httpVersion() {
		return httpProtocol;
	}
	
	/**
	 * Returns the status code.
	 * 
	 * @return the statusCode
	 */
	public int statusCode() {
		return statusCode;
	}

	/**
	 * Returns the reason phrase.
	 * 
	 * @return the reasonPhrase
	 */
	public String reasonPhrase() {
		return reasonPhrase;
	}
	
}
