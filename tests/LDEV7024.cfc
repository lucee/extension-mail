/**
 * LDEV-7024: cfmail with a listener and async=true (or spoolEnable) sent the mail in the calling thread, never retried
 * it and never called the listener. The listener was not serializable, so the spooler task could not be stored and core
 * executed it inline; and core only calls the listener of its own task classes. The listener is now serializable and the
 * extension's SpoolerTaskSupport calls it before and after every try.
 */
component extends="org.lucee.cfml.test.LuceeTestCase" labels="mail" javaSettings='{
		"maven": [
			"com.icegreen:greenmail:2.1.7"
		]
	}' {

	import "com.icegreen.greenmail.util.ServerSetup";
	import "com.icegreen.greenmail.util.GreenMail";

	variables.port = 30270;
	variables.from = "susi@sorglos.de";
	variables.to = "geisse@peter.ch";

	function beforeAll() {
		variables.smtp = new GreenMail( new ServerSetup( variables.port, nullValue(), ServerSetup::PROTOCOL_SMTP ) );
		variables.smtp.start();
		variables.rcDir = getPageContext().getConfig().getRemoteClientDirectory();
	}

	function afterAll() {
		if ( !isNull( variables.smtp ) ) variables.smtp.stop();
		if ( !isNull( variables.silent ) ) variables.silent.close();
		structDelete( server, "LDEV7024", false );
	}

	function run( testResults, testBox ) {
		describe( "LDEV-7024 cfmail async with a listener", function() {

			it( title="is queued, the request does not wait for an SMTP server that does not answer", body=function( currentSpec ) {
				// accepts connections (backlog) but never answers, a mail sent inline waits until the timeout (30s)
				variables.silent = createObject( "java", "java.net.ServerSocket" ).init( javaCast( "int", 0 ) );
				var subject = "LDEV7024-queued-" & createUUID();
				var id = "";
				try {
					var start = getTickCount();
					mail to=variables.to from=variables.from subject=subject server="127.0.0.1" port=variables.silent.getLocalPort()
							async=true timeout=30 useTLS=false useSSL=false listener=new LDEV7024.MailListener() {
						echo( "LDEV-7024 queued" );
					}
					var elapsed = getTickCount() - start;
					var task = findTask( subject );
					id = task.id ?: "";

					expect( elapsed ).toBeLT( 10000, "cfmail waited for the SMTP server, the mail was sent in the calling thread" );
					expect( task ).notToBeEmpty( "the mail was not queued (no spooler task)" );
					expect( waitForEvents( subject, 1 ) ).toBeGTE( 1, "listener [before] was not called" );
					expect( events( subject )[ 1 ].event ).toBe( "before" );

					// the server goes away, the try fails and the listener gets [after] with passed=false
					variables.silent.close();
					expect( waitForEvents( subject, 2, 45 ) ).toBeGTE( 2, "listener [after] was not called" );
					var after = events( subject )[ 2 ];
					expect( after.event ).toBe( "after" );
					expect( after.passed ).toBeFalse();
					expect( after.closed ).toBeFalse( "a failed first try is retried, the task is not closed" );

					// the failed try is stored again for the retry
					expect( waitForTries( subject, 1 ) ).toBe( 1 );
				}
				finally {
					// close the silent server first, so no task keeps retrying against it
					if ( !isNull( variables.silent ) ) variables.silent.close();
					cleanup( id );
				}
			});

			it( title="calls the listener before and after the try and delivers the mail", body=function( currentSpec ) {
				variables.smtp.purgeEmailFromAllMailboxes();
				var subject = "LDEV7024-sent-" & createUUID();
				var id = "";
				try {
					mail to=variables.to from=variables.from subject=subject server="localhost" port=variables.port
							async=true timeout=10 useTLS=false useSSL=false listener=new LDEV7024.MailListener() {
						echo( "LDEV-7024 sent" );
					}
					id = findTask( subject ).id ?: "";

					expect( waitForEvents( subject, 2 ) ).toBe( 2, "listener was not called before and after the try" );
					var ev = events( subject );
					expect( ev[ 1 ].event ).toBe( "before" );
					expect( ev[ 2 ].event ).toBe( "after" );
					expect( ev[ 2 ].passed ).toBeTrue();
					expect( ev[ 2 ].closed ).toBeTrue();

					variables.smtp.waitForIncomingEmail( 5000, 1 );
					var received = false;
					for ( var msg in variables.smtp.getReceivedMessages() ) {
						if ( msg.getSubject() == subject ) received = true;
					}
					expect( received ).toBeTrue( "mail not delivered" );
				}
				finally {
					cleanup( id );
				}
			});
		});
	}

	private array function events( required string subject ) {
		lock name="LDEV7024-listener" type="readonly" timeout=10 {
			return duplicate( server.LDEV7024[ arguments.subject ] ?: [] );
		}
	}

	// waits until the listener was called [count] times, returns how often it was called
	private numeric function waitForEvents( required string subject, required numeric count, numeric seconds=30 ) {
		var end = getTickCount() + arguments.seconds * 1000;
		while ( arrayLen( events( arguments.subject ) ) < arguments.count && getTickCount() < end ) {
			sleep( 100 );
		}
		return arrayLen( events( arguments.subject ) );
	}

	private struct function findTask( required string subject ) {
		var engine = getPageContext().getConfig().getSpoolerEngine();
		for ( var q in [ engine.getOpenTasksAsQuery( 1, 100000 ), engine.getClosedTasksAsQuery( 1, 100000 ) ] ) {
			loop query=q {
				if ( q.name == arguments.subject ) return queryGetRow( q, q.currentrow );
			}
		}
		return {};
	}

	private numeric function waitForTries( required string subject, required numeric tries, numeric seconds=10 ) {
		var end = getTickCount() + arguments.seconds * 1000;
		var task = {};
		while ( getTickCount() < end ) {
			task = findTask( arguments.subject );
			if ( ( task.tries ?: 0 ) >= arguments.tries ) break;
			sleep( 100 );
		}
		return task.tries ?: 0;
	}

	private void function cleanup( required string id ) {
		if ( !len( arguments.id ) ) return;
		for ( var d in [ "open", "closed" ] ) {
			var f = variables.rcDir.getRealResource( d & "/" & arguments.id & ".tsk" ).getAbsolutePath();
			if ( fileExists( f ) ) fileDelete( f );
		}
	}
}
