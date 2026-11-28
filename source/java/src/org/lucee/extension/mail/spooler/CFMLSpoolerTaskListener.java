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
import lucee.runtime.spooler.listener.SpoolerTaskListener;
import lucee.runtime.type.Struct;

/**
 * Implements the loader's (Serializable) SpoolerTaskListener: the listener is a field of the spooled
 * MailSpoolerTask, so it is written to the .tsk file with the task. A listener that is not
 * serializable makes the task impossible to store, and the spooler then sends the mail in the
 * calling thread, without queue, retries or listener (LDEV-7024).
 */
public abstract class CFMLSpoolerTaskListener implements SpoolerTaskListener {

	private static final long serialVersionUID = 4160934716394586277L;

	private final SpoolerTask task;
	private TemplateLine currTemplate;
	// the engine is not serializable and is the same for the whole JVM, so it is looked up again
	private transient CFMLEngine eng;

	public CFMLSpoolerTaskListener(TemplateLine currTemplate, SpoolerTask task) {
		this.task = task;
		this.currTemplate = currTemplate;
	}

	@Override
	public void listen(Config config, Exception e, boolean before) {

		if (!(config instanceof ConfigWeb))
			return;
		CFMLEngine eng = engine();
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
			args.set("remainingtries", e == null ? 0 : Math.max(0, task.getPlans().length - task.tries()));
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

	private CFMLEngine engine() {
		if (eng == null)
			eng = CFMLEngineFactory.getInstance();
		return eng;
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