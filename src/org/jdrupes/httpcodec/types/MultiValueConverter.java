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

package org.jdrupes.httpcodec.types;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Implemented by converters that convert header fields with a list of values.
 * 
 * Minimal restrictions are imposed on the type used as container for the 
 * values. It must be {@link Iterable} to provide read access. A supplier
 * and a function for appending values provide the required write access.
 * 
 * @param <T> the container for the values
 * @param <V> the type of the values
 */
public interface MultiValueConverter<T extends Iterable<V>, V>
	extends Converter<T> {

	/**
	 * Returns the container supplier
	 * 
	 * @return the container supplier
	 */
	Supplier<T> containerSupplier();

	/**
	 * Returns the value adder
	 * 
	 * @return the value adder
	 */
	BiConsumer<T, V> valueAdder();

	/**
	 * Returns the value converter.
	 * 
	 * @return the value converter
	 */
	Converter<V> valueConverter();

	/**
	 * Return whether values should be converted to separate
	 * header fields in {@link Converter#asFieldValue(Object)}.
	 * 
	 * @return the value
	 */
	boolean separateValues();

}