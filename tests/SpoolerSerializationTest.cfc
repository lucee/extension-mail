/**
 * Regression test for LDEV-6455.
 *
 * A spooled cfmail is persisted to a .tsk file by serializing the MailSpoolerTask, which holds an
 * SMTPClient. When SMTPClient stored the charset as a raw java.nio.charset.Charset (not
 * Serializable), the write aborted with "java.io.NotSerializableException: sun.nio.cs.UTF_8" and the
 * mail was silently dropped. The charset is now held via CharsetSerializable, so the client must
 * serialize cleanly.
 *
 * The failure happens on the WRITE (ObjectOutputStream.writeObject), so serializing the client and
 * asserting it does not throw fully reproduces and guards the bug.
 */
component extends="org.lucee.cfml.test.LuceeTestCase" labels="mail" {

	// JDK helpers (visible to lucee.core, so a plain import resolves them)
	import "java.io.ByteArrayOutputStream";
	import "java.io.ObjectOutputStream";
	import "java.nio.charset.Charset";

	/*
	 * SMTPClient lives in the internal package org.lucee.extension.mail.smtp, which the extension
	 * bundle does NOT OSGi-export. A plain `import`/`new` therefore resolves against lucee.core and
	 * throws ClassNotFoundException. It must be loaded from the extension bundle by its symbolic
	 * name ("mail.extension"); the bundle's own classloader can see its non-exported classes.
	 * (Passing no version resolves whichever mail.extension bundle is installed - i.e. the build
	 * under test.)
	 *
	 * Alternative, loading a published artifact from maven instead of the installed bundle:
	 *   createObject("java", "org.lucee.extension.mail.smtp.SMTPClient",
	 *                { maven: [ "org.lucee:mail:1.1.0.8-SNAPSHOT" ] });
	 */
	private function newSMTPClient() {
		return createObject( "java", "org.lucee.extension.mail.smtp.SMTPClient", "mail.extension" ).init();
	}

	function run( testResults, testBox ) {
		describe( title="SMTPClient spooler serialization (LDEV-6455)", body=function() {

			it( title="serializes a default client without NotSerializableException (UTF-8)", body=function() {
				// throws java.io.NotSerializableException: sun.nio.cs.UTF_8 before the fix
				expect( serializedSize( newSMTPClient() ) ).toBeGT( 0 );
			});

			it( title="serializes with an explicit charset set (iso-8859-1)", body=function() {
				var client = newSMTPClient();
				client.setCharset( Charset::forName( "ISO-8859-1" ) );
				expect( serializedSize( client ) ).toBeGT( 0 );
			});

			it( title="serializes with all three charset-bearing fields populated", body=function() {
				var client = newSMTPClient();
				client.setCharset( Charset::forName( "UTF-8" ) );
				client.setPlainText( "plain body", Charset::forName( "ISO-8859-1" ) );
				client.setHTMLText( "<b>html body</b>", Charset::forName( "UTF-16" ) );
				expect( serializedSize( client ) ).toBeGT( 0 );
			});

		});
	}

	// Serialize a java object via ObjectOutputStream and return the number of bytes written.
	// Rethrows NotSerializableException (the failure this test guards against).
	private numeric function serializedSize( required any obj ) {
		var baos = new ByteArrayOutputStream();
		var oos = new ObjectOutputStream( baos );
		oos.writeObject( arguments.obj );
		oos.close();
		return baos.size();
	}

}
