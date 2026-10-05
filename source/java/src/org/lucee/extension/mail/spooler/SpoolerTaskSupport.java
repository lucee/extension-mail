package org.lucee.extension.mail.spooler;

import java.io.PrintWriter;
import java.io.StringWriter;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.config.Config;
import lucee.runtime.exp.PageException;
import lucee.runtime.spooler.ExecutionPlan;
import lucee.runtime.spooler.SpoolerTask;
import lucee.runtime.type.Array;
import lucee.runtime.type.Struct;

public abstract class SpoolerTaskSupport implements SpoolerTask {

	private static final long serialVersionUID = 2150341858025259745L;

	private long creation;
	private long lastExecution;
	private int tries = 0;
	private long nextExecution;
	private Array exceptions;
	private boolean closed;
	private String id;
	private ExecutionPlan[] plans;

	/**
	 * Constructor of the class
	 * 
	 * @param plans
	 * @param nextExecution
	 */
	public SpoolerTaskSupport(ExecutionPlan[] plans, long nextExecution) {
		this.plans = plans;
		creation = System.currentTimeMillis();

		if (nextExecution > 0)
			this.nextExecution = nextExecution;
	}

	public SpoolerTaskSupport(ExecutionPlan[] plans) {

		this(plans, 0);
	}

	@Override
	public final String getId() {
		return id;
	}

	@Override
	public final void setId(String id) {
		this.id = id;
	}

	/**
	 * return last execution of this task
	 * 
	 * @return last execution
	 */
	@Override
	public final long lastExecution() {
		return lastExecution;
	}

	@Override
	public final void setNextExecution(long nextExecution) {
		this.nextExecution = nextExecution;
	}

	@Override
	public final long nextExecution() {
		return nextExecution;
	}

	/**
	 * returns how many tries to send are already done
	 * 
	 * @return tries
	 */
	@Override
	public final int tries() {
		return tries;
	}

	final void _execute(Config config) throws PageException {
		started();
		try {
			execute(config);
		} catch (Exception e) {
			throw failed(e);
		} finally {
			lastExecution = System.currentTimeMillis();
		}
	}

	/**
	 * Count this try. Lucee core only does this bookkeeping for its own
	 * SpoolerTaskSupport and calls execute(Config) directly for tasks of the
	 * extension, so execute() has to call this itself, otherwise "tries" stays 0
	 * and the execution plan never advances (the task is retried every minute
	 * forever, LDEV-3092).
	 */
	protected final void started() {
		lastExecution = System.currentTimeMillis();
		tries++;
		if (exceptions == null)
			exceptions = CFMLEngineFactory.getInstance().getCreationUtil().createArray();
	}

	/**
	 * keep the exception of a failed try (shown in the admin task list) and
	 * return it as PageException to rethrow
	 */
	protected final PageException failed(Exception e) {
		CFMLEngine eng = CFMLEngineFactory.getInstance();
		PageException pe = eng.getCastUtil().toPageException(e);
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		Struct sct = eng.getCreationUtil().createStruct();
		sct.setEL("message", pe.getMessage());
		sct.setEL("detail", pe.getDetail());
		sct.setEL("stacktrace", sw.toString());
		sct.setEL("time", eng.getCastUtil().toLong(System.currentTimeMillis()));
		if (exceptions == null)
			exceptions = eng.getCreationUtil().createArray();
		exceptions.appendEL(sct);
		lastExecution = System.currentTimeMillis();
		return pe;
	}

	/**
	 * the task failed in a way another try cannot fix: use up the execution plan,
	 * so the spooler closes the task instead of rescheduling it
	 */
	protected final void noMoreTries() {
		int max = 0;
		if (plans != null) {
			for (ExecutionPlan plan : plans) {
				max += plan.getTries();
			}
		}
		if (tries < max)
			tries = max;
	}

	@Override
	public final Array getExceptions() {
		return exceptions;
	}

	@Override
	public final void setClosed(boolean closed) {
		this.closed = closed;
	}

	@Override
	public final boolean closed() {
		return closed;
	}

	@Override
	public ExecutionPlan[] getPlans() {
		return plans;
	}

	@Override
	public long getCreation() {
		return creation;
	}

	@Override
	public void setLastExecution(long lastExecution) {
		this.lastExecution = lastExecution;
	}
}