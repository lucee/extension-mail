/**
 * Copyright (c) 2014, the Railo Company Ltd.
 * Copyright (c) 2015, Lucee Association Switzerland
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
 */
package org.lucee.extension.mail.smtp;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.lang.ref.SoftReference;
import java.nio.charset.Charset;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

import javax.activation.DataHandler;
import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimePart;

import org.apache.commons.mail.DefaultAuthenticator;
import org.lucee.extension.mail.MailException;
import org.lucee.extension.mail.MailPart;
import org.lucee.extension.mail.MailUtil;
import org.lucee.extension.mail.ReflectionUtil;
import org.lucee.extension.mail.proxy.ProxyDataImpl;
import org.lucee.extension.mail.smtp.SMTPConnectionPool.SessionAndTransport;
import org.lucee.extension.mail.spooler.CFMLSpoolerTaskListener;
import org.lucee.extension.mail.spooler.ComponentSpoolerTaskListener;
import org.lucee.extension.mail.spooler.MailSpoolerTask;
import org.lucee.extension.mail.spooler.UDFSpoolerTaskListener;

import com.sun.mail.smtp.SMTPMessage;

import lucee.commons.io.log.Log;
import lucee.commons.io.res.Resource;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.Component;
import lucee.runtime.PageContext;
import lucee.runtime.config.Config;
import lucee.runtime.config.ConfigWeb;
import lucee.runtime.exp.PageException;
import lucee.runtime.net.mail.Server;
import lucee.runtime.net.proxy.ProxyData;
import lucee.runtime.spooler.SpoolerTask;
import lucee.runtime.type.Struct;
import lucee.runtime.type.UDF;
import lucee.runtime.util.ListUtil;
import lucee.runtime.util.ResourceUtil;

public final class SMTPClient implements Serializable {

	private static final long serialVersionUID = 5227282806519740328L;

	private static final int SPOOL_UNDEFINED = 0;
	private static final int SPOOL_YES = 1;
	private static final int SPOOL_NO = 2;

	private static final int SSL_NONE = 0;
	private static final int SSL_YES = 1;
	private static final int SSL_NO = 2;

	private static final int TLS_NONE = 0;
	private static final int TLS_YES = 1;
	private static final int TLS_NO = 2;

	private static final String TEXT_HTML = "text/html";
	private static final String TEXT_PLAIN = "text/plain";
	private static final String MESSAGE_ID = "Message-ID";
	// private static final SerializableObject LOCK = new SerializableObject();

	private static Map<TimeZone, SoftReference<DateTimeFormatter>> formatters = new ConcurrentHashMap<>();
	// private static final int PORT = 25;

	private int spool = SPOOL_UNDEFINED;

	private int timeout = -1;

	private String plainText;
	private Charset plainTextCharset;

	private String htmlText;
	private Charset htmlTextCharset;

	private Attachment[] attachmentz;

	private String[] host;
	private Charset charset = MailUtil.UTF8;
	private InternetAddress from;
	private InternetAddress[] tos;
	private InternetAddress[] bccs;
	private InternetAddress[] ccs;
	private InternetAddress[] rts;
	private InternetAddress[] fts;
	private String subject = "";
	private String xmailer = "Lucee Mail";
	private Map<String, String> headers = new HashMap<String, String>();
	private int port = -1;

	private String username;
	private String password = "";

	private int ssl = SSL_NONE;
	private int tls = TLS_NONE;

	ProxyData proxyData = new ProxyDataImpl();
	private ArrayList<MailPart> parts;

	private TimeZone timeZone;
	private long lifeTimespan = 100 * 60 * 5;
	private long idleTimespan = 100 * 60 * 1;

	private Object listener;

	private boolean debug;
	private int priority = 0;

	public static String getNow(TimeZone tz) {
		if (tz == null) {
			tz = CFMLEngineFactory.getInstance().getThreadTimeZone();
		}
		SoftReference<DateTimeFormatter> tmp = formatters.get(tz);
		DateTimeFormatter df = tmp == null ? null : tmp.get();
		if (df == null) {

			df = DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z (z)");
			df = df.withLocale(Locale.US);
			formatters.put(tz, new SoftReference<DateTimeFormatter>(df));
		}
		Date date = new Date();

		return date.toInstant()
				.atZone(ReflectionUtil
						.DateTimeUtil_toOffsetIfNeeded(tz != null ? tz.toZoneId() : ZoneId.systemDefault(), date))
				.format(df);
	}

	public void setSpoolenable(boolean spoolenable) {
		spool = spoolenable ? SPOOL_YES : SPOOL_NO;
	}

	/**
	 * set port of the mailserver
	 * 
	 * @param port
	 */
	public void setPort(int port) {
		this.port = port;
	}

