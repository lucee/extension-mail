/**
 * LDEV-7024: cfmail with a listener and async/spoolEnable=true.
 *
 * The extension's listener classes were not Serializable, so the spooler could not write the task to disk. It
 * then sent the mail in the calling thread (no queue, no retries) and, because the task was not a core
 * SpoolerTaskPro, never called the listener's before/after either.
 *
 * A local fake SMTP server (like LDEV-6597) either accepts the mail or answers RCPT TO with 550, after an
 * optional delay.
 */
component extends="org.lucee.cfml.test.LuceeTestCase" labels="mail" {

	variables.from = "susi@sorglos.de";
	variables.to = "nobody@example.com";

	function beforeAll() {
		variables.sinks = [];
	}

	function afterAll() {
		for ( var sink in variables.sinks ) {
			sink.running.set( false );
			sink.server.close();
		}
		for ( var sink in variables.sinks ) {
			thread action="join" name=sink.thread timeout=5000;
		}
	}

	/**
	 * starts a fake SMTP server on a free port. rcptReply is the answer to RCPT TO, delay (ms) is waited before it.
	 * sink.delivered counts the mails the server accepted (DATA finished).
	 */
	private struct function startSink( string rcptReply="250 ok", numeric delay=0 ) {
		var sink = {
			server: createObject( "java", "java.net.ServerSocket" ).init( 0 ),
			running: createObject( "java", "java.util.concurrent.atomic.AtomicBoolean" ).init( true ),
			delivered: createObject( "java", "java.util.concurrent.atomic.AtomicInteger" ).init( 0 ),
			thread: "ldev7024smtp" & createUUID()
		};
		sink.server.setSoTimeout( 500 );
		sink.port = sink.server.getLocalPort();
		arrayAppend( variables.sinks, sink );

		thread name=sink.thread action="run" sink=sink rcptReply=arguments.rcptReply delay=arguments.delay {
			var sink = attributes.sink;
			while ( sink.running.get() ) {
				try {
					var sock = sink.server.accept();
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
					var inData = false;
					while ( true ) {
						var line = reader.readLine();
						if ( isNull( line ) ) break;
						if ( inData ) {
							if ( line == "." ) {
								inData = false;
								sink.delivered.incrementAndGet();
								write( "250 queued" );
							}
							continue;
						}
						var cmd = uCase( left( line, 4 ) );
						if ( cmd == "QUIT" ) { write( "221 bye" ); break; }
						else if ( cmd == "RCPT" ) {
							if ( attributes.delay > 0 ) sleep( attributes.delay );
							write( attributes.rcptReply );
						}
						else if ( cmd == "DATA" ) { inData = true; write( "354 go ahead" ); }
						else if ( cmd == "EHLO" ) write( "250 localhost" );
						else write( "250 ok" );
					}
				}
				catch ( any e ) {}
				finally {
					try { sock.close(); } catch ( any e ) {}
				}
			}
		}
		return sink;
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

	/** the lines the listener wrote for this mail */
	private array function listenerCalls( required string subject ) {
		var file = new LDEV7024.MailListener().logFile( arguments.subject );
		return fileExists( file ) ? listToArray( fileRead( file ), chr( 10 ) ) : [];
	}

	private array function waitForListener( required string subject, required numeric count, numeric timeout=30000 ) {
		var start = getTickCount();
		var calls = [];
		while ( getTickCount() - start < arguments.timeout ) {
			calls = listenerCalls( arguments.subject );
			if ( arrayLen( calls ) >= arguments.count ) break;
			sleep( 100 );
		}
		return calls;
	}

	function run( testResults, testBox ) {
		describe( "LDEV-7024 spooled cfmail with a listener", function() {

			it( title="queues the mail instead of sending it in the calling thread", body=function( currentSpec ) {
				// the server waits 3 seconds before it answers RCPT TO, a mail sent in the calling thread blocks for that long
				var sink = startSink( rcptReply="550 5.1.1 mailbox unavailable (LDEV-7024)", delay=3000 );
				var subject = "LDEV-7024 queue " & createUUID();
				var start = getTickCount();
				mail to=variables.to from=variables.from subject=subject spoolEnable=true listener=new LDEV7024.MailListener() server="localhost" port=sink.port useTLS=false useSSL=false {
					echo( "queued, not sent in this thread" );
				}
				var duration = getTickCount() - start;

				// before the fix: the task could not be written, the request thread ran the whole SMTP conversation
				expect( duration ).toBeLT( 2500, "cfmail blocked for #duration# ms, the mail was sent in the calling thread" );

				// the spooler thread then tries (and fails with the 550) on its own: before, then after passed=false
				var calls = waitForListener( subject, 2 );
				expect( calls ).toBe( [ "before", "after passed=false" ] );
			});

			it( title="calls the listener before and after a successful try, and closes the task", body=function( currentSpec ) {
				var sink = startSink();
				var subject = "LDEV-7024 sent " & createUUID();
				mail to=variables.to from=variables.from subject=subject spoolEnable=true listener=new LDEV7024.MailListener() server="localhost" port=sink.port useTLS=false useSSL=false {
					echo( "this mail is accepted" );
				}

				// before the fix the listener was never called
				var calls = waitForListener( subject, 2 );
				expect( calls ).toBe( [ "before", "after passed=true" ] );
				expect( sink.delivered.get() ).toBe( 1 );
				expect( findTask( subject ) ).toBeEmpty( "task is still open after it was sent" );
			});

			it( title="keeps a failed mail in the queue, retries it and calls the listener on every try", body=function( currentSpec ) {
				var sink = startSink( rcptReply="550 5.1.1 mailbox unavailable (LDEV-7024)" );
				var subject = "LDEV-7024 retry " & createUUID();
				mail to=variables.to from=variables.from subject=subject spoolEnable=true listener=new LDEV7024.MailListener() server="localhost" port=sink.port useTLS=false useSSL=false {
					echo( "this mail is rejected" );
				}

				var calls = waitForListener( subject, 2 );
				expect( calls ).toBe( [ "before", "after passed=false" ] );
				// the task was written to disk and is still open for the next try
				var task = findTask( subject );
				expect( task ).notToBeEmpty( "the failed mail is not in the spooler queue" );
				expect( task.tries ).toBe( 1 );
				expect( task.closed ).toBeFalse();

				// second try, read back from the .tsk file with its listener
				spooler().execute( task.id );
				calls = listenerCalls( subject );
				expect( calls ).toBe( [ "before", "after passed=false", "before", "after passed=false" ] );
				expect( findTask( subject ).tries ).toBe( 2 );
			});
		});
	}
}
