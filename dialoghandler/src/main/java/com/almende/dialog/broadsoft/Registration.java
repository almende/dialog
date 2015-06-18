package com.almende.dialog.broadsoft;

import java.io.ByteArrayInputStream;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Registration {
    
    private static final Logger log = Logger.getLogger(Registration.class.getName());

    private String endpointType = null;
    private String uri = null;
    private String linePort = null;
    
    public Registration() {}
    
    public Registration(String xml) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();

            Document dom = db.parse( new ByteArrayInputStream( xml.getBytes( "UTF-8" ) ) );
            
            if ( dom.getElementsByTagName( "registration" ).getLength() > 0) {
                Node details = dom.getElementsByTagName( "registration" ).item( 0 );
                NodeList nodeList = details.getChildNodes();

                for ( int i = 0; i < nodeList.getLength(); i++ ) {
                    Node node = nodeList.item( i );

                    //Identifying the child tag of details encountered. 
                    if ( node instanceof Element ) {
                        String content = node.getTextContent();
                        if(content!=null) {
                            content = content.trim();
                            switch ( node.getNodeName() ) {
                                case "endpointType":
                                    this.endpointType = content;
                                    break;
                                case "uri":
                                    this.uri = content;
                                    break;
                                case "linePort":
                                    this.linePort = content;
                                    break;
                                 default:
                                     break;
                            }
                        }
                    }
                }
            }
        }
        catch ( Exception e ) {
            log.severe( "Failed to parse user profile xml e: " + e.getMessage() );
        }
    }
    
    public String getEndpointType() {
        return endpointType;
    }
    
    public void setEndpointType( String endpointType ) {
        this.endpointType = endpointType;
    }
    
    public String getUri() {
        return uri;
    }
    
    public void setUri( String uri ) {
        this.uri = uri;
    }
    
    public String getLinePort() {
        return linePort;
    }
    
    public void setLinePort( String linePort ) {
        this.linePort = linePort;
    }
}