	/**
	 * enable console logging of the mail session to console
	 * 
	 * @param debug
	 */
	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	/**
	 * set the mail priority
	 * 
	 * @param priority
	 */
	public void setPriority(int priority) {
		this.priority = priority;
	}

	/**
	 * @param charset
	 *            the charset to set
	 */
	public void setCharset(Charset charset) {
		this.charset = charset;
	}

	public static Server toServerImpl(String server, int port, String usr, String pwd, long lifeTimespan,
			long idleTimespan) throws MailException {
		int index;

		// username/password
		index = server.indexOf('@');
		if (index != -1) {
			usr = server.substring(0, index);
			server = server.substring(index + 1);
			index = usr.indexOf(':');
			if (index != -1) {
				pwd = usr.substring(index + 1);
				usr = usr.substring(0, index);
			}
		}

		// port
		index = server.indexOf(':');
		if (index != -1) {
			try {
				port = CFMLEngineFactory.getInstance().getCastUtil().toIntValue(server.substring(index + 1));
			} catch (PageException e) {
				throw new MailException(e.getMessage());
			}
			server = server.substring(0, index);
		}

		Server srv = ServerPro.getInstance(server, port, usr, pwd, lifeTimespan, idleTimespan, false, false);
		return srv;
	}

	public void setHost(String host) throws PageException {
		if (!Util.isEmpty(host, true)) {
			ListUtil util = CFMLEngineFactory.getInstance().getListUtil();
			this.host = util.toStringArray(util.toArrayRemoveEmpty(host, ","));
		}
	}

	public void setLifeTimespan(long life) {
		this.lifeTimespan = life;
	}

	public void setIdleTimespan(long idle) {
		this.idleTimespan = idle;
	}

	/**
	 * @param password
	 *            the password to set
	 */
	public void setPassword(String password) {
		this.password = password;
	}

	/**
	 * @param username
	 *            the username to set
	 */
	public void setUsername(String username) {
		this.username = username;
	}

	public void addHeader(String name, String value) {
		headers.put(name, value);
	}

	public void addTo(InternetAddress to) {
		tos = add(tos, to);
	}

	public void setTos(InternetAddress[] tos) {
		this.tos = tos;
	}

	public void addTo(Object to) throws UnsupportedEncodingException, PageException, MailException {
		InternetAddress[] tmp = MailUtil.toInternetAddresses(to);
		for (int i = 0; i < tmp.length; i++) {
			addTo(tmp[i]);
		}
	}

	public void setFrom(InternetAddress from) {
		this.from = from;
	}

	public boolean setFrom(Object from) throws UnsupportedEncodingException, MailException, PageException {
		InternetAddress[] addrs = MailUtil.toInternetAddresses(from);
		if (addrs.length == 0)
			return false;
		setFrom(addrs[0]);
		return true;
	}

	public void addBCC(InternetAddress bcc) {
		bccs = add(bccs, bcc);
	}

	public void setBCCs(InternetAddress[] bccs) {
		this.bccs = bccs;
	}

	public void addBCC(Object bcc) throws UnsupportedEncodingException, MailException, PageException {
		InternetAddress[] tmp = MailUtil.toInternetAddresses(bcc);
		for (int i = 0; i < tmp.length; i++) {
			addBCC(tmp[i]);
		}
	}

	public void addCC(InternetAddress cc) {
		ccs = add(ccs, cc);
	}

	public void setCCs(InternetAddress[] ccs) {
		this.ccs = ccs;
	}

	public void addCC(Object cc) throws UnsupportedEncodingException, MailException, PageException {
		InternetAddress[] tmp = MailUtil.toInternetAddresses(cc);
		for (int i = 0; i < tmp.length; i++) {
			addCC(tmp[i]);
		}
	}

	public void addReplyTo(InternetAddress rt) {
		rts = add(rts, rt);
	}

	public void setReplyTos(InternetAddress[] rts) {
		this.rts = rts;
	}

	public void addReplyTo(Object rt) throws UnsupportedEncodingException, MailException, PageException {
		InternetAddress[] tmp = MailUtil.toInternetAddresses(rt);
		for (int i = 0; i < tmp.length; i++) {
			addReplyTo(tmp[i]);
		}
	}

	public void addFailTo(InternetAddress ft) {
		fts = add(fts, ft);
	}

	public void setFailTos(InternetAddress[] fts) {
		this.fts = fts;
	}

	public String getHTMLTextAsString() {
		return htmlText;
	}

	public String getPlainTextAsString() {
		return plainText;
	}

	public void addFailTo(Object ft) throws UnsupportedEncodingException, MailException, PageException {
		InternetAddress[] tmp = MailUtil.toInternetAddresses(ft);
		for (int i = 0; i < tmp.length; i++) {
			addFailTo(tmp[i]);
		}
	}

