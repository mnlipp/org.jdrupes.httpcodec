/*
 * This file is part of the JDrupes non-blocking HTTP Codec
 * Copyright (C) 2017 Michael N. Lipp
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

package org.jdrupes.httpcodec.types;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

public class MediaType extends MediaBase {

	/**
	 * Create a new object with the given type and subtype.
	 * 
	 * @param type the top-level type
	 * @param subtype the subtype
	 */
	public MediaType(String type, String subtype) {
		super(new MediaTypePair(type, subtype));
	}
	
	/**
	 * Create a new object with the given type, subtype and parameters.
	 * 
	 * @param type the top-level type
	 * @param subtype the subtype
	 * @param parameters the parameters
	 */
	public MediaType(String type, String subtype, 
			Map<String, String> parameters) {
		super(new MediaTypePair(type, subtype), parameters);
	}

	/**
	 * Create a new object with the given type and parameters.
	 * 
	 * @param type the type
	 * @param parameters the parameters
	 */
	public MediaType(MediaTypePair type, Map<String, String> parameters) {
		super(type, parameters);
	}

	/**
	 * Creates a new instance with values obtained from parsing
	 * the given text.
	 * 
	 * @param text the text
	 * @return the mime type
	 * @throws ParseException if the text is not well-formed
	 */
	public static MediaType fromString(String text) 
			throws ParseException {
		return Converters.MEDIA_TYPE_CONVERTER.fromFieldValue(text);
	}
	
	/**
	 * Creates a new builder for a media type.
	 * 
	 * @return the builder
	 */
	@SuppressWarnings("unchecked")
	public static Builder builder() {
		return new Builder();
	}
	
	/**
	 * A builder for the (immutable) parameterized type.
	 */
	public static class Builder 
		extends ParameterizedValue.Builder<MediaType, MediaTypePair> {

		private Builder() {
			super(new MediaType("text", "plain", new HashMap<>()));
		}
		
		@Override
		public MediaType build() {
			return (MediaType)super.build();
		}

		/**
		 * Sets the media type.
		 * 
		 * @param topLevelType the top level type
		 * @param subtype the subtype
		 * @return the builder for easy chaining
		 */
		public Builder setType(String topLevelType, String subtype) {
			setValue(new MediaTypePair(topLevelType, subtype));
			return this;
		}
	}
	
	public static class MediaTypeConverter
	        extends AbstractParamValueConverter<MediaTypePair>
	        implements Converter<MediaType> {

		public MediaTypeConverter() {
			super(new MediaTypePairConverter(),
			        Converters.UNQUOTE_ONLY_CONVERTER);
		}

		@Override
		public String asFieldValue(MediaType value) {
			return super.asFieldValue(value);
		}

		@Override
		public MediaType fromFieldValue(String text) throws ParseException {
			return super.fromFieldValue(text, MediaType::new);
		}
	}

}