component extends="org.lucee.cfml.test.LuceeTestCase" labels="mail" javaSettings='{
		"maven": [
			"com.icegreen:greenmail:2.1.7"
		]
	}' {

	import "com.icegreen.greenmail.util.ServerSetup";
	import "com.icegreen.greenmail.util.GreenMail";

	variables.port = 30253;
	variables.from = "susi@sorglos.de";
	variables.to = "geisse@peter.ch";

	function beforeAll() {
		variables.smtp = new GreenMail( new ServerSetup( variables.port, nullValue(), ServerSetup::PROTOCOL_SMTP ) );
		variables.smtp.start();
	}

	function afterAll() {
		if ( !isNull( variables.smtp ) ) variables.smtp.stop();
	}

	function run( testResults, testBox ) {
		describe( "Test suite for LDEV-6500 (SMTP sender thread, spool disabled)", function() {

			it( title="synchronous cfmail is delivered after javax.mail was used in the request", body=function( currentSpec ) {
				// same pollution as LDEV-6485 (core still ships javax.mail via commons-email-all)
				var sess = createObject( "java", "javax.mail.Session" ).getInstance( createObject( "java", "java.util.Properties" ).init() );
				try { sess.getStore( "imap" ); } catch ( any e ) { /* ignore */ }

				expect( sendAll() ).toBe( "" );
			});

			it( title="the sender thread does not inherit the caller's TCCL", body=function( currentSpec ) {
				// The SMTP work (connect, saveChanges, sendMessage) runs on SMTPSender, a new thread. Simulate a request
				// TCCL that exposes javax.mail: its META-INF/mailcap maps multipart/* to the javax com.sun.mail.handlers.*.
				// A sender thread inheriting it gets a ClassCastException from jakarta.activation while writing the
				// multipart body (mid DATA) and the send ends with "timeout occurred after N seconds".
				var thread = createObject( "java", "java.lang.Thread" ).currentThread();
				var tccl = thread.getContextClassLoader();
				thread.setContextClassLoader( createObject( "java", "javax.mail.Session" ).getClass().getClassLoader() );
				try {
					var err = sendAll();
				}
				finally {
					thread.setContextClassLoader( tccl );
				}
				expect( err ).toBe( "" );
			});

		});
	}

	// sends a text, an html and a multipart mail with attachment synchronously (spool disabled);
	// returns the error message, or the subjects that did not arrive, or an empty string
	private string function sendAll() {
		variables.smtp.purgeEmailFromAllMailboxes();
		var subjects = [ "LDEV6500-text-#createUUID()#", "LDEV6500-html-#createUUID()#", "LDEV6500-parts-#createUUID()#" ];
		try {
			mail to=variables.to from=variables.from subject=subjects[ 1 ] spoolEnable=false server="localhost" port=variables.port timeout=10 {
				echo( "text" );
			}
			mail type="html" to=variables.to from=variables.from subject=subjects[ 2 ] spoolEnable=false server="localhost" port=variables.port timeout=10 {
				echo( "<b>html</b>" );
			}
			mail to=variables.to from=variables.from subject=subjects[ 3 ] spoolEnable=false server="localhost" port=variables.port timeout=10 {
				mailpart type="text" { echo( "text" ); }
				mailpart type="html" { echo( "<b>html</b>" ); }
				mailparam file="LDEV6500.txt" content="attachment";
			}
		}
		catch ( any e ) {
			return e.message;
		}
		var received = [];
		for ( var msg in variables.smtp.getReceivedMessages() ) arrayAppend( received, msg.getSubject() );
		var missing = [];
		for ( var s in subjects ) {
			if ( !arrayContains( received, s ) ) arrayAppend( missing, s );
		}
		return arrayLen( missing ) ? "not received: " & arrayToList( missing ) : "";
	}
}
