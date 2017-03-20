/*
 * This file is part of the JDrupes non-blocking HTTP Codec
 * Copyright (C) 2016  Michael N. Lipp
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

import org.jdrupes.httpcodec.MessageHeader;

import static org.jdrupes.httpcodec.protocols.http.HttpConstants.*;

import org.jdrupes.httpcodec.protocols.http.fields.HttpField;
import org.jdrupes.httpcodec.protocols.http.fields.HttpStringField;
import org.jdrupes.httpcodec.protocols.http.fields.HttpStringListField;

/**
 * Represents an HTTP message header (either request or response).
 */
public abstract class HttpMessageHeader implements MessageHeader {

	private HttpProtocol httpProtocol;
	private Map<String,HttpField<?>> headers 
		= new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	private boolean messageHasBody;
	private Map<Class<?>, Method> fromStringMethods = new HashMap<>();

	/**
	 * Creates a new message header.
	 * 
	 * @param httpProtocol the HTTP protocol
	 * @param messageHasBody indicates that a body is expected after the header
	 */
	public HttpMessageHeader(HttpProtocol httpProtocol, boolean messageHasBody) {
		this.httpProtocol = httpProtocol;
		this.messageHasBody = messageHasBody;
	}

	/**
	 * Return the protocol.
	 * 
	 * @return the HTTP protocol
	 */
	public HttpProtocol getProtocol() {
		return httpProtocol;
	}

	/**
	 * Returns all header fields as unmodifiable map.
	 * 
	 * @return the headers
	 */
	public Map<String, HttpField<?>> fields() {
		return Collections.unmodifiableMap(headers);
	}
	
	/**
	 * Set a header field for the message.
	 * 
	 * @param value the header field's value
	 * @return the message header for easy chaining
	 */
	public HttpMessageHeader setField(HttpField<?> value) {
		headers.put(value.getName(), value);
		// Check some consistency rules
		if (value.getName().equalsIgnoreCase(HttpField.UPGRADE)) {
			computeIfAbsent(HttpStringListField.class, HttpField.CONNECTION,
					n -> new HttpStringListField(n))
				.appendIfNotContained(HttpField.UPGRADE);
		}
		return this;
	}

	/**
	 * Clear all headers.
	 * 
	 * @return the message header for easy chaining
	 */
	public HttpMessageHeader clearHeaders() {
		headers.clear();
		return this;
	}
	
	/**
	 * Removes a header field from the message.
	 * 
	 * @param name the header field's name
	 */
	public void removeField(String name) {
		headers.remove(name);
	}

	/**
	 * Returns the header field with the given type if it exists.
	 * 
	 * Header fields are provisionally parsed as 
	 * {@link HttpStringField}s. When an attempt is made to
	 * retrieve such a string field with its real type,
	 * it is automatically converted to the real type.
	 * 
	 * In order for the automatic conversion to take place,
	 * the requested type must declare a static method `fromString`
	 * with one or two parameters of type `String`. A method with
	 * one parameter is assumed to return a concrete field type,
	 * i.e. a type that has a predefined field name. It is invoked
	 * with the text to be parsed. A `fromString` method with two 
	 * parameters is invoked with the requested field name as first,
	 * and the text to be parsed as second parameter.
	 * 
	 * If the conversion fails, the field is considered ill-formatted 
	 * and handled as if it didn't exist.
	 * 
	 * Note that field type conversion may already occur while doing internal
	 * checks. This implies that not all fields can initially be
	 * accessed as {@link HttpStringField}s.   
	 * 
	 * @param <T> the header field class
	 * @param type the header field type
	 * @param name the field name
	 * @return the header field if it exists
	 */
	public <T extends HttpField<?>> Optional<T> 
		getField(Class<T> type, String name) {
		HttpField<?> field = headers.get(name);
		// Not found or type matches
		if (field == null || type.isAssignableFrom(field.getClass()) ) {
			return Optional.ofNullable(type.cast(field));
		}
		// Not a string field
		if (!(field instanceof HttpStringField)) {
			return Optional.empty();
		}
		// Try conversion...
		Method fromString = fromStringMethods.get(type);
		if (fromString == null) {
			for (Method m: type.getDeclaredMethods()) {
				if ("fromString".equals(m.getName())) {
					if (!Modifier.isStatic(m.getModifiers())) {
						continue;
					}
					Class<?>[] paramTypes = m.getParameterTypes();
					if (paramTypes.length == 1 
							&& paramTypes[0].isAssignableFrom(String.class)) {
						fromString = m;
						break;
					}
					if (paramTypes.length == 2
							&& paramTypes[0].isAssignableFrom(String.class)
							&& paramTypes[1].isAssignableFrom(String.class)) {
						fromString = m;
					}
				}
			}
			fromStringMethods.put(type, fromString);
		}
		if (fromString == null) {
			return Optional.empty();
		}
		try {
			T newField = null;
			if (fromString.getParameterTypes().length == 1) {
				@SuppressWarnings("unchecked")
				T result = (T)fromString.invoke(
						null, ((HttpStringField)field).getValue());
				newField = result;
			} else {
				@SuppressWarnings("unchecked")
				T result = (T)fromString.invoke(
						null, name, ((HttpStringField)field).getValue());
				newField = result;
			}
			removeField(name);
			setField(newField);
			return Optional.of(newField);
		} catch (IllegalAccessException | IllegalArgumentException
		        | InvocationTargetException e) {
			return Optional.empty();
		}
	}

	/**
	 * Convenience method for getting a field with a string value.
	 * 
	 * @param name the field name
	 * @return the header field if it exists
	 * @see #getField(Class, String)
	 */
	public Optional<HttpStringField> getStringField(String name) {
		return getField(HttpStringField.class, name);
	}
	
	/**
	 * Returns the header field with the given type, computing 
	 * and adding it if it doesn't exist.
	 * 
	 * @param <T> the header field class
	 * @param type the header field type
	 * @param name the field name
	 * @param computeFunction the function that computes the filed if
	 * it doesn't exist
	 * @return the header field if it exists
	 */
	public <T extends HttpField<?>> T computeIfAbsent(
			Class<T> type, String name, Function<String, T> computeFunction) {
		Optional<T> result = getField(type, name);
		if (result.isPresent()) {
			return result.get();
		}
		T value = computeFunction.apply(name);
		setField(value);
		return value;
	}
	
	/**
	 * Set the flag that indicates whether this header is followed by a body.
	 * 
	 * @param messageHasBody new value
	 * @return the message for easy chaining
	 */
	public MessageHeader setMessageHasBody(boolean messageHasBody) {
		this.messageHasBody = messageHasBody;
		return this;
	}
	
	/**
	 * Returns {@code true} if the header is followed by a body.
	 * 
	 * @return {@code true} if body data follows
	 */
	public boolean messageHasBody() {
		return messageHasBody;
	}

	/**
	 * Returns true if this is a final message. A message is
	 * final if the value of the `Connection` header field
	 * includes the value "`close`".
	 * 
	 * @return the result
	 */
	public boolean isFinal() {
		return getField(HttpStringListField.class, HttpField.CONNECTION)
				.map(f -> f.contains("close")).orElse(false);
	}
}
