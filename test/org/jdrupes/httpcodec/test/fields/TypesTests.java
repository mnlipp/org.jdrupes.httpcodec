package org.jdrupes.httpcodec.test.fields;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.jdrupes.httpcodec.protocols.http.HttpConstants.HttpProtocol;
import org.jdrupes.httpcodec.protocols.http.HttpField;
import org.jdrupes.httpcodec.protocols.http.HttpMessageHeader;
import org.jdrupes.httpcodec.protocols.http.HttpRequest;
import org.jdrupes.httpcodec.types.Converters;
import org.jdrupes.httpcodec.types.MediaRange;
import org.jdrupes.httpcodec.types.MediaType;

import static org.junit.Assert.*;
import org.junit.Test;

public class TypesTests {

	@Test
	public void testMediaTypeCreation() throws ParseException {
		MediaType media = MediaType.builder().setType("text", "html")
				.setParameter("charset", "utf-8").build();
		assertEquals("text", media.getTopLevelType());
		assertEquals("html", media.getSubtype());
		assertEquals("utf-8", media.getParameter("charset"));
		// from string
		media = Converters.MEDIA_TYPE.fromFieldValue("text/html; charset=utf-8");
		assertEquals("text", media.getTopLevelType());
		assertEquals("html", media.getSubtype());
		assertEquals("utf-8", media.getParameter("charset"));
	}

	@Test
	public void testMediaMatch() {
		MediaType typeHtmlUtf8 = MediaType.builder().setType("text", "html")
				.setParameter("charset", "utf-8").build();
		
		assertTrue(MediaRange.ALL_MEDIA.matches(typeHtmlUtf8));

		MediaRange text = MediaRange.builder().setType("text", "*")
				.setParameter("q", "0.5").build();
		assertTrue(text.matches(typeHtmlUtf8));
		
		MediaRange audio = MediaRange.builder().setType("audio", "*").build();
		assertFalse(audio.matches(typeHtmlUtf8));
		
		MediaRange textHtml = MediaRange.builder().setType("text", "html").build();
		assertTrue(textHtml.matches(typeHtmlUtf8));
		
		MediaRange textUtf8 = MediaRange.builder().setType("text", "*")
				.setParameter("charset", "utf-8").build();
		assertTrue(textUtf8.matches(typeHtmlUtf8));
		
		MediaRange textIso = MediaRange.builder().setType("text", "*")
				.setParameter("charset", "iso-8859-15").build();
		assertFalse(textIso.matches(typeHtmlUtf8));

		MediaRange special = MediaRange.builder().setType("*", "*")
				.setParameter("special", "true").build();
		assertFalse(special.matches(typeHtmlUtf8));
		
		MediaType typePlain = MediaType.builder().setType("text", "plain")
				.setParameter("charset", "utf-8").build();
		assertTrue(MediaRange.ALL_MEDIA.matches(typePlain));
		assertTrue(text.matches(typePlain));
		assertFalse(audio.matches(typePlain));
		assertFalse(textHtml.matches(typePlain));
		assertTrue(textUtf8.matches(typePlain));
		assertFalse(textIso.matches(typePlain));
	}
	
	@Test
	public void testAcceptSorting() throws ParseException, URISyntaxException {
		HttpMessageHeader hdr = new HttpRequest("GET", new URI("/"),
		        HttpProtocol.HTTP_1_1, false);
		hdr.setField(HttpField.ACCEPT,
				"text/plain; q=0.5, text/html, text/x-dvi; q=0.8, text/x-c");
		List<MediaRange> value = hdr.getValue(
				HttpField.ACCEPT, Converters.MEDIA_RANGE_LIST).get();
		Collections.sort(value);
		Iterator<MediaRange> itr = value.iterator();
		assertEquals("text/html", itr.next().toString());
		assertEquals("text/x-c", itr.next().toString());
		assertEquals("text/x-dvi; q=0.8", itr.next().toString());
		assertEquals("text/plain; q=0.5", itr.next().toString());

		// Second
		hdr.setField(HttpField.ACCEPT, "audio/*; q=0.2, audio/basic");
		value = hdr.getValue(
				HttpField.ACCEPT, Converters.MEDIA_RANGE_LIST).get(); 
		Collections.sort(value);
		itr = value.iterator();
		assertEquals("audio/basic", itr.next().toString());
		assertEquals("audio/*; q=0.2", itr.next().toString());
		
		// Third
		hdr.setField(HttpField.ACCEPT, 
				"text/*, text/plain, text/plain;format=flowed, */*");
		value = hdr.getValue(
				HttpField.ACCEPT, Converters.MEDIA_RANGE_LIST).get(); 
		Collections.sort(value);
		itr = value.iterator();
		assertEquals("text/plain; format=flowed", itr.next().toString());
		assertEquals("text/plain", itr.next().toString());
		assertEquals("text/*", itr.next().toString());
		assertEquals("*/*", itr.next().toString());
		
		// Fourth
		hdr.setField(HttpField.ACCEPT, 
				"text/plain; q=1; format=flowed, text/plain;format=flowed, "
				+ "*/*, */*, audio/*");
		value = hdr.getValue(
				HttpField.ACCEPT, Converters.MEDIA_RANGE_LIST).get(); 
		Collections.sort(value);
		itr = value.iterator();
		assertEquals("text/plain; q=1; format=flowed", itr.next().toString());
		assertEquals("text/plain; format=flowed", itr.next().toString());
		assertEquals("audio/*", itr.next().toString());
		assertEquals("*/*", itr.next().toString());
		assertEquals("*/*", itr.next().toString());
	}	
	
}