	/**
	 * @param timeout
	 *            the timeout to set
	 */
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public void setXMailer(String xmailer) {
		this.xmailer = xmailer;
	}

	public void setListener(Object listener) throws PageException {
		if (!(listener instanceof UDF) && !(listener instanceof Component) && !dblUDF(listener))
			throw CFMLEngineFactory.getInstance().getExceptionUtil()
					.createApplicationException("Listener must be a Function or a Component.");
		this.listener = listener;
	}

	private boolean dblUDF(Object o) {
		if (!(o instanceof Struct))
			return false;
		Struct sct = (Struct) o;
		return sct.get("before", null) instanceof UDF || sct.get("after", null) instanceof UDF; // we need "before" OR
																								// "after"!
	}

	/**
	 * creates a new expanded array and return it;
	 * 
	 * @param oldArr
	 * @param newValue
	 * @return new expanded array
	 */
	protected static InternetAddress[] add(InternetAddress[] oldArr, InternetAddress newValue) {
		if (oldArr == null)
			return new InternetAddress[] { newValue };
		// else {
		InternetAddress[] tmp = new InternetAddress[oldArr.length + 1];
		for (int i = 0; i < oldArr.length; i++) {
			tmp[i] = oldArr[i];
		}
		tmp[oldArr.length] = newValue;
		return tmp;
		// }
	}

	protected static Attachment[] add(Attachment[] oldArr, Attachment newValue) {
		if (oldArr == null)
			return new Attachment[] { newValue };
		// else {
		Attachment[] tmp = new Attachment[oldArr.length + 1];
		for (int i = 0; i < oldArr.length; i++) {
			tmp[i] = oldArr[i];
		}
		tmp[oldArr.length] = newValue;
		return tmp;
		// }
	}

	public static class MimeMessageAndSession {
		public final MimeMessage message;
		public final SessionAndTransport session;
		public final String messageId;

		public MimeMessageAndSession(MimeMessage message, SessionAndTransport session, String messageId) {
			this.message = message;
			this.session = session;
			this.messageId = messageId;
		}
	}

