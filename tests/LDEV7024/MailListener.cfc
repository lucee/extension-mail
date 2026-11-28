/**
 * LDEV-7024: mail listener used by LDEV7024.cfc. It is stored with the spooled mail and runs in the spooler thread,
 * so it reports the calls through the server scope (key: the subject of the mail).
 */
component {

	function before( id, type, detail, tries ) {
		record( "before", arguments );
	}

	function after( id, type, detail, tries, passed ) {
		record( "after", arguments );
	}

	private void function record( required string event, required struct args ) {
		lock name="LDEV7024-listener" type="exclusive" timeout=10 {
			if ( !structKeyExists( server, "LDEV7024" ) ) server.LDEV7024 = {};
			var subject = arguments.args.detail.subject ?: "";
			if ( !structKeyExists( server.LDEV7024, subject ) ) server.LDEV7024[ subject ] = [];
			arrayAppend( server.LDEV7024[ subject ], {
				"event": arguments.event,
				"passed": arguments.args.passed ?: "",
				"closed": arguments.args.closed ?: "",
				"tries": arguments.args.tries ?: ""
			} );
		}
	}
}
