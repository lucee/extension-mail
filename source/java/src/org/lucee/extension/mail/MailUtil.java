/**
 *
 * Copyright (c) 2014, the Railo Company Ltd. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either 
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public 
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 * 
 **/
package org.lucee.extension.mail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.IDN;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeUtility;
import javax.servlet.http.Cookie;

import lucee.commons.io.log.Log;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.PageContext;
import lucee.runtime.config.Config;
import lucee.runtime.config.ConfigWeb;
import lucee.runtime.exp.PageException;
import lucee.runtime.ext.function.BIF;
import lucee.runtime.type.Array;
import lucee.runtime.type.Struct;
import lucee.runtime.util.Cast;
import lucee.runtime.util.Operation;

public final class MailUtil {

	public static final Charset UTF8 = Charset.forName("UTF-8");
	private static final long TIMEOUT = 3000;
	public static final String SYSTEM_PROP_MAIL_SSL_PROTOCOLS = "mail.smtp.ssl.protocols";

	private static final Map<String, String> tokens = new ConcurrentHashMap<>();

	public static String encode(String text, String encoding) throws UnsupportedEncodingException {
		// print.ln(StringUtil.changeCharset(text,encoding));
		return MimeUtility.encodeText(text, encoding, "Q");
	}

	public static String decode(String text) throws UnsupportedEncodingException {
		return MimeUtility.decodeText(text);
	}

	public static InternetAddress toInternetAddress(Object emails)
			throws MailException, UnsupportedEncodingException, PageException {
		if (emails instanceof String) {
			return parseEmail(emails, null);
		}
		InternetAddress[] addresses = toInternetAddresses(emails);
		if (addresses != null && addresses.length > 0)
			return addresses[0];
		throw new MailException("invalid email address definition");// should never come to this!
	}

	public static InternetAddress[] toInternetAddresses(Object emails)
			throws MailException, UnsupportedEncodingException, PageException {

		if (emails instanceof InternetAddress[])
			return (InternetAddress[]) emails;

		else if (emails instanceof String)
			return fromList((String) emails);

		else if (CFMLEngineFactory.getInstance().getDecisionUtil().isArray(emails))
			return fromArray(CFMLEngineFactory.getInstance().getCastUtil().toArray(emails));

		else if (CFMLEngineFactory.getInstance().getDecisionUtil().isStruct(emails))
			return new InternetAddress[] { fromStruct(CFMLEngineFactory.getInstance().getCastUtil().toStruct(emails)) };

		else
			throw new MailException("e-mail definitions must be one of the following types [string,array,struct], not ["
					+ emails.getClass().getName() + "]");
	}

	private static InternetAddress[] fromArray(Array array)
			throws MailException, PageException, UnsupportedEncodingException {

		Iterator it = array.valueIterator();
		Object el;
		ArrayList<InternetAddress> pairs = new ArrayList();

		while (it.hasNext()) {
			el = it.next();
			if (CFMLEngineFactory.getInstance().getDecisionUtil().isStruct(el)) {

				pairs.add(fromStruct(CFMLEngineFactory.getInstance().getCastUtil().toStruct(el)));
			} else {

				InternetAddress addr = parseEmail(CFMLEngineFactory.getInstance().getCastUtil().toString(el), null);
				if (addr != null)
					pairs.add(addr);
			}
		}

		return pairs.toArray(new InternetAddress[pairs.size()]);
	}

	private static InternetAddress fromStruct(Struct sct) throws MailException, UnsupportedEncodingException {

		String name = CFMLEngineFactory.getInstance().getCastUtil().toString(sct.get("label", null), null);
		if (name == null)
			name = CFMLEngineFactory.getInstance().getCastUtil().toString(sct.get("name", null), null);

		String email = CFMLEngineFactory.getInstance().getCastUtil().toString(sct.get("email", null), null);
		if (email == null)
			email = CFMLEngineFactory.getInstance().getCastUtil().toString(sct.get("e-mail", null), null);
		if (email == null)
			email = CFMLEngineFactory.getInstance().getCastUtil().toString(sct.get("mail", null), null);

		if (Util.isEmpty(email))
			throw new MailException("missing e-mail definition in struct");

		if (name == null)
			name = "";

		return new InternetAddress(email, name);
	}

