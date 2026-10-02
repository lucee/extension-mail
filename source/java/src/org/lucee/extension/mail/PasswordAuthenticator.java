package org.lucee.extension.mail;

import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;

/**
 * Username/password authenticator for jakarta.mail Sessions (replaces commons-email2's
 * DefaultAuthenticator, see LDEV-6485).
 *
 * The Authenticator's classloader is the one jakarta.mail uses to discover its providers, so this
 * class has to live in the extension (next to Angus Mail), not in a third-party library.
 */
public final class PasswordAuthenticator extends Authenticator {

	private final PasswordAuthentication authentication;

	public PasswordAuthenticator(String username, String password) {
		this.authentication = new PasswordAuthentication(username, password);
	}

	@Override
	protected PasswordAuthentication getPasswordAuthentication() {
		return authentication;
	}
}
