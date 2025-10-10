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
package org.lucee.extension.mail;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.mail.BodyPart;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.UIDFolder;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;

import org.apache.commons.mail.DefaultAuthenticator;
import org.lucee.extension.mail.imap.ImapClient;
import org.lucee.extension.mail.pool.Pool;
import org.lucee.extension.mail.pool.PoolItem;
import org.lucee.extension.mail.pop.PopClient;

import lucee.commons.io.res.Resource;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.exp.PageException;
import lucee.runtime.type.Array;
import lucee.runtime.type.Query;
import lucee.runtime.type.Struct;
import lucee.runtime.util.Cast;
import lucee.runtime.util.Creation;
import lucee.runtime.util.ListUtil;

public abstract class MailClient implements PoolItem {

	@Override
	public boolean isValid() {
		if (_store != null && !_store.isConnected()) {
			// goal is to be valid if requested so we try to be
			try {
				start();
			} catch (MessagingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return _store != null && _store.isConnected();
	}

	public static final int TYPE_POP3 = 0;
	public static final int TYPE_IMAP = 1;

	private String _popHeaders[] = { "date", "from", "messagenumber", "messageid", "replyto", "subject", "cc", "to",
			"size", "header", "uid" };
	private String _popAll[] = { "date", "from", "messagenumber", "messageid", "replyto", "subject", "cc", "to", "size",
			"header", "uid", "answered", "deleted", "draft", "flagged", "recent", "seen", "body", "textBody",
			"HTMLBody", "attachments", "attachmentfiles", "cids", "deliverystatus" };

	private String _imapHeaders[] = { "date", "from", "messagenumber", "messageid", "replyto", "subject", "cc", "to",
			"size", "header", "uid", "answered", "deleted", "draft", "flagged", "recent", "seen" };
	private String _imapAll[] = { "date", "from", "messagenumber", "messageid", "replyto", "subject", "cc", "to",
			"size", "header", "uid", "answered", "deleted", "draft", "flagged", "recent", "seen", "body", "textBody",
			"HTMLBody", "attachments", "attachmentfiles", "cids", "deliverystatus" };

	private String server = null;
	private String username = null;
	private String password = null;
	private Session _session = null;
	private Store _store = null;
	private int port = 0;
	private int timeout = 0;
	private int startrow = 0;
	private int maxrows = 0;
	private boolean uniqueFilenames = false;
	private Resource attachmentDirectory = null;
	private final boolean secure;
	private static Pool pool = new Pool(60000, 100, 5000);
	private String delimiter = ",";
	private boolean stopOnError = true;

	public static MailClient getInstance(int type, String server, int port, String username, String password,
			boolean secure, String name, String id) throws Exception {
		String uid;
		if (Util.isEmpty(name))
			uid = createName(type, server, port, username, password, secure);
		else
			uid = name;
		uid = type + ";" + uid + ";" + id;

		PoolItem item = pool.get(uid);
		if (item == null) {
			if (Util.isEmpty(server)) {
				if (Util.isEmpty(name))
					throw CFMLEngineFactory.getInstance().getExceptionUtil()
							.createApplicationException("missing mail server information");
				else
					throw CFMLEngineFactory.getInstance().getExceptionUtil()
							.createApplicationException("There is no connection available with name [" + name + "]");
			}
			if (TYPE_POP3 == type)
				pool.put(uid, item = new PopClient(server, port, username, password, secure));
			if (TYPE_IMAP == type)
				pool.put(uid, item = new ImapClient(server, port, username, password, secure));
		}
		return (MailClient) item;
	}

	public static void removeInstance(MailClient client) throws Exception {
		pool.remove(client); // this will also call the stop method of the
	}

	private static String createName(int type, String server, int port, String username, String password,
			boolean secure) {
		return MailStringUtil.create64BitHashAsString(new StringBuilder().append(server).append(';').append(port)
				.append(';').append(username).append(';').append(password).append(';').append(secure).append(';'));
	}

	/**
	 * constructor of the class
	 * 
	 * @param server
	 * @param port
	 * @param username
	 * @param password
	 * @param secure
	 */
	public MailClient(String server, int port, String username, String password, boolean secure) {
		timeout = 60000;
		startrow = 0;
		maxrows = -1;
		delimiter = ",";
		uniqueFilenames = false;
		this.server = server;
		this.port = port;
		this.username = username;
		this.password = password;
		this.secure = secure;
	}

	/**
	 * @param maxrows
	 *            The maxrows to set.
	 */
	public void setMaxrows(int maxrows) {
		this.maxrows = maxrows;
	}

	/**
	 * @param startrow
	 *            The startrow to set.
	 */
	public void setStartrow(int startrow) {
		this.startrow = startrow;
	}

	/**
	 * @param timeout
	 *            The timeout to set.
	 */
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	/**
	 * @param uniqueFilenames
	 *            The uniqueFilenames to set.
	 */
	public void setUniqueFilenames(boolean uniqueFilenames) {
		this.uniqueFilenames = uniqueFilenames;
	}

	/**
	 * @param attachmentDirectory
	 *            The attachmentDirectory to set.
	 */
	public void setAttachmentDirectory(Resource attachmentDirectory) {
		this.attachmentDirectory = attachmentDirectory;
	}

	/**
	 * @param delimiter
	 *            The delimiter to set.
	 */
	public void setDelimiter(String delimiter) {
		this.delimiter = delimiter;
	}

	/**
	 * @param stopOnError
	 *            whether to stop on error, IMAP only
	 */
	public void stopOnError(boolean stopOnError) {
		this.stopOnError = stopOnError;
	}

	/**
	 * connects to pop server
	 * 
	 * @throws MessagingException
	 */
	@Override
	public void start() throws MessagingException {
		Properties properties = new Properties();
		String type = getTypeAsString();
		properties.setProperty("mail." + type + ".host", server);
		properties.setProperty("mail." + type + ".port", String.valueOf(port));
		properties.setProperty("mail." + type + ".connectiontimeout", String.valueOf(timeout));
		properties.setProperty("mail." + type + ".timeout", String.valueOf(timeout));
		// properties.setProperty("mail.mime.charset", "UTF-8");
		if (secure) {
			properties.setProperty("mail." + type + ".ssl.enable", "true");
			// properties.setProperty("mail."+type+".starttls.enable", "true" );
			// allow using untrusted certs, good for CI
			if (!CFMLEngineFactory.getInstance().getCastUtil()
					.toBooleanValue(Util.getSystemPropOrEnvVar("lucee.ssl.checkserveridentity", null), true)) {
				properties.setProperty("mail." + type + ".ssl.trust", "*");
				properties.setProperty("mail." + type + ".ssl.checkserveridentity", "false");
			}
		}

		if (TYPE_IMAP == getType()) {
			if (secure) {
				properties.put("mail.store.protocol", "imaps");
				properties.put("mail.imaps.partialfetch", "false");
				properties.put("mail.imaps.fetchsize", "1048576");
			} else {
				properties.put("mail.store.protocol", "imap");
				properties.put("mail.imap.partialfetch", "false");
				properties.put("mail.imap.fetchsize", "1048576");
			}
		}
		// if(TYPE_POP3==getType()){}
		_session = username != null ? Session.getInstance(properties, new DefaultAuthenticator(username, password))
				: Session.getInstance(properties);

		Thread t = Thread.currentThread();
		ClassLoader ccl = t.getContextClassLoader();
		t.setContextClassLoader(_session.getClass().getClassLoader());
		try {
			_store = _session.getStore(type);
			if (!Util.isEmpty(username))
				_store.connect(server, port, username, password);
			else
				_store.connect();
		} finally {
			t.setContextClassLoader(ccl);
		}
	}

	protected abstract String getTypeAsString();

	protected abstract int getType();

	/**
	 * delete all message in ibox that match given criteria
	 * 
	 * @param messageNumber
	 * @param uid
	 * @throws MessagingException
	 * @throws IOException
	 * @throws PageException
	 */
	public void deleteMails(String messageNumber, String uid) throws MessagingException, IOException, PageException {
		Folder folder;
		Message amessage[];
		folder = _store.getFolder("INBOX");
		folder.open(2);
		Map<String, Message> map = getMessages(null, folder, uid, messageNumber, startrow, maxrows, false);
		Iterator<String> iterator = map.keySet().iterator();
		amessage = new Message[map.size()];
		int i = 0;
		while (iterator.hasNext()) {
			amessage[i++] = map.get(iterator.next());
		}
		try {
			folder.setFlags(amessage, new Flags(Flags.Flag.DELETED), true);
		} catch (MessagingException e) {
			if (this.stopOnError) {
				throw e;
			}
		} finally {
			folder.close(true);
		}
	}

	/**
	 * return all messages from inbox
	 * 
	 * @param messageNumbers
	 *            all messages with this ids
	 * @param uids
	 *            all messages with this uids
	 * @param all
	 * @param folderName
	 * @return all messages from inbox
	 * @throws MessagingException
	 * @throws IOException
	 * @throws PageException
	 */
	public Query getMails(String messageNumbers, String uids, boolean all, String folderName)
			throws MessagingException, IOException, PageException {
		Query qry;

		Creation creator = CFMLEngineFactory.getInstance().getCreationUtil();
		if (getType() == TYPE_IMAP) {
			qry = creator.createQuery(all ? _imapAll : _imapHeaders, 0, "query");
		} else {
			qry = creator.createQuery(all ? _popAll : _popHeaders, 0, "query");
		}

		if (Util.isEmpty(folderName, true))
			folderName = "INBOX";
		else
			folderName = folderName.trim();

		Folder folder = _store.getFolder(folderName);
		folder.open(Folder.READ_ONLY);
		try {
			getMessages(qry, folder, uids, messageNumbers, startrow, maxrows, all);
		} catch (MessagingException e) {
			if (this.stopOnError) {
				throw e;
			}
		} finally {
			folder.close(false);
		}
		return qry;
	}

	private void toQuery(Query qry, Message message, Object uid, boolean all) throws MessagingException {
		int row = qry.addRow();
		// date
		try {
			qry.setAtEL("DATE", row,
					CFMLEngineFactory.getInstance().getCastUtil().toDate(message.getSentDate(), true, null, null));
		} catch (MessagingException e) {
		}

		// subject
		try {
			qry.setAtEL("SUBJECT", row, message.getSubject());
		} catch (MessagingException e) {
			if (this.stopOnError) {
				throw e;
			}
			qry.setAtEL("SUBJECT", row, "MessagingException:" + e.getMessage());
		}

		// size
		try {
			qry.setAtEL("SIZE", row, Double.valueOf(message.getSize()));
		} catch (MessagingException e) {
		}

		qry.setAtEL("FROM", row, toList(getHeaderEL(message, "from")));
		qry.setAtEL("messagenumber", row, Double.valueOf(message.getMessageNumber()));
		qry.setAtEL("messageid", row, toList(getHeaderEL(message, "Message-ID")));
		String s = toList(getHeaderEL(message, "reply-to"));
		if (s.length() == 0) {
			s = CFMLEngineFactory.getInstance().getCastUtil().toString(qry.getAt("FROM", row, null), "");
		}
		qry.setAtEL("REPLYTO", row, s);
		qry.setAtEL("CC", row, toList(getHeaderEL(message, "cc")));
		qry.setAtEL("BCC", row, toList(getHeaderEL(message, "bcc")));
		qry.setAtEL("TO", row, toList(getHeaderEL(message, "to")));
		qry.setAtEL("UID", row, uid);
		if (getType() == TYPE_IMAP) {
			qry.setAtEL("ANSWERED", row, isSetEL(message, Flags.Flag.ANSWERED));
			qry.setAtEL("DELETED", row, isSetEL(message, Flags.Flag.DELETED));
			qry.setAtEL("DRAFT", row, isSetEL(message, Flags.Flag.DRAFT));
			qry.setAtEL("FLAGGED", row, isSetEL(message, Flags.Flag.FLAGGED));
			qry.setAtEL("RECENT", row, isSetEL(message, Flags.Flag.RECENT));
			qry.setAtEL("SEEN", row, isSetEL(message, Flags.Flag.SEEN));
		}

		StringBuilder content = new StringBuilder();
		try {
			for (Enumeration enumeration = message.getAllHeaders(); enumeration.hasMoreElements(); content
					.append('\n')) {
				Header header = (Header) enumeration.nextElement();
				content.append(header.getName());
				content.append(": ");
				content.append(header.getValue());
			}
		} catch (MessagingException e) {
			if (this.stopOnError) {
				throw e;
			}
		}
		qry.setAtEL("HEADER", row, content.toString());

		if (all) {
			getContentEL(qry, message, row);
		}
	}

	private String[] getHeaderEL(Message message, String key) {
		try {
			return message.getHeader(key);
		} catch (MessagingException e) {
			return null;
		}
	}

	private boolean isSetEL(Message message, Flags.Flag flag) {
		try {
			return message.isSet(flag);
		} catch (MessagingException e) {
			return false;
		}
	}

	/**
	 * gets all messages from given Folder that match given criteria
	 * 
	 * @param qry
	 * @param folder
	 * @param uIds
	 * @param messageNumbers
	 * @param all
	 * @param startrow
	 * @param maxrows
	 * @return
	 * @return matching Messages
	 * @throws MessagingException
	 * @throws PageException
	 */
	private Map<String, Message> getMessages(Query qry, Folder folder, String uids, String messageNumbers, int startRow,
			int maxRow, boolean all) throws MessagingException, PageException {

		Message[] messages = null;
		String[] uidsStringArray = null;
		Map<String, Message> map = qry == null ? new HashMap<String, Message>() : null;
		int k = 0;
		if (uids != null || messageNumbers != null) {
			startRow = 0;
			maxRow = -1;
		}
		ListUtil listUtil = CFMLEngineFactory.getInstance().getListUtil();
		if (uids != null) {
			if (getType() == TYPE_IMAP) {
				messages = ((UIDFolder) folder).getMessagesByUID(toLongArray(uids, delimiter));
			} else { // POP3 folder doesn't supports the getMessagesByUID method from UIDFolder
				uidsStringArray = listUtil
						.trimItems(listUtil.toStringArray(listUtil.toArrayRemoveEmpty(uids, delimiter)));
			}
		} else if (messageNumbers != null) {
			messages = folder
					.getMessages(MailUtil.toIntArrayWithMaxRange(messageNumbers, ',', folder.getMessageCount()));
		}

		if (messages == null)
			messages = folder.getMessages();

		Message message;
		for (int l = startRow; l < messages.length; l++) {
			if (maxRow != -1 && k == maxRow) {
				break;
			}
			message = messages[l];
			if (message == null)
				continue; // because the message can be a null for non existing messageNumbers

			String id = getId(folder, message);

			if (uidsStringArray == null || (uidsStringArray != null && contains(uidsStringArray, id))) {
				k++;
				if (qry != null) {
					toQuery(qry, message, id, all);
				} else
					map.put(id, message);
			}
		}
		return map;
	}

	protected String getId(Folder folder, Message message) throws MessagingException {
		return _getId(folder, message);
	}

	protected abstract String _getId(Folder folder, Message message) throws MessagingException;

	private void getContentEL(Query query, Message message, int row) {
		try {
			getContent(query, message, row);
		} catch (Exception e) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			query.setAtEL("BODY", row, sw.toString());
		}
	}

	/**
	 * write content data to query
	 * 
	 * @param qry
	 * @param content
	 * @param row
	 * @throws MessagingException
	 * @throws IOException
	 */
	private void getContent(Query query, Message message, int row) throws MessagingException, IOException {
		StringBuilder body = new StringBuilder();
		CFMLEngine engine = CFMLEngineFactory.getInstance();
		Creation creator = engine.getCreationUtil();
		Struct cids = creator.createStruct();
		query.setAtEL("CIDS", row, cids);
		if (message.isMimeType("text/plain")) {
			String content = getConent(message);
			query.setAtEL("textBody", row, content);
			body.append(content);
		} else if (message.isMimeType("text/html")) {
			String content = getConent(message);
			query.setAtEL("HTMLBody", row, content);
			body.append(content);
		} else if (message.isMimeType("message/delivery-status")) {
			String content = getConent(message);
			query.setAtEL("deliverystatus", row, content);
			body.append(content);
		} else {
			Object content = message.getContent();
			if (content instanceof MimeMultipart) {
				Array attachments = creator.createArray();
				Array attachmentFiles = creator.createArray();
				getMultiPart(query, row, attachments, attachmentFiles, cids, (MimeMultipart) content, body);

				if (attachments.size() > 0) {
					try {
						query.setAtEL("ATTACHMENTS", row, engine.getListUtil().toList(attachments, "\t"));
					} catch (PageException pageexception) {
					}
				}
				if (attachmentFiles.size() > 0) {
					try {
						query.setAtEL("attachmentfiles", row, engine.getListUtil().toList(attachmentFiles, "\t"));
					} catch (PageException pageexception1) {
					}
				}

			}
		}
		query.setAtEL("BODY", row, body.toString());
	}

	private void getMultiPart(Query query, int row, Array attachments, Array attachmentFiles, Struct cids,
			Multipart multiPart, StringBuilder body) throws MessagingException, IOException {
		int j = multiPart.getCount();

		for (int k = 0; k < j; k++) {
			BodyPart bodypart = multiPart.getBodyPart(k);
			Object content;

			if (bodypart.getFileName() != null) {
				String filename = bodypart.getFileName();
				try {
					filename = Normalizer.normalize(MimeUtility.decodeText(filename), Normalizer.Form.NFC);
				} catch (Exception t) {
					// TODO log
				}

				if (bodypart.getHeader("Content-ID") != null) {
					String[] ids = bodypart.getHeader("Content-ID");
					String cid = ids[0].substring(1, ids[0].length() - 1);
					cids.setEL(CFMLEngineFactory.getInstance().getCastUtil().toKey(filename), cid);
				}

				if (filename != null && MailUtil.find(attachments, filename) >= 0) {

					attachments.appendEL(filename);
					if (attachmentDirectory != null) {
						Resource file = attachmentDirectory.getRealResource(filename);
						int l = 1;
						String s2;
						for (; uniqueFilenames && file.exists(); file = attachmentDirectory.getRealResource(s2)) {
							String as[] = MailUtil.splitFileName(filename);
							s2 = as.length != 1 ? as[0] + l++ + '.' + as[1] : as[0] + l++;
						}

						CFMLEngineFactory.getInstance().getIOUtil().copy(bodypart.getInputStream(), file, true);
						attachmentFiles.appendEL(file.getAbsolutePath());
					}
				}
			} else if (bodypart.isMimeType("text/plain")) {
				content = getConent(bodypart);
				query.setAtEL("textBody", row, content);
				if (body.length() == 0)
					body.append(content);
			} else if (bodypart.isMimeType("text/html")) {
				content = getConent(bodypart);
				query.setAtEL("HTMLBody", row, content);
				if (body.length() == 0)
					body.append(content);
			} else if (bodypart.isMimeType("message/delivery-status")) {
				content = getConent(bodypart);
				query.setAtEL("deliverystatus", row, content);
				if (body.length() == 0)
					body.append(content);
			} else if ((content = bodypart.getContent()) instanceof Multipart) {
				getMultiPart(query, row, attachments, attachmentFiles, cids, (Multipart) content, body);
			} else if (bodypart.getHeader("Content-ID") != null) {
				String[] ids = bodypart.getHeader("Content-ID");
				String cid = ids[0].substring(1, ids[0].length() - 1);
				String filename = "cid:" + cid;

				attachments.appendEL(filename);
				if (attachmentDirectory != null) {
					filename = "_" + CFMLEngineFactory.getInstance().getSystemUtil().hashMd5(filename);
					Resource file = attachmentDirectory.getRealResource(filename);
					int l = 1;
					String s2;
					for (; uniqueFilenames && file.exists(); file = attachmentDirectory.getRealResource(s2)) {
						String as[] = MailUtil.splitFileName(filename);
						s2 = as.length != 1 ? as[0] + l++ + '.' + as[1] : as[0] + l++;
					}

					CFMLEngineFactory.getInstance().getIOUtil().copy(bodypart.getInputStream(), file, true);
					attachmentFiles.appendEL(file.getAbsolutePath());
				}

				cids.setEL(CFMLEngineFactory.getInstance().getCastUtil().toKey(filename), cid);
			} else if ((content = bodypart.getContent()) instanceof MimeMessage) {
				content = getConent(bodypart);
				if (body.length() == 0)
					body.append(content);
			}
		}
	}

	/*
	 * * writes BodyTag data to query, if there is a problem with encoding, encoding
	 * will removed a do it again
	 * 
	 * @param qry
	 * 
	 * @param columnName
	 * 
	 * @param row
	 * 
	 * @param bp
	 * 
	 * @param body
	 * 
	 * @throws IOException
	 * 
	 * @throws MessagingException / private void setBody(Query qry, String
	 * columnName, int row, BodyPart bp, StringBuilder body) throws IOException,
	 * MessagingException { String content = getConent(bp);
	 * 
	 * qry.setAtEL(columnName,row,content);
	 * if(body.length()==0)body.append(content);
	 * 
	 * }
	 */

	private String getConent(Part bp) throws MessagingException {
		InputStream is = null;

		try {
			if ((bp.getContent()) instanceof MimeMessage) {
				MimeMessage mimeContent = (MimeMessage) bp.getContent();
				is = mimeContent.getInputStream();
			} else {
				is = bp.getInputStream();
			}
			return getContent(is, CFMLEngineFactory.getInstance().getCastUtil()
					.toCharset(getCharsetFromContentType(bp.getContentType())));
		} catch (IOException | PageException mie) {
			Util.closeEL(is);
			try {
				return getContent(is, CFMLEngineFactory.getInstance().getSystemUtil().getCharset());
			} catch (IOException e) {
				return "Cannot read body of this message: " + e.getMessage();
			}
		} finally {
			Util.closeEL(is);
		}
	}

	private String getContent(InputStream is, Charset charset) throws IOException {
		return MailUtil.decode(CFMLEngineFactory.getInstance().getIOUtil().toString(is, charset));
	}

	private static String getCharsetFromContentType(String contentType) {
		ListUtil listUtil = CFMLEngineFactory.getInstance().getListUtil();
		Array arr = listUtil.toArrayRemoveEmpty(contentType, "; ");

		for (int i = 1; i <= arr.size(); i++) {
			Array inner = listUtil.toArray((String) arr.get(i, null), "= ");
			if (inner.size() == 2 && ((String) inner.get(1, "")).trim().equalsIgnoreCase("charset")) {
				String charset = (String) inner.get(2, "");
				charset = charset.trim();
				if (!Util.isEmpty(charset)) {
					if (charset.startsWith("\"") && charset.endsWith("\"")) {
						charset = charset.substring(1, charset.length() - 1);
					}
					if (charset.startsWith("'") && charset.endsWith("'")) {
						charset = charset.substring(1, charset.length() - 1);
					}
				}
				return charset;
			}
		}
		return "us-ascii";
	}

	/**
	 * checks if a String Array (ids) has one element that is equal to id
	 * 
	 * @param ids
	 * @param id
	 * @return has element found or not
	 * @throws PageException
	 */
	private boolean contains(String ids[], String id) throws PageException {
		for (int i = 0; i < ids.length; i++) {
			if (CFMLEngineFactory.getInstance().getOperationUtil().compare(ids[i], id) == 0)
				return true;
		}
		return false;
	}

	/**
	 * translate a String Array to String List
	 * 
	 * @param arr
	 *            Array to translate
	 * @return List from Array
	 */
	private String toList(String ids[]) {
		if (ids == null)
			return "";
		return CFMLEngineFactory.getInstance().getListUtil().toList(ids, ",");
	}

	/**
	 * disconnect without an exception
	 */
	@Override
	public void end() {
		try {
			if (_store != null)
				_store.close();
		} catch (Exception exception) {
		}
	}

	// IMAP only
	public void createFolder(String folderName) throws MessagingException, PageException {
		if (folderExists(folderName))
			throw CFMLEngineFactory.getInstance().getExceptionUtil().createApplicationException(
					"Cannot create imap folder [" + folderName + "], the folder already exists.");

		Folder folder = getFolder(folderName, null, false, true);
		if (!folder.exists())
			folder.create(Folder.HOLDS_MESSAGES);
	}

	private boolean folderExists(String folderName) throws MessagingException {
		String[] folderNames = toFolderNames(folderName);
		Folder folder = null;
		for (int i = 0; i < folderNames.length; i++) {
			folder = folder == null ? _store.getFolder(folderNames[i]) : folder.getFolder(folderNames[i]);
			if (!folder.exists())
				return false;
		}
		return true;
	}

	private String[] toFolderNames(String folderName) {
		if (Util.isEmpty(folderName))
			return new String[0];

		ListUtil util = CFMLEngineFactory.getInstance().getListUtil();
		return util.trimItems(util.trim(util.toStringArray(folderName, "/")));
	}

	public void deleteFolder(String folderName) throws MessagingException, PageException {

		if (folderName.equalsIgnoreCase("INBOX") || folderName.equalsIgnoreCase("OUTBOX"))
			throw CFMLEngineFactory.getInstance().getExceptionUtil()
					.createApplicationException("Cannot delete folder [" + folderName + "], this folder is protected.");

		String[] folderNames = toFolderNames(folderName);
		Folder folder = _store.getFolder(folderNames[0]);
		if (!folder.exists()) {
			throw CFMLEngineFactory.getInstance().getExceptionUtil()
					.createApplicationException("There is no folder with name [" + folderName + "].");
		}
		folder.delete(true);
	}

	public void renameFolder(String srcFolderName, String trgFolderName) throws MessagingException, PageException {
		if (srcFolderName.equalsIgnoreCase("INBOX") || srcFolderName.equalsIgnoreCase("OUTBOX"))
			throw CFMLEngineFactory.getInstance().getExceptionUtil().createApplicationException(
					"Cannot rename folder [" + srcFolderName + "], this folder is protected.");
		if (trgFolderName.equalsIgnoreCase("INBOX") || trgFolderName.equalsIgnoreCase("OUTBOX"))
			throw CFMLEngineFactory.getInstance().getExceptionUtil().createApplicationException(
					"Cannot rename folder to [" + trgFolderName + "], this folder name is protected.");

		Folder src = getFolder(srcFolderName, true, true, false);
		Folder trg = getFolder(trgFolderName, null, false, true);

		if (!src.renameTo(trg))
			throw CFMLEngineFactory.getInstance().getExceptionUtil().createApplicationException(
					"Cannot rename folder [" + srcFolderName + "] to [" + trgFolderName + "].");
	}

	public Query listAllFolder(String folderName, boolean recurse, int startrow, int maxrows)
			throws MessagingException, PageException {

		Creation creator = CFMLEngineFactory.getInstance().getCreationUtil();

		Query qry = creator.createQuery(new String[] { "FULLNAME", "NAME", "TOTALMESSAGES", "UNREAD", "PARENT", "NEW" },
				0, "folders");
		// if(Util.isEmpty(folderName)) folderName="INBOX";
		Folder folder = (Util.isEmpty(folderName)) ? _store.getDefaultFolder() : _store.getFolder(folderName);
		// Folder folder=_store.getFolder(folderName);
		if (!folder.exists())
			throw CFMLEngineFactory.getInstance().getExceptionUtil()
					.createApplicationException("There is no folder with name [" + folderName + "].");

		list(folder, qry, recurse, startrow, maxrows, 0);
		return qry;
	}

	public void moveMail(String srcFolderName, String trgFolderName, String messageNumber, String uid)
			throws MessagingException, PageException {
		if (Util.isEmpty(srcFolderName, true))
			srcFolderName = "INBOX";

		Folder srcFolder = getFolder(srcFolderName, true, true, false);
		Folder trgFolder = getFolder(trgFolderName, true, true, false);
		try {

			srcFolder.open(2);
			trgFolder.open(2);
			Message amessage[];
			Map<String, Message> map = getMessages(null, srcFolder, uid, messageNumber, startrow, maxrows, false);
			Iterator<String> iterator = map.keySet().iterator();
			amessage = new Message[map.size()];
			int i = 0;
			while (iterator.hasNext()) {
				amessage[i++] = map.get(iterator.next());
			}
			srcFolder.copyMessages(amessage, trgFolder);
			srcFolder.setFlags(amessage, new Flags(Flags.Flag.DELETED), true);
		} catch (MessagingException e) {
			if (this.stopOnError) {
				throw e;
			}
		} finally {
			srcFolder.close(true);
			trgFolder.close(true);
		}
	}

	public void markRead(String folderName) throws MessagingException, PageException {
		if (Util.isEmpty(folderName))
			folderName = "INBOX";

		Folder folder = null;
		try {
			folder = getFolder(folderName, true, true, false);
			folder.open(2);
			Message[] msgs = folder.getMessages();
			folder.setFlags(msgs, new Flags(Flags.Flag.SEEN), true);
		} catch (MessagingException e) {
			if (this.stopOnError) {
				throw e;
			}
		} finally {
			if (folder != null)
				folder.close(false);
		}
	}

	private Folder getFolder(String folderName, Boolean existingParent, Boolean existing,
			boolean createParentIfNotExists) throws MessagingException, PageException {
		String[] folderNames = toFolderNames(folderName);
		Folder folder = null;
		String fn;
		for (int i = 0; i < folderNames.length; i++) {
			fn = folderNames[i];
			folder = folder == null ? _store.getFolder(fn) : folder.getFolder(fn);

			// top
			if (i + 1 == folderNames.length) {
				if (existing != null) {
					if (existing.booleanValue() && !folder.exists())
						throw CFMLEngineFactory.getInstance().getExceptionUtil()
								.createApplicationException("There is no folder with name [" + folderName + "].");
					if (!existing.booleanValue() && folder.exists())
						throw CFMLEngineFactory.getInstance().getExceptionUtil().createApplicationException(
								"There is already a folder with name [" + folderName + "].");
				}
			}
			// parent
			else {
				if (existingParent != null) {
					if (existingParent.booleanValue() && !folder.exists())
						throw CFMLEngineFactory.getInstance().getExceptionUtil().createApplicationException(
								"There is no parent folder for folder with name [" + folderName + "].");
					if (!existingParent.booleanValue() && folder.exists())
						throw CFMLEngineFactory.getInstance().getExceptionUtil().createApplicationException(
								"There is already a parent folder for folder with name [" + folderName + "].");
				}
				if (createParentIfNotExists && !folder.exists()) {
					folder.create(Folder.HOLDS_MESSAGES);
				}
			}
		}
		return folder;
	}

	private void list(Folder folder, Query qry, boolean recurse, int startrow, int maxrows, int rowsMissed)
			throws MessagingException, PageException {
		Folder[] folders = folder.list();
		if (folders == null || folders.length == 0)
			return;

		for (Folder f : folders) {
			// start row
			if ((startrow - 1) > rowsMissed) {
				rowsMissed++;
				continue;
			}
			// max rows
			if (maxrows > 0 && qry.getRecordcount() >= maxrows)
				break;
			if ((f.getType() & Folder.HOLDS_MESSAGES) == 0)
				continue;
			int row = qry.addRow();

			Folder p = null;
			try {
				p = f.getParent();
			} catch (MessagingException me) {
			}

			qry.setAt("NAME", row, f.getName());
			qry.setAt("FULLNAME", row, f.getFullName());
			qry.setAt("UNREAD", row, CFMLEngineFactory.getInstance().getCastUtil().toDouble(f.getUnreadMessageCount()));
			qry.setAt("TOTALMESSAGES", row,
					CFMLEngineFactory.getInstance().getCastUtil().toDouble(f.getMessageCount()));
			qry.setAt("NEW", row, CFMLEngineFactory.getInstance().getCastUtil().toDouble(f.getNewMessageCount()));
			qry.setAt("PARENT", row, p != null ? p.getName() : null);
			if (recurse)
				list(f, qry, recurse, startrow, maxrows, rowsMissed);
		}
	}

	/**
	 * Open: Initiates an open session or connection with the IMAP server.
	 * 
	 * Close: Terminates the open session or connection with the IMAP server.
	 * 
	 */
	public static long[] toLongArray(String list, String delimiter) {
		int len = list.length();
		CFMLEngine eng = CFMLEngineFactory.getInstance();
		List<Long> arr = new ArrayList<>();
		if (len == 0)
			return new long[0];
		int last = 0;
		long l;
		Cast caster = eng.getCastUtil();
		char[] del = delimiter.toCharArray();
		char c;
		for (int i = 0; i < len; i++) {
			c = list.charAt(i);
			for (int y = 0; y < del.length; y++) {
				if (c == del[y]) {
					l = caster.toLong(list.substring(last, i).trim(), 0L);
					if (l > 0)
						arr.add(l);
					last = i + 1;
					break;
				}
			}
		}
		if (last < len) {
			l = caster.toLong(list.substring(last).trim(), 0L);
			if (l > 0)
				arr.add(l);
		}

		long[] longArr = new long[arr.size()];
		int index = 0;
		for (Long ll : arr) {
			longArr[index++] = ll.longValue();
		}
		return longArr;
	}
}
