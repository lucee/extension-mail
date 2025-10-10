package org.lucee.extension.mail.tag;

import jakarta.servlet.jsp.tagext.Tag;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.PageContext;
import lucee.runtime.exp.PageException;

/**
 * Implementation of the Tag
 */
public abstract class TagImpl implements Tag {

	protected transient PageContext pageContext;
	private transient Tag parent;
	private transient CFMLEngine eng;

	public CFMLEngine eng() {
		if (eng == null)
			eng = CFMLEngineFactory.getInstance();
		return eng;
	}

	/**
	 * sets a PageContext
	 * 
	 * @param pageContext
	 */
	public void setPageContext(PageContext pageContext) {
		this.pageContext = pageContext;
	}

	@Override
	public void setPageContext(jakarta.servlet.jsp.PageContext pageContext) {
		this.pageContext = (PageContext) pageContext;
	}

	@Override
	public void setParent(Tag parent) {
		this.parent = parent;
	}

	@Override
	public Tag getParent() {
		return parent;
	}

	@Override
	public int doStartTag() throws PageException {
		return SKIP_BODY;
	}

	@Override
	public int doEndTag() throws PageException {
		return EVAL_PAGE;
	}

	@Override
	public void release() {
		pageContext = null;
		parent = null;
		eng = null;
	}

	/**
	 * check if value is not empty
	 * 
	 * @param tagName
	 * @param attributeName
	 * @param attribute
	 */
	public void required(String tagName, String actionName, String attributeName, Object attribute)
			throws PageException {
		if (attribute == null)
			throw eng().getExceptionUtil().createApplicationException("Attribute [" + attributeName + "] for tag ["
					+ tagName + "] is required if attribute action has the value [" + actionName + "]");

	}

	public void required(String tagName, String attributeName, Object attribute) throws PageException {
		if (attribute == null)
			throw eng().getExceptionUtil().createApplicationException(
					"Attribute [" + attributeName + "] for tag [" + tagName + "] is required");

	}

	public void required(String tagName, String actionName, String attributeName, String attribute, boolean trim)
			throws PageException {
		if (Util.isEmpty(attribute, trim))
			throw eng().getExceptionUtil().createApplicationException("Attribute [" + attributeName + "] for tag ["
					+ tagName + "] is required if attribute action has the value [" + actionName + "]");
	}

	public void required(String tagName, String actionName, String attributeName, double attributeValue,
			double nullValue) throws PageException {
		if (attributeValue == nullValue)
			throw eng().getExceptionUtil().createApplicationException("Attribute [" + attributeName + "] for tag ["
					+ tagName + "] is required if attribute action has the value [" + actionName + "]");
	}
}