	private MimeMessageAndSession createMimeMessage(lucee.runtime.config.Config config, String hostName, int port,
			String username, String password, long lifeTimesan, long idleTimespan, boolean tls, boolean ssl,
			boolean sendPartial, boolean newConnection, boolean userset) throws MessagingException {

		Properties props = (Properties) System.getProperties().clone();
		String strTimeout = CFMLEngineFactory.getInstance().getCastUtil().toString(getTimeout(config));

		props.put("mail.smtp.host", hostName);
		props.put("mail.smtp.timeout", strTimeout);
		props.put("mail.smtp.connectiontimeout", strTimeout);
		props.put("mail.smtp.sendpartial", CFMLEngineFactory.getInstance().getCastUtil().toString(sendPartial));
		props.put("mail.smtp.userset", userset);

		if (port > 0) {
			props.put("mail.smtp.port", CFMLEngineFactory.getInstance().getCastUtil().toString(port));
		}
		if (ssl) {
			props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");/* JAVJAK */
			props.put("mail.smtp.socketFactory.port", CFMLEngineFactory.getInstance().getCastUtil().toString(port));
			props.put("mail.smtp.socketFactory.fallback", "false");
		} else {
			props.put("mail.smtp.socketFactory.class", "javax.net.SocketFactory");/* JAVJAK */
			props.remove("mail.smtp.socketFactory.port");
			props.remove("mail.smtp.socketFactory.fallback");
		}
		Authenticator auth = null;
		if (!Util.isEmpty(username)) {
			props.put("mail.smtp.auth", "true");
			props.put("mail.smtp.starttls.enable", tls ? "true" : "false");

			props.put("mail.smtp.user", username);
			props.put("mail.smtp.password", password);
			props.put("password", password);
			auth = new DefaultAuthenticator(username, password);
		} else {
			props.put("mail.smtp.auth", "false");
			props.remove("mail.smtp.starttls.enable");

			props.remove("mail.smtp.user");
			props.remove("mail.smtp.password");
			props.remove("password");
		}

		SessionAndTransport sat = newConnection
				? new SessionAndTransport(hash(props), props, auth, lifeTimesan, idleTimespan)
				: SMTPConnectionPool.getSessionAndTransport(props, hash(props), auth, lifeTimesan, idleTimespan);

		if (debug)
			sat.session.setDebug(true); // enable logging mail debug output to console

		// Contacts
		SMTPMessage msg = new SMTPMessage(sat.session);
		if (from == null)
			throw new MessagingException("A [from] email address is required to send an email");
		// if(tos==null)throw new MessagingException("A [to] email address is required
		// to send an email");

		checkAddress(from, charset);
		// checkAddress(tos,charset);

		msg.setFrom(from);
		// msg.setRecipients(Message.RecipientType.TO, tos);

		if (tos != null) {
			checkAddress(tos, charset);
			msg.setRecipients(Message.RecipientType.TO, tos);
		}
		if (ccs != null) {
			checkAddress(ccs, charset);
			msg.setRecipients(Message.RecipientType.CC, ccs);
		}
		if (bccs != null) {
			checkAddress(bccs, charset);
			msg.setRecipients(Message.RecipientType.BCC, bccs);
		}
		if (rts != null) {
			checkAddress(rts, charset);
			msg.setReplyTo(rts);
		}
		if (fts != null) {
			checkAddress(fts, charset);
			msg.setEnvelopeFrom(fts[0].toString());
		}

		// Subject and headers
		try {
			msg.setSubject(MailUtil.encode(subject, charset.name()));
		} catch (UnsupportedEncodingException e) {
			throw new MessagingException("the encoding " + charset + " is not supported");
		}
		msg.setHeader("X-Mailer", xmailer);

		if (priority > 0)
			msg.setHeader("X-Priority", CFMLEngineFactory.getInstance().getCastUtil().toString(priority));

		msg.setHeader("Date", getNow(timeZone));

		String messageId = getMessageId(headers); // Message-Id needs to be set after calling message.saveChanges();

		Multipart mp = null;

		// only Plain
		if (Util.isEmpty(htmlText)) {
			if (isEmpty(attachmentz) && isEmpty(parts)) {
				fillPlainText(config, msg);
				setHeaders(msg, headers);
				return new MimeMessageAndSession(msg, sat, messageId);
			}
			mp = new MimeMultipart("mixed");
			mp.addBodyPart(getPlainText(config));
		}
		// Only HTML
		else if (Util.isEmpty(plainText)) {
			if (isEmpty(attachmentz) && isEmpty(parts)) {
				fillHTMLText(config, msg);
				setHeaders(msg, headers);
				return new MimeMessageAndSession(msg, sat, messageId);
			}
			mp = new MimeMultipart("mixed");
			mp.addBodyPart(getHTMLText(config));
		}

		// Plain and HTML
		else {
			mp = new MimeMultipart("alternative");
			mp.addBodyPart(getPlainText(config));
			mp.addBodyPart(getHTMLText(config));// this need to be last

			if (!isEmpty(attachmentz) || !isEmpty(parts)) {
				MimeBodyPart content = new MimeBodyPart();
				content.setContent(mp);
				mp = new MimeMultipart("mixed");
				mp.addBodyPart(content);
			}
		}
		/*
		 * - mixed -- alternative --- text --- related ---- html ---- inline image ----
		 * inline image -- attachment -- attachment
		 * 
		 */

		// parts
		if (!isEmpty(parts)) {
			Iterator<MailPart> it = parts.iterator();
			if (mp instanceof MimeMultipart)
				((MimeMultipart) mp).setSubType("alternative");
			while (it.hasNext()) {
				mp.addBodyPart(toMimeBodyPart(config, it.next()));
			}
		}

		// Attachments
		if (!isEmpty(attachmentz)) {
			for (int i = 0; i < attachmentz.length; i++) {
				mp.addBodyPart(toMimeBodyPart(mp, config, attachmentz[i]));
			}
		}
		msg.setContent(mp);
		setHeaders(msg, headers);

		return new MimeMessageAndSession(msg, sat, messageId);
	}

	public static boolean isEmpty(Object[] arr) {
		return arr == null || arr.length == 0;
	}

	public static boolean isEmpty(List list) {
		return list == null || list.size() == 0;
	}

	private static String hash(Properties props) {
		Enumeration<?> e = props.propertyNames();
		java.util.List<String> names = new ArrayList<String>();
		String str;
		while (e.hasMoreElements()) {
			str = CFMLEngineFactory.getInstance().getCastUtil().toString(e.nextElement(), null);
			if (!Util.isEmpty(str) && str.startsWith("mail.smtp."))
				names.add(str);

		}
		Collections.sort(names);
		StringBuilder sb = new StringBuilder();
		Iterator<String> it = names.iterator();
		while (it.hasNext()) {
			str = it.next();
			sb.append(str).append(':').append(props.getProperty(str)).append(';');
		}
		str = sb.toString();
		try {
			return CFMLEngineFactory.getInstance().getSystemUtil().hashMd5(str);
		} catch (Exception e1) {
			return str;
		}
	}

	private static void setHeaders(SMTPMessage msg, Map<String, String> headers) throws MessagingException {
		Iterator<Entry<String, String>> it = headers.entrySet().iterator();
		Entry<String, String> e;
		while (it.hasNext()) {
			e = it.next();
			msg.setHeader(e.getKey(), e.getValue());
		}
	}

