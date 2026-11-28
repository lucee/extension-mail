/**
 * Listener for LDEV7024.cfc. It keeps no state on purpose: a spooled task is written to a .tsk file and read
 * again by the spooler thread, so everything it needs comes from the arguments the mail extension passes in.
 * It appends one line per call to a log file named after the subject of the mail.
 */
component {

	function before( detail ) {
		fileAppend( logFile( arguments.detail.subject ), "before" & chr( 10 ) );
	}

	function after( detail, passed ) {
		fileAppend( logFile( arguments.detail.subject ), "after passed=" & arguments.passed & chr( 10 ) );
	}

	static function logFile( required string subject ) {
		return getTempDirectory() & "ldev7024-" & hash( arguments.subject ) & ".log";
	}

}
