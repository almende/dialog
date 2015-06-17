package com.almende.dialog.broadsoft;

import java.io.ByteArrayInputStream;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class UserProfile{

    private static final Logger log = Logger.getLogger(UserProfile.class.getName());
    
    private String userId = null;
    private String groupId = null;
    private String serviceProvider = null;
    private String number = null;
    private String extension = null;
    
    public UserProfile() {}
    
    public UserProfile( String xml ) {

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();

            Document dom = db.parse( new ByteArrayInputStream( xml.getBytes( "UTF-8" ) ) );
            
            if ( dom.getElementsByTagName( "details" ).getLength() > 0) {
                Node details = dom.getElementsByTagName( "details" ).item( 0 );
                NodeList nodeList = details.getChildNodes();

                for ( int i = 0; i < nodeList.getLength(); i++ ) {
                    Node node = nodeList.item( i );

                    //Identifying the child tag of details encountered. 
                    if ( node instanceof Element ) {
                        String content = node.getLastChild().getTextContent()
                            .trim();
                        switch ( node.getNodeName() ) {
                            case "userId":
                                this.userId = content;
                                break;
                            case "groupId":
                                this.groupId = content;
                                break;
                            case "serviceProvider":
                                this.serviceProvider = content;
                                break;
                            case "number":
                                this.number = content;
                                break;
                            case "extension":
                                this.extension = content;
                                break;
                        }
                    }
                }
            }

        }
        catch ( Exception e ) {
            log.severe( "Failed to parse user profile xml e: " + e.getMessage() );
        }
    }
    
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId( String userId ) {
        this.userId = userId;
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public void setGroupId( String groupId ) {
        this.groupId = groupId;
    }
    
    public String getServiceProvider() {
        return serviceProvider;
    }
    
    public void setServiceProvider( String serviceProvider ) {
        this.serviceProvider = serviceProvider;
    }
    
    public String getNumber() {
        return number;
    }
    
    public void setNumber( String number ) {
        this.number = number;
    }
    
    public String getExtension() {
        return extension;
    }
    
    public void setExtension( String extension ) {
        this.extension = extension;
    }
}
