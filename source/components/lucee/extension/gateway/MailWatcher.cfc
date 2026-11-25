<!---
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
 --->
 component hint="Mail Watcher" {
	variables.state = "stopped";

	public void function init( string id, struct config, component listener ) output=false {
		variables.id = id;
		variables.config = config;
		variables.listener = listener;
		variables.logFile = config.logFile ?: "MailWatcher";
		variables.config.deleteAfterFetch = config.deleteAfterFetch ?: false;
		variables.config.processBacklog = config.processBacklog ?: false;
		variables.config.attachmentPath = expandPath( variables.config.attachmentPath );

		variables.backlogDate = CreateDate( 1970, 1, 1 );

		logger( text="init" );
	}

	public void function start() output=false localmode=true {

		if ( getState() EQ "stopping" ) {
			sleep(10);
		}
		variables.state="running";
		logger ( text="start" );
		var last = now();
		var mail = "";

		if ( variables.config.processBacklog )
			last = variables.backlogDate;

		while ( variables.state EQ "running" ) {

			if ( !directoryExists( config.attachmentPath ) )
				directoryCreate( config.attachmentPath );

			try {
				lock name="mailwatcher-#id#" throwOnTimeout=true timeout=1 type="exclusive" {
					if ( len( trim( config.functionFetch ?: "" ) ) ) {
						mails = variables.listener[ config.functionFetch ]( config, last );
					} else {
						mails = getMailsNewerThan( server=config.server, port=config.port, secure=config.secure?:false,
							user=config.username, pass=config.password, attachmentPath=config.attachmentPath,
							folder=config.folder, newerThan=last);
					}
					for ( var el in mails ) {
						if ( len( trim( config.functionName ) ) ) {
							variables.listener[ config.functionName ]( el );
						}
					}
					last = now();
				}
			} catch (any cfcatch) {
				logger ( text=cfcatch.message, file=variables.logFile, type="error", cause=cfcatch );
			}
			if ( getState() != "running" ) {
				break;
			}
			sleep( variables.config.interval?:10 );
		}
		// set to stopped when we leave
		variables.state =  "stopped";
	}

	public array function getMailsNewerThan( required string server, required numeric port, required boolean secure,
			required string user, required string pass,
			required string attachmentPath, required string folder,
			required date newerThan) output=true {
		var mails="";
		var arr=[];
		var startTime = getTickCount();
		var processed = 0;
		var deleted = 0;

		switch( variables.config.serverType ){
			case "pop3":
				cfpop( attachmentPath=arguments.attachmentPath,
					server=arguments.server,
					port=arguments.port,
					secure=arguments.secure,
					generateUniqueFilenames=true,
					password=arguments.pass,
					name="mails",
					action="getAll",
					username=arguments.user );
				break;
			case "imap":
				mails = fetchImap( arguments, newerThan );
				break;
			default:
				throw "Unsupported server type [#variables.config.serverType#]"
		}

		loop query="mails" {
			if (mails.date GTE arguments.newerThan
					|| (variables.config.processBacklog && variables.backlogDate EQ arguments.newerThan ) ){
				processed++;
				ArrayAppend( arr, QueryRowData( mails, mails.currentrow ) );

				switch(variables.config.serverType){
					case "pop3":
						if ( variables.config.deleteAfterFetch ){
							cfpop(server=arguments.server,
								port=arguments.port,
								secure=arguments.secure,
								username=arguments.user,
								password=arguments.pass,
								name="mails",
								action="delete",
								uid=mails.uid
							);
							deleted++;
						}
						break;
					case "imap":
						if ( variables.config.deleteAfterFetch ){
							cfimap(server=arguments.server,
								port=arguments.port,
								secure=arguments.secure,
								username=arguments.user,
								password=arguments.pass,
								folder=arguments.folder,
								action="delete",
								messagenumber=mails.messageNumber
							);
							deleted++;
						} else {
							cfimap(server=arguments.server,
								port=arguments.port,
								secure=arguments.secure,
								folder=arguments.folder,
								username=arguments.user,
								password=arguments.pass,
								action="markread",
								messagenumber=mails.messageNumber
							);
						}
						break;
				}
			}
		}
		logger ( text="found [ #arrayLen(arr)# ] mails, processed [ #processed# ], deleted [#deleted#] in #numberFormat(getTickCount()-startTime)#ms", type="DEBUG");

		return arr;
	}

	public void function stop() output=false {
		logger ( text="stop", type="information" );
		variables.state="stopping";

		sleep( (variables.config.interval?:10)+10 );
		// should be stopped, so we guess it is blockes somehow, because it will not run again, we can ignore it
		if (getState() EQ "stopping" ) {
			variables.state="stopped";
		}
	}

	public void function restart() output=false {
		if ( variables.state == "running" ) {
			stop();
		}
		start();
	}

	public string function getState() output=false {
		return variables.state;
	}

	public string function sendMessage(struct data) output=false {
		return "sendGatewayMessage() has !been implemented for the event gateway [MailWatcher]. If you want to modify it, please edit the following CFC:"& expandpath("./") & "MailWatcher.cfc";
	}

	private function logger(text, type="INFO", cause){
		var prefix = "MailWatcher ";
		if ( isNull( arguments.cause ) ) {
			writeLog ( text=prefix & arguments.text, file=variables.logFile, type=arguments.type, application=false );
		} else {
			writeLog ( text=prefix & arguments.text, file=variables.logFile, type=arguments.type, application=false, cause=arguments.cause);
		}
	}

	// only fetch new mails
	public query function fetchImap( required struct config, required date newerThan ) output=false {

		// check inbox status, fetching inbox can be expensive
		cfimap( server=arguments.config.server,
			port=arguments.config.port,
			secure=arguments.config.secure,
			password=arguments.config.pass,
			username=arguments.config.user,
			name="local.folders",
			action="listallfolders"
		);

		local.folders = queryToStruct( folders, "name" );
		if ( structKeyExists( folders, arguments.config.folder ) ) {
			if ( folders[ arguments.config.folder ].unread eq 0 
					and folders[ arguments.config.folder ].new eq 0 ){
				logger(text="Imap folder [#arguments.config.folder#] had 0 new and 0 unread messages, skip reading", type="TRACE");
				return queryNew("empty");
			}
		} else {
			logger(text="Imap folder [#arguments.config.folder#] doesn't exist on server?", type="TRACE");
		}

		cfimap( attachmentPath=arguments.config.attachmentPath,
			server=arguments.config.server,
			port=arguments.config.port,
			secure=arguments.config.secure,
			generateUniqueFilenames=true,
			password=arguments.config.pass,
			username=arguments.config.user,
			folder=arguments.config.folder,
			name="local.mail_headers",
			action="getHeaderOnly"
		);
		var messages = [];
		var backlog = 0;
		loop query="mail_headers" {
			if ( mail_headers.date GTE arguments.newerThan ){
				arrayAppend( messages, mail_headers.uid );
			} else if ( variables.config.processBacklog && variables.backlogDate EQ arguments.newerThan ){
				// processing backlog, check message flags
				if ( !mail_headers.seen || variables.config.deleteAfterFetch ) {
					arrayAppend( messages, mail_headers.uid );
					backlog++;
				}
			}
		}
		if (backlog > 0)
			logger(text="imap.processBacklog found [#backlog#] older messages in backlog", type="TRACE");
		logger(text="imap.getHeaderOnly returned [#mail_headers.recordcount#], found [#arrayLen( messages )#] new messages", type="TRACE");
		if ( !arrayLen( messages ) )
			return queryNew("empty");
		// only fetch the latest
		cfimap( attachmentPath=arguments.config.attachmentPath,
			server=arguments.config.server,
			port=arguments.config.port,
			secure=arguments.config.secure,
			generateUniqueFilenames=true,
			password=arguments.config.pass,
			username=arguments.config.user,
			folder=arguments.config.folder,
			name="local.mails",
			uid="#messages.toList()#",
			action="getAll"
		);
		return mails;
	}
}