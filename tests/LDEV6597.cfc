/**
 * LDEV-6597: a spooled mail that keeps failing (e.g. SMTP 550) was retried every 60 seconds forever.
 * Lucee core's SpoolerEngineImpl only counts tries, records exceptions and closes the task for its own
 * SpoolerTaskSupport; MailSpoolerTask extends the extension's copy, so tries stayed 0, nothing was recorded
 * and the task was never closed. The extension's SpoolerTaskSupport now does that bookkeeping itself.
 *
 * A local fake SMTP server answers every RCPT TO with "550".
 */
component extends="org.lucee.cfml.test.LuceeTestCase" labels="mail" {

	variables.from = "susi@sorglos.de";
	variables.to = "nobody@example.com";

	function beforeAll() {
		variables.server550 = createObject( "java", "java.net.ServerSocket" ).init( 0 );
		variables.server550.setSoTimeout( 500 );
		variables.port = variables.server550.getLocalPort();
		variables.running = true;
		thread name="ldev6597smtp" action="run" {
			while ( variables.running ) {
				try {
					var sock = variables.server550.accept();
				}
				catch ( any e ) {
					continue; // accept timeout, check running flag again
				}
				try {
					sock.setSoTimeout( 10000 );
					var reader = createObject( "java", "java.io.BufferedReader" ).init(
						createObject( "java", "java.io.InputStreamReader" ).init( sock.getInputStream(), "US-ASCII" ) );
					var out = sock.getOutputStream();
					var write = function( str ) {
						out.write( charsetDecode( str & chr( 13 ) & chr( 10 ), "US-ASCII" ) );
						out.flush();
					};
					write( "220 localhost fake smtp" );
					while ( true ) {
						var line = reader.readLine();
						if ( isNull( line ) ) break;
						var cmd = uCase( left( line, 4 ) );
						if ( cmd == "QUIT" ) { write( "221 bye" ); break; }
						else if ( cmd == "RCPT" ) write( "550 5.1.1 mailbox unavailable (LDEV-6597)" );
						else write( "250 ok" );
					}
				}
				catch ( any e ) {}
				finally {
					try { sock.close(); } catch ( any e ) {}
				}
			}
		}
	}

	function afterAll() {
		variables.running = false;
		if ( !isNull( variables.server550 ) ) variables.server550.close();
		thread action="join" name="ldev6597smtp" timeout=5000;
	}

	private function spooler() {
		return getPageContext().getConfig().getSpoolerEngine();
	}

	private struct function findTask( required string subject, boolean closed=false ) {
		var q = arguments.closed ? spooler().getClosedTasksAsQuery( 1, 100000 ) : spooler().getOpenTasksAsQuery( 1, 100000 );
		loop query=q {
			if ( q.name == arguments.subject ) return queryGetRow( q, q.currentrow );
		}
		return {};
	}

	function run( testResults, testBox ) {
		describe( "LDEV-6597 spooled mail failing with SMTP 550", function() {

			it( title="counts tries, records exceptions, backs off per plan and closes after the last try", body=function( currentSpec ) {
				var subject = "LDEV-6597 " & createUUID();
				mail to=variables.to from=variables.from subject=subject spoolEnable=true server="localhost" port=variables.port useTLS=false useSSL=false {
					echo( "this mail is rejected" );
				}

				// the spooler thread picks the task up right away and fails on the 550
				var task = {};
				var start = getTickCount();
				while ( getTickCount() - start < 30000 ) {
					task = findTask( subject );
					if ( !structIsEmpty( task ) && task.tries >= 1 ) break;
					sleep( 250 );
				}
				expect( task ).notToBeEmpty( "spooled task not found" );
				// before the fix: tries stays 0, no exception, next execution stays "now" (retried every 60s forever)
				expect( task.tries ).toBe( 1 );
				expect( task.closed ).toBeFalse();
				expect( arrayLen( task.exceptions ) ).toBe( 1 );
				expect( task.exceptions[ 1 ].message ).notToBeEmpty();
				// core's SpoolerEngineImpl.calculateNextExecution picks the plan entry after the tries already done
				// (plans: 1x60s, 1x5min, 1x1h, 2x24h = 5 tries), so after the first try the back off is 5 minutes
				expect( dateDiff( "s", task.lastExecution, task.nextExecution ) ).toBeBetween( 295, 305 );

				// second try: 1 hour back off
				spooler().execute( task.id );
				task = findTask( subject );
				expect( task.tries ).toBe( 2 );
				expect( dateDiff( "s", task.lastExecution, task.nextExecution ) ).toBeBetween( 3595, 3605 );

				// third try: 24 hour back off
				spooler().execute( task.id );
				task = findTask( subject );
				expect( task.tries ).toBe( 3 );
				expect( dateDiff( "s", task.lastExecution, task.nextExecution ) ).toBeBetween( 86395, 86405 );

				// fourth and fifth try (2 x 24 hours), after the fifth the plan is used up and the task gets closed
				var id = task.id;
				spooler().execute( id );
				task = findTask( subject );
				expect( task.tries ).toBe( 4 );
				spooler().execute( id );

				expect( findTask( subject ) ).toBeEmpty( "task is still open after the last try" );
				task = findTask( subject, true );
				expect( task ).notToBeEmpty( "closed task not found" );
				expect( task.closed ).toBeTrue();
				expect( task.tries ).toBe( 5 );
				expect( arrayLen( task.exceptions ) ).toBe( 5 );
			});
		});
	}
}
