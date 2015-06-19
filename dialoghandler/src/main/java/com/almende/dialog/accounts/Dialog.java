package com.almende.dialog.accounts;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.apache.http.client.utils.URIBuilder;
import com.almende.dialog.Settings;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.dialog.util.AFHttpClient;
import com.almende.dialog.util.ServerUtils;
import com.almende.dialog.util.TimeUtils;
import com.almende.util.ParallelInit;
import com.almende.util.twigmongo.FilterOperator;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore.RootFindCommand;
import com.almende.util.twigmongo.annotations.Id;
import com.askfast.commons.entity.TTSInfo;
import com.askfast.commons.intf.DialogInterface;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.squareup.okhttp.Credentials;
import com.sun.research.ws.wadl.HTTPMethods;

public class Dialog implements DialogInterface {

    static final Logger log = Logger.getLogger(Dialog.class.getName());
    public static final String DIALOG_BASIC_AUTH_HEADER_KEY = "DIALOG_BASIC_AUTH_HEADER";
    public static final String DIALOG_ID_KEY = "DIALOG_ID";
    
    @Id
    public String id = null;
    String name = null;
    String url = null;
    String owner = null;
    Long creationTime; 
    String userName = null;
    String password = null;
    Boolean useBasicAuth = false;
    private HTTPMethods method = HTTPMethods.GET;
    private TTSInfo ttsInfo = null;
    
    public Dialog() {

    }

    public Dialog(String id) {

        this.id = id;
    }

    public Dialog(String name, String url) {

        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.url = url;
    }

    @Override
    public boolean equals(Object obj) {

        if (obj instanceof Dialog) {
            Dialog dialog = (Dialog) obj;
            return dialog.getId().equals(id);
        }
        return false;
    }

    public String getId() {

        return id;
    }

    public void setId(String id) {

        this.id = id;
    }

    public String getName() {

        return name;
    }

    public void setName(String name) {

        this.name = name;
    }

    public String getUrl() {

        return url;
    }

    public void setUrl(String url) {

        this.url = url;
    }

    public String getOwner() {

        return owner;
    }

    public void setOwner(String owner) {

        this.owner = owner;
    }
    
    public String getUserName() {
    
        return userName;
    }
    
    public void setUserName(String userName) {
    
        this.userName = userName;
    }

    public String getPassword() {
    
        return password;
    }

    public void setPassword(String password) {
    
        this.password = password;
    }
    
    public Boolean getUseBasicAuth() {
    
        return useBasicAuth;
    }
    
    public void setUseBasicAuth(Boolean useBasicAuth) {
    
        this.useBasicAuth = useBasicAuth;
    }
    
    public TTSInfo getTtsInfo() {

        return ttsInfo;
    }

    public void setTtsInfo(TTSInfo ttsInfo) {

        this.ttsInfo = ttsInfo;
    }

    /**
     * stores or updates the dialog object
     */
    public void storeOrUpdate() {

        id = id != null ? id : UUID.randomUUID().toString();
        creationTime = creationTime != null ? creationTime : TimeUtils.getServerCurrentTimeInMillis();
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        datastore.storeOrUpdate(this);
    }

    /**
     * creates a simple dialog with name and url
     * 
     * @param name
     * @param url
     * @return
     * @throws Exception
     */
    public static Dialog createDialog(String name, String url, String owner) throws Exception {

        if (url != null && !url.isEmpty()) {
            List<Dialog> dialogs = getDialogs(owner, url);
            if (dialogs == null || dialogs.isEmpty()) {
                Dialog dialog = new Dialog(name, url);
                dialog.setOwner(owner);
                dialog.storeOrUpdate();
                dialogs.add(dialog);
            }
            return dialogs.iterator().next();
        }
        return null;
    }

    /**
     * gets the dialog if its owned by the accountId. If owner is null, fetches
     * any dialog
     * 
     * @param id
     * @param accountId
     * @return
     */
    public static Dialog getDialog(String id, String accountId) {

        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        Dialog dialog = datastore.load(Dialog.class, id);

        if (dialog != null && accountId != null) {
            if (dialog.getOwner().equals(accountId)) {
                return dialog;
            }
            else {
                log.severe(String.format("AccountId: %s does not own Dialog: %s", accountId, dialog.getId()));
            }
        }
        return dialog;
    }

    /**
     * gets all the dialogs owned by the accountId. If accountId is null, gets
     * all the dialogs not owned by any
     * 
     * @param id
     * @param accountId
     * @return
     * @throws Exception
     */
    public static List<Dialog> getDialogs(String accountId) throws Exception {

        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        RootFindCommand<Dialog> cmd = datastore.find().type(Dialog.class);
        cmd.addFilter("owner", FilterOperator.EQUAL, accountId);
        return cmd.now().toArray();
    }

    /**
     * get all dialogs owner by a user by url
     * 
     * @param accountId
     * @return
     * @throws Exception
     */
    public static List<Dialog> getDialogs(String accountId, String url) throws Exception {

        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        RootFindCommand<Dialog> cmd = datastore.find().type(Dialog.class);
        cmd.addFilter("owner", FilterOperator.EQUAL, accountId);
        cmd.addFilter("url", FilterOperator.EQUAL, url);
        return cmd.now().toArray();
    }

