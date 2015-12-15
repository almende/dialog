package com.almende.dialog.adapter;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.accounts.Dialog;
import com.almende.dialog.adapter.tools.Broadsoft;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.ServerUtils;
import com.askfast.commons.entity.AccountType;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.entity.TTSInfo;
import com.askfast.commons.entity.TTSInfo.TTSProvider;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;

@Path("comsys/vxml")
public class ComsysVoiceXMLRESTProxy extends VoiceXMLRESTProxy {

    
    /**
     * This is the common entry point for all calls (both inbound and outbound)
     * when a connection is established. This is called in by the provider and
     * is configured in the default.ccxml
     * 
     * @param direction
     *            The direction in the call has been triggered.
     * @param remoteID
     *            The number to which an outbound call is done to, or an inbound
     *            call is received from. Even for an anonymous call this field
     *            is always populated.
     * @param externalRemoteID
     *            This is to make sure we distinguish anonymous calls. This
     *            field is empty when it is an anonymous call.
     * @param localID
     *            The number bought from the provider
     * @param ui
     * @return
     */
    @Path("new")
    @GET
    @Produces("application/voicexml")
    public Response getNewDialog(@QueryParam("callId") String callId, @QueryParam("remoteID") String remoteID,
        @QueryParam("localID") String localID, @QueryParam("direction") String direction, @QueryParam("isTest") Boolean isTest, @Context UriInfo ui) {

        this.host = ui.getBaseUri().toString().replace(":80/", "/");
        
        localID = formatComsysAddress( localID );
        remoteID = formatComsysAddress( remoteID );
        
        String formatedLocalID = PhoneNumberUtils.formatNumber(localID, PhoneNumberFormat.E164);
        
        Map<String, String> extraParams = new HashMap<String, String>();
        AdapterConfig config = AdapterConfig.findAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, formatedLocalID);
        //format the remote number
        String formattedRemoteId = PhoneNumberUtils.formatNumber(remoteID, PhoneNumberFormat.E164);

        if (formattedRemoteId == null) {
            log.severe(String.format("RemoveId address is invalid: %s. Ignoring.. ", remoteID));
            return Response.ok().build();
        }

        //get or create a session based on the remoteId that is always populated.  
        Session session = Session.getSessionByExternalKey(callId);
        String url = "";
        DDRRecord ddrRecord = null;
        
        if (session != null && direction.equalsIgnoreCase("outbound")) {
            try {
                url = Dialog.getDialogURL(session.getStartUrl(), session.getAccountId(), session);
            }
            catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            if (isTest != null && Boolean.TRUE.equals(isTest)) {
                session.setAsTestSession();
            }
            ddrRecord = session.getDDRRecord();
        }
        else if (direction.equals("inbound")) {
            //create a session for incoming only. Flush any existing one
            if (session != null) {
                session.drop();
            }
            //check if an existing session exists
            session = Session.getSessionByInternalKey(config.getAdapterType(), config.getMyAddress(), formattedRemoteId);
            if (session != null) {
                String responseMessage = checkIfCallAlreadyInSession(formattedRemoteId, config, session);
                if(responseMessage == null) {
                    session.drop();
                }
            }
            session = Session.createSession(config, formattedRemoteId);
            if (isTest != null && Boolean.TRUE.equals(isTest)) {
                session.setAsTestSession();
            }
            session.setAccountId(config.getOwner());
            session.setRemoteAddress(formattedRemoteId);
            session.storeSession();
            url = config.getURLForInboundScenario(session);
            try {
                ddrRecord = DDRUtils.createDDRRecordOnIncomingCommunication(config, config.getOwner(),
                                                                            formattedRemoteId, url, session);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            Broadsoft bs = new Broadsoft(config);
            bs.startSubscription();
        }
        
        Question question = null;
        if (session != null) {
        
            session.setStartUrl(url);
            session.setDirection(direction);
            session.setRemoteAddress(formattedRemoteId);
            session.setType(AdapterAgent.ADAPTER_TYPE_CALL);
            session.addExtras(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.BROADSOFT.toString());
            session.setAdapterID(config.getConfigId());
            session.storeSession();
            session.setDdrRecordId(ddrRecord != null ? ddrRecord.getId() : null);
            session.storeSession();
            
            question = session.getQuestion();
            if (question == null) {
                question = Question.fromURL(url, formattedRemoteId, config.getFormattedMyAddress(),
                                            session.getDdrRecordId(), session, extraParams);
            }
            if (!ServerUtils.isValidBearerToken(session, config)) {
                TTSInfo ttsInfo = ServerUtils.getTTSInfoFromSession(question, session);
                ttsInfo.setProvider(TTSProvider.VOICE_RSS);
                String insufficientCreditMessage = ServerUtils.getInsufficientMessage(ttsInfo.getLanguage());
                String ttsurl = ServerUtils.getTTSURL(ttsInfo, insufficientCreditMessage, session);
                return Response.ok(renderExitQuestion(Arrays.asList(ttsurl), session.getKey())).build();
            }
        }
        else {
            log.severe(String.format("Session not found for externalKey: %s", callId));
            return null;
        }

        // Check if we were able to load a question
        if (question == null) {
            //If not load a default error message
            question = Question.getError(config.getPreferred_language(), config.getOwner());
        }
        session.setQuestion(question);
        session.storeSession();
        log.info("Current session info: " + ServerUtils.serializeWithoutException(session));

        if (session.getQuestion() != null) {
            //play trial account audio if the account is trial
            if (AccountType.TRIAL.equals(session.getAccountType())) {
                session.addExtras(PLAY_TRIAL_AUDIO_KEY, "true");
            }
            return handleQuestion(question, config, formattedRemoteId, session);
        }
        else {
            return Response.ok().build();
        }
    }
    
    private String formatComsysAddress(String address) {
        String[] parts = address.split( "\\?" );
        return parts[0];
    }
}
