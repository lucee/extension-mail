/**
 * LDEV-6993: Mail Watcher event gateway missing after mail moved from Lucee core to the mail extension.
 *
 * Lucee core removed core/src/main/cfml/context/gateway/MailWatcher.cfc when mail moved to this extension,
 * but core still ships the admin driver (admin/gdriver/MailWatcher.cfc, CFC path "lucee.extension.gateway.MailWatcher")
 * and MailWatcherListener.cfc. A Mail Watcher gateway therefore did not start and Lucee logged
 * "Page [/lucee/extension/gateway/MailWatcher.cfc] not found".
 *
 * The extension now ships components/lucee/extension/gateway/MailWatcher.cfc (the last core version, with three fixes:
 * POP deleteAfterFetch did not delete, arrayLen() on a number in the IMAP backlog log, missing comma in the IMAP getAll call).
 *
 * The gateway component is created the way the gateway engine does it (createObject, then init( id, config, listener )),
 * against an in-process GreenMail server (SMTP, POP3, IMAP), like ImapTest.cfc and PopTest.cfc.
 */
component extends="org.lucee.cfml.test.LuceeTestCase" labels="LDEV6993" javaSettings='{
		"maven": [
			"com.icegreen:greenmail:2.1.7"
		]
	}' {

	import "com.icegreen.greenmail.util.ServerSetup";
	import "com.icegreen.greenmail.util.GreenMail";

	variables.smtpPort = 30253;
	variables.popPort = 30111;
	variables.imapPort = 30144;
	variables.username = "ldev6993@localhost.com";
	variables.password = "ldev6993pass";
	variables.from = "sender@localhost.com";
	variables.to = "ldev6993@localhost.com";

	function beforeAll() {
		var setups = [
			new ServerSetup( variables.smtpPort, nullValue(), ServerSetup::PROTOCOL_SMTP ),
			new ServerSetup( variables.popPort, nullValue(), ServerSetup::PROTOCOL_POP3 ),
			new ServerSetup( variables.imapPort, nullValue(), ServerSetup::PROTOCOL_IMAP )
		];
		variables.greenMail = new GreenMail( setups );
		variables.greenMail.start();
		variables.greenMail.setUser( variables.username, variables.username, variables.password );

		variables.attachmentPath = getTempDirectory() & "ldev6993_attachments/";
		if ( !directoryExists( variables.attachmentPath ) ) directoryCreate( variables.attachmentPath );
	}

	function afterAll() {
		if ( !isNull( variables.greenMail ) ) {
			variables.greenMail.purgeEmailFromAllMailboxes();
			variables.greenMail.stop();
		}
		if ( directoryExists( variables.attachmentPath ) ) directoryDelete( variables.attachmentPath, true );
	}

	function run( testResults, testBox ) {
		describe( title="LDEV-6993: Mail Watcher event gateway (lucee.extension.gateway.MailWatcher) is shipped with the mail extension", body=function() {

			it( title="the gateway CFC lucee.extension.gateway.MailWatcher, used by the admin driver, can be created", body=function( currentSpec ) {
				// no "new": that would call init() without the arguments the gateway engine passes
				var gw = createObject( "component", "lucee.extension.gateway.MailWatcher" );
				expect( gw.getState() ).toBe( "stopped" );
			});

			it( title="the gateway components shipped by Lucee core (MailWatcherListener, DirectoryWatcher) are still available", body=function( currentSpec ) {
				expect( getMetadata( createObject( "component", "lucee.extension.gateway.MailWatcherListener" ) ).name )
					.toBe( "lucee.extension.gateway.MailWatcherListener" );
				expect( getMetadata( createObject( "component", "lucee.extension.gateway.DirectoryWatcher" ) ).name )
					.toBe( "lucee.extension.gateway.DirectoryWatcher" );
			});

			it( title="imap: getMailsNewerThan() returns a mail received after the given date", body=function( currentSpec ) {
				lock name="test:ldev6993" {
					variables.greenMail.purgeEmailFromAllMailboxes();
					var newerThan = dateAdd( "n", -1, now() );
					sendTestEmail( "LDEV6993 imap", "LDEV6993 imap body" );
					sleep( 500 );

					var gw = createGateway( serverType="imap", port=variables.imapPort, deleteAfterFetch=false );
					var mails = fetch( gw, variables.imapPort, newerThan );

					expect( mails ).toHaveLength( 1 );
					expect( mails[ 1 ].subject ).toBe( "LDEV6993 imap" );
					expect( mails[ 1 ].body ).toInclude( "LDEV6993 imap body" );
				}
			});

			it( title="pop3: getMailsNewerThan() returns a mail received after the given date and keeps it when deleteAfterFetch=false", body=function( currentSpec ) {
				lock name="test:ldev6993" {
					variables.greenMail.purgeEmailFromAllMailboxes();
					var newerThan = dateAdd( "n", -1, now() );
					sendTestEmail( "LDEV6993 pop3", "LDEV6993 pop3 body" );
					sleep( 500 );

					var gw = createGateway( serverType="pop3", port=variables.popPort, deleteAfterFetch=false );
					var mails = fetch( gw, variables.popPort, newerThan );

					expect( mails ).toHaveLength( 1 );
					expect( mails[ 1 ].subject ).toBe( "LDEV6993 pop3" );
					expect( mails[ 1 ].body ).toInclude( "LDEV6993 pop3 body" );
					expect( popCount() ).toBe( 1 );
				}
			});

			it( title="pop3: deleteAfterFetch=true deletes the fetched mail from the server (cfpop action=delete)", body=function( currentSpec ) {
				lock name="test:ldev6993" {
					variables.greenMail.purgeEmailFromAllMailboxes();
					var newerThan = dateAdd( "n", -1, now() );
					sendTestEmail( "LDEV6993 pop3 delete", "LDEV6993 pop3 delete body" );
					sleep( 500 );
					expect( popCount() ).toBe( 1 );

					var gw = createGateway( serverType="pop3", port=variables.popPort, deleteAfterFetch=true );
					var mails = fetch( gw, variables.popPort, newerThan );

					expect( mails ).toHaveLength( 1 );
					expect( mails[ 1 ].subject ).toBe( "LDEV6993 pop3 delete" );
					expect( popCount() ).toBe( 0 );
				}
			});

			it( title="start() polls the server and passes each new mail to the listener function, stop() ends the gateway", body=function( currentSpec ) {
				lock name="test:ldev6993" {
					variables.greenMail.purgeEmailFromAllMailboxes();
					sendTestEmail( "LDEV6993 start", "LDEV6993 start body" );
					sleep( 500 );

					var listener = new component {
						variables.received = [];
						public void function onMail( struct mail ) {
							arrayAppend( variables.received, arguments.mail.subject );
						}
						public array function getReceived() {
							return variables.received;
						}
					};
					// processBacklog=true, so the first poll also picks up the mail sent before start()
					var gw = createGateway( serverType="pop3", port=variables.popPort, deleteAfterFetch=true,
						processBacklog=true, listener=listener );

					thread name="ldev6993gateway" action="run" gw=gw {
						attributes.gw.start();
					}
					try {
						var waited = 0;
						while ( arrayLen( listener.getReceived() ) == 0 && waited < 10000 ) {
							sleep( 100 );
							waited += 100;
						}
						expect( gw.getState() ).toBe( "running" );
					}
					finally {
						gw.stop();
						thread action="join" name="ldev6993gateway" timeout=10000;
					}

					expect( listener.getReceived() ).toBe( [ "LDEV6993 start" ] );
					expect( gw.getState() ).toBe( "stopped" );
					expect( popCount() ).toBe( 0 );
				}
			});

		});
	}

	private function createGateway( required string serverType, required numeric port, required boolean deleteAfterFetch,
			boolean processBacklog=false, component listener ) {
		if ( isNull( arguments.listener ) ) {
			arguments.listener = new component {
				public void function onMail( struct mail ) {}
			};
		}
		// like the gateway engine: create the component, then call init( id, config, listener )
		var gw = createObject( "component", "lucee.extension.gateway.MailWatcher" );
		gw.init( "ldev6993-#arguments.serverType#", {
			"serverType": arguments.serverType,
			"server": "localhost",
			"port": arguments.port,
			"secure": false,
			"username": variables.username,
			"password": variables.password,
			"folder": "INBOX",
			"attachmentPath": variables.attachmentPath,
			"deleteAfterFetch": arguments.deleteAfterFetch,
			"processBacklog": arguments.processBacklog,
			"functionName": "onMail",
			"interval": 200
		}, arguments.listener );
		return gw;
	}

	private array function fetch( required gw, required numeric port, required date newerThan ) {
		return arguments.gw.getMailsNewerThan(
			server="localhost",
			port=arguments.port,
			secure=false,
			user=variables.username,
			pass=variables.password,
			attachmentPath=variables.attachmentPath,
			folder="INBOX",
			newerThan=arguments.newerThan
		);
	}

	private numeric function popCount() {
		pop action="getHeaderOnly"
			name="local.qry"
			server="localhost"
			port=variables.popPort
			username=variables.username
			password=variables.password
			secure=false;
		return qry.recordCount;
	}

	private function sendTestEmail( required string subject, required string body ) {
		mail to=variables.to
			from=variables.from
			subject=arguments.subject
			spoolEnable=false
			server="localhost"
			port=variables.smtpPort {
			echo( arguments.body );
		}
	}
}
