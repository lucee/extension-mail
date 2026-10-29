component extends="org.lucee.cfml.test.LuceeTestCase" labels="mail" javaSettings='{
		"maven": [
			"com.icegreen:greenmail:2.1.7"
		]
	}' {

	import "com.icegreen.greenmail.util.ServerSetup";
	import "com.icegreen.greenmail.util.GreenMail";

	variables.popPort = 30111;
	variables.smtpPort = 30254;
	variables.username = "ldev1566@localhost.com";
	variables.password = "ldev1566pass";
	variables.from = "sender@localhost.com";

	function beforeAll() {
		variables.server = new GreenMail( [
			new ServerSetup( variables.popPort, nullValue(), ServerSetup::PROTOCOL_POP3 ),
			new ServerSetup( variables.smtpPort, nullValue(), ServerSetup::PROTOCOL_SMTP )
		] );
		variables.server.start();
		variables.server.setUser( variables.username, variables.username, variables.password );
	}

	function afterAll() {
		if ( !isNull( variables.server ) ) variables.server.stop();
	}

	function run( testResults, testBox ) {
		describe( "LDEV-1566 cfpop returntype", function() {

			beforeEach( function() {
				variables.server.purgeEmailFromAllMailboxes();
				send( "LDEV1566 one" );
				send( "LDEV1566 two" );
				sleep( 500 );
			});

			it( "default is a query", function() {
				pop action="getHeaderOnly" name="local.res" server="localhost" port=variables.popPort username=variables.username password=variables.password secure=false;
				expect( isQuery( local.res ) ).toBeTrue();
				expect( local.res.recordCount ).toBe( 2 );
			});

			it( "returntype=array returns an array of structs", function() {
				pop action="getHeaderOnly" name="local.res" server="localhost" port=variables.popPort username=variables.username password=variables.password secure=false returntype="array";
				expect( isArray( local.res ) ).toBeTrue();
				expect( arrayLen( local.res ) ).toBe( 2 );
				expect( local.res[ 1 ] ).toHaveKey( "subject" );
				expect( local.res[ 1 ].subject ).toInclude( "LDEV1566" );
			});

			it( "returntype=struct with columnKey returns a struct keyed by that column", function() {
				pop action="getHeaderOnly" name="local.res" server="localhost" port=variables.popPort username=variables.username password=variables.password secure=false returntype="struct" columnKey="subject";
				expect( isStruct( local.res ) ).toBeTrue();
				expect( structCount( local.res ) ).toBe( 2 );
				expect( local.res ).toHaveKey( "LDEV1566 one" );
				expect( local.res[ "LDEV1566 two" ].subject ).toBe( "LDEV1566 two" );
			});

			it( "LDEV-1567 columnKey with a list of columns creates nested structs", function() {
				pop action="getHeaderOnly" name="local.res" server="localhost" port=variables.popPort username=variables.username password=variables.password secure=false returntype="struct" columnKey="from,subject";
				expect( isStruct( local.res ) ).toBeTrue();
				var outer = structKeyArray( local.res );
				expect( arrayLen( outer ) ).toBe( 1 );
				expect( structCount( local.res[ outer[ 1 ] ] ) ).toBe( 2 );
				expect( local.res[ outer[ 1 ] ][ "LDEV1566 one" ].subject ).toBe( "LDEV1566 one" );
				expect( local.res[ outer[ 1 ] ][ "LDEV1566 two" ].subject ).toBe( "LDEV1566 two" );
			});

			it( "LDEV-1567 the last row wins if a key is used more than once", function() {
				pop action="getHeaderOnly" name="local.res" server="localhost" port=variables.popPort username=variables.username password=variables.password secure=false returntype="struct" columnKey="from";
				expect( structCount( local.res ) ).toBe( 1 );
			});

			it( "LDEV-1567 a missing column in columnKey throws", function() {
				expect( function() {
					pop action="getHeaderOnly" name="local.res" server="localhost" port=variables.popPort username=variables.username password=variables.password secure=false returntype="struct" columnKey="from,nope";
				}).toThrow();
			});

			it( "returntype=struct without columnKey and more than one row throws", function() {
				expect( function() {
					pop action="getHeaderOnly" name="local.res" server="localhost" port=variables.popPort username=variables.username password=variables.password secure=false returntype="struct";
				}).toThrow();
			});

			it( "getAll honours returntype", function() {
				pop action="getAll" name="local.res" server="localhost" port=variables.popPort username=variables.username password=variables.password secure=false returntype="array";
				expect( isArray( local.res ) ).toBeTrue();
				expect( arrayLen( local.res ) ).toBe( 2 );
				expect( local.res[ 1 ] ).toHaveKey( "body" );
			});

			it( "invalid returntype throws", function() {
				expect( function() {
					pop action="getHeaderOnly" name="local.res" server="localhost" port=variables.popPort username=variables.username password=variables.password secure=false returntype="xml";
				}).toThrow();
			});
		});
	}

	private function send( required string subject ) {
		mail to=variables.username from=variables.from subject=arguments.subject spoolEnable=false server="localhost" port=variables.smtpPort {
			echo( "body of " & arguments.subject );
		}
	}
}
