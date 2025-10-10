package org.lucee.extension.mail.spooler;

import org.lucee.extension.mail.MailUtil.TemplateLine;

import lucee.runtime.Component;
import lucee.runtime.PageContext;
import lucee.runtime.exp.PageException;
import lucee.runtime.spooler.SpoolerTask;
import lucee.runtime.type.Struct;
import lucee.runtime.type.UDF;

public final class ComponentSpoolerTaskListener extends CFMLSpoolerTaskListener {

	private static final long serialVersionUID = -4726393142628827635L;
	private Component component;

	public ComponentSpoolerTaskListener(TemplateLine currTemplate, SpoolerTask task, Component component) {
		super(currTemplate, task);
		this.component = component;
	}

	@Override
	public Object _listen(PageContext pc, Struct args, boolean before) throws PageException {
		if (before) {
			if (component.get("before", null) instanceof UDF)
				return component.callWithNamedValues(pc, "before", args);
		} else {
			if (component.get("after", null) instanceof UDF)
				return component.callWithNamedValues(pc, "after", args);
			else if (component.get("listen", null) instanceof UDF)
				return component.callWithNamedValues(pc, "listen", args);
		}
		return null;
	}
}
