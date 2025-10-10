package org.lucee.extension.mail.tag;

import java.nio.charset.Charset;

import jakarta.servlet.jsp.tagext.Tag;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.exp.PageException;

/**
 * Specifies one part of a multipart e-mail message. Can only be used in the
 * cfmail tag. You can use more than one cfmailpart tag within a cfmail tag
 *
 *
 *
 **/
public final class MailPart extends BodyTagImpl {

	org.lucee.extension.mail.MailPart part = new org.lucee.extension.mail.MailPart();

	@Override
	public void release() {
		super.release();
		part = new org.lucee.extension.mail.MailPart();
	}

	/**
	 * @param type
	 *            The type to set.
	 */
	public void setType(String type) throws PageException {
		part.setType(type);
	}

	/**
	 * @param charset
	 *            The charset to set.
	 */
	public void setCharset(Charset charset) {
		part.setCharset(charset);
	}

	public void setCharset(String charset) throws PageException {
		setCharset(CFMLEngineFactory.getInstance().getCastUtil().toCharset(charset));
	}

	/**
	 * @param wraptext
	 *            The wraptext to set.
	 */
	public void setWraptext(double wraptext) {
		part.setWraptext((int) wraptext);
	}

	@Override
	public int doStartTag() {

		return EVAL_BODY_BUFFERED;
	}

	@Override
	public void doInitBody() {

	}

	@Override
	public int doAfterBody() {
		part.setBody(bodyContent.getString());
		return SKIP_BODY;
	}

	@Override
	public int doEndTag() throws PageException {

		getMail().addPart(part);
		/*
		 * String type = part.getType(); if(StringUtil.isEmpty(part.getCharset()))
		 * part.setCharset(mail.getCharset()); if(type!=null &&
		 * (type.equals("text/plain") || type.equals("plain") || type.equals("text"))){
		 * part.isPlain(true); mail.setBodyPart(part); } else if(type!=null &&
		 * (type.equals("text/html") || type.equals("html") || type.equals("htm"))){
		 * part.isHTML(true); mail.setBodyPart(part); } else {
		 * 
		 * getMail().setParam(type, null, "susi", part.getBody(), "inline", null); }
		 */
		// throw new ApplicationException("attribute type of tag mailpart has an invalid
		// values","valid
		// values are [plain,text,html] but value is now ["+type+"]");

		return EVAL_PAGE;
	}

	private Mail getMail() throws PageException {
		Tag parent = getParent();
		while (parent != null && !(parent instanceof Mail)) {
			parent = parent.getParent();
		}
		if (parent instanceof Mail)
			return (Mail) parent;
		throw CFMLEngineFactory.getInstance().getExceptionUtil()
				.createApplicationException("Wrong Context, tag MailPart must be inside a Mail tag");
	}
}