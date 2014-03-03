package com.almende.dialog.auth;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.FacebookApi;
import org.scribe.builder.api.TwitterApi;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.Facebook;
import com.almende.dialog.adapter.tools.Twitter;
import com.almende.dialog.state.StringStore;
import com.almende.dialog.util.RequestUtil;
import com.almende.util.ParallelInit;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class OAuthServlet extends HttpServlet {

	private static final Logger log = Logger.getLogger(OAuthServlet.class.getName()); 	
	
	private static final long serialVersionUID = 5268254806497451474L;
	
	private OAuthService service = null;
	
	@Override
	public void init() throws ServletException {
		
		log.setLevel(Level.INFO);
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		
		String service = req.getParameter("service");
		String host = RequestUtil.getHost(req);
		setService(host, service);
		PrintWriter out = resp.getWriter();
		
		log.info("Got request on: "+req.getRequestURL());
		
		String[] parts = req.getRequestURI().substring(1).split("/");
		if(parts.length==2) {
		
			// directly redirect to Google authorization page if an agents URL is provided
			if (this.service != null) {
				
				redirectToAuthorization(resp, service);
				return;
			}
	
			// First step: show a form to authenticate
			printPageStart(out);
			printAuthorizeForm(host, out);
			printPageEnd(out);
			return;
		} else {
			
			String error = req.getParameter("error");
			String success = req.getParameter("success");
			
			// print error if any
			if (error != null) {
				printPageStart(out);
				printError(out, error);		
				printPageEnd(out);
				return;
			} else {
				
				AdapterConfig adapterConfig = obtainAccessToken(req, service);
				printPageStart(out);
				printSuccess(out, success);
				printAddInitialAgent( adapterConfig.getConfigId(), out );
				printPageEnd(out);
				return;
			}
		}
	}
	
	private void redirectToAuthorization(HttpServletResponse resp, String serv) {
		
		try {		
			Token requestToken=null;
			if(!serv.equals("facebook")) {
				requestToken = service.getRequestToken();
				StringStore.storeString(requestToken.getToken(), requestToken.getSecret());
			}
            String authUrl = service.getAuthorizationUrl(requestToken);
            
            resp.sendRedirect(authUrl);
            
		} catch (Exception ex) {
			ex.printStackTrace();
			log.warning("Error: "+ex.getMessage());
		}
	}
	
	private void setService(String host, String service) {
		
		String callbackURL = host+"/dialoghandler/oauth/callback";
		
		if(service==null || service.equals("")) {
			this.service = null;
			return;
		}
		
		callbackURL += "?service="+service;
		
		if(service.equals("twitter")) {
			
			this.service = new ServiceBuilder()
	        .provider(TwitterApi.class)
	        .apiKey(Twitter.OAUTH_KEY)
	        .apiSecret(Twitter.OAUTH_SECRET)
	        .callback(callbackURL)
	        .build();
		} else if(service.equals("facebook")) {

			this.service = new ServiceBuilder()
	        .provider(FacebookApi.class)
	        .apiKey(Facebook.OAUTH_KEY)
	        .apiSecret(Facebook.OAUTH_SECRET)
	        .callback(callbackURL)
	        .scope("email,read_stream,publish_stream,read_mailbox,read_requests,publish_actions,manage_pages")
	        .build();
		}
		
		log.info("Set service to "+service);
	}
	
	private AdapterConfig obtainAccessToken(HttpServletRequest req, String service) {
		
		if(service.equals("twitter")) {
			
			String oauthToken = req.getParameter("oauth_token");
			String oauthVerifier = req.getParameter("oauth_verifier");
			
			String secret = StringStore.getString(oauthToken);
			StringStore.dropEntity(oauthToken);
			
			Token requestToken = new Token(oauthToken, secret);
			
			Verifier v = new Verifier(oauthVerifier);
			Token accessToken = this.service.getAccessToken(requestToken, v);
			
			OAuthRequest request = new OAuthRequest(Verb.GET, "http://api.twitter.com/1.1/account/verify_credentials.json");
			this.service.signRequest(accessToken, request);
			Response response = request.send();
			
			ObjectMapper om = ParallelInit.getObjectMapper();
			ObjectNode res=null;
			try {
				res = om.readValue(response.getBody(), ObjectNode.class);
			} catch (Exception e) {
				log.warning("Unable to parse result");
			}
			
			return storeAccount(accessToken, "@"+res.get("screen_name").asText(), service.toUpperCase());
			
		} else if(service.equals("facebook")) {
			
			String oauthVerifier = req.getParameter("code");
			Verifier v = new Verifier(oauthVerifier);
			Token accessToken = this.service.getAccessToken(null, v);
			
			OAuthRequest request = new OAuthRequest(Verb.GET, "https://graph.facebook.com/me");
			this.service.signRequest(accessToken, request);
			Response response = request.send();
			
			ObjectMapper om = ParallelInit.getObjectMapper();
			ObjectNode res=null;
			try {
				res = om.readValue(response.getBody(), ObjectNode.class);
			} catch (Exception e) {
				log.warning("Unable to parse result");
			}
			
			log.info(res.toString());
			
			return storeAccount(accessToken, res.get("id").asText(), service.toUpperCase());
		}
		return null;
	}
	
	private String createAuthorizationUrl() throws IOException {
		
		return "/dialoghandler/oauth";
	}
	
	private void printPageStart(PrintWriter out) {
		out.print("<html>" +
				"<head>" +
				"<title>Authorize Dialog Handler</title>" +
				"<style>" +
				"body {width: 700px;}" +
				"body, th, td, input {font-family: arial; font-size: 10pt; color: #4d4d4d;}" +
				"th {text-align: left;}" + 
				"input[type=text] {border: 1px solid lightgray;}" +
				".error {color: red;}" + 
				"</style>" +
				"</head>" +
				"<body>" + 
				"<h1>Authorize Dialog Handler</h1>" +
				"<p>" +
				"On this page, you can grant the dialog handler access to your data, " +
				"for example access to your tweets." +
				"</p>");		
	}
	
	private void printPageEnd(PrintWriter out) {
		out.print(
			"</body>" +
			"</html>"			
		);
	}
	
	private void printAuthorizeForm(String host, PrintWriter out) throws IOException {
		String url = host+createAuthorizationUrl();
		out.print("<script type='text/javascript'>" +
			"function auth(send) {" +
			" var elements = document.getElementsByName('media');" +
			" var service='';" +
			" for(x in elements) {" +
			"	 console.log(elements[x].value); "+
			"    if(elements[x].checked) {" +
			"		service=elements[x].value;" +
			"		break; "+
			"	 } " +
			" } " +
			" var url='" + url + "?send='+send+'&service='+service;" +
			"  window.location.href=url;" + 
			"}" +
			"</script>" +
			"<table>" +
			"<tr><td><input type='radio' name='media' value='twitter' /> Twitter</td></tr>" +
			"<tr><td><input type='radio' name='media' value='facebook' /> Facebook</td></tr>" +
			"<tr><td><button onclick='auth(1);'>Authorize</button></td></tr>" +
			"</table>"
		);		
	}
	
	private void printSuccess(PrintWriter out, String agentUrl) {
		out.print("<p>Agent is succesfully authorized.</p>");
	}
	
    private void printAddInitialAgent( String adapterId, PrintWriter out )
    {
        out.print( "<form action='/twitter/changeAgent?adapterId="+ adapterId +"' method='POST'>"
            + "<p>You can configure your first agent now. Agent URL: "
            + "<input id='agentURL' name='agentURL' type='text' placeholder='Agent URL' style='min-width: 300px;'>"
            + "<input type='submit' value='Submit'> </p>"
            + "</form>");
    }
	
	private void printError(PrintWriter out, String error) {
		out.print("<p>An error occurred</p>");			
	}
	
	private AdapterConfig storeAccount(Token accessToken, String myAddress, String type) {
		
		TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
		AdapterConfig config = AdapterConfig.findAdapterConfig(type, myAddress, null);
		
		if(config==null) {
			config = new AdapterConfig();
			config.setConfigId(UUID.randomUUID().toString());
			config.setAdapterType(type);
			config.setMyAddress(myAddress);
		}
		
		log.info("Old token: "+config.getAccessToken());
		
		config.setAccessToken(accessToken.getToken());
		config.setAccessTokenSecret(accessToken.getSecret());
		
		log.info("New token: "+config.getAccessToken()+" for: "+config.getMyAddress());
		
		datastore.store(config);
		return config;
	}
}
