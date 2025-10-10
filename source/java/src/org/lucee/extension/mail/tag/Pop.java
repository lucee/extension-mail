package org.lucee.extension.mail.tag;

import org.lucee.extension.mail.MailClient;

/**
 * Retrieves and deletes e-mail messages from a POP mail server.
 */
public final class Pop extends _Mail {

	@Override
	protected int getDefaultPort() {
		if (isSecure())
			return 995;
		return 110;
	}

	@Override
	protected String getTagName() {
		return "Pop";
	}

	@Override
	protected int getType() {
		return MailClient.TYPE_POP3;
	}
}