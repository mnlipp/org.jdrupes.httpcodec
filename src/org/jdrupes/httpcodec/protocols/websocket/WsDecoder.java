/*
 * This file is part of the JDrupes non-blocking HTTP Codec
 * Copyright (C) 2016, 2018  Michael N. Lipp
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

package org.jdrupes.httpcodec.protocols.websocket;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CoderResult;
import java.util.Optional;

import org.jdrupes.httpcodec.Decoder;
import org.jdrupes.httpcodec.Encoder;
import org.jdrupes.httpcodec.ProtocolException;
import org.jdrupes.httpcodec.util.ByteBufferUtils;
import org.jdrupes.httpcodec.util.OptimizedCharsetDecoder;

/**
 * The Websocket decoder.
 */
public class WsDecoder	extends WsCodec
	implements Decoder<WsFrameHeader, WsFrameHeader> {

	private static enum State { READING_HEADER, READING_LENGTH,
		READING_MASK, READING_PAYLOAD, READING_PING_DATA,
		READING_PONG_DATA, READING_CLOSE_DATA }
	
	private static enum Opcode { CONT_FRAME, TEXT_FRAME, BIN_FRAME,
		CON_CLOSE, PING, PONG;

		public static Opcode fromInt(int value) {
			switch (value) {
			case 0: return Opcode.CONT_FRAME;
			case 1: return Opcode.TEXT_FRAME;
			case 2: return Opcode.BIN_FRAME;
			case 8: return Opcode.CON_CLOSE;
			case 9: return Opcode.PING;
			case 10: return Opcode.PONG;
			}
			throw new IllegalArgumentException();
		}
	}
	
	private static Result.Factory resultFactory = new Result.Factory();
	
	private State state = State.READING_HEADER;
	private long bytesExpected = 2;
	private boolean dataMessageFinished = true;
	private int curHeaderHead = 0;
	private byte[] maskingKey = new byte[4];
	private int maskIndex;
	private long payloadLength = 0;
	private OptimizedCharsetDecoder charDecoder = null;
	private boolean receivedDataIsMasked;
	private WsFrameHeader receivedHeader = null;
	private WsFrameHeader reportedHeader = null;
	private ByteBuffer controlData = null;
	private CharBuffer controlChars = null;
	
	public Decoder<WsFrameHeader, WsFrameHeader> setPeerEncoder(
			Encoder<WsFrameHeader, WsFrameHeader> encoder) {
		linkClosingState((WsCodec)encoder);
		return this;
	}
	
	/**
	 * Returns the result factory for this codec.
	 * 
	 * @return the factory
	 */
	protected Result.Factory resultFactory() {
		return resultFactory;
	}
	
	/* (non-Javadoc)
	 * @see org.jdrupes.httpcodec.Decoder#decoding()
	 */
	@Override
	public Class<WsFrameHeader> decoding() {
		return WsFrameHeader.class;
	}

	private void expectNextFrame() {
		state = State.READING_HEADER;
		bytesExpected = 2;
		curHeaderHead = 0;
		payloadLength = 0;
		if (dataMessageFinished && charDecoder != null) {
			charDecoder.reset();
		}		
	}
	
	/* (non-Javadoc)
	 * @see org.jdrupes.httpcodec.Decoder#getHeader()
	 */
	@Override
	public Optional<WsFrameHeader> header() {
		return Optional.ofNullable(receivedHeader);
	}

	private Result createResult(boolean overflow, boolean underflow, 
				boolean closeConnection, WsFrameHeader response, 
				boolean responseOnly) {
		if (receivedHeader != null && receivedHeader != reportedHeader) {
			reportedHeader = receivedHeader;
			return resultFactory().newResult(overflow, underflow, 
					closeConnection, true, response, responseOnly);
		}
		return resultFactory().newResult(overflow, underflow, 
				closeConnection, false, response, responseOnly);
	}

	private Result createResult(boolean overflow, boolean underflow) {
		return createResult(overflow, underflow, false, null, false);
	}

	
	/* (non-Javadoc)
	 * @see RequestDecoder#decode(java.nio.ByteBuffer, java.nio.Buffer, boolean)
	 */
	@Override
	public Decoder.Result<WsFrameHeader> decode(ByteBuffer in, Buffer out, 
			boolean endOfInput) throws ProtocolException {
		Decoder.Result<WsFrameHeader> result = null;
		while (in.hasRemaining()) {
			switch (state) {
			case READING_HEADER:
				curHeaderHead = (curHeaderHead << 8) | (in.get() & 0xFF);
				if (--bytesExpected == 0) {
					// "Header head" is complete, retrieve some information
					receivedDataIsMasked = (curHeaderHead & 0x80) != 0;
					payloadLength = curHeaderHead & 0x7f;
					if (payloadLength == 126) {
						payloadLength = 0;
						bytesExpected = 2;
						state = State.READING_LENGTH;
						continue; // shortcut, no need to check result
					}
					if (payloadLength == 127) {
						payloadLength = 0;
						bytesExpected = 8;
						state = State.READING_LENGTH;
						continue; // shortcut, no need to check result
					}
					if (receivedDataIsMasked) {
						bytesExpected = 4;
						state = State.READING_MASK;
						continue; // shortcut, no need to check result
					}
					result = headerComplete();
					break;
				}
				break;
				
			case READING_LENGTH:
				payloadLength = (payloadLength << 8) | (in.get() & 0xff);
				if (--bytesExpected > 0) {
					continue; // shortcut, no need to check result
				}
				if (receivedDataIsMasked) {
					bytesExpected = 4;
					state = State.READING_MASK;
					continue; // shortcut, no need to check result
				}
				result = headerComplete();
				break;
				
			case READING_MASK:
				maskingKey[4 - (int)bytesExpected] = in.get();
				if (--bytesExpected > 0) {
					continue; // shortcut, no need to check result
				}
				maskIndex = 0;
				result = headerComplete();
				break;
				
			case READING_PAYLOAD:
				if (out == null) {
					return createResult(true, false);
				}
				int initiallyAvailable = in.remaining();
				CoderResult decRes = copyData(out, in,
				        bytesExpected > Integer.MAX_VALUE
			                ? Integer.MAX_VALUE : (int) bytesExpected, 
			            endOfInput);
				bytesExpected -= (initiallyAvailable - in.remaining());
				if (bytesExpected == 0) {
					expectNextFrame();
					if (dataMessageFinished) {
						result = createResult(false, false);
					}
					break;
				}
				return createResult(
				        (in.hasRemaining() && !out.hasRemaining())
				                || (decRes != null && decRes.isOverflow()),
				        !in.hasRemaining()
				                || (decRes != null && decRes.isUnderflow()));

			case READING_PING_DATA:
			case READING_PONG_DATA:
				initiallyAvailable = in.remaining();
				copyData(controlData, in, (int) bytesExpected, endOfInput);
				bytesExpected -= (initiallyAvailable - in.remaining());
				if (bytesExpected == 0) {
					controlData.flip();
					if (state == State.READING_PING_DATA) {
						receivedHeader = new WsPingFrame(controlData);
						result = createResult(false, !dataMessageFinished, 
							false, new WsPongFrame(controlData.duplicate()), true);
						expectNextFrame();
					} else {
						receivedHeader = new WsPongFrame(controlData);
						result = createResult(false, !dataMessageFinished);
						expectNextFrame();
					}
					controlData = null;
					return result;
				}
				return createResult(false, true);
				
			case READING_CLOSE_DATA:
				if (controlData.position() < 2) {
					if (receivedDataIsMasked) {
						controlData.put(
								(byte)(in.get() ^ maskingKey[maskIndex]));
						maskIndex = (maskIndex + 1) % 4;
					} else {
						controlData.put(in.get());
					}
					bytesExpected -= 1;
					if (bytesExpected == 0) {
						// Close frame with status code only
						expectNextFrame();
						return createCloseResult();
					}
					continue;
				}
				if (charDecoder == null) {
					charDecoder = new OptimizedCharsetDecoder(
					        Charset.forName("UTF-8").newDecoder());
				}
				initiallyAvailable = in.remaining();
				copyData(controlChars, in, (int) bytesExpected, endOfInput);
				bytesExpected -= (initiallyAvailable - in.remaining());
				if (bytesExpected == 0) {
					expectNextFrame();
					return createCloseResult();
				}
				return createResult(false, true);
			}
			if (result != null) {
				return result;
			}
		}
		return createResult(false, bytesExpected > 0);
	}

	private Decoder.Result<WsFrameHeader> headerComplete() {
		receivedHeader = null;
		reportedHeader = null;
		boolean finalFrame = isFinalFrame();
		if ((curHeaderHead >> 8 & 0x8) == 0) {
			// Not a control frame, update from FIN bit
			dataMessageFinished = finalFrame;
		}
		bytesExpected = payloadLength;
		Opcode opcode = Opcode.fromInt(curHeaderHead >> 8 & 0xf);
		switch (opcode) {
		case CONT_FRAME:
			if (bytesExpected == 0) {
				// kind of ridiculous
				expectNextFrame();
				return createResult(false, !finalFrame);
			}
			state = State.READING_PAYLOAD;
			return null;
		case TEXT_FRAME:
			if (charDecoder == null) {
				charDecoder = new OptimizedCharsetDecoder(
				        Charset.forName("UTF-8").newDecoder());
			}
			break;
		case PING:
			if (bytesExpected == 0) {
				expectNextFrame();
				receivedHeader = new WsPingFrame(null);
				return createResult(false, !dataMessageFinished, 
						false, new WsPongFrame(null), true);
			}
			controlData = ByteBuffer.allocate((int)bytesExpected);
			state = State.READING_PING_DATA;
			return null;
		case PONG:
			if (bytesExpected == 0) {
				expectNextFrame();
				receivedHeader = new WsPongFrame(null);
				return createResult(false, !dataMessageFinished);
			}
			controlData = ByteBuffer.allocate((int)bytesExpected);
			state = State.READING_PONG_DATA;
			return null;
		case CON_CLOSE:
			if (bytesExpected == 0) {
				expectNextFrame();
				return createCloseResult();
			}
			controlData = ByteBuffer.allocate(2);
			// upper limit (reached if each byte becomes a char)
			controlChars = CharBuffer.allocate((int)bytesExpected);
			state = State.READING_CLOSE_DATA;
			return null;
		default:
			break;
		}
		receivedHeader = new WsMessageHeader(opcode == Opcode.TEXT_FRAME,
				bytesExpected > 0);
		if (bytesExpected == 0) {
			expectNextFrame();
			return createResult(false, false);
		}
		state = State.READING_PAYLOAD;
		return null;
	}
	
	private Decoder.Result<WsFrameHeader> createCloseResult() {
		Integer status = null;
		if (controlData != null) {
			controlData.flip();
			status = 0;
			while (controlData.hasRemaining()) {
				status = (status << 8) | (controlData.get() & 0xff);
			}
			controlData = null;
			controlChars.flip();
			receivedHeader = new WsCloseFrame(status, controlChars);
			controlChars = null;
		} else {
			receivedHeader = new WsCloseFrame(status, null);
		}
		
		// Handle close status
		WsCloseResponse ctrlResponse = null;
		switch (closingState()) {
		case OPEN:
			setClosingState(ClosingState.CLOSE_RECEIVED);
			// fall through
		case CLOSE_RECEIVED:
			// Actually *sending* (i.e. encoding) the response will
			// advance the state furher.
			ctrlResponse = new WsCloseResponse(status);
			break;
		case CLOSE_SENT:
			// Was sent, is now received, cycle completed.
			setClosingState(ClosingState.CLOSED);
			break;
		case CLOSED:
			break;
		}
		// If received data is masked, we're on the server side.
		return createResult(false, false, 
				receivedDataIsMasked && closingState() == ClosingState.CLOSED, 
				ctrlResponse, false);
	}

	private boolean isFinalFrame() {
		return (curHeaderHead & 0x8000) != 0;
	}
	
	private CoderResult copyData(
			Buffer out, ByteBuffer in, int limit, boolean endOfInput) {
		if (out instanceof ByteBuffer) {
			if (!receivedDataIsMasked) {
				ByteBufferUtils.putAsMuchAsPossible((ByteBuffer) out, in, limit);
				return null;
			}
			while (limit > 0 && in.hasRemaining() && out.hasRemaining()) {
				((ByteBuffer) out).put(
						(byte)(in.get() ^ maskingKey[maskIndex]));
				maskIndex = (maskIndex + 1) % 4;
				limit -= 1;
			}
			return null;
		} 
		if (out instanceof CharBuffer) {
			if (receivedDataIsMasked) {
				ByteBuffer unmasked = ByteBuffer.allocate(1);
				CoderResult res = null;
				while (limit > 0 && in.hasRemaining() && out.hasRemaining()) {
					unmasked.put((byte)(in.get() ^ maskingKey[maskIndex]));
					maskIndex = (maskIndex + 1) % 4;
					limit -= 1;
					unmasked.flip();
					res = charDecoder.decode(unmasked, (CharBuffer)out, 
							!in.hasRemaining() && endOfInput);
					unmasked.clear();
				}
				return res;
			}
			int oldLimit = in.limit();
			try {
				if (in.remaining() > limit) {
					in.limit(in.position() + limit);
				}
				return charDecoder.decode(in, (CharBuffer)out, endOfInput);
			} finally {
				in.limit(oldLimit);
			}
		} else {
			throw new IllegalArgumentException(
			        "Only Byte- or CharBuffer are allowed.");
		}
	}

	/**
	 * Results from {@link WsDecoder} add no additional
	 * information to {@link org.jdrupes.httpcodec.Decoder.Result}. This
	 * class just provides a factory for creating concrete results.
	 * 
	 * The class is declared abstract to promote the usage of the factory
	 * method.
	 */
	public abstract static class Result
		extends Decoder.Result<WsFrameHeader> {

		protected Result(boolean overflow, boolean underflow,
		        boolean closeConnection, boolean headerCompleted,
		        WsFrameHeader response, boolean responseOnly) {
			super(overflow, underflow, closeConnection, headerCompleted, response,
			        responseOnly);
		}

		protected static class Factory 
			extends Decoder.Result.Factory<WsFrameHeader> {
			
			/**
			 * Create a new result.
			 * 
			 * @param overflow
			 *            {@code true} if the data didn't fit in the out buffer
			 * @param underflow
			 *            {@code true} if more data is expected
			 * @param closeConnection
			 *            {@code true} if the connection should be closed
			 * @param headerCompleted
			 *            {@code true} if the header has completely been decoded
			 * @param response
			 *            a response to send due to an error
			 * @param responseOnly
			 *            if the result includes a response this flag indicates
			 *            that no further processing besides sending the
			 *            response is required
			 * @return the result
			 */
			public Result newResult(boolean overflow, boolean underflow, 
					boolean closeConnection, boolean headerCompleted, 
					WsFrameHeader response, boolean responseOnly) {
				return new Result(overflow, underflow, closeConnection,
						headerCompleted, response, responseOnly) {
				};
			}
		}
	}
}