	private static InternetAddress[] fromList(String strEmails) throws MailException {

		if (Util.isEmpty(strEmails, true))
			return new InternetAddress[0];

		Array raw = CFMLEngineFactory.getInstance().getListUtil().listWithQuotesToArray(strEmails, ",;", "\"");

		Iterator<Object> it = raw.valueIterator();
		ArrayList<InternetAddress> al = new ArrayList();

		while (it.hasNext()) {

			InternetAddress addr = parseEmail(it.next());

			if (addr != null)
				al.add(addr);
		}

		return al.toArray(new InternetAddress[al.size()]);
	}

	/**
	 * returns true if the passed value is a in valid email address format
	 * 
	 * @param value
	 * @return
	 */
	public static boolean isValidEmail(Object value) {
		try {
			InternetAddress addr = parseEmail(value, null);
			if (addr != null) {
				String address = addr.getAddress();
				if (address.contains(".."))
					return false;
				int pos = address.indexOf('@');
				if (pos < 1 || pos == address.length() - 1)
					return false;
				String local = address.substring(0, pos);
				String domain = address.substring(pos + 1);
				if (local.length() > 64)
					return false; // local part may only be 64 characters
				if (domain.length() > 255)
					return false; // domain may only be 255 characters
				if (domain.charAt(0) == '.' || local.charAt(0) == '.' || local.charAt(local.length() - 1) == '.')
					return false;
				pos = domain.lastIndexOf('.');
				if (pos > 0 && pos < domain.length() - 2) { // test TLD to be at
					// least 2 chars all
					// alpha characters
					if (isAllAlpha(domain.substring(pos + 1)))
						return true;
					try {
						addr.validate();
						return true;
					} catch (AddressException e) {
					}
				}
			}
		} catch (Exception e) {

		}
		return false;
	}

	public static InternetAddress parseEmail(Object value) throws MailException {
		InternetAddress ia = parseEmail(value, null);
		if (ia != null)
			return ia;
		if (value instanceof CharSequence) {
			if (Util.isEmpty(value.toString()))
				return null;
			throw new MailException("[" + value + "] cannot be converted to an email address");
		}
		throw new MailException("input cannot be converted to an email address");
	}

	/**
	 * returns an InternetAddress object or null if the parsing fails. to be be used
	 * in multiple places.
	 * 
	 * @param value
	 * @return
	 */
	public static InternetAddress parseEmail(Object value, InternetAddress defaultValue) {
		String str = CFMLEngineFactory.getInstance().getCastUtil().toString(value, "");
		if (Util.isEmpty(str))
			return defaultValue;
		if (str.indexOf('@') > -1) {
			try {
				str = fixIDN(str);
				InternetAddress addr = new InternetAddress(str);
				// fixIDN( addr );
				return addr;
			} catch (AddressException ex) {
			}
		}
		return defaultValue;
	}

	/**
	 * converts IDN to ASCII if needed
	 * 
	 * @param addr
	 * @return
	 */
	public static String fixIDN(String addr) {
		int pos = addr.indexOf('@');
		if (pos > 0 && pos < addr.length() - 1) {
			String domain = addr.substring(pos + 1);
			if (!MailStringUtil.isAscii(domain)) {
				domain = IDN.toASCII(domain);
				return addr.substring(0, pos) + "@" + domain;
			}
		}
		return addr;
	}

	/**
	 * This method should be called when TLS is used to ensure that the supported
	 * protocols are set. Some servers, e.g. Outlook365, reject lists with older
	 * protocols so we only pass protocols that start with the prefix "TLS"
	 */
	public static void setSystemPropMailSslProtocols() {
		String protocols = Util.getSystemPropOrEnvVar(SYSTEM_PROP_MAIL_SSL_PROTOCOLS, "");
		if (protocols.isEmpty()) {
			List<String> supportedProtocols = SSLConnectionSocketFactoryImpl.getSupportedSslProtocols();
			protocols = supportedProtocols.stream().filter(el -> el.startsWith("TLS")).collect(Collectors.joining(" "));
			if (!protocols.isEmpty()) {
				System.setProperty(SYSTEM_PROP_MAIL_SSL_PROTOCOLS, protocols);
				Config config = CFMLEngineFactory.getInstance().getThreadConfig();
				if (config != null) {
					Log log = config.getLog("mail");
					if (log != null) {
						log.info("mail", "Lucee system property " + SYSTEM_PROP_MAIL_SSL_PROTOCOLS + " set to ["
								+ protocols + "]");
					}
				}
			}
		}
	}