	private static String getMessageId(Map<String, String> headers) {
		Iterator<Entry<String, String>> it = headers.entrySet().iterator();
		Entry<String, String> e;
		while (it.hasNext()) {
			e = it.next();
			if (e.getKey().equalsIgnoreCase(MESSAGE_ID))
				return e.getValue();
		}
		return null;
	}

	private void checkAddress(InternetAddress[] ias, Charset charset) { // DIFF 23
		for (int i = 0; i < ias.length; i++) {
			checkAddress(ias[i], charset);
		}
	}

	private void checkAddress(InternetAddress ia, Charset charset) { // DIFF 23
		try {
			if (!Util.isEmpty(ia.getPersonal())) {
				String personal = MailUtil.encode(ia.getPersonal(), charset.name());
				if (!personal.equals(ia.getPersonal()))
					ia.setPersonal(personal);
			}
		} catch (UnsupportedEncodingException e) {
		}
	}

	/**
	 * @param plainText
	 */
	public void setPlainText(String plainText) {
		this.plainText = plainText;
		this.plainTextCharset = charset;
	}

	/**
	 * @param plainText
	 * @param plainTextCharset
	 */
	public void setPlainText(String plainText, Charset plainTextCharset) {
		this.plainText = plainText;
		this.plainTextCharset = plainTextCharset;
	}

	/**
	 * @param htmlText
	 */
	public void setHTMLText(String htmlText) {
		this.htmlText = htmlText;
		this.htmlTextCharset = charset;
	}

	public boolean hasHTMLText() {
		return htmlText != null;
	}

	public boolean hasPlainText() {
		return plainText != null;
	}

	/**
	 * @param htmlText
	 * @param htmlTextCharset
	 */
	public void setHTMLText(String htmlText, Charset htmlTextCharset) {
		this.htmlText = htmlText;
		this.htmlTextCharset = htmlTextCharset;
	}

	public void addAttachment(Resource resource, String fileName, String type, String disposition, String contentID,
			boolean removeAfterSend) {
		Attachment att = new Attachment(resource, fileName, type, disposition, contentID, removeAfterSend);
		attachmentz = add(attachmentz, att);
	}

	public MimeBodyPart toMimeBodyPart(Multipart mp, lucee.runtime.config.Config config, Attachment att)
			throws MessagingException {

		MimeBodyPart mbp = new MimeBodyPart();

		// set Data Source
		String strRes = att.getAbsolutePath();
		if (!Util.isEmpty(strRes)) {

			mbp.setDataHandler(new DataHandler(new ResourceDataSource(config.getResource(strRes))));
		} else
			mbp.setDataHandler(new DataHandler(new URLDataSource2(att.getURL())));
		//
		String fileName = att.getFileName();

		// Set to comment for LDEV-4249 because of JavaMail choosing best encoding by
		// itself,
		// as specified in https://javaee.github.io/javamail/FAQ#encodefilename and it
		// should be
		// set in very special cases for legacy purpose.
		// if (!StringUtil.isAscii(fileName)) {
		// try {
		// fileName = MimeUtility.encodeText(fileName, "UTF-8", null);
		// }
		// catch (UnsupportedEncodingException e) {
		// } // that should never happen!
		// }

		mbp.setFileName(fileName);
		if (!Util.isEmpty(att.getType()))
			mbp.setHeader("Content-Type", att.getType());

		String disposition = att.getDisposition();
		if (!Util.isEmpty(disposition)) {

			mbp.setDisposition(disposition);
			if (mp instanceof MimeMultipart && MimePart.INLINE.equalsIgnoreCase(disposition)) {
				((MimeMultipart) mp).setSubType("related");
			}
		}

		if (!Util.isEmpty(att.getContentID()))
			mbp.setContentID("<" + att.getContentID() + ">");

		return mbp;
	}

	/**
	 * @param file
	 * @throws MessagingException
	 */
	public void addAttachment(Resource file) throws MessagingException {
		addAttachment(file, null, null, null, null, false);
	}

	public void send(PageContext pc, long sendTime) throws MailException, PageException {
		if (plainText == null && htmlText == null)
			throw new MailException("you must define plaintext or htmltext");
		Server[] servers = pc.getMailServers();

		ConfigWeb config = pc.getConfig();
		if (isEmpty(servers) && isEmpty(host))
			throw new MailException("no SMTP Server defined");

		if (spool == SPOOL_YES || (spool == SPOOL_UNDEFINED && config.isMailSpoolEnable())) {
			MailSpoolerTask mst = new MailSpoolerTask(this, servers, sendTime);
			if (listener != null)
				mst.setListener(toListener(mst, listener));
			ReflectionUtil.add(config.getSpoolerEngine(), config, mst);
		} else
			_send(config, servers);
	}

