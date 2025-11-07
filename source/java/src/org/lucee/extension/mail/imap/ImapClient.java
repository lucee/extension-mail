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
package org.lucee.extension.mail.imap;

import org.lucee.extension.mail.MailClient;

import com.sun.mail.imap.IMAPFolder;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import lucee.loader.engine.CFMLEngineFactory;

public final class ImapClient extends MailClient {

	public ImapClient(String server, int port, String username, String password, boolean secure) {
		super(server, port, username, password, secure);
	}

	@Override
	protected String _getId(Folder folder, Message message) throws MessagingException {
		return CFMLEngineFactory.getInstance().getCastUtil().toString(((IMAPFolder) folder).getUID(message));
	}

	@Override
	protected String getTypeAsString() {
		return "imap";
	}

	@Override
	protected int getType() {
		return TYPE_IMAP;
	}

}