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
import org.scribe.builder.api.TwitterApi;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;

import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.Twitter;
import com.almende.dialog.state.StringStore;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.code.twig.annotation.AnnotationObjectDatastore;

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
		setService(service);
		PrintWriter out = resp.getWriter();
		
		String[] parts = req.getRequestURI().substring(1).split("/");
		if(parts.length==1) {
		
			// directly redirect to Google authorization page if an agents URL is provided
			if (this.service != null) {
				
				redirectToAuthorization(resp);
				return;
			}
	
			// First step: show a form to authenticate
			printPageStart(out);
			printAuthorizeForm(out);
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
				
				String oauthToken = req.getParameter("oauth_token");
				String oauthVerifier = req.getParameter("oauth_verifier");
				
				obtainAccessToken(oauthToken, oauthVerifier, service);
				
				printPageStart(out);
				printSuccess(out, success);
				printPageEnd(out);
				return;
			}
		}
	}
	
	private void redirectToAuthorization(HttpServletResponse resp) {
		
		try {		
			Token requestToken = service.getRequestToken();
            String authUrl = service.getAuthorizationUrl(requestToken);
            StringStore.storeString(requestToken.getToken(), requestToken.getSecret());
            
            resp.sendRedirect(authUrl);
            
		} catch (Exception ex) {
			ex.printStackTrace();
			log.warning("Error: "+ex.getMessage());
		}
	}
	
	private void setService(String service) {
		
		String callbackURL = "http://"+Settings.HOST+"/oauth/callback";
		
		if(service==null || service.equals(""))
			return;
		
		callbackURL += "?service="+service;
		
		if(service.equals("twitter")) {
			
			this.service = new ServiceBuilder()
	        .provider(TwitterApi.class)
	        .apiKey(Twitter.OAUTH_KEY)
	        .apiSecret(Twitter.OAUTH_SECRET)
	        .callback(callbackURL)
	        .build();
		} else if(service.equals("facebook")) {
			
		}
		
		log.info("Set service to "+service);
	}
	
	private void obtainAccessToken(String oauthToken, String oauthVerifier, String service) {
		
		String secret = StringStore.getString(oauthToken);
		StringStore.dropEntity(oauthToken);
		
		Token requestToken = new Token(oauthToken, secret);
		
		Verifier v = new Verifier(oauthVerifier);
		Token accessToken = this.service.getAccessToken(requestToken, v);
		
		if(service.equals("twitter")) {
			
			OAuthRequest request = new OAuthRequest(Verb.GET, "http://api.twitter.com/1/account/verify_credentials.json");
			this.service.signRequest(accessToken, request);
			Response response = request.send();
			
			ObjectMapper om = ParallelInit.getObjectMapper();
			ObjectNode res=null;
			try {
				res = om.readValue(response.getBody(), ObjectNode.class);
			} catch (Exception e) {
				log.warning("Unable to parse result");
			}
			
			storeAccount(accessToken, "@"+res.get("screen_name").asText());
			
		} else if(service.equals("facebook")) {
			
		}
	}
	
	private String createAuthorizationUrl() throws IOException {
		
		return "http://"+Settings.HOST+"/oauth";
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
	
	private void printAuthorizeForm(PrintWriter out) throws IOException {
		String url = createAuthorizationUrl();
		out.print("<script type='text/javascript'>" +
			"function auth() {" +
			" var elements = document.getElementsByName('media');" +
			" var service='';" +
			" for(x in elements) {" +
			"	 console.log(elements[x].value); "+
			"    if(elements[x].checked) {" +
			"		service=elements[x].value;" +
			"		break; "+
			"	 } " +
			" } " +
			" var url='" + url + "?send=1&service='+service;" +
			"  window.location.href=url;" + 
			"}" +
			"</script>" +
			"<table>" +
			"<tr><td><input type='radio' name='media' value='twitter' /> Twitter</td></tr>" +
			"<tr><td><input type='radio' name='media' value='facebook' /> Facebook</td></tr>" +
			"<tr><td><button onclick='auth();'>Authorize</button></td></tr>" +
			"</table>"
		);		
	}
	
	private void printSuccess(PrintWriter out, String agentUrl) {
		out.print("<p>Agent is succesfully authorized.</p>");
	}
	
	private void printError(PrintWriter out, String error) {
		out.print("<p>An error occurred</p>");			
	}
	
	private void storeAccount(Token accessToken, String myAddress) {
		
		AnnotationObjectDatastore datastore = new AnnotationObjectDatastore();
		AdapterConfig config = AdapterConfig.findAdapterConfig("TWITTER", myAddress);
		
		if(config==null) {
			config = new AdapterConfig();
			config.setConfigId(UUID.randomUUID().toString());
			config.setAdapterType("TWITTER");
			config.setMyAddress(myAddress);
		}
		
		config.setAccessToken(accessToken.getToken());
		config.setAccessTokenSecret(accessToken.getSecret());
		
		datastore.store(config);
	}
}