	// TODO take interface from loader 7.0.1
	public static CFMLSpoolerTaskListener toListener(SpoolerTask st, Object listener) throws PageException {
		CFMLEngine eng = CFMLEngineFactory.getInstance();

		if (listener instanceof Component)
			return new ComponentSpoolerTaskListener(MailUtil.getCurrentContext(null), st, (Component) listener);

		if (listener instanceof UDF)
			return new UDFSpoolerTaskListener(MailUtil.getCurrentContext(null), st, null, (UDF) listener);

		if (listener instanceof Struct) {

			// before
			UDF before;
			Object tmp = ((Struct) listener).get("before", null);
			if (tmp instanceof UDF)
				before = (UDF) tmp;
			else
				before = null;

			// after
			UDF after;
			tmp = ((Struct) listener).get("after", null);
			if (tmp instanceof UDF)
				after = (UDF) tmp;
			else
				after = null;

			return new UDFSpoolerTaskListener(MailUtil.getCurrentContext(null), st, before, after);
		}
		throw eng.getExceptionUtil().createApplicationException(
				"cannot convert [" + eng.getCastUtil().toTypeName(listener) + "] to a listener");
	}

	public void _send(lucee.runtime.config.ConfigWeb config, Server[] servers) throws MailException {
		long start = System.nanoTime();
		long _timeout = getTimeout(config);
		try {

			ReflectionUtil.Proxy_start(proxyData);

			Log log = config != null ? config.getLog("mail") : null;
			// Server
			// Server[] servers = config.getMailServers();
			if (host != null) {
				int prt;
				String usr, pwd;
				Server[] nServers = new Server[host.length];
				for (int i = 0; i < host.length; i++) {
					usr = null;
					pwd = null;
					prt = Server.DEFAULT_PORT;

					if (port > 0)
						prt = port;
					if (!Util.isEmpty(username)) {
						usr = username;
						pwd = password;
					}

					nServers[i] = toServerImpl(host[i], prt, usr, pwd, lifeTimespan, idleTimespan);
					if (ssl == SSL_YES)
						ServerPro.setSSL(nServers[i], true);
					if (tls == TLS_YES)
						ServerPro.setTLS(nServers[i], true);

				}
				servers = nServers;
			}
			if (servers.length == 0) {
				// return;
				throw new MailException("no SMTP Server defined");
			}

			boolean _ssl, _tls;
			for (int i = 0; i < servers.length; i++) {

				Server server = servers[i];
				String _username = null, _password = "";
				// int _port;

				// username/password

				if (server.hasAuthentication()) {
					_username = server.getUsername();
					_password = server.getPassword();
				}

				// tls
				if (tls != TLS_NONE)
					_tls = tls == TLS_YES;
				else
					_tls = ServerPro.isTLS(server);

				if (_tls) {
					MailUtil.setSystemPropMailSslProtocols();
				}

				// ssl
				if (ssl != SSL_NONE)
					_ssl = ssl == SSL_YES;
				else
					_ssl = ServerPro.isSSL(server);

				MimeMessageAndSession msgSess;
				boolean recyleConnection = ServerPro.reuseConnections(server);
				{// synchronized(LOCK) {
					try {
						msgSess = createMimeMessage(config, server.getHostName(), server.getPort(), _username,
								_password, ServerPro.getLifeTimeSpan(server), ServerPro.getIdleTimeSpan(server), _tls,
								_ssl, ReflectionUtil.isMailSendPartial(config), !recyleConnection,
								ReflectionUtil.isUserset(config));
					} catch (MessagingException e) {
						// listener
						listener(config, server, log, e, System.nanoTime() - start);
						MailException me = new MailException(e.getMessage());
						me.setStackTrace(e.getStackTrace());
						throw me;
					}
					try {
						SerializableObject lock = new SerializableObject();
						SMTPSender sender = new SMTPSender(lock, msgSess, server.getHostName(), server.getPort(),
								_username, _password, recyleConnection);
						sender.start();
						synchronized (lock) {
							lock.wait(_timeout);
						}

						if (!sender.isSent()) {
							Throwable t = sender.getThrowable();
							if (t != null)
								throw CFMLEngineFactory.getInstance().getCastUtil().toPageException(new Exception(t));

							// stop when still running
							try {
								if (sender.isAlive())
									sender.stop();
							} catch (Exception t2) {
							}

							// after thread is stopped check sent flag again
							if (!sender.isSent()) {
								throw new MessagingException("timeout occurred after " + (_timeout / 1000)
										+ " seconds while sending mail message");
							}
						}
						// could have an exception but was send anyway
						if (sender.getThrowable() != null) {
							Throwable t = new Exception(sender.getThrowable());
							if (log != null)
								log.log(Log.LEVEL_ERROR, "send mail", t);
						}
						clean(config, attachmentz);

						listener(config, server, log, null, System.nanoTime() - start);
						break;
					} catch (Exception e) {
						if (log != null) {
							log.error(SMTPClient.class.getName(), e);
						}
						if (i + 1 == servers.length) {

							listener(config, server, log, e, System.nanoTime() - start);
							MailException me = new MailException(server.getHostName() + " " + e.getMessage() + ":" + i);
							me.initCause(e.getCause());
							throw me;
						}
					}
				}
			}
		} finally {
			ReflectionUtil.Proxy_end();
		}
	}

