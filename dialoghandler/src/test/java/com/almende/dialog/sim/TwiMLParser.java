package com.almende.dialog.sim;

import java.io.ByteArrayInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.twilio.sdk.verbs.Conference;
import com.twilio.sdk.verbs.Dial;
import com.twilio.sdk.verbs.Gather;
import com.twilio.sdk.verbs.Hangup;
import com.twilio.sdk.verbs.Pause;
import com.twilio.sdk.verbs.Play;
import com.twilio.sdk.verbs.Record;
import com.twilio.sdk.verbs.Redirect;
import com.twilio.sdk.verbs.Say;
import com.twilio.sdk.verbs.TwiMLException;
import com.twilio.sdk.verbs.TwiMLResponse;
import com.twilio.sdk.verbs.Verb;

public class TwiMLParser extends TwiMLResponse{
    
    public TwiMLResponse parseXML(String xml) throws TwiMLException {
            try {
                    Document doc = getXMLDocumentBuilder( xml );
                    Verb v = getVerb(doc.getDocumentElement());
                    if (v instanceof TwiMLResponse) {
                            return (TwiMLResponse)v;        
                    } else {
                            throw new TwiMLException("Error parsing the document");
                    }
            } catch (Exception e) {
                    throw new TwiMLException(e.getMessage());
            }
    }
    private Verb newVerb(Element e) {
            Verb v = null;
            String verbName = e.getNodeName();
            if (verbName.equals(TwiMLResponse.V_CONFERENCE)) {
                    v = new Conference(e.getTextContent());
            } else if (verbName.equals(TwiMLResponse.V_DIAL)) {
                    v = new Dial(e.getTextContent());
            } else if (verbName.equals(TwiMLResponse.V_GATHER)) {
                    v = new Gather();
            } else if (verbName.equals(TwiMLResponse.V_HANGUP)) {
                    v = new Hangup();
            } else if (verbName.equals(TwiMLResponse.V_NUMBER)) {
                    v = new com.twilio.sdk.verbs.Number(e.getTextContent());
            } else if (verbName.equals(TwiMLResponse.V_PAUSE)) {
                    v = new Pause();
            } else if (verbName.equals(TwiMLResponse.V_PLAY)) {
                    v = new Play(e.getTextContent());
            } else if (verbName.equals(TwiMLResponse.V_RECORD)) {
                    v = new Record();
            } else if (verbName.equals(TwiMLResponse.V_REDIRECT)) {
                    v = new Redirect(e.getTextContent());
            } else if (verbName.equals(TwiMLResponse.V_RESPONSE)) {
                    v = new TwiMLResponse();
            } else /*if (verbName.equals(TwiMLResponse.V_SAY))*/ {
                    v = new Say(e.getTextContent());
            }
            /*if (v== null ) {
                    throw new UnsupportedOperationException("Have not implemented this verb yet: " +verbName);
            }*/
            return v;
    }
    
    private Verb getVerb(Element e) throws TwiMLException {
            Verb verb = newVerb(e);
            
            
            NamedNodeMap attributes = e.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attribute = attributes.item( i );
                verb.set(attribute.getNodeName(), attribute.getNodeValue());
            }
            
            NodeList children = e.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node nNode = children.item(i);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    
                    Element element = (Element) nNode;
                    verb.append(getVerb(element));
                }
            }

            return verb;
    }
    
    private Document getXMLDocumentBuilder(String xmlContent) throws Exception
    {
        DocumentBuilderFactory newInstance = DocumentBuilderFactory.newInstance();
        DocumentBuilder newDocumentBuilder = newInstance.newDocumentBuilder();
        Document parse = newDocumentBuilder.parse( new ByteArrayInputStream(xmlContent.getBytes("UTF-8")) );
        return parse;
    }
}
