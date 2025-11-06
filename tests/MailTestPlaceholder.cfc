component extends="org.lucee.cfml.test.LuceeTestCase" labels="mail" {
    /*

    the lucee test suite runs without a http server, so the mail stuff can't be unit tested

    the test suite fails if no matching tests are found, so this is a no-op test so CI passes

    */ 
    function testNothing() {
        expect (true ).toBeTrue();
    }
}