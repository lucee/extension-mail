package org.lucee.extension.mail.tag;

import org.lucee.extension.mail.MailClient;

public final class Imap extends _Mail {

	@Override
	protected int getDefaultPort() {
		if (isSecure())
			return 993;
		return 143;
	}

	@Override
	protected String getTagName() {
		return "Imap";
	}

	@Override
	protected int getType() {
		return MailClient.TYPE_IMAP;
	}
}