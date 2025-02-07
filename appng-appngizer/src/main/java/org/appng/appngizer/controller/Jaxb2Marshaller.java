/*
 * Copyright 2011-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.appng.appngizer.controller;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.oxm.MarshallingFailureException;

import com.sun.xml.bind.marshaller.CharacterEscapeHandler;

public class Jaxb2Marshaller extends org.springframework.oxm.jaxb.Jaxb2Marshaller implements CharacterEscapeHandler {

	private final String[] searchList = new String[] { "<", ">", "&" };
	private final String[] replacementList = new String[] { "&lt;", "&gt;", "&amp;" };

	@Override
	public boolean supports(Class<?> clazz) {
		return clazz.getPackage().getName().startsWith("org.appng.appngizer.model");
	}

	@Override
	protected void initJaxbMarshaller(Marshaller marshaller) throws JAXBException {
		super.initJaxbMarshaller(marshaller);
		try {
			marshaller.setProperty(CharacterEscapeHandler.class.getName(), this);
		} catch (PropertyException e) {
			throw new MarshallingFailureException("error setting CharacterEscapeHandler", e);
		}
	}

	public void escape(char[] buf, int start, int len, boolean isAttValue, Writer out) throws IOException {
		StringWriter buffer = new StringWriter();
		for (int i = start; i < start + len; i++) {
			buffer.write(buf[i]);
		}
		String value = buffer.toString();
		if (value.contains(StringUtils.CR) || value.contains(StringUtils.LF)) {
			out.write("<![CDATA[");
			out.write(value);
			out.write("]]>");
		} else {
			out.write(StringUtils.replaceEach(value, searchList, replacementList));
		}
	}

}
