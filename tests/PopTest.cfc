component extends="org.lucee.cfml.test.LuceeTestCase" labels="mailx"  javaSettings='{
		"maven": [
			"com.icegreen:greenmail:2.1.7"
		]
	}'  { 
	
	import "com.icegreen.greenmail.util.ServerSetup";
	import "com.icegreen.greenmail.util.GreenMail";
	import "com.icegreen.greenmail.util.GreenMailUtil";
	import "javax.mail.Message";
	import "javax.mail.internet.MimeMessage";
	import "javax.mail.internet.InternetAddress";
	import "javax.mail.Session";
	processingdirective pageencoding="UTF-8";

	variables.popPort = 30110;
	variables.smtpPort = 30252;
	variables.username = "poptest@localhost.com";
	variables.password = "poptestpass";
	variables.from = "sender@localhost.com";
	variables.to = "poptest@localhost.com";

	function beforeAll() {
		if(isNull(application.testPOPServer)) {
			var setups = [
				new ServerSetup(variables.popPort, nullValue(), ServerSetup::PROTOCOL_POP3),
				new ServerSetup(variables.smtpPort, nullValue(), ServerSetup::PROTOCOL_SMTP)
			];
			application.testPOPServer = new GreenMail(setups);
			application.testPOPServer.start();
			
			// Create test user
			application.testPOPServer.setUser(variables.username, variables.username, variables.password);
		}
		else {
			application.testPOPServer.purgeEmailFromAllMailboxes();
		}
	}

	function afterAll() {
		if(!isNull(application.testPOPServer)) {
			application.testPOPServer.purgeEmailFromAllMailboxes();
			application.testPOPServer.stop();
		}
	}

	function run( testResults , testBox ) {
		describe( title="Test suite for the tag cfpop", body=function() {
			
			it(title="test POP connection open/close", body = function( currentSpec ) {
				lock name="test:pop" {
					// Open connection
					pop action="getHeaderOnly"
						connection="testPopConn" 
						server="localhost" 
						port=variables.popPort 
						username=variables.username 
						password=variables.password 
						secure=false
						name="local.qryTest";
					
					expect(true).toBeTrue(); // If no error, test passes
				}
			});

			it(title="test POP getHeaderOnly action", body = function( currentSpec ) {
				lock name="test:pop" {
					application.testPOPServer.purgeEmailFromAllMailboxes();
					
					// Send test emails via SMTP
					sendTestEmail("POP Test Subject 1", "POP Test Body 1");
					sendTestEmail("POP Test Subject 2", "POP Test Body 2");
					
					sleep(500); // Give server time to process
					
					// Retrieve headers only
					pop action="getHeaderOnly" 
						name="local.qryEmails" 
						server="localhost" 
						port=variables.popPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qryEmails.recordCount).toBe(2);
					expect(qryEmails.subject).toInclude("POP Test Subject");
					
					application.testPOPServer.purgeEmailFromAllMailboxes();
				}
			});

			it(title="test POP getAll action", body = function( currentSpec ) {
				lock name="test:pop" {
					application.testPOPServer.purgeEmailFromAllMailboxes();
					
					// Send test email
					sendTestEmail("Full POP Email Test", "This is the complete POP email body");
					
					sleep(500);
					
					// Retrieve full emails
					pop action="getAll" 
						name="local.qryEmails" 
						server="localhost" 
						port=variables.popPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qryEmails.recordCount).toBe(1);
					expect(qryEmails.subject).toBe("Full POP Email Test");
					expect(qryEmails.body).toInclude("This is the complete POP email body");
					
					application.testPOPServer.purgeEmailFromAllMailboxes();
				}
			});

			it(title="test POP getAll with attachmentPath", body = function( currentSpec ) {
				lock name="test:pop" {
					application.testPOPServer.purgeEmailFromAllMailboxes();
					
					var attachDir = getTempDirectory() & "pop_attach_test/";
					if(!directoryExists(attachDir)) directoryCreate(attachDir);
					
					try {
						// Send email with attachment
						sendTestEmailWithAttachment("POP Email with Attachment", "See attached file");
						
						sleep(500);
						
						// Retrieve with attachment path
						pop action="getAll" 
							name="local.qryEmails" 
							attachmentPath="#attachDir#"
							server="localhost" 
							port=variables.popPort 
							username=variables.username 
							password=variables.password 
							secure=false;
						
						expect(qryEmails.recordCount).toBe(1);
						expect(qryEmails.attachments).toBeGT(0);
						
						// Check if attachment file was created
						var files = directoryList(attachDir);
						expect(arrayLen(files)).toBeGT(0);
						
					} finally {
						if(directoryExists(attachDir)) {
							directoryDelete(attachDir, true);
						}
					}
					
					application.testPOPServer.purgeEmailFromAllMailboxes();
				}
			});

			it(title="test POP delete action", body = function( currentSpec ) {
				lock name="test:pop" {
					application.testPOPServer.purgeEmailFromAllMailboxes();
					
					// Send test email
					sendTestEmail("POP Email to Delete", "This will be deleted");
					
					sleep(500);
					
					// Get message number first
					pop action="getHeaderOnly" 
						name="local.qryEmails" 
						server="localhost" 
						port=variables.popPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qryEmails.recordCount).toBe(1);
					
					var msgNum = qryEmails.messageNumber;
					
					// Delete the message
					pop action="delete" 
						messageNumber="#msgNum#"
						server="localhost" 
						port=variables.popPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					// Verify deletion
					pop action="getHeaderOnly" 
						name="local.qryEmails2" 
						server="localhost" 
						port=variables.popPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qryEmails2.recordCount).toBe(0);
					
					application.testPOPServer.purgeEmailFromAllMailboxes();
				}
			});

			it(title="test POP with uid parameter", body = function( currentSpec ) {
				lock name="test:pop" {
					application.testPOPServer.purgeEmailFromAllMailboxes();
					
					// Send test emails
					sendTestEmail("POP Email 1", "POP Body 1");
					sendTestEmail("POP Email 2", "POP Body 2");
					sendTestEmail("POP Email 3", "POP Body 3");
					
					sleep(500);
					
					// Get UIDs
					pop action="getHeaderOnly" 
						name="local.qryEmails" 
						server="localhost" 
						port=variables.popPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qryEmails.recordCount).toBe(3);
					
					var firstUid = qryEmails.uid[1];
					
					// Get specific email by UID
					pop action="getAll" 
						uid="#firstUid#"
						name="local.qrySpecific" 
						server="localhost" 
						port=variables.popPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qrySpecific.recordCount).toBe(1);
					expect(qrySpecific.uid).toBe(firstUid);
					
					application.testPOPServer.purgeEmailFromAllMailboxes();
				}
			});

			it(title="test POP with multiple UIDs", body = function( currentSpec ) {
				lock name="test:pop" {
					application.testPOPServer.purgeEmailFromAllMailboxes();
					
					// Send test emails
					sendTestEmail("POP Email A", "Body A");
					sendTestEmail("POP Email B", "Body B");
					sendTestEmail("POP Email C", "Body C");
					
					sleep(500);
					
					// Get UIDs
					pop action="getHeaderOnly" 
						name="local.qryEmails" 
						server="localhost" 
						port=variables.popPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					var uid1 = qryEmails.uid[1];
					var uid2 = qryEmails.uid[2];
					
					// Get specific emails by UIDs (comma-delimited)
					pop action="getAll" 
						uid="#uid1#,#uid2#"
						name="local.qrySpecific" 
						server="localhost" 
						port=variables.popPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qrySpecific.recordCount).toBe(2);
					
					application.testPOPServer.purgeEmailFromAllMailboxes();
				}
			});

			it(title="test POP with messageNumber parameter", body = function( currentSpec ) {
				lock name="test:pop" {
					application.testPOPServer.purgeEmailFromAllMailboxes();
					
					// Send test emails
					sendTestEmail("Message 1", "Body 1");
					sendTestEmail("Message 2", "Body 2");
					sendTestEmail("Message 3", "Body 3");
					
					sleep(500);
					
					// Get specific message by number
					pop action="getAll" 
						messageNumber="1"
						name="local.qrySpecific" 
						server="localhost" 
						port=variables.popPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qrySpecific.recordCount).toBe(1);
					expect(qrySpecific.messageNumber).toBe(1);
					
					application.testPOPServer.purgeEmailFromAllMailboxes();
				}
			});

			it(title="test POP with multiple messageNumbers", body = function( currentSpec ) {
				lock name="test:pop" {
					application.testPOPServer.purgeEmailFromAllMailboxes();
					
					// Send test emails
					sendTestEmail("Multi Message 1", "Body 1");
					sendTestEmail("Multi Message 2", "Body 2");
					sendTestEmail("Multi Message 3", "Body 3");
					
					sleep(500);
					
					// Get specific messages by numbers (comma-separated)
					pop action="getAll" 
						messageNumber="1,3"
						name="local.qrySpecific" 
						server="localhost" 
						port=variables.popPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qrySpecific.recordCount).toBe(2);
					
					application.testPOPServer.purgeEmailFromAllMailboxes();
				}
			});

			it(title="test POP with maxRows and startRow", body = function( currentSpec ) {
				lock name="test:pop" {
					application.testPOPServer.purgeEmailFromAllMailboxes();
					
					// Send multiple test emails
					for(var i=1; i<=5; i++) {
						sendTestEmail("POP Email #i#", "Body #i#");
					}
					
					sleep(500);
					
					// Get only 2 emails starting from row 2
					pop action="getHeaderOnly" 
						name="local.qryEmails" 
						startRow=2
						maxRows=2
						server="localhost" 
						port=variables.popPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qryEmails.recordCount).toBe(2);
					
					application.testPOPServer.purgeEmailFromAllMailboxes();
				}
			});

			it(title="test POP with timeout parameter", body = function( currentSpec ) {
				lock name="test:pop" {
					// Test with short timeout
					pop action="getHeaderOnly" 
						name="local.qryEmails" 
						timeout=5
						server="localhost" 
						port=variables.popPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(true).toBeTrue(); // If no timeout error, test passes
				}
			});

			it(title="test POP with delimiter parameter", body = function( currentSpec ) {
				lock name="test:pop" {
					application.testPOPServer.purgeEmailFromAllMailboxes();
					
					// Send test emails
					sendTestEmail("Delim Email 1", "Body 1");
					sendTestEmail("Delim Email 2", "Body 2");
					sendTestEmail("Delim Email 3", "Body 3");
					
					sleep(500);
					
					// Get UIDs
					pop action="getHeaderOnly" 
						name="local.qryEmails" 
						server="localhost" 
						port=variables.popPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					var uid1 = qryEmails.uid[1];
					var uid2 = qryEmails.uid[2];
					
					// Get specific emails using custom delimiter
					pop action="getAll" 
						uid="#uid1#;#uid2#"
						delimiter=";"
						name="local.qrySpecific" 
						server="localhost" 
						port=variables.popPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qrySpecific.recordCount).toBe(2);
					
					application.testPOPServer.purgeEmailFromAllMailboxes();
				}
			});

			it(title="test POP with generateUniqueFilenames", body = function( currentSpec ) {
				lock name="test:pop" {
					application.testPOPServer.purgeEmailFromAllMailboxes();
					
					var attachDir = getTempDirectory() & "pop_unique_test/";
					if(!directoryExists(attachDir)) directoryCreate(attachDir);
					
					try {
						// Send emails with same attachment name
						sendTestEmailWithAttachment("Unique Test 1", "Body 1");
						sendTestEmailWithAttachment("Unique Test 2", "Body 2");
						
						sleep(500);
						
						// Retrieve with unique filename generation
						pop action="getAll" 
							name="local.qryEmails" 
							attachmentPath="#attachDir#"
							generateUniqueFilenames=true
							server="localhost" 
							port=variables.popPort 
							username=variables.username 
							password=variables.password 
							secure=false;
						
						var files = directoryList(attachDir);
						// Should have unique filenames for duplicate attachments
						expect(arrayLen(files)).toBeGTE(2);
						
					} finally {
						if(directoryExists(attachDir)) {
							directoryDelete(attachDir, true);
						}
					}
					
					application.testPOPServer.purgeEmailFromAllMailboxes();
				}
			});

			it(title="test POP delete with multiple messages", body = function( currentSpec ) {
				lock name="test:pop" {
					application.testPOPServer.purgeEmailFromAllMailboxes();
					
					// Send test emails
					sendTestEmail("Delete Multi 1", "Body 1");
					sendTestEmail("Delete Multi 2", "Body 2");
					sendTestEmail("Delete Multi 3", "Body 3");
					
					sleep(500);
					
					// Get message numbers
					pop action="getHeaderOnly" 
						name="local.qryEmails" 
						server="localhost" 
						port=variables.popPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qryEmails.recordCount).toBe(3);
					
					// Delete multiple messages
					pop action="delete" 
						messageNumber="1,2"
						server="localhost" 
						port=variables.popPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					// Verify deletion
					pop action="getHeaderOnly" 
						name="local.qryEmails2" 
						server="localhost" 
						port=variables.popPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qryEmails2.recordCount).toBe(1);
					
					application.testPOPServer.purgeEmailFromAllMailboxes();
				}
			});

			it(title="test POP with connection parameter", body = function( currentSpec ) {
				lock name="test:pop" {
					application.testPOPServer.purgeEmailFromAllMailboxes();
					
					sendTestEmail("Connection Test", "Testing connection parameter");
					
					sleep(500);
					
					// Use connection parameter
					pop action="getHeaderOnly" 
						connection="myPopConnection"
						name="local.qryEmails" 
						server="localhost" 
						port=variables.popPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qryEmails.recordCount).toBe(1);
					
					application.testPOPServer.purgeEmailFromAllMailboxes();
				}
			});

			it(title="test POP with HTML email", body = function( currentSpec ) {
				lock name="test:pop" {
					application.testPOPServer.purgeEmailFromAllMailboxes();
					
					// Send HTML email
					mail to=variables.to 
						from=variables.from 
						subject="HTML Email Test" 
						type="html"
						spoolEnable=false 
						server="localhost" 
						port=variables.smtpPort {
						echo("<html><body><h1>HTML Test</h1><p>This is HTML content</p></body></html>");
					}
					
					sleep(500);
					
					// Retrieve email
					pop action="getAll" 
						name="local.qryEmails" 
						server="localhost" 
						port=variables.popPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qryEmails.recordCount).toBe(1);
					expect(qryEmails.subject).toBe("HTML Email Test");
					expect(qryEmails.htmlBody).toInclude("HTML Test");
					
					application.testPOPServer.purgeEmailFromAllMailboxes();
				}
			});

			it(title="test POP with multipart email", body = function( currentSpec ) {
				lock name="test:pop" {
					application.testPOPServer.purgeEmailFromAllMailboxes();
					
					// Send multipart email
					mail to=variables.to 
						from=variables.from 
						subject="Multipart Email Test" 
						spoolEnable=false 
						server="localhost" 
						port=variables.smtpPort {
						mailpart type="text" {
							echo("Plain text version");
						}
						mailpart type="html" {
							echo("<html><body>HTML version</body></html>");
						}
					}
					
					sleep(500);
					
					// Retrieve email
					pop action="getAll" 
						name="local.qryEmails" 
						server="localhost" 
						port=variables.popPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qryEmails.recordCount).toBe(1);
					expect(qryEmails.subject).toBe("Multipart Email Test");
					expect(qryEmails.body).toInclude("Plain text version");
					expect(qryEmails.htmlBody).toInclude("HTML version");
					
					application.testPOPServer.purgeEmailFromAllMailboxes();
				}
			});

			it(title="test POP invalid credentials", body = function( currentSpec ) {
				lock name="test:pop" {
					var hasError = false;
					try {
						// Try with invalid password
						pop action="getHeaderOnly" 
							name="local.qryEmails" 
							server="localhost" 
							port=variables.popPort 
							username=variables.username 
							password="wrongpassword" 
							secure=false;
					} catch(any e) {
						hasError = true;
					}
					
					expect(hasError).toBeTrue();
				}
			});

		});
	}

	// Helper function to send test email via SMTP
	private function sendTestEmail(required string subject, required string body) {
		mail to=variables.to 
			from=variables.from 
			subject=arguments.subject 
			spoolEnable=false 
			server="localhost" 
			port=variables.smtpPort {
			echo(arguments.body);
		}
	}

	// Helper function to send test email with attachment
	private function sendTestEmailWithAttachment(required string subject, required string body) {
		var tempFile = getTempDirectory() & "pop_test_attachment.txt";
		fileWrite(tempFile, "This is a POP test attachment");
		
		try {
			mail to=variables.to 
				from=variables.from 
				subject=arguments.subject 
				spoolEnable=false 
				server="localhost" 
				port=variables.smtpPort {
				echo(arguments.body);
				mailparam file="#tempFile#";
			}
		} finally {
			if(fileExists(tempFile)) fileDelete(tempFile);
		}
	}
}
