/**
 *
 * Copyright (c) 2014, the Railo Company Ltd. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either 
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public 
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 * 
 **/
package org.lucee.extension.mail.smtp;

import javax.mail.Address;
import javax.mail.SendFailedException;
import javax.mail.Transport;

import org.lucee.extension.mail.smtp.SMTPClient.MimeMessageAndSession;

import lucee.loader.util.Util;

public final class SMTPSender extends Thread {

	private boolean isSent = false;
	private Exception throwable;
	private Object lock;
	private String host;
	private int port;
	private String user;
	private String pass;
	private MimeMessageAndSession mmas;
	private boolean recyleConnection;

	public SMTPSender(Object lock, MimeMessageAndSession mmas, String host, int port, String user, String pass,
			boolean reuseConnection) {
		this.lock = lock;
		this.mmas = mmas;

		this.host = host;
		this.port = port;
		this.user = user;
		this.pass = pass;
		this.recyleConnection = reuseConnection;
	}

	@Override
	public void run() {
		Transport transport = null;
		try {
			transport = mmas.session.transport;
			if (user == null)
				pass = null;

			// connect
			if (!transport.isConnected())
				transport.connect(host, port, user, pass);

			mmas.message.saveChanges();
			if (!Util.isEmpty(mmas.messageId))
				mmas.message.setHeader("Message-ID", mmas.messageId);

			try {
				transport.sendMessage(mmas.message, mmas.message.getAllRecipients());
				isSent = true;
			} catch (Exception sendException) {
				// Connection might be stale - check and retry once
				if (!transport.isConnected() || isConnectionStale(sendException)) {
					try {
						// Force reconnect
						if (transport.isConnected()) {
							transport.close();
						}
						transport.connect(host, port, user, pass);

						// Retry send
						transport.sendMessage(mmas.message, mmas.message.getAllRecipients());
						isSent = true;
					} catch (Exception retryException) {
						// Retry failed, throw original exception
						throw retryException;
					}
				} else {
					// Not a connection issue, rethrow
					throw sendException;
				}
			}

		} catch (SendFailedException sfe) {
			Address[] valid = sfe.getValidSentAddresses();
			if (valid != null && valid.length > 0)
				isSent = true;
			this.throwable = sfe;
		} catch (Exception e) {
			this.throwable = e;
		} finally {
			try {
				if (recyleConnection)
					SMTPConnectionPool.releaseSessionAndTransport(mmas.session);
				else
					SMTPConnectionPool.disconnect(mmas.session.transport);
			} catch (Exception e) {
				// TODO log
			}
			synchronized (lock) {
				lock.notify();
			}
		}
	}

	// ========== ADD THIS HELPER METHOD ==========
	/**
	 * Check if exception indicates a stale/closed connection
	 */
	private boolean isConnectionStale(Exception e) {
		String message = e.getMessage();
		if (message == null)
			return false;

		// Common indicators of closed/stale connections
		return message.contains("[EOF]") || message.contains("Connection closed") || message.contains("Broken pipe")
				|| message.contains("Connection reset")
				|| e instanceof javax.mail.MessagingException && e.getCause() instanceof java.net.SocketException;
	}

	/**
	 * @return the messageExpection
	 */
	public Throwable getThrowable() {
		return throwable;
	}

	/**
	 * @return was message sent
	 */
	public boolean isSent() {
		return isSent;
	}

}