    public static void deleteDialog(String id, String accountId) throws Exception {

        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        Dialog dialog = datastore.load(Dialog.class, id);

        if (dialog != null) {
            //delete if the dialog is either not owned by any or owned by the logged in user
            if (accountId == null || (accountId != null && dialog.getOwner().equals(accountId))) {
                datastore.delete(dialog);
            }
            else {
                throw new Exception(String.format("AccountId: %s does not own Dialog: %s", accountId, dialog.getId()));
            }
        }
    }
    
    public Long getCreationTime() {
    
        return creationTime;
    }
    
    public void setCreationTime(Long creationTime) {
    
        this.creationTime = creationTime;
    }
    
    /**
     * Fetches the question from the dialog url. Uses basic authentication if
     * {@link Dialog#useBasicAuth} is turned on
     * @param queryParams
     * @param encodeParams
     * @return
     * @throws IOException
     * @throws URISyntaxException 
     */
    @JsonIgnore
    public String getQuestionFromDialog(Map<String, String> queryParams) throws Exception {

        AFHttpClient client = ParallelInit.getAFHttpClient();
        if (Boolean.TRUE.equals(useBasicAuth)) {
            client.authorizeClient(userName, password);
        }
        URIBuilder uriBuilder = new URIBuilder(url);
        if (queryParams != null) {
            for (String query : queryParams.keySet()) {
                uriBuilder.addParameter(query, queryParams.get(query));
            }
        }
        return client.get(uriBuilder.build().toString()).getResponseBody();
    }

    @JsonIgnore
    public HTTPMethods getMethod() {

        return method;
    }
    @JsonIgnore
    public void setMethod(HTTPMethods method) {

        this.method = method;
    }
    @JsonProperty("method")
    public String getMethodString() {

        return method != null ? method.name() : null;
    }
    @JsonProperty("method")
    public void setMethod(String method) {

        try {
            this.method = HTTPMethods.valueOf(method.toUpperCase());
        }
        catch (Exception e) {
            log.severe(String.format("Could not deserialize method: %s", method));
            this.method = null;
        }
    }
    
    /**
     * Gets the URL of the given dialogId or just returns the url encoded.
     * 
     * @param dialogIdOrUrl
     * @param accountId
     *            used to fetch the dialog from the mongo db
     * @param session
     *            if not null, and a dialogId is fetched, stores the
     *            authorization token in the session
     * @return
     * @throws UnsupportedEncodingException
     */
    public static String getDialogURL(String dialogIdOrUrl, String accountId, Session session)
        throws UnsupportedEncodingException {

        if (dialogIdOrUrl.startsWith("http")) {
            return ServerUtils.encodeURLParams(dialogIdOrUrl);
        }
        else if (dialogIdOrUrl.startsWith("text://")) {
            return "http://" + Settings.HOST + "/question/comment?message=" +
                   URLEncoder.encode(dialogIdOrUrl.replace("text://", ""), "UTF-8");
        }
        else {
            Dialog dialog = Dialog.getDialog(dialogIdOrUrl, accountId);
            if (dialog != null) {
                session = addDialogCredentialsToSession(dialog, session);
                return dialog.getUrl();
            }
            String errorText = Question.getError(session != null ? session.getLanguage() : null).getQuestion_text()
                                            .replace("text://", "");
            return "http://" + Settings.HOST + "/question/comment?" + URLEncoder.encode(errorText, "UTF-8");
        }
    }
    
    /**
     * Simply stores the Dialog credentials in the session extras 
     * @param dialog
     * @param session
     */
    public static Session addDialogCredentialsToSession(Dialog dialog, Session session) {

        if (dialog != null && session != null) {
            if (dialog.getUseBasicAuth()) {
                String credential = Credentials.basic(dialog.getUserName(), dialog.getPassword());
                session.addExtras(DIALOG_BASIC_AUTH_HEADER_KEY, credential);
            }
            session.addExtras(DIALOG_ID_KEY, dialog.getId());
            session.storeSession();
        }
        return session;
    }
    
    /**
     * Returns the credentials that is stored in the session corresponding to
     * {@link Dialog#DIALOG_BASIC_AUTH_HEADER_KEY}
     * 
     * @param sessionKey
     * @return
     */
    public static String getCredentialsFromSession(String sessionKey) {

        if (sessionKey != null) {
            Session session = Session.getSession(sessionKey);
            if (session != null) {
                return session.getAllExtras().get(DIALOG_BASIC_AUTH_HEADER_KEY);
            }
        }
        return null;
    }
    
    /**
     * Returns the TTSInfo in the session corresponding to
     * {@link Dialog#DIALOG_ID_KEY}
     * 
     * @param sessionKey
     * @return
     */
    public static TTSInfo getTTSInfoFromSession(String sessionKey) {

        if (sessionKey != null) {
            Session session = Session.getSession(sessionKey);
            return getTTSInfoFromSession(session);
        }
        return null;
    }
    
    /**
     * Returns the TTSInfo in the session corresponding to
     * {@link Dialog#DIALOG_ID_KEY}
     * 
     * @param sessionKey
     * @return
     */
    public static TTSInfo getTTSInfoFromSession(Session session) {

        if (session != null) {
            if (session != null) {
                String dialogId = session.getAllExtras().get(DIALOG_ID_KEY);
                if(dialogId != null) {
                    Dialog dialog = Dialog.getDialog(dialogId, session.getAccountId());
                    if(dialog != null) {
                        return dialog.getTtsInfo();
                    }
                }
            }
        }
        return null;
    }
}
