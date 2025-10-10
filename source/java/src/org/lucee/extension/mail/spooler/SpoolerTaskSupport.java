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

		lastExecution = System.currentTimeMillis();
		tries++;
		CFMLEngine eng = CFMLEngineFactory.getInstance();

		if (exceptions == null)
			exceptions = eng.getCreationUtil().createArray();

		try {
			execute(config);
		} catch (Exception e) {
			PageException pe = eng.getCastUtil().toPageException(e);
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			Struct sct = eng.getCreationUtil().createStruct();
			sct.setEL("message", pe.getMessage());
			sct.setEL("detail", pe.getDetail());
			sct.setEL("stacktrace", sw.toString());
			sct.setEL("time", eng.getCastUtil().toLong(System.currentTimeMillis()));
			exceptions.appendEL(sct);

			throw pe;
		} finally {
			lastExecution = System.currentTimeMillis();
		}
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