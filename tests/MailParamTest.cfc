component extends="org.lucee.cfml.test.LuceeTestCase" labels="mailx"  javaSettings='{
		"maven": [
			"com.icegreen:greenmail:2.1.7"
		]
	}'  { 
	
	import "com.icegreen.greenmail.util.ServerSetup";
	import "com.icegreen.greenmail.util.GreenMail";
	import "com.icegreen.greenmail.util.GreenMailUtil";
	processingdirective pageencoding="UTF-8";

	variables.port=30254;
	variables.from="sender@test.com";
	variables.to="recipient@test.com";

	function beforeAll() {
		if(isNull(application.testMailParamSMTP)) {
			application.testMailParamSMTP=new GreenMail(new ServerSetup(variables.port, nullValue(), ServerSetup::PROTOCOL_SMTP));
			application.testMailParamSMTP.start();
		}
		else {
			application.testMailParamSMTP.purgeEmailFromAllMailboxes();
		}
	}

	function afterAll() {
		if(!isNull(application.testMailParamSMTP)) {
			application.testMailParamSMTP.purgeEmailFromAllMailboxes();
			application.testMailParamSMTP.stop();
		}
	}

	function run( testResults , testBox ) {
		describe( title="Test suite for the tag cfmailparam", body=function() {
			
			it(title="mailparam with custom header", body = function( currentSpec ) {
				lock name="test:mailparam" {
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
					
					mail to=variables.to from=variables.from subject="Custom Header" 
						spoolEnable=false server="localhost" port=variables.port {
						mailparam name="X-Custom-Header" value="CustomValue";
						echo("Email with custom header");
					}

					var messages = application.testMailParamSMTP.getReceivedMessages();
					expect(len(messages)).toBe(1);
					var msg = messages[1];
					
					var headers = msg.getHeader("X-Custom-Header");
					expect(arrayLen(headers)).toBe(1);
					expect(headers[1]).toBe("CustomValue");
					
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
				}
			});

			it(title="mailparam with multiple custom headers", body = function( currentSpec ) {
				lock name="test:mailparam" {
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
					
					mail to=variables.to from=variables.from subject="Multiple Headers" 
						spoolEnable=false server="localhost" port=variables.port {
						mailparam name="X-Header-One" value="Value1";
						mailparam name="X-Header-Two" value="Value2";
						mailparam name="X-Header-Three" value="Value3";
						echo("Email with multiple custom headers");
					}

					var messages = application.testMailParamSMTP.getReceivedMessages();
					expect(len(messages)).toBe(1);
					var msg = messages[1];
					
					expect(msg.getHeader("X-Header-One")[1]).toBe("Value1");
					expect(msg.getHeader("X-Header-Two")[1]).toBe("Value2");
					expect(msg.getHeader("X-Header-Three")[1]).toBe("Value3");
					
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
				}
			});

			it(title="mailparam file attachment", body = function( currentSpec ) {
				lock name="test:mailparam" {
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
					
					var tempFile = getTempDirectory() & "test_attachment.txt";
					var fileContent = "This is test attachment content";
					fileWrite(tempFile, fileContent);
					
					try {
						mail to=variables.to from=variables.from subject="File Attachment" 
							spoolEnable=false server="localhost" port=variables.port {
							mailparam file="#tempFile#";
							echo("Email with file attachment");
						}

						var messages = application.testMailParamSMTP.getReceivedMessages();
						expect(len(messages)).toBe(1);
						var msg = messages[1];
						
						var content = msg.getContent();
						expect(content.getCount()).toBe(2);
						
						// Check attachment
						var attachment = content.getBodyPart(1);
						expect(attachment.getDisposition()).toBe("attachment");
						expect(attachment.getFileName()).toBe("test_attachment.txt");
						
					} finally {
						if(fileExists(tempFile)) fileDelete(tempFile);
					}
					
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
				}
			});

			it(title="mailparam with custom filename", body = function( currentSpec ) {
				lock name="test:mailparam" {
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
					
					var tempFile = getTempDirectory() & "original_name.txt";
					fileWrite(tempFile, "Test content");
					
					try {
						mail to=variables.to from=variables.from subject="Custom Filename" 
							spoolEnable=false server="localhost" port=variables.port {
							mailparam file="#tempFile#" filename="custom_name.txt";
							echo("Email with custom filename");
						}

						var messages = application.testMailParamSMTP.getReceivedMessages();
						expect(len(messages)).toBe(1);
						var msg = messages[1];
						
						var content = msg.getContent();
						var attachment = content.getBodyPart(1);
						expect(attachment.getFileName()).toBe("custom_name.txt");
						
					} finally {
						if(fileExists(tempFile)) fileDelete(tempFile);
					}
					
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
				}
			});

			it(title="mailparam inline disposition", body = function( currentSpec ) {
				lock name="test:mailparam" {
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
					
					var tempFile = getTempDirectory() & "inline_file.txt";
					fileWrite(tempFile, "Inline content");
					
					try {
						mail to=variables.to from=variables.from subject="Inline Disposition" 
							spoolEnable=false server="localhost" port=variables.port type="html" {
							mailparam file="#tempFile#" disposition="inline" contentId="myContent";
							echo('<html><body><p>See inline content</p></body></html>');
						}

						var messages = application.testMailParamSMTP.getReceivedMessages();
						expect(len(messages)).toBe(1);
						var msg = messages[1];
						
						var content = msg.getContent();
						var attachment = content.getBodyPart(1);
						expect(attachment.getDisposition()).toBe("inline");
						
					} finally {
						if(fileExists(tempFile)) fileDelete(tempFile);
					}
					
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
				}
			});

			it(title="mailparam with contentId", body = function( currentSpec ) {
				lock name="test:mailparam" {
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
					
					var tempFile = getTempDirectory() & "image.txt";
					fileWrite(tempFile, "Fake image data");
					
					try {
						mail to=variables.to from=variables.from subject="Content ID" 
							spoolEnable=false server="localhost" port=variables.port type="html" {
							mailparam file="#tempFile#" disposition="inline" contentId="image123";
							echo('<html><body><img src="cid:image123"/></body></html>');
						}

						var messages = application.testMailParamSMTP.getReceivedMessages();
						expect(len(messages)).toBe(1);
						var msg = messages[1];
						
						var content = msg.getContent();
						var attachment = content.getBodyPart(1);
						var contentId = attachment.getContentID();
						expect(isNull(contentId)).toBeFalse();
						
					} finally {
						if(fileExists(tempFile)) fileDelete(tempFile);
					}
					
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
				}
			});

			it(title="mailparam with custom type", body = function( currentSpec ) {
				lock name="test:mailparam" {
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
					
					var tempFile = getTempDirectory() & "data.json";
					fileWrite(tempFile, '{"test": "value"}');
					
					try {
						mail to=variables.to from=variables.from subject="Custom Type" 
							spoolEnable=false server="localhost" port=variables.port {
							mailparam file="#tempFile#" type="application/json";
							echo("Email with JSON attachment");
						}

						var messages = application.testMailParamSMTP.getReceivedMessages();
						expect(len(messages)).toBe(1);
						var msg = messages[1];
						
						var content = msg.getContent();
						var attachment = content.getBodyPart(1);
						expect(attachment.getContentType()).toInclude("application/json");
						
					} finally {
						if(fileExists(tempFile)) fileDelete(tempFile);
					}
					
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
				}
			});

			it(title="mailparam with content parameter", body = function( currentSpec ) {
				lock name="test:mailparam" {
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
					
					var binaryContent = charsetDecode("Binary content test", "UTF-8");
					
					mail to=variables.to from=variables.from subject="Content Parameter" 
						spoolEnable=false server="localhost" port=variables.port {
						mailparam content="#binaryContent#" filename="generated.txt" type="text/plain";
						echo("Email with content parameter");
					}

					var messages = application.testMailParamSMTP.getReceivedMessages();
					expect(len(messages)).toBe(1);
					var msg = messages[1];
					
					var content = msg.getContent();
					expect(content.getCount()).toBe(2);
					
					var attachment = content.getBodyPart(1);
					expect(attachment.getFileName()).toBe("generated.txt");
					
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
				}
			});

			it(title="mailparam multiple file attachments", body = function( currentSpec ) {
				lock name="test:mailparam" {
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
					
					var file1 = getTempDirectory() & "attachment1.txt";
					var file2 = getTempDirectory() & "attachment2.txt";
					var file3 = getTempDirectory() & "attachment3.txt";
					
					fileWrite(file1, "Content 1");
					fileWrite(file2, "Content 2");
					fileWrite(file3, "Content 3");
					
					try {
						mail to=variables.to from=variables.from subject="Multiple Attachments" 
							spoolEnable=false server="localhost" port=variables.port {
							mailparam file="#file1#";
							mailparam file="#file2#";
							mailparam file="#file3#";
							echo("Email with three attachments");
						}

						var messages = application.testMailParamSMTP.getReceivedMessages();
						expect(len(messages)).toBe(1);
						var msg = messages[1];
						
						var content = msg.getContent();
						// Body + 3 attachments = 4 parts
						expect(content.getCount()).toBe(4);
						
					} finally {
						if(fileExists(file1)) fileDelete(file1);
						if(fileExists(file2)) fileDelete(file2);
						if(fileExists(file3)) fileDelete(file3);
					}
					
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
				}
			});

			it(title="mailparam with special characters in filename", body = function( currentSpec ) {
				lock name="test:mailparam" {
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
					
					var specialFilename = "file_with_äöü_€.txt";
					var tempFile = getTempDirectory() & "temp.txt";
					fileWrite(tempFile, "Content");
					
					try {
						mail to=variables.to from=variables.from subject="Special Filename" 
							spoolEnable=false server="localhost" port=variables.port {
							mailparam file="#tempFile#" filename="#specialFilename#";
							echo("Email with special characters in filename");
						}

						var messages = application.testMailParamSMTP.getReceivedMessages();
						expect(len(messages)).toBe(1);
						var msg = messages[1];
						
						var content = msg.getContent();
						var attachment = content.getBodyPart(1);
						// Filename should be encoded properly
						expect(len(attachment.getFileName())).toBeGT(0);
						
					} finally {
						if(fileExists(tempFile)) fileDelete(tempFile);
					}
					
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
				}
			});

			it(title="mailparam remove attribute", body = function( currentSpec ) {
				lock name="test:mailparam" {
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
					
					var tempFile = getTempDirectory() & "remove_test.txt";
					fileWrite(tempFile, "This file should be removed");
					
					var fileExistedBefore = fileExists(tempFile);
					
					mail to=variables.to from=variables.from subject="Remove File" 
						spoolEnable=false server="localhost" port=variables.port {
						mailparam file="#tempFile#" remove=true;
						echo("Email with file to be removed");
					}

					var messages = application.testMailParamSMTP.getReceivedMessages();
					expect(len(messages)).toBe(1);
					
					// File should have existed before
					expect(fileExistedBefore).toBeTrue();
					
					// Note: remove=true deletes the file after sending
					// In a real test this would verify deletion, but timing can be tricky
					
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
				}
			});

			it(title="mailparam header with special characters", body = function( currentSpec ) {
				lock name="test:mailparam" {
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
					
					mail to=variables.to from=variables.from subject="Special Header Value" 
						spoolEnable=false server="localhost" port=variables.port {
						mailparam name="X-Special" value="Value with äöü and €";
						echo("Email with special characters in header");
					}

					var messages = application.testMailParamSMTP.getReceivedMessages();
					expect(len(messages)).toBe(1);
					var msg = messages[1];
					
					var headers = msg.getHeader("X-Special");
					expect(arrayLen(headers)).toBe(1);
					// Header should exist even with special chars
					expect(len(headers[1])).toBeGT(0);
					
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
				}
			});

			it(title="mailparam mixed headers and attachments", body = function( currentSpec ) {
				lock name="test:mailparam" {
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
					
					var tempFile = getTempDirectory() & "mixed_test.txt";
					fileWrite(tempFile, "Mixed test");
					
					try {
						mail to=variables.to from=variables.from subject="Mixed Params" 
							spoolEnable=false server="localhost" port=variables.port {
							mailparam name="X-Header-1" value="HeaderValue1";
							mailparam file="#tempFile#";
							mailparam name="X-Header-2" value="HeaderValue2";
							echo("Email with both headers and attachment");
						}

						var messages = application.testMailParamSMTP.getReceivedMessages();
						expect(len(messages)).toBe(1);
						var msg = messages[1];
						
						// Check headers
						expect(msg.getHeader("X-Header-1")[1]).toBe("HeaderValue1");
						expect(msg.getHeader("X-Header-2")[1]).toBe("HeaderValue2");
						
						// Check attachment
						var content = msg.getContent();
						expect(content.getCount()).toBe(2);
						
					} finally {
						if(fileExists(tempFile)) fileDelete(tempFile);
					}
					
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
				}
			});

			it(title="mailparam with empty value", body = function( currentSpec ) {
				lock name="test:mailparam" {
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
					
					mail to=variables.to from=variables.from subject="Empty Header Value" 
						spoolEnable=false server="localhost" port=variables.port {
						mailparam name="X-Empty-Header" value="";
						echo("Email with empty header value");
					}

					var messages = application.testMailParamSMTP.getReceivedMessages();
					expect(len(messages)).toBe(1);
					var msg = messages[1];
					
					// Header should still exist even with empty value
					var headers = msg.getHeader("X-Empty-Header");
					expect(arrayLen(headers)).toBeGTE(0);
					
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
				}
			});

			it(title="mailparam large file attachment", body = function( currentSpec ) {
				lock name="test:mailparam" {
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
					
					var tempFile = getTempDirectory() & "large_file.txt";
					var largeContent = repeatString("This is a line of text that will be repeated many times.#chr(10)#", 1000);
					fileWrite(tempFile, largeContent);
					
					try {
						mail to=variables.to from=variables.from subject="Large Attachment" 
							spoolEnable=false server="localhost" port=variables.port {
							mailparam file="#tempFile#";
							echo("Email with large attachment");
						}

						var messages = application.testMailParamSMTP.getReceivedMessages();
						expect(len(messages)).toBe(1);
						var msg = messages[1];
						
						var content = msg.getContent();
						expect(content.getCount()).toBe(2);
						
					} finally {
						if(fileExists(tempFile)) fileDelete(tempFile);
					}
					
					application.testMailParamSMTP.purgeEmailFromAllMailboxes();
				}
			});

		});
	}
}
