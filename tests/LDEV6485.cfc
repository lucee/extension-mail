/**
 * LDEV-6485: after javax.mail was used in the same JVM (Lucee core still ships it in
 * org.lucee.commons-email-all), every cfmail failed with
 *   "jakarta.mail.Provider: com.sun.mail.imap.IMAPProvider not a subtype"
 * until restart (and the other way round: javax.mail failed after cfmail).
 *
 * The extension used com.sun.mail:jakarta.mail 2.0.x, whose implementation classes have the same names
 * (com.sun.mail.*) as javax.mail 1.6. Lucee's thread context classloader can bind only one class per
 * name, so whichever side came first won. The extension now uses Angus Mail (org.eclipse.angus.*).
 *
 * The specs cover the request thread, a cfthread and the mail spooler thread.
 */
component extends="org.lucee.cfml.test.LuceeTestCase" labels="mail" javaSettings='{
		"maven": [
			"com.icegreen:greenmail:2.1.7"
		]
	}' {

	import "com.icegreen.greenmail.util.ServerSetup";
	import "com.icegreen.greenmail.util.GreenMail";

	variables.port = 30265;

	function beforeAll() {
		variables.smtp = new GreenMail( new ServerSetup( variables.port, nullValue(), ServerSetup::PROTOCOL_SMTP ) );
		variables.smtp.start();
	}

	function afterAll() {
		if ( !isNull( variables.smtp ) ) variables.smtp.stop();
	}

	// use javax.mail (core) first: create a Session, a Store and send a message through javax Transport
	private function useJavaxMail() {
		var props = createObject( "java", "java.util.Properties" ).init();
		props.put( "mail.smtp.host", "127.0.0.1" );
		props.put( "mail.smtp.port", javaCast( "string", variables.port ) );
		var sess = createObject( "java", "javax.mail.Session" ).getInstance( props );
		try { sess.getStore( "imap" ); } catch ( any e ) {}
		var msg = createObject( "java", "javax.mail.internet.MimeMessage" ).init( sess );
		msg.setFrom( createObject( "java", "javax.mail.internet.InternetAddress" ).init( "javax@lucee.org" ) );
		msg.setRecipients( createObject( "java", "javax.mail.Message$RecipientType" ).TO, "javax@lucee.org" );
		msg.setSubject( "LDEV6485-javax" );
		msg.setText( "sent via javax.mail" );
		createObject( "java", "javax.mail.Transport" ).send( msg );
	}

	private boolean function received( required string subject, numeric timeoutMs = 20000 ) {
		var start = getTickCount();
		while ( getTickCount() - start < arguments.timeoutMs ) {
			for ( var m in variables.smtp.getReceivedMessages() ) {
				if ( m.getSubject() == arguments.subject ) return true;
			}
			sleep( 100 );
		}
		return false;
	}

	function run( testResults, testBox ) {
		describe( "LDEV-6485 javax.mail / jakarta.mail provider clash", function() {

			it( title="cfmail works after javax.mail was used (request thread)", body=function( currentSpec ) {
				useJavaxMail();
				var subject = "LDEV6485-request-" & createUUID();
				var err = "";
				try {
					mail to="a@lucee.org" from="b@lucee.org" subject=subject server="127.0.0.1" port=variables.port spoolEnable=false {
						echo( "x" );
					}
				}
				catch ( any e ) {
					err = e.message;
				}
				expect( err ).toBe( "" );
				expect( received( subject ) ).toBeTrue();
			});

			it( title="cfmail works after javax.mail was used (cfthread)", body=function( currentSpec ) {
				useJavaxMail();
				var subject = "LDEV6485-thread-" & createUUID();
				var tn = "ldev6485_" & createUUID();
				thread name=tn subject=subject port=variables.port {
					thread.err = "";
					try {
						mail to="a@lucee.org" from="b@lucee.org" subject=attributes.subject server="127.0.0.1" port=attributes.port spoolEnable=false {
							echo( "x" );
						}
					}
					catch ( any e ) {
						thread.err = e.message;
					}
				}
				threadJoin( tn, 20000 );
				expect( cfthread[ tn ].err ?: "thread did not finish" ).toBe( "" );
				expect( received( subject ) ).toBeTrue();
			});

			// skipped on 8.x: there the core spooler can't read back any mail task, even without javax.mail
			// (ClassNotFoundException org.lucee.extension.mail.spooler.MailSpoolerTask in SpoolerEngineImpl.getTask), unrelated to LDEV-6485
			it( title="spooled cfmail is delivered after javax.mail was used (spooler thread)", skip=( listFirst( server.lucee.version, "." ) >= 8 ), body=function( currentSpec ) {
				useJavaxMail();
				var subject = "LDEV6485-spool-" & createUUID();
				mail to="a@lucee.org" from="b@lucee.org" subject=subject server="127.0.0.1" port=variables.port spoolEnable=true {
					echo( "x" );
				}
				expect( received( subject, 30000 ) ).toBeTrue( "spooled mail [#subject#] was not delivered" );
			});

			it( title="javax.mail still works after cfmail (reverse direction)", body=function( currentSpec ) {
				mail to="a@lucee.org" from="b@lucee.org" subject="LDEV6485-rev-#createUUID()#" server="127.0.0.1" port=variables.port spoolEnable=false {
					echo( "x" );
				}
				var err = "";
				try {
					useJavaxMail();
				}
				catch ( any e ) {
					err = e.message;
				}
				expect( err ).toBe( "" );
			});

			it( title="cfmail to a closed port fails with a connect error, not with a Provider error", body=function( currentSpec ) {
				useJavaxMail();
				var err = "";
				try {
					mail to="a@lucee.org" from="b@lucee.org" subject="LDEV6485-closed-#createUUID()#" server="127.0.0.1" port=1 spoolEnable=false timeout=1 {
						echo( "x" );
					}
				}
				catch ( any e ) {
					err = e.message;
				}
				expect( err ).notToInclude( "not a subtype" );
				expect( err ).notToBe( "" );
			});

		});
	}
}
