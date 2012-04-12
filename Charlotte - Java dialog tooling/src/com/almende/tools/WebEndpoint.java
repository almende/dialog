package com.almende.tools;

import java.util.HashMap;

/* 
 * Bean to easily convert jQueries Ajax options to a java structure
 */
public class WebEndpoint {
	String type="GET";
	String url;
	String username="";
	String password="";
	HashMap<String,String> headers = new HashMap<String,String>();
	String data=null;
	String contentType="application/x-www-form-urlencoded";
	public WebEndpoint(){};
	
	public String getType() {
		return type;
	}
	public String getUrl() {
		return url;
	}
	public String getUsername() {
		return username;
	}
	public String getPassword() {
		return password;
	}
	public HashMap<String, String> getHeaders() {
		return headers;
	}
	public String getData() {
		return data;
	}
	public String getContentType() {
		return contentType;
	}
	public void setType(String type) {
		this.type = type;
	}
	public void setUrl(String url) {
		this.url = url;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	public void setHeaders(HashMap<String, String> headers) {
		this.headers = headers;
	}
	public void setData(String data) {
		this.data = data;
	}
	public void setContentType(String contentType) {
		this.contentType = contentType;
	}
}
