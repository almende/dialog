package com.almende.dialog.auth;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;

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
import com.almende.dialog.util.KeyServerLib;
import com.almende.dialog.util.RequestUtil;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.ParallelInit;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;

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
        setService(req, service);
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
                if ( adapterConfig != null )
                {
                    printPageStart( out );
                    printSuccess( out, success );
                    printAdapterCreated(out, adapterConfig.getConfigId());
                    if ( req.getParameter( "showInitialAgent" ) != null )
                    {
                        printAddInitialAgent( adapterConfig.getConfigId(), out );
                    }
                    //perform a PUT request on the adapter details 
                    if ( req.getParameter( "adapterCallback" ) != null )
                    {
                        String adapterCallback = ServerUtils.getURLWithQueryParams(
                            req.getParameter( "adapterCallback" ), "username", req.getParameter( "username" ) );
                        adapterCallback = ServerUtils.getURLWithQueryParams( adapterCallback, "password",
                            req.getParameter( "password" ) );
                        Client client = ParallelInit.getClient();
                        WebResource resource = client.resource( adapterCallback ).queryParam( "id",
                            URLEncoder.encode( adapterConfig.getConfigId(), "UTF-8" ) );
                        resource.type( MediaType.TEXT_PLAIN ).put( String.class );
                    }
                    printPageEnd( out );
                }
                if(req.getParameter( "frontendCallback" ) != null)
                {
                    resp.setStatus( HttpServletResponse.SC_MOVED_PERMANENTLY );
                    resp.setHeader( "Location", req.getParameter( "frontendCallback" ) );
                }
                return;
            }
        }
    }
    
    @Override
    protected void doPost( HttpServletRequest req, HttpServletResponse resp ) throws ServletException, IOException
    {
        //verify authorization of adapters
        if ( req.getPathInfo().startsWith( "/verify" ) )
        {
            String payload = streamToString( req.getInputStream() );
            JsonNode res = null;
            try
            {
                res = ServerUtils.deserialize( payload, JsonNode.class );
                Map<String, Boolean> adapterValidityMap = new HashMap<String, Boolean>();
                for ( JsonNode jsonNode : res )
                {
                    JsonNode adapterIDNode = jsonNode.get( "adapterID" );
                    JsonNode accountIdNode = jsonNode.get( "accountId" );
                    JsonNode bearerTokenNode = jsonNode.get( "bearerToken" );
                    if ( adapterIDNode != null && accountIdNode != null && bearerTokenNode != null && 
                    KeyServerLib.checkAccount( accountIdNode.asText(), bearerTokenNode.asText() ))
                    {
                        AdapterConfig adapterConfig = AdapterConfig.getAdapterForOwner( adapterIDNode.asText(),
                            accountIdNode.asText() );
                        
                        if ( adapterConfig != null )
                        {
                            //verify twitter and facebook accounts
                            if ( adapterConfig.getAdapterType().toLowerCase().equals( "twitter" ) )
                            {
                                Token accessToken = new Token( adapterConfig.getAccessToken(),
                                    adapterConfig.getAccessTokenSecret() );
                                setService( req, "twitter" );
                                ObjectNode verifyResponse = verifyTwitterAuthForToken( accessToken );
                                JsonNode errorResponse = verifyResponse.get( "errors" );
                                Iterator<JsonNode> elements = errorResponse != null ? errorResponse.elements() : null;
                                while ( elements != null && elements.hasNext() )
                                {
                                    JsonNode error = elements.next();
                                    if ( error.get( "code" ) != null && error.get( "code" ).asText().equals( "89" ) )
                                    {
                                        //remove adapter
                                        adapterValidityMap.put( adapterConfig.getConfigId(), false );
                                        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
                                        adapterConfig = datastore.load( AdapterConfig.class,
                                            adapterConfig.getConfigId() );
                                        datastore.delete( adapterConfig );
                                    }
                                }
                                //if a adapter is valid (i.e. not added yet), add it as valid
                                if ( !adapterValidityMap.containsKey( adapterIDNode.asText() ) )
                                {
                                    adapterValidityMap.put( adapterConfig.getConfigId(), true );
                                }
                            }
                            else if ( adapterConfig.getAdapterType().toLowerCase().equals( "facebook" ) )
                            {
                                //TODO: implement facebook verification method
                            }
                            else
                            {
                                //TODO: auto verified now all other adapters. 
                                //But may have to implement some verification mechanism
                                adapterValidityMap.put( adapterConfig.getConfigId(), true );
                            }
                        }
                        else
                        {
                            //for adapters that are not found.
                            adapterValidityMap.put( adapterIDNode.asText(), null );
                        }
                    }
                }
                resp.getOutputStream().println( ServerUtils.serialize( adapterValidityMap ) );
            }
            catch ( Exception e )
            {
                log.warning( "Unable to parse result" );
                e.printStackTrace();
            }
        }
        else
        {
            super.doPost( req, resp );
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
    
    /**
     * sets the service using the Scribe library. Currently configured only for Twitter and Facebook.
     * @param req
     * @param service if its not null, this is considered as the default service. If null, service is fetched from 
     * req.getParameter("service")
     * @throws MalformedURLException
     * @throws UnsupportedEncodingException
     */
    private void setService( HttpServletRequest req, String service ) throws MalformedURLException, UnsupportedEncodingException
    {
        if ( service == null || service.equals( "" ) && req.getParameter( "service" ) == null)
        {
            this.service = null;
            return;
        }
        String callbackURL = RequestUtil.getHost( req ) + "/dialoghandler/oauth/callback";
        for ( Object parameterKey : req.getParameterMap().keySet() )
        {
            String parameterValue = req.getParameter( parameterKey.toString() );
            callbackURL = ServerUtils.getURLWithQueryParams( callbackURL, parameterKey.toString(), parameterValue );
        }
        if ( service.equals( "twitter" ) )
        {
            this.service = new ServiceBuilder().provider( TwitterApi.SSL.class ).apiKey( Twitter.OAUTH_KEY )
                .apiSecret( Twitter.OAUTH_SECRET ).callback( callbackURL ).build();
        }
        else if ( service.equals( "facebook" ) )
        {

            this.service = new ServiceBuilder().provider( FacebookApi.class ).apiKey( Facebook.OAUTH_KEY )
                .apiSecret( Facebook.OAUTH_SECRET ).callback( callbackURL )
                .scope( "email,read_stream,publish_stream,read_mailbox,read_requests,publish_actions,manage_pages" )
                .build();
        }
        log.info( "Set service to " + service );
    }
    
    private AdapterConfig obtainAccessToken(HttpServletRequest req, String service) {
        
        if(service.equals("twitter")) {
            
            String oauthToken = req.getParameter("oauth_token");
            String oauthVerifier = req.getParameter("oauth_verifier");

            if(oauthToken == null || oauthVerifier == null)
            {
                return null;
            }
            
            String secret = StringStore.getString(oauthToken);
            StringStore.dropEntity(oauthToken);
            Token requestToken = new Token(oauthToken, secret);
            
            Verifier v = new Verifier(oauthVerifier);
            Token accessToken = this.service.getAccessToken(requestToken, v);
            
            ObjectNode res = verifyTwitterAuthForToken( accessToken );
            
            return storeAccount(accessToken, "@"+res.get("screen_name").asText(), service.toLowerCase());
            
        } else if(service.equals("facebook")) {
            
            String oauthVerifier = req.getParameter("code");
            Verifier v = new Verifier(oauthVerifier);
            Token accessToken = this.service.getAccessToken(null, v);
            
            ObjectNode res = verifyFBAuthForToken( accessToken );
            
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
            "    console.log(elements[x].value); "+
            "    if(elements[x].checked) {" +
            "       service=elements[x].value;" +
            "       break; "+
            "    } " +
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
    
    private void printAdapterCreated(PrintWriter out, String adapterId)
    {
        out.print(String.format( "<p>Twitter adapter with id: %s created.</p>", adapterId));
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
    
    /**
     * Convert a stream to a string 
     * @param in
     * @return
     * @throws IOException
     */
    private static String streamToString(InputStream in) throws IOException {
        StringBuffer out = new StringBuffer();
        byte[] b = new byte[4096];
        for (int n; (n = in.read(b)) != -1;) {
            out.append(new String(b, 0, n));
        }
        return out.toString();
    }
    
    /** for a particular token check if its still valid and not revoked in Fabecook
     * @param accessToken
     * @return
     */
    private ObjectNode verifyFBAuthForToken( Token accessToken )
    {
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
        return res;
    }

    /** for a particular token check if its still valid and not revoked in Twitter
     * @param accessToken
     * @return
     */
    private ObjectNode verifyTwitterAuthForToken( Token accessToken )
    {
        OAuthRequest request = new OAuthRequest(Verb.GET, "https://api.twitter.com/1.1/account/verify_credentials.json");
        this.service.signRequest(accessToken, request);
        Response response = request.send();
        
        ObjectMapper om = ParallelInit.getObjectMapper();
        ObjectNode res=null;
        try {
            res = om.readValue(response.getBody(), ObjectNode.class);
        } catch (Exception e) {
            log.warning("Unable to parse result");
        }
        return res;
    }
}