	private void listener(ConfigWeb config, Server server, Log log, Exception e, long exe) {
		if (e == null)
			log.info("mail",
					"mail sent (subject:" + subject + "; server:" + server.getHostName() + "; port:" + server.getPort()
							+ "; from:" + toString(from) + "; to:" + toString(tos) + "; cc:" + toString(ccs) + "; bcc:"
							+ toString(bccs) + "; ft:" + toString(fts) + "; rt:" + toString(rts) + ")");
		else
			log.log(Log.LEVEL_ERROR, "mail", e);

		// listener

		Map<String, Object> props = new HashMap<String, Object>();
		props.put("attachments", this.attachmentz);
		props.put("bccs", this.bccs);
		props.put("ccs", this.ccs);
		props.put("charset", this.charset);
		props.put("from", this.from);
		props.put("fts", this.fts);
		props.put("headers", this.headers);
		props.put("host", server.getHostName());
		props.put("htmlText", this.htmlText);
		props.put("htmlTextCharset", this.htmlTextCharset);
		props.put("parts", this.parts);
		props.put("password", this.password);
		props.put("plainText", this.plainText);
		props.put("plainTextCharset", this.plainTextCharset);
		props.put("port", server.getPort());
		props.put("proxyData", this.proxyData);
		props.put("rts", this.rts);
		props.put("subject", this.subject);
		props.put("timeout", getTimeout(config));
		props.put("timezone", this.timeZone);
		props.put("tos", this.tos);
		props.put("username", this.username);
		props.put("xmailer", this.xmailer);
		Object amc = ReflectionUtil.getActionMonitorCollector(config);
		ReflectionUtil.log(amc, config, "mail", "Mail", exe, props);
	}

