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

package org.jdrupes.httpcodec;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * The general interface of an encoder.
 * 
 * @param <T> the type of the message header to be encoded
 * @param <D> the type of the message header decoded by the peer decoder
 */
public interface Encoder<T extends MessageHeader, D extends MessageHeader>
	extends Codec {

	/**
	 * Sets the peer decoder. Some decoder implementations need to know
	 * the state of the decoder or the last decoded message.
	 *
	 * @param decoder the decoder
	 * @return the encoder
	 */
	Encoder<T, D> setPeerDecoder(Decoder<D, T> decoder);
	
	/**
	 * Returns the type of the messages encoded by this encoder.
	 * 
	 * @return the value
	 */
	Class<T> encoding();
	
	/**
	 * Set the header of the message that is to be encoded. Must be invoked
	 * before the first invocation to any {@code encode} method for a given
	 * message.
	 * 
	 * @param messageHeader
	 *            the message header
	 */
	public void encode(T messageHeader);
	
	/**
	 * Encodes a message. First encodes the message header set by
	 * {@link #encode(MessageHeader)} and then (optionally) adds payload 
	 * data from {@code in}.
	 * 
	 * @param in
	 *            the body data
	 * @param out
	 *            the buffer to which data is written
	 * @param endOfInput
	 *            {@code true} if there is no input left beyond the data
	 *            currently in the {@code in} buffer (indicates end of body or
	 *            no body at all)
	 * @return the result
	 */
	public Result encode(Buffer in, ByteBuffer out,
	        boolean endOfInput)	;

	/**
	 * Convenience method for invoking
	 * {@link #encode(Buffer, ByteBuffer, boolean)} with an empty {@code in}
	 * buffer and {@code true}. Can be used to get the result of encoding a 
	 * message without body.
	 * 
	 * @param out
	 *            the buffer to which data is written
	 * @return the result
	 */
	public default Result encode(ByteBuffer out) {
		return encode(EMPTY_IN, out, true);
	}

	/**
	 * Returns the last message (header) encoded. 
	 * 
	 * @return the result
	 */
	public Optional<T> header();
	
}
