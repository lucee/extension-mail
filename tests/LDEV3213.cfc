/**
 * LDEV-3213: queued mails are stored as serialized MailSpoolerTask objects (.tsk files). A class without an
 * explicit serialVersionUID gets one computed from its shape, so a later change (a new method is enough) makes
 * every mail queued by the previous version unreadable ("local class incompatible"). The classes of a spooled
 * mail now pin the value Java computed for every release so far (1.1.0.8-RC to 1.1.0.13), so existing .tsk
 * files stay readable.
 */
component extends="org.lucee.cfml.test.LuceeTestCase" labels="mail" {

	variables.mailExtensionId = "212BA548-F15A-4EBD-8B1EEDF8DD8A844D";

	variables.expected = {
		"org.lucee.extension.mail.spooler.MailSpoolerTask": "6690233946508798338",
		"org.lucee.extension.mail.spooler.MailSpoolerTask$ExecutionPlanImpl": "-9000557328580396853",
		"org.lucee.extension.mail.spooler.SpoolerTaskSupport": "2150341858025259745",
		"org.lucee.extension.mail.smtp.SMTPClient": "5227282806519740328",
		"org.lucee.extension.mail.smtp.Attachment": "-2819067813866402187",
		"org.lucee.extension.mail.proxy.ProxyDataImpl": "-6345158304783143024",
		"org.lucee.extension.mail.CharsetSerializable": "1"
	};

	private string function mailArtifact() {
		var q = extensionList();
		loop query=q {
			if ( q.id == variables.mailExtensionId ) return "org.lucee:mail:" & q.version;
		}
		throw( message="mail extension [#variables.mailExtensionId#] not found via extensionList()" );
	}

	private function extensionLoader() {
		return createObject( "java", "org.lucee.extension.mail.smtp.SMTPClient", { maven: [ mailArtifact() ] } ).init().getClass().getClassLoader();
	}

	function run( testResults, testBox ) {
		describe( title="LDEV-3213 spooled mail classes keep their serialVersionUID", body=function() {

			it( title="every class of a spooled mail declares the serialVersionUID of the previous releases", body=function() {
				var cl = extensionLoader();
				var Clazz = createObject( "java", "java.lang.Class" );
				var OSC = createObject( "java", "java.io.ObjectStreamClass" );
				loop collection=variables.expected key="local.name" value="local.suid" {
					var c = Clazz.forName( name, false, cl );
					var declared = c.getDeclaredField( "serialVersionUID" ); // throws if it is not declared
					// compare as text (ObjectStreamClass.toString), a CFML number would lose precision on 64 bit values
					expect( OSC.lookup( c ).toString() ).toInclude( "serialVersionUID = " & suid & "L", "serialVersionUID of [#name#]" );
				}
			});
		});
	}
}
