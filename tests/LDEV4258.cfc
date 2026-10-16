/* LDEV-4258: several cfmailpart with the same type - only the first part of a type was sent,
   and after the first fix a second part with a short type (type="html") failed the whole send. */
component extends="org.lucee.cfml.test.LuceeTestCase" labels="mail" javaSettings='{
		"maven": [
			"com.icegreen:greenmail:2.1.7"
		]
	}' {

	import "com.icegreen.greenmail.util.ServerSetup";
	import "com.icegreen.greenmail.util.GreenMail";
	processingdirective pageencoding="UTF-8";

	variables.port = 30266;
	variables.from = "test@lucee.org";
	variables.to = "abc@lucee.org";

	function beforeAll() {
		variables.smtp = new GreenMail( new ServerSetup( variables.port, nullValue(), ServerSetup::PROTOCOL_SMTP ) );
		variables.smtp.start();
	}

	function afterAll() {
		if ( !isNull( variables.smtp ) ) variables.smtp.stop();
	}

	function run( testResults, testBox ) {
		describe( "LDEV-4258 multiple cfmailpart of the same type", function() {

			it( title="two text/plain parts are both sent", body=function( currentSpec ) {
				var parts = send( function( subject ) {
					mail from=variables.from to=variables.to subject=arguments.subject server="localhost" port=variables.port spoolEnable=false {
						mailpart type="text/plain" { echo( "First mailpart" ); }
						mailpart type="text/plain" { echo( "Second mailpart" ); }
					}
				});
				expectParts( parts, [ "text/plain|First mailpart", "text/plain|Second mailpart" ] );
			});

			it( title="text + html + second text part: all three are sent", body=function( currentSpec ) {
				var parts = send( function( subject ) {
					mail from=variables.from to=variables.to subject=arguments.subject server="localhost" port=variables.port spoolEnable=false {
						mailpart type="text/plain" { echo( "Part one plain" ); }
						mailpart type="text/html" { echo( "<b>Part two html</b>" ); }
						mailpart type="text/plain" { echo( "Part three plain" ); }
					}
				});
				expectParts( parts, [ "text/plain|Part one plain", "text/html|<b>Part two html</b>", "text/plain|Part three plain" ] );
			});

			it( title="two type=html parts are both sent", body=function( currentSpec ) {
				var parts = send( function( subject ) {
					mail from=variables.from to=variables.to subject=arguments.subject server="localhost" port=variables.port spoolEnable=false {
						mailpart type="html" { echo( "<p>first html</p>" ); }
						mailpart type="html" { echo( "<p>second html Grüße</p>" ); }
					}
				});
				expectParts( parts, [ "text/html|<p>first html</p>", "text/html|<p>second html Grüße</p>" ] );
			});

			it( title="text + html + html with short types: all three are sent", body=function( currentSpec ) {
				var parts = send( function( subject ) {
					mail from=variables.from to=variables.to subject=arguments.subject server="localhost" port=variables.port spoolEnable=false {
						mailpart type="text" { echo( "plain one" ); }
						mailpart type="html" { echo( "<p>html two</p>" ); }
						mailpart type="htm" { echo( "<p>html three</p>" ); }
					}
				});
				expectParts( parts, [ "text/plain|plain one", "text/html|<p>html two</p>", "text/html|<p>html three</p>" ] );
			});

			it( title="second plain part with short type and its own charset keeps the charset", body=function( currentSpec ) {
				var parts = send( function( subject ) {
					mail from=variables.from to=variables.to subject=arguments.subject server="localhost" port=variables.port spoolEnable=false {
						mailpart type="plain" { echo( "plain one" ); }
						mailpart type="plain" charset="ISO-8859-1" { echo( "plain two Grüße" ); }
					}
				});
				expectParts( parts, [ "text/plain|plain one", "text/plain|plain two Grüße" ] );
				expect( parts[ 2 ].contentType ).toInclude( "ISO-8859-1" );
			});
		});
	}

	// sends the mail and returns the received parts as [{contentType, text}]; fails if the send throws
	private array function send( required function sender ) {
		variables.smtp.purgeEmailFromAllMailboxes();
		var subject = "LDEV4258-#createUUID()#";
		var err = "";
		try {
			arguments.sender( subject );
		}
		catch ( any e ) {
			err = e.message;
		}
		expect( err ).toBe( "", "mail was not sent" );
		var messages = variables.smtp.getReceivedMessages();
		expect( arrayLen( messages ) ).toBe( 1 );
		expect( messages[ 1 ].getSubject() ).toBe( subject );
		var result = [];
		collectParts( messages[ 1 ], result );
		return result;
	}

	private void function collectParts( required part, required array result ) {
		if ( arguments.part.isMimeType( "multipart/*" ) ) {
			var mp = arguments.part.getContent();
			for ( var i = 0; i < mp.getCount(); i++ ) collectParts( mp.getBodyPart( i ), arguments.result );
			return;
		}
		arrayAppend( arguments.result, {
			contentType: arguments.part.getContentType(),
			text: trim( arguments.part.getContent() )
		});
	}

	// expected: array of "mimeType|text", in order
	private void function expectParts( required array parts, required array expected ) {
		systemOutput( "LDEV4258 parts: " & serializeJSON( arguments.parts ), true );
		expect( arrayLen( arguments.parts ) ).toBe( arrayLen( arguments.expected ), "not all parts arrived" );
		loop array=arguments.expected index="local.i" item="local.exp" {
			var mimeType = listFirst( exp, "|" );
			var text = listRest( exp, "|" );
			expect( lCase( arguments.parts[ i ].contentType ) ).toInclude( mimeType );
			expect( arguments.parts[ i ].text ).toBe( text );
		}
	}
}
