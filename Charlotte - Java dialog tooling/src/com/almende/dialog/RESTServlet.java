package com.almende.dialog;

import java.io.IOException;
import java.net.URL;
import java.util.logging.Logger;
//import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.*;

@SuppressWarnings("serial")
//public class RESTServlet extends com.sun.jersey.spi.container.servlet.ServletContainer {
public class RESTServlet {
	private static final Logger log = Logger
			.getLogger("DialogHandler");
//	private static long startTime = new Date().getTime();
	
	public static boolean get_CORS_headers(java.util.HashMap<String,String> ret, java.util.HashMap<String,String> httpHeaders)
	{
		Boolean isAllowed = true;
		String s = httpHeaders.get( "Origin" );	
		//Android Hack:
		//Android CORS implementation is a bit strange, one request (not sure which) doesn't provide correct Origin, overruling for android.
		if(s==null) {
			//String ua_s = httpHeaders.get( "User-Agent");
			//if (ua_s.indexOf("Android") > 0 || ua_s.indexOf("iPad") > 0 || ua_s.indexOf("HTC_Sens")){
				String ref_s = httpHeaders.get( "Referer");
				if (ref_s != null){
					try {
						URL url = new URL(ref_s);
						s = url.getProtocol()+"://"+url.getAuthority();
					} catch(Exception e) {
						log.severe("Failed to parse: "+ref_s);
						s=null;
					}
				} else {
					log.info("No Referer found!");
				}
			//}
		}
		//End of Android Hack.
		
		if( s != null )
		{
			ret.put("Access-Control-Allow-Origin", s );
	
			ret.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
			ret.put("Access-Control-Allow-Credentials", "true" );
			ret.put("Access-Control-Max-Age", "60" );
			
			String returnMethod = httpHeaders.get("Access-Control-Request-Headers");	//what?
			if (!"".equals(returnMethod)) {
				ret.put("Access-Control-Allow-Headers", returnMethod);
			}
		}
		
		return isAllowed;
	}
	public boolean makeCORS(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException
	{
		//copy headers into the next collection
		java.util.HashMap<String,String> headers = new java.util.HashMap<String, String>();
		@SuppressWarnings("unchecked")
		java.util.Enumeration<String> headerNames = req.getHeaderNames();
		while( headerNames.hasMoreElements() )
		{
			String name = headerNames.nextElement();
			headers.put( name, req.getHeader(name) );
		}
		
		java.util.HashMap<String,String> CORS_headers = new java.util.HashMap<String,String>();
		boolean isAllowed = get_CORS_headers( CORS_headers, headers );
		
		if(isAllowed)
			for( java.util.Map.Entry<String,String> entry : CORS_headers.entrySet() )
				res.setHeader( entry.getKey(),entry.getValue() );
	
		return isAllowed;
	}
	
//	@Override
	public void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException
	{
//		log.warning("Starting REST service: "+startTime+"/"+(new Date().getTime()));
		makeCORS(req,res);
		//TODO: Do Authentication

		//run Jersey
//		super.service(req,res);
//		log.warning("Done REST service: "+ (new Date().getTime()));
	}
}
