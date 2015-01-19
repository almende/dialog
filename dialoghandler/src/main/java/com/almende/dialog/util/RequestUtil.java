package com.almende.dialog.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import org.apache.http.client.utils.URIBuilder;
import org.apache.wink.common.model.wadl.HTTPMethods;

import com.almende.util.ParallelInit;

public class RequestUtil {
    
        private static final Logger log = Logger.getLogger( RequestUtil.class.getSimpleName() );
    
	public static String getHost(HttpServletRequest req) throws MalformedURLException {
		int port = req.getServerPort();
		if (req.getScheme().equals("http") && port == 80) {
		    port = -1;
		} else if (req.getScheme().equals("https") && port == 443) {
		    port = -1;
		}
		URL serverURL = new URL(req.getScheme(), req.getServerName(), port, "");
		return serverURL.toString();
	}
	
    /**
     * fetches the response in string format.
     * 
     * @param httpMethod
     *            GET/PUT/POST
     * @param url
     * @param queryParams
     *            Map<queryKey, paramValue> ?queryKey="paramValue"
     * @param type
     *            text/plain application/json
     * @return
     */
    public static String fromURL( HTTPMethods httpMethod, String url,Map<String, String> queryParams, String type,String json_payload ) {

        AFHttpClient client = ParallelInit.getAFHttpClient();
        
        type = type == null || type.isEmpty() ? "text/plain" : type;
        String json = "";
        try {
            URIBuilder builder = new URIBuilder(url);
            if ( queryParams != null ) {
                for ( String key : queryParams.keySet() ) {
                    builder.addParameter( key, queryParams.get( key ) );
                }
            }
            url = builder.build().toString();
            switch ( httpMethod ) {
                case GET:
                    json = client.get(url);
                    break;
                case POST:
                    json = client.post( (json_payload != null ? json_payload : "" ), url, type);
                    break;
                case PUT:
                    json = client.put( (json_payload != null ? json_payload : "" ), url, type);
                    break;
                default:
                    throw new Exception( "Unsupported http" + "Method Type" );
            }
        }
        catch ( Exception e ) {
            log.severe( e.toString() );
        }
        return json;
    }
}
