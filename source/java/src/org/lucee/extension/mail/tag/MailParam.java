package org.lucee.extension.mail.tag;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.commons.mail.EmailAttachment;

import jakarta.ejb.ApplicationException;
import jakarta.servlet.jsp.tagext.Tag;
import lucee.commons.io.res.Resource;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.exp.PageException;

/**
 * Can either attach a file or add a header to a message. It is nested within a
 * cfmail tag. You can use more than one cfmailparam tag within a cfmail tag.
 *
 *
 *
 **/
public final class MailParam extends TagImpl {

	/** Indicates the value of the header. */
	private String value = "";

	/**
	 * Attaches the specified file to the message. This attribute is mutually
	 * exclusive with the name attribute.
	 */
	private String file;

	/**
	 * Specifies the name of the header. Header names are case insensitive. This
	 * attribute is mutually exclusive with the file attribute.
	 */
	private String name;
	private String fileName;

	private String type = "";
	private String disposition = null;
	private String contentID = null;
	private Boolean remove = false;
	private byte[] content = null;

	@Override
	public void release() {
		super.release();
		value = "";
		file = null;
		name = null;
		type = "";
		disposition = null;
		contentID = null;
		remove = null;
		content = null;
		fileName = null;
	}

	/**
	 * @param remove
	 *            the remove to set
	 */
	public void setRemove(boolean remove) {
		this.remove = CFMLEngineFactory.getInstance().getCastUtil().toBoolean(remove);
	}

	/**
	 * @param content
	 *            the content to set
	 * @throws ExpressionException
	 */
	public void setContent(Object content) throws PageException {
		if (content instanceof String)
			this.content = ((String) content).getBytes();
		else
			this.content = CFMLEngineFactory.getInstance().getCastUtil().toBinary(content);
	}

	/**
	 * @param type
	 */
	public void setType(String type) {
		type = type.toLowerCase().trim();

		if (type.equals("text"))
			type = "text/plain";
		else if (type.equals("plain"))
			type = "text/plain";
		else if (type.equals("html"))
			type = "text/html";
		else if (type.startsWith("multipart/"))
			return; // TODO see LDEV-570 maybe add support for content-type in the future

		this.type = type;
	}

	/**
	 * set the value value Indicates the value of the header.
	 * 
	 * @param value
	 *            value to set
	 **/
	public void setValue(String value) {
		this.value = value;
	}

	/**
	 * set the value file Attaches the specified file to the message. This attribute
	 * is mutually exclusive with the name attribute.
	 * 
	 * @param strFile
	 *            value to set
	 * @throws PageException
	 **/
	public void setFile(String strFile) throws PageException {
		this.file = strFile;
	}

	/**
	 * set the value name Specifies the name of the header. Header names are case
	 * insensitive. This attribute is mutually exclusive with the file attribute.
	 * 
	 * @param name
	 *            value to set
	 **/
	public void setName(String name) {
		this.name = name;
	}

	public void setFilename(String fileName) {
		this.fileName = fileName;
	}

	/**
	 * @param disposition
	 *            The disposition to set.
	 * @throws ApplicationException
	 */
	public void setDisposition(String disposition) throws PageException {
		disposition = disposition.trim().toLowerCase();
		if (disposition.equals("attachment"))
			this.disposition = EmailAttachment.ATTACHMENT;
		else if (disposition.equals("inline"))
			this.disposition = EmailAttachment.INLINE;
		else
			throw CFMLEngineFactory.getInstance().getExceptionUtil().createApplicationException(
					"For the tag [MailParam], the attribute [disposition] must be one of the following values [attachment, inline]");

	}

	/**
	 * @param contentID
	 *            The contentID to set.
	 */
	public void setContentid(String contentID) {
		this.contentID = contentID;
	}

	@Override
	public int doStartTag() throws PageException {
		CFMLEngine en = CFMLEngineFactory.getInstance();
		try {
			if (content != null) {
				required("mailparam", "file", file);
				String ext = en.getResourceUtil().getExtension(file, "tmp");

				if (Util.isEmpty(fileName) && !Util.isEmpty(file))
					fileName = en.getListUtil().last(file, "/\\", true);

				Resource res = en.getSystemUtil().getTempFile(ext, true);

				try {
					en.getIOUtil().copy(new ByteArrayInputStream(content), res.getOutputStream(), true, true);
				} catch (IOException e) {
					throw en.getCastUtil().toPageException(e);
				}
				this.file = res.getCanonicalPath();
				remove = true;
			} else if (!Util.isEmpty(this.file)) {
				Resource res = en.getResourceUtil().toResourceNotExisting(pageContext, this.file);
				if (res != null) {
					if (res.exists())
						pageContext.getConfig().getSecurityManager().checkFileLocation(res);
					this.file = res.getCanonicalPath();
				}
			}
		} catch (IOException ioe) {
			throw CFMLEngineFactory.getInstance().getCastUtil().toPageException(ioe);
		}

		// check attributes
		boolean hasFile = !Util.isEmpty(file);
		boolean hasName = !Util.isEmpty(name);
		// both attributes
		if (hasName && hasFile) {
			throw CFMLEngineFactory.getInstance().getExceptionUtil().createApplicationException(
					"Invalid attribute combination for tag [MailParam], you cannot use the attributes [file] and [name] together");
		}
		// no attributes
		if (!hasName && !hasFile) {
			throw CFMLEngineFactory.getInstance().getExceptionUtil().createApplicationException(
					"Invalid attribute combination for tag [MailParam], one of the attributes [file] or [name] is required");
		}

		// get Mail Tag
		Tag parent = getParent();
		while (parent != null && !(parent instanceof Mail)) {
			parent = parent.getParent();
		}

		if (parent instanceof Mail) {
			Mail mail = (Mail) parent;
			mail.setParam(type, file, fileName, name, value, disposition, contentID, remove);
		} else {
			throw CFMLEngineFactory.getInstance().getExceptionUtil()
					.createApplicationException("Wrong Context, tag [MailParam] must be inside a [Mail] tag");
		}
		return SKIP_BODY;
	}

	@Override
	public int doEndTag() {
		return EVAL_PAGE;
	}

}