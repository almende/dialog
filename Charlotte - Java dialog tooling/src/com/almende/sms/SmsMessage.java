package com.almende.sms;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

import com.fasterxml.jackson.databind.ObjectMapper;


public class SmsMessage implements Serializable {
	
	private static final long serialVersionUID = -6199026685594642787L;
	private Collection<String> toList;
	private String message="";
	
	public SmsMessage() {}
	
	public SmsMessage(Collection<String> toList, String message) {
		this.toList = toList;
		this.message = message;
	}
	
	public Collection<String> getToList() {
		return toList;
	}
	
	public void setTo(ArrayList<String> toList) {
		this.toList = toList;
	}
	
	public String getMessage() {
		return message;
	}
	
	public void setMessage(String message) {
		this.message = message;
	}
	
        public String toJson()
        {
            try
            {
                return new ObjectMapper().writeValueAsString( getToList() );
            }
            catch ( Exception e )
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
                return null;
            }
            
        }
}
