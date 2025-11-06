component extends="org.lucee.cfml.test.LuceeTestCase" labels="mail"  javaSettings='{
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

	variables.imapPort = 30143;
	variables.smtpPort = 30251;
	variables.username = "test@localhost.com";
	variables.password = "testpass";
	variables.from = "sender@localhost.com";
	variables.to = "test@localhost.com";

	function beforeAll() {
		if(isNull(application.testIMAPServer)) {
			var setups = [
				new ServerSetup(variables.imapPort, nullValue(), ServerSetup::PROTOCOL_IMAP),
				new ServerSetup(variables.smtpPort, nullValue(), ServerSetup::PROTOCOL_SMTP)
			];
			application.testIMAPServer = new GreenMail(setups);
			application.testIMAPServer.start();
			
			// Create test user
			application.testIMAPServer.setUser(variables.username, variables.username, variables.password);
		}
		else {
			application.testIMAPServer.purgeEmailFromAllMailboxes();
		}
	}

	function afterAll() {
		if(!isNull(application.testIMAPServer)) {
			application.testIMAPServer.purgeEmailFromAllMailboxes();
			application.testIMAPServer.stop();
		}
	}

	function run( testResults , testBox ) {
		describe( title="Test suite for the tag cfimap", body=function() {
			
			it(title="test IMAP connection open/close", body = function( currentSpec ) {
				lock name="test:imap" {
					// Open connection
					imap action="open" 
						connection="testConn" 
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					// Close connection
					imap action="close" connection="testConn";
					
					expect(true).toBeTrue(); // If no error, test passes
				}
			});

			it(title="test IMAP getHeaderOnly action", body = function( currentSpec ) {
				lock name="test:imap" {
					application.testIMAPServer.purgeEmailFromAllMailboxes();
					
					// Send test emails via SMTP
					sendTestEmail("Test Subject 1", "Test Body 1");
					sendTestEmail("Test Subject 2", "Test Body 2");
					
					sleep(500); // Give server time to process
					
					// Retrieve headers only
					imap action="getHeaderOnly" 
						name="local.qryEmails" 
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qryEmails.recordCount).toBe(2);
					expect(qryEmails.subject).toInclude("Test Subject");
					
					application.testIMAPServer.purgeEmailFromAllMailboxes();
				}
			});

			it(title="test IMAP getAll action", body = function( currentSpec ) {
				lock name="test:imap" {
					application.testIMAPServer.purgeEmailFromAllMailboxes();
					
					// Send test email
					sendTestEmail("Full Email Test", "This is the complete email body");
					
					sleep(500);
					
					// Retrieve full emails
					imap action="getAll" 
						name="local.qryEmails" 
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qryEmails.recordCount).toBe(1);
					expect(qryEmails.subject).toBe("Full Email Test");
					expect(qryEmails.body).toInclude("This is the complete email body");
					
					application.testIMAPServer.purgeEmailFromAllMailboxes();
				}
			});

			it(title="test IMAP getAll with attachmentPath", body = function( currentSpec ) {
				lock name="test:imap" {
					application.testIMAPServer.purgeEmailFromAllMailboxes();
					
					var attachDir = getTempDirectory() & "imap_attach_test/";
					if(!directoryExists(attachDir)) directoryCreate(attachDir);
					
					try {
						// Send email with attachment
						sendTestEmailWithAttachment("Email with Attachment", "See attached file");
						
						sleep(500);
						
						// Retrieve with attachment path
						imap action="getAll" 
							name="local.qryEmails" 
							attachmentPath="#attachDir#"
							server="localhost" 
							port=variables.imapPort 
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
					
					application.testIMAPServer.purgeEmailFromAllMailboxes();
				}
			});

			it(title="test IMAP delete action", body = function( currentSpec ) {
				lock name="test:imap" {
					application.testIMAPServer.purgeEmailFromAllMailboxes();
					
					// Send test emails
					sendTestEmail("Email to Delete", "This will be deleted");
					
					sleep(500);
					
					// Get message number first
					imap action="getHeaderOnly" 
						name="local.qryEmails" 
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qryEmails.recordCount).toBe(1);
					
					var msgNum = qryEmails.messageNumber;
					
					// Delete the message
					imap action="delete" 
						messageNumber="#msgNum#"
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					// Verify deletion
					imap action="getHeaderOnly" 
						name="local.qryEmails2" 
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qryEmails2.recordCount).toBe(0);
					
					application.testIMAPServer.purgeEmailFromAllMailboxes();
				}
			});

			it(title="test IMAP with uid parameter", body = function( currentSpec ) {
				lock name="test:imap" {
					application.testIMAPServer.purgeEmailFromAllMailboxes();
					
					// Send test emails
					sendTestEmail("Email 1", "Body 1");
					sendTestEmail("Email 2", "Body 2");
					
					sleep(500);
					
					// Get UIDs
					imap action="getHeaderOnly" 
						name="local.qryEmails" 
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qryEmails.recordCount).toBe(2);
					
					var firstUid = qryEmails.uid[1];
					
					// Get specific email by UID
					imap action="getAll" 
						uid="#firstUid#"
						name="local.qrySpecific" 
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qrySpecific.recordCount).toBe(1);
					expect(qrySpecific.uid).toBe(firstUid);
					
					application.testIMAPServer.purgeEmailFromAllMailboxes();
				}
			});

			it(title="test IMAP markRead action", body = function( currentSpec ) {
				lock name="test:imap" {
					application.testIMAPServer.purgeEmailFromAllMailboxes();
					
					// Send test email
					sendTestEmail("Unread Email", "Mark me as read");
					
					sleep(500);
					
					// Get message
					imap action="getHeaderOnly" 
						name="local.qryEmails" 
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					var msgNum = qryEmails.messageNumber;
					
					// Mark as read
					imap action="markRead" 
						messageNumber="#msgNum#"
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(true).toBeTrue(); // If no error, test passes
					
					application.testIMAPServer.purgeEmailFromAllMailboxes();
				}
			});

			it(title="test IMAP createFolder action", body = function( currentSpec ) {
				lock name="test:imap" {
					// Create a new folder
					imap action="createFolder" 
						folder="TestFolder"
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					// List folders to verify creation
					imap action="listAllFolders" 
						name="local.qryFolders"
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					var folderList = valueList(qryFolders.name);
					expect(folderList).toInclude("TestFolder");
				}
			});

			it(title="test IMAP renameFolder action", body = function( currentSpec ) {
				lock name="test:imap" {
					// Create folder
					imap action="createFolder" 
						folder="OldFolderName"
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					// Rename folder
					imap action="renameFolder" 
						folder="OldFolderName"
						newFolder="NewFolderName"
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					// List folders to verify rename
					imap action="listAllFolders" 
						name="local.qryFolders"
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					var folderList = valueList(qryFolders.name);
					expect(folderList).toInclude("NewFolderName");
					expect(folderList).notToInclude("OldFolderName");
				}
			});

			it(title="test IMAP deleteFolder action", body = function( currentSpec ) {
				lock name="test:imap" {
					// Create folder
					imap action="createFolder" 
						folder="FolderToDelete"
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					// Delete folder
					imap action="deleteFolder" 
						folder="FolderToDelete"
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					// List folders to verify deletion
					imap action="listAllFolders" 
						name="local.qryFolders"
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					var folderList = valueList(qryFolders.name);
					expect(folderList).notToInclude("FolderToDelete");
				}
			});

			it(title="test IMAP moveMail action", body = function( currentSpec ) {
				lock name="test:imap" {
					application.testIMAPServer.purgeEmailFromAllMailboxes();
					
					// Create destination folder
					imap action="createFolder" 
						folder="DestinationFolder"
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					// Send test email
					sendTestEmail("Email to Move", "Moving to another folder");
					
					sleep(500);
					
					// Get message number
					imap action="getHeaderOnly" 
						name="local.qryEmails" 
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					var msgNum = qryEmails.messageNumber;
					
					// Move mail to destination folder
					imap action="moveMail" 
						messageNumber="#msgNum#"
						newFolder="DestinationFolder"
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					// Verify email moved from INBOX
					imap action="getHeaderOnly" 
						name="local.qryInbox" 
						folder="INBOX"
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qryInbox.recordCount).toBe(0);
					
					// Verify email in destination folder
					imap action="getHeaderOnly" 
						name="local.qryDest" 
						folder="DestinationFolder"
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qryDest.recordCount).toBe(1);
					expect(qryDest.subject).toBe("Email to Move");
					
					application.testIMAPServer.purgeEmailFromAllMailboxes();
				}
			});

			it(title="test IMAP with maxRows and startRow", body = function( currentSpec ) {
				lock name="test:imap" {
					application.testIMAPServer.purgeEmailFromAllMailboxes();
					
					// Send multiple test emails
					for(var i=1; i<=5; i++) {
						sendTestEmail("Email #i#", "Body #i#");
					}
					
					sleep(500);
					
					// Get only 2 emails starting from row 2
					imap action="getHeaderOnly" 
						name="local.qryEmails" 
						startRow=2
						maxRows=2
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qryEmails.recordCount).toBe(2);
					
					application.testIMAPServer.purgeEmailFromAllMailboxes();
				}
			});

			it(title="test IMAP with timeout parameter", body = function( currentSpec ) {
				lock name="test:imap" {
					// Test with short timeout
					imap action="getHeaderOnly" 
						name="local.qryEmails" 
						timeout=5
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(true).toBeTrue(); // If no timeout error, test passes
				}
			});

			it(title="test IMAP with delimiter parameter", body = function( currentSpec ) {
				lock name="test:imap" {
					application.testIMAPServer.purgeEmailFromAllMailboxes();
					
					// Send test emails
					sendTestEmail("Email 1", "Body 1");
					sendTestEmail("Email 2", "Body 2");
					sendTestEmail("Email 3", "Body 3");
					
					sleep(500);
					
					// Get UIDs
					imap action="getHeaderOnly" 
						name="local.qryEmails" 
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					var uid1 = qryEmails.uid[1];
					var uid2 = qryEmails.uid[2];
					
					// Get specific emails using custom delimiter
					imap action="getAll" 
						uid="#uid1#;#uid2#"
						delimiter=";"
						name="local.qrySpecific" 
						server="localhost" 
						port=variables.imapPort 
						username=variables.username 
						password=variables.password 
						secure=false;
					
					expect(qrySpecific.recordCount).toBe(2);
					
					application.testIMAPServer.purgeEmailFromAllMailboxes();
				}
			});

			it(title="test IMAP with generateUniqueFilenames", body = function( currentSpec ) {
				lock name="test:imap" {
					application.testIMAPServer.purgeEmailFromAllMailboxes();
					
					var attachDir = getTempDirectory() & "imap_unique_test/";
					if(!directoryExists(attachDir)) directoryCreate(attachDir);
					
					try {
						// Send emails with same attachment name
						sendTestEmailWithAttachment("Email 1", "Body 1");
						sendTestEmailWithAttachment("Email 2", "Body 2");
						
						sleep(500);
						
						// Retrieve with unique filename generation
						imap action="getAll" 
							name="local.qryEmails" 
							attachmentPath="#attachDir#"
							generateUniqueFilenames=true
							server="localhost" 
							port=variables.imapPort 
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
					
					application.testIMAPServer.purgeEmailFromAllMailboxes();
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
		var tempFile = getTempDirectory() & "test_attachment.txt";
		fileWrite(tempFile, "This is a test attachment");
		
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
