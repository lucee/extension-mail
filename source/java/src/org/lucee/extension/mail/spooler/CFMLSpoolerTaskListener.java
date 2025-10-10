package org.lucee.extension.mail.spooler;

import java.io.OutputStream;
import java.io.Serializable;

import org.lucee.extension.mail.MailUtil;
import org.lucee.extension.mail.MailUtil.TemplateLine;

import lucee.commons.io.log.Log;
import lucee.commons.lang.Pair;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.PageContext;
import lucee.runtime.config.Config;
import lucee.runtime.config.ConfigWeb;
import lucee.runtime.exp.PageException;
import lucee.runtime.spooler.SpoolerTask;
import lucee.runtime.type.Struct;

public abstract class CFMLSpoolerTaskListener {

	private final SpoolerTask task;
	private TemplateLine currTemplate;
	private CFMLEngine eng;

	public CFMLSpoolerTaskListener(TemplateLine currTemplate, SpoolerTask task) {
		this.eng = CFMLEngineFactory.getInstance();
		this.task = task;
		this.currTemplate = currTemplate;
	}

	public void listen(Config config, Exception e, boolean before) {

		if (!(config instanceof ConfigWeb))
			return;
		ConfigWeb cw = (ConfigWeb) config;

		PageContext pc = eng.getThreadPageContext();
		boolean pcCreated = false;
		try {
			if (pc == null) {
				pcCreated = true;
				Pair[] parr = new Pair[0];
				DevNullOutputStream os = DevNullOutputStream.DEV_NULL_OUTPUT_STREAM;
				pc = MailUtil.createPageContext(cw);

				pc.setRequestTimeout(config.getRequestTimeout().getMillis());
			}

			Struct args = eng.getCreationUtil().createStruct();

			long l = task.lastExecution();
			if (l > 0)
				args.set("lastExecution", eng.getCreationUtil().createDate(l));
			l = task.nextExecution();
			if (l > 0)
				args.set("nextExecution", eng.getCreationUtil().createDate(l));
			args.set("created", eng.getCreationUtil().createDate(task.getCreation()));
			args.set("id", task.getId());
			args.set("type", task.getType());

			Struct details = task.detail();
			if (task instanceof MailSpoolerTask) {
				details.set("charset", ((MailSpoolerTask) task).getCharset());
				details.set("replyto", ((MailSpoolerTask) task).getReplyTos());
				details.set("failto", ((MailSpoolerTask) task).getFailTos());
			}
			args.set("detail", details);

			args.set("tries", task.tries());
			args.set("remainingtries", e == null ? 0 : task.getPlans().length - task.tries());
			args.set("closed", task.closed());
			if (!before)
				args.set("passed", e == null);
			if (e != null)
				args.set("exception", eng.getCastUtil().toPageException(e).getCatchBlock(cw));

			Struct curr = eng.getCreationUtil().createStruct();
			args.set("caller", curr);
			curr.set("template", currTemplate.template);
			curr.set("line", Double.valueOf(currTemplate.line));

			Struct adv = eng.getCreationUtil().createStruct();
			args.set("advanced", adv);
			adv.set("exceptions", task.getExceptions());
			adv.set("executedPlans", task.getPlans());

			Object o = _listen(pc, args, before);
			if (before && o instanceof Struct && task instanceof MailSpoolerTask) {
				((MailSpoolerTask) task).mod((Struct) o);
			}

		} catch (Exception pe) {
			Log log = config.getLog("mail");
			if (log != null)
				log.error(CFMLSpoolerTaskListener.class.getName(), pe);

		} finally {
			if (pcCreated)
				MailUtil.releasePageContext(pc);
		}
	}

	public abstract Object _listen(PageContext pc, Struct args, boolean before) throws PageException;

	private static final class DevNullOutputStream extends OutputStream implements Serializable {

		private static final long serialVersionUID = -6738851699671626485L;
		public static final DevNullOutputStream DEV_NULL_OUTPUT_STREAM = new DevNullOutputStream();

		/**
		 * Constructor of the class
		 */
		private DevNullOutputStream() {
		}

		@Override
		public void close() {
		}

		@Override
		public void flush() {
		}

		@Override
		public void write(byte[] b, int off, int len) {
		}

		@Override
		public void write(byte[] b) {
		}

		@Override
		public void write(int b) {
		}

	}
}