package com.almende.dialog.proxy;

import java.io.StringWriter;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.znerd.xmlenc.XMLOutputter;

import com.sun.jersey.spi.resource.Singleton;
@Singleton
@Path("/ccxml/")
public class CCXMLProxy {
	
	private String renderCCXML(String selectedDialog){
		StringWriter sw = new StringWriter();
		try {
			XMLOutputter outputter = new XMLOutputter(sw, "UTF-8");
			outputter.declaration();
			outputter.startTag("ccxml");
				outputter.attribute("version", "1.0");
				outputter.attribute("xmlns", "http://www.w3.org/2002/09/ccxml");
				outputter.startTag("var");
					outputter.attribute("name", "currentState");
					outputter.attribute("expr", "'initial'");
				outputter.endTag();
				outputter.startTag("var");
					outputter.attribute("name", "vxmlScript");
					outputter.attribute("expr", "'"+selectedDialog+"'");
				outputter.endTag();
				outputter.startTag("var");
					outputter.attribute("name", "dialogID");
					outputter.attribute("expr", "''");
				outputter.endTag();
				outputter.startTag("var");
					outputter.attribute("name", "connectionID");
					outputter.attribute("expr", "''");
				outputter.endTag();
				outputter.startTag("var");
					outputter.attribute("name", "remoteID");
					outputter.attribute("expr", "''");
				outputter.endTag();
				outputter.startTag("eventprocessor");
					outputter.attribute("statevariable", "currentState");
					outputter.startTag("transition");
						outputter.attribute("state", "initial");
						outputter.attribute("event", "connection.alerting");
						outputter.startTag("assign");
							outputter.attribute("name", "connectionID");
							outputter.attribute("expr", "event$.connectionid");
						outputter.endTag();
						outputter.startTag("assign");
							outputter.attribute("name","remoteID");
							outputter.attribute("expr","event$.connection.remote");
						outputter.endTag();
						outputter.startTag("accept");
						outputter.endTag();
					outputter.endTag();
					outputter.startTag("transition");
						outputter.attribute("state", "initial");
						outputter.attribute("event", "connection.connected");
						outputter.startTag("dialogstart");
							outputter.attribute("connectionid", "connectionID");
							outputter.attribute("dialogid", "dialogID");
							outputter.attribute("src", "vxmlScript");
							outputter.attribute("namelist", "remoteID");
						outputter.endTag();
						outputter.startTag("assign");
							outputter.attribute("name", "currentState");
							outputter.attribute("expr", "'connestablished'");
						outputter.endTag();
					outputter.endTag();
					outputter.startTag("transition");
						outputter.attribute("state", "connestablished");
						outputter.attribute("event", "dialog.exit");
						outputter.startTag("exit");
						outputter.endTag();
					outputter.endTag();
					outputter.startTag("transition");
						outputter.attribute("state", "connestablished");
						outputter.attribute("event", "dialog.transfer");
						outputter.startTag("redirect");
							outputter.attribute("connectionid", "connectionID");
							outputter.attribute("dest", "event$.URI");
						outputter.endTag();
					outputter.endTag();
					outputter.startTag("transition");
						outputter.attribute("event", "connection.failed");
						outputter.startTag("exit");
						outputter.endTag();
					outputter.endTag();
				outputter.endTag();
			outputter.endTag();
			outputter.endDocument();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return sw.toString();
	}
	
	@GET
	@Produces("application/ccxml+xml")
	public Response getCCXML(){
		
		return Response.ok(renderCCXML("/vxml/new?url=http://char-a-lot.appspot.com/howIsTheWeather/%3Fpreferred_medium=audio/wav")).build();
	}
}
