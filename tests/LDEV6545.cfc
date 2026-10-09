component extends="org.lucee.cfml.test.LuceeTestCase" labels="mail" javaSettings='{
		"maven": [
			"com.icegreen:greenmail:2.1.7"
		]
	}' {

	import "com.icegreen.greenmail.util.ServerSetup";
	import "com.icegreen.greenmail.util.GreenMail";
	import "com.icegreen.greenmail.util.GreenMailUtil";
	processingdirective pageencoding="UTF-8";

	variables.port = 30265;
	variables.from = "susi@sorglos.de";
	variables.to = "geisse@peter.ch";
	variables.prop = "lucee.mail.use.7bit.transfer.encoding.for.html.parts";

	// minified CSS (no line break for > 998 chars) and a link longer than 998 chars without any whitespace
	variables.html = "<html><head><style>" & repeatString( ".c{color:red} ", 120 ) & "</style></head><body>"
		& "<a href=""https://www.lucee.org/?q=" & repeatString( "x", 1500 ) & """>link</a> Grüße</body></html>";

	function beforeAll() {
		variables.smtp = new GreenMail( new ServerSetup( variables.port, nullValue(), ServerSetup::PROTOCOL_SMTP ) );
		variables.smtp.start();
	}

	function afterAll() {
		createObject( "java", "java.lang.System" ).clearProperty( variables.prop );
		if ( !isNull( variables.smtp ) ) variables.smtp.stop();
	}

	function run( testResults, testBox ) {
		describe( "LDEV-6545 HTML mail parts are sent quoted-printable by default", function() {

			afterEach( function() {
				createObject( "java", "java.lang.System" ).clearProperty( variables.prop );
			});

			it( title="type=html mail uses quoted-printable and keeps every line within 998 characters", body=function( currentSpec ) {
				var msg = send( function( subject ) {
					mail to=variables.to from=variables.from subject=arguments.subject type="html" spoolEnable=false server="localhost" port=variables.port {
						echo( variables.html );
					}
				});
				expect( msg.getHeader( "Content-Transfer-Encoding", "" ) ).toBe( "quoted-printable" );
				expectValidLines( msg );
				expect( trim( msg.getContent() ) ).toBe( variables.html );
			});

			it( title="html mailpart next to a text mailpart uses quoted-printable", body=function( currentSpec ) {
				var msg = send( function( subject ) {
					mail to=variables.to from=variables.from subject=arguments.subject spoolEnable=false server="localhost" port=variables.port {
						mailpart type="text" { echo( "plain text" ); }
						mailpart type="html" { echo( variables.html ); }
					}
				});
				var htmlPart = findPart( msg.getContent(), "text/html" );
				expect( htmlPart.getHeader( "Content-Transfer-Encoding", "" ) ).toBe( "quoted-printable" );
				expect( findPart( msg.getContent(), "text/plain" ).getHeader( "Content-Transfer-Encoding", "" ) ).toBe( "7bit" );
				expectValidLines( msg );
				expect( trim( htmlPart.getContent() ) ).toBe( variables.html );
			});

			it( title="mailpart with an uppercase TEXT/HTML type is not wrapped", body=function( currentSpec ) {
				var msg = send( function( subject ) {
					mail to=variables.to from=variables.from subject=arguments.subject spoolEnable=false server="localhost" port=variables.port {
						mailpart type="text" { echo( "plain text" ); }
						mailpart type="TEXT/HTML" { echo( variables.html ); }
					}
				});
				expectValidLines( msg );
				// this part carries no charset, so only compare the ASCII content: no line break may be inserted
				var content = toString( findPart( msg.getContent(), "text/html" ).getContent() );
				expect( content ).toInclude( repeatString( ".c{color:red} ", 120 ) );
				expect( content ).toInclude( "?q=" & repeatString( "x", 1500 ) & """>link</a>" );
			});

			it( title="7bit opt-out still sends 7bit when the wrapped lines fit", body=function( currentSpec ) {
				createObject( "java", "java.lang.System" ).setProperty( variables.prop, "true" );
				var shortHtml = "<p>" & repeatString( "word ", 400 ) & "</p>";
				var msg = send( function( subject ) {
					mail to=variables.to from=variables.from subject=arguments.subject type="html" spoolEnable=false server="localhost" port=variables.port {
						echo( shortHtml );
					}
				});
				expect( msg.getHeader( "Content-Transfer-Encoding", "" ) ).toBe( "7bit" );
				expectValidLines( msg );
			});

			it( title="7bit opt-out falls back to quoted-printable instead of exceeding 998 characters", body=function( currentSpec ) {
				createObject( "java", "java.lang.System" ).setProperty( variables.prop, "true" );
				var msg = send( function( subject ) {
					mail to=variables.to from=variables.from subject=arguments.subject type="html" spoolEnable=false server="localhost" port=variables.port {
						echo( variables.html );
					}
				});
				expect( msg.getHeader( "Content-Transfer-Encoding", "" ) ).toBe( "quoted-printable" );
				expectValidLines( msg );
			});

		});
	}

	private function send( required function sender ) {
		variables.smtp.purgeEmailFromAllMailboxes();
		var subject = "LDEV6545-#createUUID()#";
		arguments.sender( subject );
		var messages = variables.smtp.getReceivedMessages();
		expect( arrayLen( messages ) ).toBe( 1 );
		expect( messages[ 1 ].getSubject() ).toBe( subject );
		return messages[ 1 ];
	}

	// RFC 5322: no line may be longer than 998 characters (without CRLF)
	private function expectValidLines( required msg ) {
		var raw = GreenMailUtil::getWholeMessage( arguments.msg );
		var maxLen = 0;
		loop list=raw delimiters="#chr( 13 )##chr( 10 )#" item="local.line" {
			if ( len( line ) > maxLen ) maxLen = len( line );
		}
		systemOutput( "LDEV6545 raw message: #len( raw )# chars, longest line #maxLen#", true );
		expect( maxLen ).toBeLTE( 998, "raw message has a line longer than 998 characters" );
	}

	private function findPart( required multipart, required string type ) {
		for ( var i = 0; i < arguments.multipart.getCount(); i++ ) {
			var part = arguments.multipart.getBodyPart( i );
			if ( part.isMimeType( arguments.type ) ) return part;
		}
		throw( "no [#arguments.type#] part found" );
	}

}
