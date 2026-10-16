/**
 * LDEV-5823: cfimap action="delete" ignores the folder attribute, INBOX is hard-coded.
 * Self-contained: starts an embedded greenmail (IMAP + SMTP) on its own ports.
 */
component extends="org.lucee.cfml.test.LuceeTestCase" labels="mail" javaSettings='{
		"maven": [
			"com.icegreen:greenmail:2.1.7"
		]
	}' {

	import "com.icegreen.greenmail.util.ServerSetup";
	import "com.icegreen.greenmail.util.GreenMail";

	variables.imapPort = 30147;
	variables.smtpPort = 30257;
	variables.password = "ldev5823";

	function beforeAll() {
		variables.greenMail = new GreenMail( [
			new ServerSetup( variables.imapPort, nullValue(), ServerSetup::PROTOCOL_IMAP ),
			new ServerSetup( variables.smtpPort, nullValue(), ServerSetup::PROTOCOL_SMTP )
		] );
		variables.greenMail.start();
	}

	function afterAll() {
		if ( !isNull( variables.greenMail ) ) variables.greenMail.stop();
	}

	function run( testResults, testBox ) {
		describe( title="LDEV-5823 cfimap action=delete with folder", body=function() {

			it( title="deletes the message from the given folder, not from INBOX", body=function( currentSpec ) {
				var user = newMailbox( 2 );
				var folderName = "LDEV5823";

				imap action="createFolder" folder=folderName attributeCollection=imapArgs( user );
				imap action="moveMail" folder="INBOX" newFolder=folderName messageNumber="1" attributeCollection=imapArgs( user );
				expect( countMails( user, "INBOX" ) ).toBe( 1, "precondition: INBOX after move" );
				expect( countMails( user, folderName ) ).toBe( 1, "precondition: folder after move" );

				imap action="delete" folder=folderName messageNumber="1" attributeCollection=imapArgs( user );

				expect( countMails( user, folderName ) ).toBe( 0, "the message in folder [#folderName#] was not deleted" );
				expect( countMails( user, "INBOX" ) ).toBe( 1, "a message in INBOX was deleted instead of the one in [#folderName#]" );
			});

			it( title="without folder still deletes from INBOX", body=function( currentSpec ) {
				var user = newMailbox( 2 );
				imap action="delete" messageNumber="1" attributeCollection=imapArgs( user );
				expect( countMails( user, "INBOX" ) ).toBe( 1 );
			});

		});
	}

	private struct function imapArgs( required string user ) {
		return { server: "localhost", port: variables.imapPort, username: arguments.user, password: variables.password, secure: false };
	}

	private numeric function countMails( required string user, required string folder ) {
		imap action="getHeaderOnly" folder=arguments.folder name="local.qry" attributeCollection=imapArgs( arguments.user );
		return local.qry.recordCount;
	}

	// creates a fresh mailbox, sends {count} mails to it and waits until they arrived
	private string function newMailbox( required numeric count ) {
		var user = "ldev5823_" & lCase( left( replace( createUUID(), "-", "", "all" ), 16 ) ) & "@localhost";
		variables.greenMail.setUser( user, user, variables.password );
		loop from=1 to=arguments.count index="local.i" {
			mail to=user from="ldev5823@localhost" subject="LDEV-5823 mail #i#"
					server="localhost" port=variables.smtpPort spoolEnable=false {
				echo( "LDEV-5823 mail #i#" );
			}
		}
		var start = getTickCount();
		while ( countMails( user, "INBOX" ) < arguments.count && getTickCount() - start < 10000 ) sleep( 200 );
		return user;
	}
}