	private static String toString(InternetAddress... ias) {
		if (isEmpty(ias))
			return "";

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < ias.length; i++) {
			if (sb.length() > 0)
				sb.append(", ");
			sb.append(ias[i].toString());
		}
		return sb.toString();
	}

	private long getTimeout(Config config) {
		return timeout > 0 ? timeout : config.getMailTimeout() * 1000L;
	}

	// remove any attachments that are marked to remove after sending
	private static void clean(Config config, Attachment[] attachmentz) {
		if (attachmentz != null) {
			ResourceUtil util = CFMLEngineFactory.getInstance().getResourceUtil();
			for (int i = 0; i < attachmentz.length; i++) {
				if (attachmentz[i].isRemoveAfterSend()) {
					Resource res = config.getResource(attachmentz[i].getAbsolutePath());
					util.removeSilent(res, true);
				}
			}
		}
	}

	private MimeBodyPart getHTMLText(Config config) throws MessagingException {
		MimeBodyPart html = new MimeBodyPart();
		fillHTMLText(config, html);
		return html;
	}

	/*
	 * Users can opt-in to the old Lucee behavior of allowing HTML emails to be sent
	 * using 7bit encoding. When 7bit transfer encoding is used, content must be
	 * wrapped to less than 1,000 characters per line.
	 * 
	 * The new default behavior for sending HTML emails is to use "quoted-printable"
	 * encoding, encodings non-ASCII characters and automatically wraps lines to 76
	 * characters wide, but encodes word breaks. This allows for strings longer than
	 * 1000 characters to be included in the output and still have the output
	 * conform to the SMTP RFCs.
	 * 
	 * https://stackoverflow.com/questions/25710599/content-transfer-encoding-7bit-
	 * or-8-bit/28531705# 28531705
	 */
	private boolean isUse7bitHtmlEncoding() {
		try {
			return CFMLEngineFactory.getInstance().getCastUtil().toBoolean(
					Util.getSystemPropOrEnvVar("lucee.mail.use.7bit.transfer.encoding.for.html.parts", "true"));
		} catch (Throwable t) {
			return false;
		}
	}

	private void fillHTMLText(Config config, MimePart mp) throws MessagingException {
		if (htmlTextCharset == null)
			htmlTextCharset = getMailDefaultCharset(config);

		String transferEncoding;

		/*
		 * Set the "lucee.mail.use.7bit.transfer.encoding.for.html.parts" system
		 * property to "false" to force the previous behavior of using 7bit transfer
		 * encoding.
		 */
		if (isUse7bitHtmlEncoding()) {
			transferEncoding = "7bit";
			// when using 7bit, we must always wrap lines
			mp.setDataHandler(new DataHandler(new StringDataSource(htmlText, TEXT_HTML, htmlTextCharset, 998)));
			/*
			 * The default behavior is to using "quoted-printable" for HTML emails. This
			 * will force wrapping of lines to 76 characters and encoded any non-ASCII
			 * characters.
			 * 
			 * ACF uses this encoded for all HTML parts.
			 */
		} else {
			transferEncoding = "quoted-printable";
			mp.setDataHandler(new DataHandler(new StringDataSource(htmlText, TEXT_HTML, htmlTextCharset)));
		}

		// headers must always be set after data handler is set or the headers will be
		// replaced
		mp.setHeader("Content-Transfer-Encoding", transferEncoding);
		mp.setHeader("Content-Type", TEXT_HTML + "; charset=" + htmlTextCharset);
	}

	private MimeBodyPart getPlainText(Config config) throws MessagingException {
		MimeBodyPart plain = new MimeBodyPart();
		fillPlainText(config, plain);
		return plain;
	}

	private void fillPlainText(Config config, MimePart mp) throws MessagingException {
		if (plainTextCharset == null)
			plainTextCharset = getMailDefaultCharset(config);
		mp.setDataHandler(new DataHandler(
				new StringDataSource(plainText != null ? plainText : "", TEXT_PLAIN, plainTextCharset, 998)));
		// headers must always be set after data handler is set or the headers will be
		// replaced
		mp.setHeader("Content-Transfer-Encoding", "7bit");
		mp.setHeader("Content-Type", TEXT_PLAIN + "; charset=" + plainTextCharset);
	}

	private BodyPart toMimeBodyPart(Config config, MailPart part) throws MessagingException {
		Charset cs = part.getCharset();
		if (cs == null)
			cs = getMailDefaultCharset(config);
		MimeBodyPart mbp = new MimeBodyPart();

		StringDataSource partSource = null;
		/*
		 * HTML parts are encoded as "quoted-printable", which is automatically wrapped
		 * to 76 characters per line, so we do not need to wrap these lines.
		 */
		if ((part.getType() == "text/html") && !isUse7bitHtmlEncoding()) {
			partSource = new StringDataSource(part.getBody(), part.getType(), cs);
		} else {
			partSource = new StringDataSource(part.getBody(), part.getType(), cs, 998);
		}

		mbp.setDataHandler(new DataHandler(partSource));
		return mbp;
	}

	private Charset getMailDefaultCharset(Config config) {
		if (config == null)
			config = CFMLEngineFactory.getInstance().getThreadConfig();
		Charset cs = config.getMailDefaultCharset();
		if (cs == null)
			cs = MailUtil.UTF8;
		return cs;
	}

	/**
	 * @return the proxyData
	 */
	public ProxyData getProxyData() {
		return proxyData;
	}

	/**
	 * @param proxyData
	 *            the proxyData to set
	 */
	public void setProxyData(ProxyData proxyData) {
		this.proxyData = proxyData;
	}

	/**
	 * @param ssl
	 *            the ssl to set
	 */
	public void setSSL(boolean ssl) {
		this.ssl = ssl ? SSL_YES : SSL_NO;
	}

	/**
	 * @param tls
	 *            the tls to set
	 */
	public void setTLS(boolean tls) {
		this.tls = tls ? TLS_YES : TLS_NO;
	}

	/**
	 * @return the subject
	 */
	public String getSubject() {
		return subject;
	}

	/**
	 * @return the from
	 */
	public InternetAddress getFrom() {
		return from;
	}

	/**
	 * @return the tos
	 */
	public InternetAddress[] getTos() {
		return tos;
	}

	/**
	 * @return the bccs
	 */
	public InternetAddress[] getBccs() {
		return bccs;
	}

	/**
	 * @return the ccs
	 */
	public InternetAddress[] getCcs() {
		return ccs;
	}

	/**
	 * @return the charset
	 */
	public String getCharset() {
		return charset.toString();
	}

	/**
	 * @return the replyTo
	 */
	public InternetAddress[] getReplyTos() {
		return rts;
	}

	/**
	 * @return the failTo
	 */
	public InternetAddress[] getFailTos() {
		return fts;
	}

	public void setPart(MailPart part) {
		if (parts == null)
			parts = new ArrayList<MailPart>();
		parts.add(part);
	}

	public void setTimeZone(TimeZone timeZone) {
		this.timeZone = timeZone;
	}

	public final static class SerializableObject implements Serializable {

		private static final long serialVersionUID = -4779739892908161785L;

	}
}
