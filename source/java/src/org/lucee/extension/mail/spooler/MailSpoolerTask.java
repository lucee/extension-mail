package org.lucee.extension.mail.spooler;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import org.lucee.extension.mail.MailException;
import org.lucee.extension.mail.MailUtil;
import org.lucee.extension.mail.smtp.SMTPClient;

import jakarta.mail.internet.InternetAddress;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.config.Config;
import lucee.runtime.config.ConfigWeb;
import lucee.runtime.exp.PageException;
import lucee.runtime.net.mail.Server;
import lucee.runtime.spooler.ExecutionPlan;
import lucee.runtime.type.Struct;

public final class MailSpoolerTask extends SpoolerTaskSupport {
	private static final ExecutionPlan[] EXECUTION_PLANS = new ExecutionPlan[] { new ExecutionPlanImpl(1, 60),
			new ExecutionPlanImpl(1, 5 * 60), new ExecutionPlanImpl(1, 3600), new ExecutionPlanImpl(2, 24 * 3600), };

	// private static final Key CC = KeyImpl.init("cc");
	// private static final Key BCC = KeyImpl.init("bcc");

	// private static final Key FAILTO = KeyImpl.init("failto");
	// private static final Key REPLYTO = KeyImpl.init("replyto");

	private SMTPClient client;
	private Server[] servers;
	private CFMLSpoolerTaskListener listener;

	private MailSpoolerTask(ExecutionPlan[] plans, SMTPClient client, Server[] servers, long sendTime) {
		super(plans, sendTime);
		this.client = client;
		this.servers = servers;
	}

	public MailSpoolerTask(SMTPClient client, Server[] servers, long sendTime) {
		this(EXECUTION_PLANS, client, servers, sendTime);
	}

	@Override
	public String getType() {
		return "mail";
	}

	@Override
	public String subject() {
		return client.getSubject();
	}

	@Override
	public Struct detail() {
		Struct sct = CFMLEngineFactory.getInstance().getCreationUtil().createStruct();
		sct.setEL("subject", client.getSubject());

		if (client.hasHTMLText())
			sct.setEL("body", MailUtil.max(client.getHTMLTextAsString(), 1024, "..."));
		else if (client.hasPlainText())
			sct.setEL("body", MailUtil.max(client.getPlainTextAsString(), 1024, "..."));

		sct.setEL("from", toString(client.getFrom()));

		InternetAddress[] adresses = client.getTos();
		sct.setEL("to", toString(adresses));

		adresses = client.getCcs();
		if (!SMTPClient.isEmpty(adresses))
			sct.setEL("cc", toString(adresses));

		adresses = client.getBccs();
		if (!SMTPClient.isEmpty(adresses))
			sct.setEL("bcc", toString(adresses));

		return sct;
	}

	public String getCharset() {
		return client.getCharset();
	}

	public String getReplyTos() {
		return toString(client.getReplyTos());
	}

	public String getFailTos() {
		return toString(client.getFailTos());
	}

	private static String toString(InternetAddress[] adresses) {
		if (adresses == null)
			return "";

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < adresses.length; i++) {
			if (i > 0)
				sb.append(", ");
			sb.append(toString(adresses[i]));
		}
		return sb.toString();
	}

	private static String toString(InternetAddress address) {
		if (address == null)
			return "";
		String addr = address.getAddress();
		String per = address.getPersonal();
		if (Util.isEmpty(per))
			return addr;
		if (Util.isEmpty(addr))
			return per;

		return per + " (" + addr + ")";
	}

	@Override
	public Object execute(Config config) throws PageException {
		try {
			client._send((ConfigWeb) config, servers);
		} catch (MailException e) {
			throw CFMLEngineFactory.getInstance().getCastUtil().toPageException(e);
		}
		return null;
	}

	public CFMLSpoolerTaskListener getSpoolerTaskListener() {
		return listener;
	}

	// TODO switch to interface from looader
	public void setListener(CFMLSpoolerTaskListener listener) {
		this.listener = listener;
	}

	public void mod(Struct sct) throws UnsupportedEncodingException, PageException, MailException {

		// charset
		String str = CFMLEngineFactory.getInstance().getCastUtil().toString(sct.get("charset", null), null);
		if (str != null) {
			Charset cs = CFMLEngineFactory.getInstance().getCastUtil().toCharset(str, null);
			if (cs != null)
				client.setCharset(cs);
		}

		// FROM
		Object o = sct.get("from", null);
		if (o != null)
			client.setFrom(MailUtil.toInternetAddress(o));

		// TO
		o = sct.get("to", null);
		if (o != null)
			client.setTos(MailUtil.toInternetAddresses(o));

		// CC
		o = sct.get("cc", null);
		if (o != null)
			client.setCCs(MailUtil.toInternetAddresses(o));

		// BCC
		o = sct.get("bcc", null);
		if (o != null)
			client.setBCCs(MailUtil.toInternetAddresses(o));

		// failto
		o = sct.get("failto", null);
		if (o != null)
			client.setFailTos(MailUtil.toInternetAddresses(o));

		// replyto
		o = sct.get("replyto", null);
		if (o != null)
			client.setReplyTos(MailUtil.toInternetAddresses(o));

		// subject
		o = sct.get("subject", null);
		if (o != null)
			client.setSubject(MailUtil.collapseWhitespace(CFMLEngineFactory.getInstance().getCastUtil().toString(o)));

	}

	public static class ExecutionPlanImpl implements ExecutionPlan {

		private int tries;
		private int interval;

		public ExecutionPlanImpl(int tries, int interval) {
			this.tries = tries;
			this.interval = interval;
		}

		/**
		 * @return the tries
		 */
		@Override
		public int getTries() {
			return tries;
		}

		/**
		 * @return the interval in seconds
		 */
		@Override
		public int getIntervall() {
			return interval;
		}

		@Override
		public int getInterval() {
			return interval;
		}
	}
}