	public static byte[] toBytes(InputStream is) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		CFMLEngineFactory.getInstance().getIOUtil().copy(is, baos, true, true);
		return baos.toByteArray();
	}

	public static String createToken(String prefix, String name) {
		String str = prefix + ":" + name;
		String lock = tokens.putIfAbsent(str, str);
		if (lock == null) {
			lock = str;
		}
		return lock;
	}

	public static void initCauseEL(Throwable e, Throwable cause) {
		if (cause == null || e == cause)
			return;

		// get current root cause
		Throwable tmp;
		int count = 100;
		do {
			if (--count <= 0)
				break; // in case cause point to a child
			tmp = e.getCause();
			if (tmp == null)
				break;
			if (tmp == cause)
				return;
			e = tmp;
		} while (true);

		if (e == cause)
			return;
		// attach to root cause
		try {
			e.initCause(cause);
		} catch (Exception ex) {
		}
	}

	public static boolean isAllAlpha(String str) {

		if (str == null)
			return false;

		for (int i = str.length() - 1; i >= 0; i--) {

			if (!Character.isLetter(str.charAt(i)))
				return false;
		}

		return true;
	}

	public static String[] splitFileName(String fileName) {
		int pos = fileName.lastIndexOf('.');
		if (pos == -1) {
			return new String[] { fileName };
		}
		return new String[] { fileName.substring(0, pos), fileName.substring(pos + 1) };
	}

	/**
	 * casts a list to int array with max range its used in CFIMAP/CFPOP tag with
	 * messageNumber attribute
	 * 
	 * @param list
	 *            list to cast
	 * @param delimiter
	 *            delimiter of the list
	 * @param maxRange
	 *            maximum range that element can
	 * @return int array
	 */
	public static int[] toIntArrayWithMaxRange(String list, char delimiter, int maxRange) {
		int len = list.length();
		List<Integer> array = new ArrayList<>();
		if (len == 0)
			return new int[0];
		int last = 0;
		int l;
		Cast caster = CFMLEngineFactory.getInstance().getCastUtil();
		for (int i = 0; i < len; i++) {
			if (list.charAt(i) == delimiter) {
				l = caster.toIntValue(list.substring(last, i).trim(), 0);
				if (l > 0 && l <= maxRange)
					array.add(l);

				last = i + 1;
			}
		}
		if (last < len) {
			l = caster.toIntValue(list.substring(last).trim(), 0);
			if (l > 0 && l <= maxRange)
				array.add(l);
		}

		int[] intArr = new int[array.size()];
		int index = 0;
		for (Integer i : array) {
			intArr[index++] = i.intValue();
		}
		return intArr;
	}

	public static int find(Array array, Object object) {
		int len = array.size();
		CFMLEngine eng = CFMLEngineFactory.getInstance();
		Operation op = eng.getOperationUtil();
		for (int i = 1; i <= len; i++) {
			Object tmp = array.get(i, null);
			try {
				if (tmp != null && op.compare(object, tmp) == 0)
					return i;
			} catch (PageException e) {
			}
		}
		return 0;
	}

	public static TemplateLine getCurrentContext(PageContext pc) {
		// StackTraceElement[] traces = new Exception().getStackTrace();
		StackTraceElement[] traces = Thread.currentThread().getStackTrace();
		int line = 0;
		String template;

		StackTraceElement trace = null;
		for (int i = 0; i < traces.length; i++) {
			trace = traces[i];
			template = trace.getFileName();
			if (trace.getLineNumber() <= 0 || template == null
					|| CFMLEngineFactory.getInstance().getResourceUtil().getExtension(template, "").equals("java"))
				continue;
			line = trace.getLineNumber();
			try {
				if (pc == null)
					pc = CFMLEngineFactory.getInstance().getThreadPageContext();
				if (pc != null) {
					CFMLEngine eng = CFMLEngineFactory.getInstance();
					BIF bif = eng.getClassUtil().loadBIF(pc, "lucee.runtime.functions.system.ExpandPath");

					template = eng.getCastUtil().toString(bif.invoke(pc, new Object[] { template }));
				}
			} catch (Exception e) {
				// TODO log
				// LogUtil.warn("system", e);
			} // optional step, so in case it fails we are still fine

			return new TemplateLine(template, line);
		}
		return null;
	}

	public static PageContext createPageContext(final ConfigWeb cw) throws PageException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		return createPageContext(cw, baos, "/", "", TIMEOUT);
	}

	private static PageContext createPageContext(final ConfigWeb cw, final OutputStream os, final String path,
			String qs, long timeout) throws PageException {
		try {
			CFMLEngine eng = CFMLEngineFactory.getInstance();
			Class<?> clazz = eng.getClassUtil().loadClass("lucee.runtime.thread.ThreadUtil");
			Class<?> clazzPairArray = eng.getClassUtil().loadClass("lucee.commons.lang.Pair[]");

			Method method = clazz.getMethod("createPageContext",
					new Class[] { ConfigWeb.class, OutputStream.class, String.class, String.class, String.class,
							Cookie[].class, clazzPairArray, byte[].class, clazzPairArray, Struct.class, boolean.class,
							long.class });

			return (PageContext) method.invoke(null,
					new Object[] { cw, os, "", path, qs, new Cookie[0], null, null, null, null, true, timeout });
		} catch (Exception e) {
			throw CFMLEngineFactory.getInstance().getCastUtil().toPageException(e);
		}
	}

	public static void releasePageContext(PageContext pc) {
		CFMLEngineFactory.getInstance().releasePageContext(pc, true);
	}

	public static String max(String content, int max, String dotDotDot) {
		if (content == null)
			return null;
		if (content.length() <= max)
			return content;

		return content.substring(0, max) + dotDotDot;
	}

	public static String collapseWhitespace(String str) {

		if (Util.isEmpty(str))
			return str;

		StringBuilder sb = new StringBuilder(str.length());
		boolean wasLastWs = false;
		char[] carr = str.trim().toCharArray();
		for (int i = 0; i < carr.length; i++) {
			if (Character.isWhitespace(carr[i])) { // TODO use whitespace check from Lucee core
													// (StringUtil.isWhitespace)
				if (wasLastWs)
					continue;

				sb.append(' ');
				wasLastWs = true;
			} else {
				sb.append(carr[i]);
				wasLastWs = false;
			}
		}

		return sb.toString();
	}

	public static class TemplateLine implements Serializable {

		private static final long serialVersionUID = 6610978291828389799L;

		public final String template;
		public final int line;

		public TemplateLine(String template, int line) {
			this.template = template;
			this.line = line;
		}

		public TemplateLine(String templateAndLine) {
			int index = templateAndLine.lastIndexOf(':');
			this.template = index == -1 ? templateAndLine : templateAndLine.substring(0, index);
			this.line = index == -1 ? 0
					: CFMLEngineFactory.getInstance().getCastUtil().toIntValue(templateAndLine.substring(index + 1), 0);
		}

		@Override
		public String toString() {
			if (line < 1)
				return template;
			return template + ":" + line;
		}

		public StringBuilder toString(StringBuilder sb) {
			if (line < 1)
				sb.append(template);
			else
				sb.append(template).append(':').append(line);
			return sb;
		}

		public String toString(PageContext pc, boolean contract)
				throws InstantiationException, IllegalAccessException, PageException {
			CFMLEngine eng = CFMLEngineFactory.getInstance();
			BIF bif = eng.getClassUtil().loadBIF(pc, "lucee.runtime.functions.system.ContractPath");

			if (line < 1)
				return contract ? eng.getCastUtil().toString(bif.invoke(pc, new Object[] { template })) : template;
			return (contract ? eng.getCastUtil().toString(bif.invoke(pc, new Object[] { template })) : template) + ":"
					+ line;
		}

		public Object toStruct() {
			Struct caller = CFMLEngineFactory.getInstance().getCreationUtil().createStruct(Struct.TYPE_LINKED);
			caller.setEL("template", template);
			caller.setEL("line", Double.valueOf(line));
			return caller;
		}
	}

}
