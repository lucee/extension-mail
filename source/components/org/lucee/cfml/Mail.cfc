<cfcomponent extends="HelperBase" output="no">

	<cfset variables.tagname = "mail">

	<cffunction name="send" access="public" output="false" returntype="any" hint="sends the mail">
		<cfset local.tagAttributes = duplicate( getAttributes() )>
		<cfset local.tagParams = getParams()>
		<cfset local.body = "">
		<cfif structKeyExists( tagAttributes, "body" )>
			<cfset body = tagAttributes.body>
			<cfset structDelete( tagAttributes, "body" )>
		</cfif>
		<cfmail attributeCollection="#tagAttributes#">#body#<!---
			---><cfloop array="#tagParams#" index="local.param"><!---
				---><cfmailparam attributeCollection="#param#"><!---
			---></cfloop><!---
			---><cfloop array="#variables.parts#" index="local.p"><!---
				---><cfset local.part = duplicate( p )><!---
				---><cfset local.partbody = ""><!---
				---><cfif structKeyExists( part, "body" )><!---
					---><cfset partbody = part.body><!---
					---><cfset structDelete( part, "body" )><!---
				---></cfif><!---
				---><cfmailpart attributeCollection="#part#">#partbody#</cfmailpart><!---
			---></cfloop><!---
		---></cfmail>
		<cfreturn this>
	</cffunction>

</cfcomponent>
