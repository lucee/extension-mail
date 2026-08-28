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

	// SMTPClient lives in the internal package org.lucee.extension.mail.smtp. Preferred way to get
	// at it without hardcoding the extension version: borrow the classloader from a class in the
	// extension's public package (MailPart) - the same classloader can also load the internal
	// SMTPClient. Fall back to loading via the deployed maven library if that is not resolvable.
	variables.mailArtifact = "org.lucee:mail:1.1.0.8-SNAPSHOT";

	private function newSMTPClient() {
		try {
			var anchor = createObject( "java", "org.lucee.extension.mail.MailPart" ).init();
			var extLoader = anchor.getClass().getClassLoader();
			return extLoader.loadClass( "org.lucee.extension.mail.smtp.SMTPClient" ).newInstance();
		} catch ( any e ) {
			systemOutput( "SpoolerSerializationTest: classloader bridge failed [#e.message#], falling back to maven", true );
			return createObject( "java", "org.lucee.extension.mail.smtp.SMTPClient", { maven: [ variables.mailArtifact ] } ).init();
		}
	}

	function run( testResults, testBox ) {
		describe( title="SMTPClient spooler serialization (LDEV-6455)", body=function() {

			it( title="serializes a default client without NotSerializableException (UTF-8)", body=function() {
				// throws java.io.NotSerializableException: sun.nio.cs.UTF_8 before the fix
				expect( serializedSize( newSMTPClient() ) ).toBeGT( 0 );
			});

			it( title="serializes with an explicit charset set (iso-8859-1)", body=function() {
				// note: do not name this var "client" - that is a reserved CFML scope
				var smtp = newSMTPClient();
				smtp.setCharset( Charset::forName( "ISO-8859-1" ) );
				expect( serializedSize( smtp ) ).toBeGT( 0 );
			});

			it( title="serializes with all three charset-bearing fields populated", body=function() {
				var smtp = newSMTPClient();
				smtp.setCharset( Charset::forName( "UTF-8" ) );
				smtp.setPlainText( "plain body", Charset::forName( "ISO-8859-1" ) );
				smtp.setHTMLText( "<b>html body</b>", Charset::forName( "UTF-16" ) );
				expect( serializedSize( smtp ) ).toBeGT( 0 );
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
