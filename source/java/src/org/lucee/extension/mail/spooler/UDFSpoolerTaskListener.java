package org.lucee.extension.mail.spooler;

import org.lucee.extension.mail.MailUtil.TemplateLine;

import lucee.runtime.PageContext;
import lucee.runtime.exp.PageException;
import lucee.runtime.spooler.SpoolerTask;
import lucee.runtime.type.Struct;
import lucee.runtime.type.UDF;

public final class UDFSpoolerTaskListener extends CFMLSpoolerTaskListener {

	private static final long serialVersionUID = 1262226524494987654L;
	private UDF before;
	private UDF after;

	public UDFSpoolerTaskListener(TemplateLine currTemplate, SpoolerTask task, UDF before, UDF after) {
		super(currTemplate, task);
		this.before = before;
		this.after = after;
	}

	@Override
	public Object _listen(PageContext pc, Struct args, boolean before) throws PageException {
		if (before) {
			if (this.before != null)
				return this.before.callWithNamedValues(pc, args, true);
		} else {
			if (this.after != null)
				return after.callWithNamedValues(pc, args, true);
		}
		return null;
	}

}
