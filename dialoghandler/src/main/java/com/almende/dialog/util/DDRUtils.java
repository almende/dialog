package com.almende.dialog.util;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.joda.time.DateTime;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.DialogAgent;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRPrice;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.model.ddr.DDRType;
import com.almende.eve.agent.Agent;
import com.almende.eve.agent.AgentHost;
import com.askfast.commons.entity.Adapter;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.entity.AdapterType;
import com.askfast.commons.entity.DDRRecord.CommunicationStatus;
import com.askfast.commons.entity.DDRType.DDRTypeCategory;
import com.askfast.commons.entity.TTSInfo;
import com.askfast.commons.entity.TTSInfo.TTSProvider;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.askfast.commons.utils.TimeUtils;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**
 * Helper functions for creating DDR records and processing of them
 * @author Shravan
 */
@SuppressWarnings("deprecation")
public class DDRUtils
{
    private static final Logger log = Logger.getLogger( DDRUtils.class.getSimpleName() );
    protected static final com.almende.dialog.Logger dialogLog = new com.almende.dialog.Logger();
    //create a single static connection for publishing ddrs
    private static ConnectionFactory rabbitMQConnectionFactory;
    private static final String PUBLISH_QUEUE_NAME = "DDR_PUBLISH_QUEUE";
    public static final String DDR_MESSAGE_KEY = "message";
    
    /** creates a DDR Record for this adapterid and owner. <br>
     * Preconditions: <br>
     * Expects to find a {@link DDRType} of Type {@link DDRTypeCategory#ADAPTER_PURCHASE} <br>
     * @param config AdapterConfig having a non-null {@link AdapterConfig#getConfigId()} and {@link AdapterConfig#getOwner()} 
     * @throws Exception
     */
    public static DDRRecord createDDRRecordOnAdapterPurchase( AdapterConfig config, boolean publishCharges) throws Exception
    {
        DDRType adapterPurchaseDDRType = DDRType.getDDRType( DDRTypeCategory.ADAPTER_PURCHASE );
        if ( adapterPurchaseDDRType != null )
        {
            log.info( String.format( "Applying charges for account: %s and adapter: %s with address: %s",
                config.getOwner(), config.getConfigId(), config.getMyAddress() ) );
            if ( config.getConfigId() != null && config.getOwner() != null )
            {
                DDRRecord ddrRecord = new DDRRecord(adapterPurchaseDDRType.getTypeId(), config, config.getOwner(), 1);
                ddrRecord.setStart( TimeUtils.getServerCurrentTimeInMillis() );
                ddrRecord.setAccountType(config.getAccountType());
                ddrRecord.addAdditionalInfo(DDR_MESSAGE_KEY,
                                            String.format("Type: %s Address: %s", config.getAdapterType(),
                                                          config.getMyAddress()));
                ddrRecord.createOrUpdate();
                //publish charges
                if (publishCharges) {
                    Double ddrCost = calculateDDRCost(ddrRecord);
                    publishDDREntryToQueue(config.getOwner(), ddrCost);
                }
                return ddrRecord;
            }
        }
        log.warning( String.format( "Not charging this adapter purchase with address: %s adapterid: %s anything!!",
            config.getMyAddress(), config.getConfigId() ) );
        return null;
    }
    
    /**
     * creates a ddr record based on the adapterId and accoutnId from config and
     * the quantity
     * 
     * @param config
     * @param toAddress
     * @param quantity
     *            no of units to be charged. Typically for the SMS, it depends
     *            on the length of the SMS text and the address list
     * @param quantity
     * @param sessionKey
     * @throws Exception
     */
    public static DDRRecord createDDRRecordOnOutgoingCommunication(AdapterConfig config, String accountId,
        String toAddress, int quantity, String message, Session session) throws Exception {

        if (session != null) {
            HashMap<String, String> toAddressMap = new HashMap<String, String>();
            toAddressMap.put(toAddress, "");
            HashMap<String, Session> sessionKeyMap = new HashMap<String, Session>();
            sessionKeyMap.put(toAddress, session);
            return createDDRRecordOnCommunication(config, accountId, DDRTypeCategory.OUTGOING_COMMUNICATION_COST, null,
                                                  toAddressMap, CommunicationStatus.SENT, quantity, message,
                                                  sessionKeyMap);
        }
        else {
            throw new Exception("Session is not expected to be null..");
        }
    }
    
    /**
     * creates a ddr record for incoming communication charges
     * 
     * @param config
     * @param fromAddress
     * @param quantity
     * @param message
     * @param sessionKey
     * @throws Exception
     */
    public static DDRRecord createDDRRecordOnIncomingCommunication(AdapterConfig config, String accountId,
        String fromAddress, int quantity, String message, Session session) throws Exception {

        if (session != null) {
            HashMap<String, String> fromAddressMap = new HashMap<String, String>();
            fromAddressMap.put(fromAddress, "");
            HashMap<String, Session> sessionKeyMap = new HashMap<String, Session>();
            sessionKeyMap.put(fromAddress, session);
            return createDDRRecordOnCommunication(config, accountId, DDRTypeCategory.INCOMING_COMMUNICATION_COST, null,
                                                  fromAddressMap, CommunicationStatus.RECEIVED, quantity, message,
                                                  sessionKeyMap);
        }
        else {
            throw new Exception("Session is not expected to be null..");
        }
    }

    /**
     * creates a ddr record based on the adapterId and accoutnId from config and
     * the quantity based on the number of recepients in the toAddress
     * 
     * @param config
     * @param toAddress
     *            the address list
     * @param message
     * @param sessionKey
     * @throws Exception
     */
    public static DDRRecord createDDRRecordOnOutgoingCommunication(AdapterConfig config, String accountId,
        Map<String, String> toAddress, String message, Map<String, Session> sessionKeyMap) throws Exception {

        return createDDRRecordOnCommunication(config, accountId, DDRTypeCategory.OUTGOING_COMMUNICATION_COST, null,
                                              toAddress, CommunicationStatus.SENT, toAddress.size(), message,
                                              sessionKeyMap);
    }
    
    /**
     * creates a ddr record based on the adapterId and accoutnId from config and
     * the quantity
     * 
     * @param config
     * @param unitType
     * @param toAddress
     * @param quantity
     *            no of units to be charged. Typically for the SMS, it depends
     *            on the length of the SMS text and the address list
     * @param message
     *            actual message being sent. is saved in the addtionalInfo of
     *            the ddrRecord
     * @param sessionKey
     * @throws Exception
     */
    public static DDRRecord createDDRRecordOnOutgoingCommunication(AdapterConfig config, String accountId,
        String senderName, Map<String, String> toAddress, int quantity, String message,
        Map<String, Session> sessionKeyMap) throws Exception {

        return createDDRRecordOnCommunication(config, accountId, DDRTypeCategory.OUTGOING_COMMUNICATION_COST,
                                              senderName, toAddress, CommunicationStatus.SENT, quantity, message,
                                              sessionKeyMap);
    }

    /**
     * Creates a ddr record for an incoming communication based on the adapter 
     * @param config
     * @param accountId This accountId is charged for the communication
     * @param fromAddress
     * @param message
     * @param sessionKey
     * @return
     * @throws Exception
     */
    public static DDRRecord createDDRRecordOnIncomingCommunication(AdapterConfig config, String accountId,
        String fromAddress, String message, Session session) throws Exception {

        if (session != null) {
            Map<String, String> fromAddresses = new HashMap<String, String>();
            fromAddresses.put(fromAddress, "");
            Map<String, Session> sessionKeyMap = new HashMap<String, Session>();
            sessionKeyMap.put(fromAddress, session);
            return createDDRRecordOnCommunication(config, accountId, DDRTypeCategory.INCOMING_COMMUNICATION_COST,
                fromAddresses, CommunicationStatus.RECEIVED, message, sessionKeyMap);
        }
        else {
            throw new Exception("Session is not expected to be null..");
        }
    }
    
    /**
     * updates the DDR record matching the adapter, category and the status
     * @param config
     * @param category
     * @param status
     * @param remoteID
     * @param startTime actual call start timestamp
     * @param answerTime actual call answer timestamp
     * @param releaseTime actual call hangup timestamp
     * @throws Exception
     */
    public static DDRRecord updateDDRRecordOnCallStops(String ddrRecordId, AdapterConfig adapterConfig,
        String accountId, Long startTime, Long answerTime, Long releaseTime, Session session) throws Exception {

        DDRRecord ddrRecord = DDRRecord.getDDRRecord(ddrRecordId, accountId);
        if (ddrRecord != null) {
            ddrRecord.setStart(answerTime); //initialize the startTime to the answerTime
            Long duration = null;
            if (ddrRecord.getStart() != null) {
                duration = (releaseTime != null ? releaseTime : TimeUtils.getServerCurrentTimeInMillis()) -
                           ddrRecord.getStart();
                String address = "inbound".equals(session.getDirection()) ? session.getLocalAddress()
                    : session.getRemoteAddress();
                //if there is an address found, add status
                if (address != null) {
                    ddrRecord.addStatusForAddress(address, CommunicationStatus.FINISHED);
                }
            }
            else {
                ddrRecord.setStart(startTime); //if no answerTime i.e call not picked up, set to startTime
                duration = 0L;
                //mark as MISSED if its an outbound call
                if (ddrRecord.getFromAddress().equalsIgnoreCase(adapterConfig.getFormattedMyAddress())) {
                    for (String toAddress : ddrRecord.getToAddress().keySet()) {
                        ddrRecord.addStatusForAddress(toAddress, CommunicationStatus.MISSED);
                    }
                }
                else {
                    for (String toAddress : ddrRecord.getToAddress().keySet()) {
                        ddrRecord.addStatusForAddress(toAddress, CommunicationStatus.FINISHED);
                    }
                }
            }
            ddrRecord.setDuration(duration > 0L ? duration : 0);
            ddrRecord.createOrUpdateWithLog(session);
        }
        else {
            log.warning(String.format("No ddr record found for id: %s", ddrRecord));
        }
        return ddrRecord;
    }
    
    /**
     * create a ddr for an adapter being created and charge a monthly fee for
     * example
     * 
     * @param adapterConfig
     * @return
     * @throws Exception
     */
    public static DDRRecord createDDRForSubscription(AdapterConfig adapterConfig, boolean publishCharges)
        throws Exception {

        DDRType subscriptionDDRType = DDRType.getDDRType(DDRTypeCategory.SUBSCRIPTION_COST);
        DDRRecord newestDDRRecord = null;
        if (subscriptionDDRType != null) {
            List<DDRPrice> ddrPrices = DDRPrice.getDDRPrices(subscriptionDDRType.getTypeId(),
                AdapterType.getByValue(adapterConfig.getAdapterType()), adapterConfig.getConfigId(), null, null);
            //fetch the ddr based on the details
            List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords(adapterConfig.getOwner(), null,
                Arrays.asList(adapterConfig.getConfigId()), null, Arrays.asList(subscriptionDDRType.getTypeId()), null,
                null, null, null, null, null);
            DateTime serverCurrentTime = TimeUtils.getServerCurrentTime();
            newestDDRRecord = fetchNewestDdrRecord(ddrRecords);
            //flag for creating new ddrRecord
            boolean createNewDDRRecord = false;
            if (newestDDRRecord == null) {
                createNewDDRRecord = true;
            }
            else {
                //pick the first ddrPrice that is valid for now
                for (DDRPrice ddrPrice : ddrPrices) {
                    if (ddrPrice.isValidForTimestamp(serverCurrentTime.getMillis())) {
                        //check if the ddr already belong to the unitType
                        switch (ddrPrice.getUnitType()) {
                            case SECOND: //check if ddr belongs to this second
                                if (serverCurrentTime.minusSeconds(1).getMillis() > newestDDRRecord.getStart()) {
                                    createNewDDRRecord = true;
                                }
                                break;
                            case MINUTE: //check if ddr belongs to minute
                                if (serverCurrentTime.minusMinutes(1).getMillis() > newestDDRRecord.getStart()) {
                                    createNewDDRRecord = true;
                                }
                                break;
                            case HOUR: //check if ddr belongs to this hour
                                if (serverCurrentTime.minusHours(1).getMillis() > newestDDRRecord.getStart()) {
                                    createNewDDRRecord = true;
                                }
                                break;
                            case DAY: //check if ddr belongs to today
                                if (serverCurrentTime.minusDays(1).getMillis() > newestDDRRecord.getStart()) {
                                    createNewDDRRecord = true;
                                }
                                break;
                            case MONTH: //check if ddr belongs to this month
                                if (serverCurrentTime.minusMonths(1).getMillis() > newestDDRRecord.getStart()) {
                                    createNewDDRRecord = true;
                                }
                                break;
                            case YEAR: //check if ddr belongs to this year
                                if (serverCurrentTime.minusYears(1).getMillis() > newestDDRRecord.getStart()) {
                                    createNewDDRRecord = true;
                                }
                                break;
                            default:
                                throw new Exception("DDR cannot be created for Subsciption for UnitType: " +
                                    ddrPrice.getUnitType().name());
                        }
                        //create a new ddrRecord if not record found for the time
                        if (createNewDDRRecord) {
                            break;
                        }
                    }
                }
            }
            //create new ddrRecord
            if (createNewDDRRecord) {
                newestDDRRecord = new DDRRecord(subscriptionDDRType.getTypeId(), adapterConfig,
                    adapterConfig.getOwner(), 1);
                newestDDRRecord.setAccountType(adapterConfig.getAccountType());
                newestDDRRecord.setStart(serverCurrentTime.getMillis());
                //publish charges if needed
                if (publishCharges) {
                    Double ddrCost = calculateDDRCost(newestDDRRecord, false);
                    publishDDREntryToQueue(newestDDRRecord.getAccountId(), ddrCost);
                }
                newestDDRRecord.createOrUpdate();
                log.info(String.format("Added subscription fees to adapterId: %s of accountId: %s",
                    adapterConfig.getConfigId(), adapterConfig.getOwner()));
            }
            return newestDDRRecord;
        }
        else {
            throw new Exception("No Subscription DDRType found");
        }
    }
    
    /**
     * Create a ddr for a TTS being processed being created and charge a monthly
     * fee for example
     * 
     * @param adapterConfig
     * @return
     * @throws Exception
     */
    public static DDRRecord createDDRForTTS(String remoteAddress, Session session, TTSInfo ttsInfo, String message) {

        if (session != null) {
            try {
                return createDDRForTTS(message, session, true, ttsInfo.getProvider());
            }
            catch (Exception e) {
                e.printStackTrace();
                log.severe(String.format("Creating ddrRecord for TTS failed. sessionKey: %s accountid: %s message: %s localAddress: %s remoteAddress: %s",
                                         session.getKey(), session.getAccountId(), message, session.getLocalAddress(),
                                         remoteAddress));
            }
        }
        return null;
    }
    
    /**
     * create a ddr for a TTS being processed being created
     * @return
     * @throws Exception
     */
    public static DDRRecord createDDRForTTS(String message, Session session, boolean publishCharges,
        TTSProvider ttsProvider) throws Exception {

        DDRType ttsDDRType = DDRType.getDDRType(DDRTypeCategory.TTS_COST);
        DDRRecord ddrRecord = null;
        if (ttsDDRType != null && session != null) {
            String adapterType = session.getType();
            if ((adapterType == null || adapterType.isEmpty()) && session.getAdapterConfig() != null) {
                adapterType = session.getAdapterConfig().getAdapterType();
            }
            List<DDRPrice> ddrPrices = DDRPrice.getDDRPrices(ttsDDRType.getTypeId(),
                                                             AdapterType.getByValue(adapterType),
                                                             session.getAdapterID(), null, null);
            if (ddrPrices != null) {
                for (DDRPrice ddrPrice : ddrPrices) {
                    //pick the ddr price which matches the tts provider. 
                    if (ddrPrice.getKeyword() == null ||
                        (ttsProvider != null && ddrPrice.getKeyword().equalsIgnoreCase(ttsProvider.toString()))) {

                        message = message.replaceFirst("text://", "");
                        Double units = Math.ceil(message.length() / ddrPrice.getUnits());
                        units = units != null ? units : 1;
                        ddrRecord = new DDRRecord(ttsDDRType.getTypeId(), session.getAdapterConfig(),
                                                  session.getAccountId(), units.intValue());
                        String localAddress = session.getAdapterConfig() != null ? session.getAdapterConfig()
                                                    .getFormattedMyAddress() : session.getLocalAddress();
                        //set the from and to address
                        if("inbound".equalsIgnoreCase(session.getDirection())) {
                            ddrRecord.setFromAddress(session.getRemoteAddress());
                            ddrRecord.addToAddress(localAddress);
                        }
                        else {
                            ddrRecord.setFromAddress(localAddress);
                            ddrRecord.addToAddress(session.getRemoteAddress());
                        }
                        ddrRecord.addSessionKey(session.getKey());
                        if (ttsProvider != null) {
                            ddrRecord.addAdditionalInfo("TTSProvider", ttsProvider);
                        }
                        ddrRecord.addAdditionalInfo(DDR_MESSAGE_KEY, message);
                        ddrRecord.setStart(TimeUtils.getServerCurrentTimeInMillis());
                        ddrRecord.createOrUpdate();
                        break;
                    }
                }
            }
            if (publishCharges && ddrRecord != null) {
                Double ddrCost = calculateDDRCost(ddrRecord, false);
                publishDDREntryToQueue(ddrRecord.getAccountId(), ddrCost);
            }
            return ddrRecord;
        }
        else {
            throw new Exception("No DDRType found for type TTS_COST");
        }
    }
    
    /**
     * create a ddr service cost for a TTS being processed
     * 
     * @param adapterConfig
     * @return
     * @throws Exception
     */
    public static DDRRecord createDDRForTTSService(TTSProvider ttsProvider, String ttsAccountId, Session session,
        boolean publishCharges) throws Exception {

        DDRType ttsDDRType = DDRType.getDDRType(DDRTypeCategory.TTS_SERVICE_COST);
        DDRRecord ddrRecord = null;
        if (ttsDDRType != null && session != null) {
            String adapterType = session.getType();
            if ((adapterType == null || adapterType.isEmpty()) && session.getAdapterConfig() != null) {
                adapterType = session.getAdapterConfig().getAdapterType();
            }
            List<DDRPrice> ddrPrices = DDRPrice.getDDRPrices(ttsDDRType.getTypeId(),
                                                             AdapterType.getByValue(adapterType),
                                                             session.getAdapterID(), UnitType.PART, null);
            if (ddrPrices != null) {
                for (DDRPrice ddrPrice : ddrPrices) {
                    //pick the ddr price which matches the tts provider. 
                    if (ddrPrice.getKeyword() == null ||
                        (ttsProvider != null && ddrPrice.getKeyword().equalsIgnoreCase(ttsProvider.toString()))) {

                        ddrRecord = new DDRRecord(ttsDDRType.getTypeId(), session.getAdapterConfig(),
                                                  session.getAccountId(), 1);
                        //set the from and to address
                        String localAddress = session.getAdapterConfig() != null ? session.getAdapterConfig()
                                                    .getFormattedMyAddress() : session.getLocalAddress();
                        if("inbound".equalsIgnoreCase(session.getDirection())) {
                            ddrRecord.setFromAddress(session.getRemoteAddress());
                            ddrRecord.addToAddress(localAddress);
                            ddrRecord.setDirection("inbound");
                        }
                        else {
                            ddrRecord.setFromAddress(localAddress);
                            ddrRecord.addToAddress(session.getRemoteAddress());
                            ddrRecord.setDirection("outbound");
                        }
                        ddrRecord.addSessionKey(session.getKey());
                        if (ttsProvider != null) {
                            ddrRecord.addAdditionalInfo("TTSProvider", ttsProvider);
                        }
                        if (ttsAccountId != null) {
                            ddrRecord.addAdditionalInfo("ttsAccountId", ttsAccountId);
                        }
                        ddrRecord.addAdditionalInfo(DDR_MESSAGE_KEY, "Service costs");
                        ddrRecord.setStart(TimeUtils.getServerCurrentTimeInMillis());
                        ddrRecord.createOrUpdate();
                        break;
                    }
                }
            }
            if (publishCharges && ddrRecord != null) {
                Double ddrCost = calculateDDRCost(ddrRecord, false);
                publishDDREntryToQueue(ddrRecord.getAccountId(), ddrCost);
            }
            return ddrRecord;
        }
        else {
            throw new Exception("No Subscription DDRType found");
        }
    }
    
    /**
     * Pushes the given cost if its more than 0 for the given account to the
     * RabbitMQ queue. its expected to be picked up by the ddr processor agent.
     * 
     * @param accountId
     * @param totalCost
     * @throws Exception
     */
    public static void publishDDREntryToQueue(String accountId, Double totalCost) throws Exception {

        if (totalCost != null && totalCost > 0.0 && !ServerUtils.isInUnitTestingEnvironment()) {
            try {
                log.info(String.format("Publishing costs: %s for account: %s", totalCost, accountId));
                rabbitMQConnectionFactory = rabbitMQConnectionFactory != null ? rabbitMQConnectionFactory
                    : new ConnectionFactory();
                String url = (System.getProperty("AMQP_URL") != null ? System.getProperty("AMQP_URL")
                    : "amqp://localhost");
                rabbitMQConnectionFactory.setUri(url);
                Connection connection = rabbitMQConnectionFactory.newConnection();
                Channel channel = connection.createChannel();
                //create a message
                HashMap<String, String> message = new HashMap<String, String>();
                message.put("accountId", accountId);
                message.put("cost", String.valueOf(totalCost));
                channel.queueDeclare(PUBLISH_QUEUE_NAME, false, false, false, null);
                channel.basicPublish("", PUBLISH_QUEUE_NAME, null, ServerUtils.serialize(message).getBytes());
                channel.close();
                connection.close();
            }
            catch (Exception e) {
                log.severe("Error seen: " + e.getLocalizedMessage());
            }
        }
    }
    
    /**
     * Creates a ddr record based on the input parameters and quantity is the
     * size of the addresses map
     * @param config
     * @param accountId Charges are attached to this accountId
     * @param category
     * @param unitType
     * @param addresses
     * @param status
     * @param message
     * @param sessionKey
     * @throws Exception
     */
    public static DDRRecord createDDRRecordOnCommunication(AdapterConfig config, String accountId,
        DDRTypeCategory category, Map<String, String> addresses, CommunicationStatus status, String message,
        Map<String, Session> sessionKeyMap) throws Exception {

        return createDDRRecordOnCommunication(config, accountId, category, null, addresses, status, addresses.size(),
                                              message, sessionKeyMap);
    }
    
    /**
     * calculates the cost associated with this DDRRecord bsaed on the linked
     * DDRPrice. This does not add the servicecosts. Use the
     * {@link DDRUtils#calculateCommunicationDDRCost(DDRRecord, Boolean)} for
     * it. <br>
     * DDRPrice with the most recent {@link DDRPrice#getEndTime() endTime} is
     * chosen if the {@link DDRRecord#getStart() startTime} doesnt fall between
     * {@link DDRPrice#getStartTime() startTime} and
     * {@link DDRPrice#getEndTime() endTime}
     * 
     * @return cost incurred for this ddrRecord
     * @throws Exception
     */
    public static Double calculateDDRCost(DDRRecord ddrRecord) throws Exception {

        return calculateDDRCost(ddrRecord, false);
    }
    
    /**
     * Calculates the cost associated with this DDRRecord bsaed on the linked
     * DDRPrice. <br>
     * DDRPrice with the most recent {@link DDRPrice#getEndTime() endTime} is
     * chosen if the {@link DDRRecord#getStart() startTime} doesnt fall between
     * {@link DDRPrice#getStartTime() startTime} and
     * {@link DDRPrice#getEndTime() endTime}
     * 
     * @param ddrRecord
     * @return cost incurred for this ddrRecord
     * @throws Exception
     */
    public static Double calculateDDRCost(DDRRecord ddrRecord, Boolean includeServiceCharges) throws Exception {

        if (ddrRecord != null) {
            
            DDRType ddrType = ddrRecord.getDdrType();
            AdapterConfig adapter = ddrRecord.getAdapter();
            if (ddrType != null) {
                AdapterType adapterType = adapter != null ? AdapterType.getByValue(adapter.getAdapterType()) : null;
                String adapterId = adapter != null ? adapter.getConfigId() : null;
                ddrRecord.addAdditionalInfo(DDRType.DDR_CATEGORY_KEY, ddrType.getCategory());
                switch (ddrType.getCategory()) {
                    case ADAPTER_PURCHASE:
                    case SERVICE_COST: {
                        List<DDRPrice> ddrPrices = DDRPrice.getDDRPrices(ddrType.getTypeId(), adapterType, adapterId,
                                                                         UnitType.PART, null);
                        return !ddrPrices.isEmpty() ? ddrPrices.iterator().next().getPrice() : 0.0;
                    }
                    case INCOMING_COMMUNICATION_COST:
                    case OUTGOING_COMMUNICATION_COST:
                        return calculateCommunicationDDRCost(ddrRecord, includeServiceCharges);
                    case TTS_COST: {
                        Object ttsProviderObject = ddrRecord.getAdditionalInfo().get("TTSProvider");
                        String ttsProvider = ttsProviderObject != null ? ttsProviderObject.toString() : null;
                        List<DDRPrice> ddrPrices = DDRPrice.getDDRPrices(ddrType.getTypeId(), adapterType, adapterId,
                                                                         UnitType.PART, ttsProvider);
                        if (ddrPrices != null && !ddrPrices.isEmpty()) {
                            DDRPrice ddrPrice = ddrPrices.iterator().next();
                            return ddrPrice.getPrice() * ddrRecord.getQuantity();
                        }
                        else {
                            return 0.0;
                        }
                    }
                    case SUBSCRIPTION_COST:
                    case OTHER:
                    default: {
                        List<DDRPrice> ddrPrices = DDRPrice.getDDRPrices(ddrType.getTypeId(), adapterType,
                                                                         ddrRecord.getAdapterId(), null, null);
                        if (ddrPrices != null && !ddrPrices.isEmpty()) {
                            return ddrPrices.iterator().next().getPrice();
                        }
                        else {
                            String errorMessage = String.format("No DDRTypes found for this DDRRecord id: %s and ddrTypeId: %s",
                                                                ddrRecord.getId(), ddrRecord.getDdrTypeId());
                            log.severe(errorMessage);
                            throw new Exception(errorMessage);
                        }
                    }
                }
            }
            else {
                String errorMessage = String.format("No DDRTypes found for this DDRRecord id: %s and ddrTypeId: %s",
                                                    ddrRecord.getId(), ddrRecord.getDdrTypeId());
                log.severe(errorMessage);
                throw new Exception(errorMessage);
            }
        }
        else {
            log.severe("No DDRRecordfound.. returning 0.0 as costs");
            return 0.0;
        }
    }
    
    /**
     * returns the first DDRPrice based on the category and the unitType.
     * @param category returns null if this is null or a DDRType is not found for this category.
     * @param adapterType
     * @param adapterId
     * @param unitType
     * @param keyword
     * @return returns the first DDRPrice based on the category and the unitType.
     * @throws Exception
     */
    public static DDRPrice fetchDDRPrice( DDRTypeCategory category, AdapterType adapterType, String adapterId,
        UnitType unitType, String keyword ) throws Exception
    {
        DDRType ddrType = DDRType.getDDRType( category );
        if ( ddrType != null )
        {
            List<DDRPrice> ddrPrices = DDRPrice.getDDRPrices( ddrType.getTypeId(), adapterType, adapterId, unitType,
                keyword );
            return ddrPrices != null && !ddrPrices.isEmpty() ? ddrPrices.iterator().next() : null;
        }
        return null;
    }
    
    /**
     * Returns true if a ddr is processed successfully for the given session. else false.
     * @param sessionKey
     * @param pushToQueue pushes the sessionKey to the rabbitMQ queue for postprocessing if a corresponding ddr is not
     * processed successfully
     * @return true if the session can be dropped on hangup
     */
    public static boolean stopDDRCosts(Session session) {

        boolean result = false;
        if (session != null) {
            AdapterConfig adapterConfig = session.getAdapterConfig();
            //stop costs
            try {
                log.info(String.format("stopping charges for session: %s", ServerUtils.serialize(session)));
                DDRRecord ddrRecord = null;
                //if no ddr is seen for this session. try to fetch it based on the timestamps
                if (session.getDdrRecordId() == null) {
                    ddrRecord = DDRRecord.getDDRRecord(session.getKey());
                    if (ddrRecord != null) {
                        session.setDdrRecordId(ddrRecord.getId());
                    }
                }
                if (adapterConfig.isCallAdapter()) {
                    //update session with call times if missing
                    if (AdapterProviders.TWILIO.equals(adapterConfig.getProvider()) &&
                        (session.getStartTimestamp() == null || session.getReleaseTimestamp() == null)) {

                        //TODO: must be removed probably... just here to test the answerTimestmap
                        new Exception("session must already have an answer timestamp and release timestamp").printStackTrace();
                    }
                    if (session.getStartTimestamp() != null && session.getReleaseTimestamp() != null &&
                        session.getDirection() != null) {
                        ddrRecord = updateDDRRecordOnCallStops(session.getDdrRecordId(), adapterConfig,
                                               session.getAccountId(), Long.parseLong(session.getStartTimestamp()),
                                               session.getAnswerTimestamp() != null ? Long.parseLong(session.getAnswerTimestamp())
                                                   : null, Long.parseLong(session.getReleaseTimestamp()), session);
                        if (ddrRecord == null && session.getAnswerTimestamp() != null) {
                            String errorMessage = String.format("No costs added to communication currently for session: %s, as no ddr record is found",
                                                                session.getKey());
                            log.severe(errorMessage);
                            dialogLog.severe(adapterConfig, errorMessage, session);
                        }
                        else if (ddrRecord != null && session.getAnswerTimestamp() == null &&
                            session.getReleaseTimestamp() != null) {

                            String warningMessage = String.format("No costs added. Looks like a immediate hangup! Hangup timestamp: %s found. But answerTimestamp not found for session: %s",
                                                                  session.getReleaseTimestamp(), session.getKey());
                            log.warning(warningMessage);
                        }
                        //publish charges
                        Double totalCost = calculateCommunicationDDRCost(ddrRecord, true);
                        //attach cost to ddr in all cases. Change as on ddr processing taking time
                        if (ddrRecord != null) {
                            ddrRecord.setTotalCost(totalCost);
                            ddrRecord.createOrUpdateWithLog(session);
                            publishDDREntryToQueue(ddrRecord.getAccountId(), totalCost);
                        }
                        result = true;
                    }
                    //if answerTimestamp and releastTimestamp is not found, add it to the queue
                    else {
                        String errorMessage = String.format("No costs added to communication currently for session: %s, as no answerTimestamp or releaseTimestamp is found",
                                                            session.getKey());
                        log.severe(errorMessage);
                        dialogLog.severe(adapterConfig, errorMessage, session);
                    }
                }
                //text adapter. delete the session if question is null
                else if (session.getQuestion() == null) {
                    session.drop();
                    result = true;
                }
            }
            catch (Exception e) {
                String errorMessage = String.format("Applying charges failed. Direction: %s for adapterId: %s with address: %s remoteId: %s and localId: %s \n Error: %s.",
                                                    session.getDirection(), session.getAdapterID(),
                                                    adapterConfig.getMyAddress(), session.getRemoteAddress(),
                                                    session.getLocalAddress(), e.getLocalizedMessage());
                e.printStackTrace();
                log.severe(errorMessage);
                dialogLog.severe(session.getAdapterConfig(), errorMessage, session);
            }
        }

        //clear the session from the dialog publish queue
        try {
            Agent agent = AgentHost.getInstance().getAgent("dialog");
            if (agent != null) {
                DialogAgent dialogAgent = (DialogAgent) agent;
                boolean clearSessionFromCurrentQueue = dialogAgent.clearSessionFromCurrentQueue(session.getKey());
                log.severe(String.format("Tried to remove SessionKey: %s from queue. Status: %s", session.getKey(),
                                         clearSessionFromCurrentQueue));
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            log.severe(String.format("Could not clear session: %s from queue", session.getKey()));
        }
        return result;
    }

    /** always include start-up costs if the adapter is broadsoft and there is some communication costs involved.
     * Only applies to incoming phonecalls
     * @param result
     * @param config
     * @return the ddrCost added to the already calculated communication costs
     * @throws Exception
     */
    protected static double applyStartUpCost(double result, AdapterConfig config, String direction) throws Exception {

        if (config != null && config.isCallAdapter() && result > 0.0 &&
            "outbound".equalsIgnoreCase(direction)) {
            DDRPrice startUpPrice = fetchDDRPrice(DDRTypeCategory.START_UP_COST,
                                                  AdapterType.getByValue(config.getAdapterType()),
                                                  config.getConfigId(), UnitType.PART, null);
            Double startUpCost = startUpPrice != null ? startUpPrice.getPrice() : 0.0;
            result += startUpCost;
        }
        return result;
    }
    
    /**
     * check if service costs are to be included, only include it if there is
     * any communication costs and waive it off it is greater than the service costs 
     * @param ddrRecord
     * @param includeServiceCosts
     * @param result
     * @param config
     * @return the service charge applied to the commnication cost.
     * @throws Exception
     */
    protected static double applyServiceCharges(DDRRecord ddrRecord, Boolean includeServiceCosts, double result,
        AdapterConfig config) throws Exception {

        if (Boolean.TRUE.equals(includeServiceCosts)) {

            //by default always apply service charge
            boolean applyServiceCharge = true;

            //dont apply service charge if its a missed call or call ignroed (call duration is 0)
            if (ddrRecord.getAdapter() != null && ddrRecord.getAdapter().isCallAdapter() &&
                (ddrRecord.getDuration() == null || ddrRecord.getDuration() <= 0)) {
                applyServiceCharge = false;
            }
            //get the max of the service cost and the communication cost
            if (applyServiceCharge) {

                String adapterId = config != null ? config.getConfigId() : null;
                AdapterType adapterType = config != null ? AdapterType.getByValue(config.getAdapterType()) : null;
                DDRPrice ddrPriceForDialogService = fetchDDRPrice(DDRTypeCategory.SERVICE_COST, adapterType, adapterId,
                                                                  UnitType.PART, null);
                Double serviceCost = ddrPriceForDialogService != null ? ddrPriceForDialogService.getPrice() : 0.0;
                //get the max of the service cost and the communication cost
                result = Math.max(result, serviceCost);
            }
        }
        return result;
    }
    
    /**
     * creates a ddr record based on the input parameters
     * 
     * @param config
     *            picks the adpaterId, owner and myAddress from this
     * @param category
     * @param senderName
     *            will be used as the DDRRecord's fromAddress (if not null and
     *            outbound). Use null if {@link AdapterConfig#getMyAddress()} is
     *            to be used.
     * @param unitType
     * @param addresses
     * @param status
     * @param quantity
     * @throws Exception
     */
    private static DDRRecord createDDRRecordOnCommunication(AdapterConfig config, String accountId,
        DDRTypeCategory category, String senderName, Map<String, String> addresses, CommunicationStatus status,
        int quantity, String message, Map<String, Session> sessionKeyMap) throws Exception {

        DDRType communicationCostDDRType = DDRType.getDDRType(category);
        if (communicationCostDDRType != null && config != null) {
            log.info(String.format("Applying charges for account: %s and adapter: %s with address: %s", accountId,
                                   config.getConfigId(), config.getMyAddress()));
            if (config.getConfigId() != null && accountId != null) {
                DDRRecord ddrRecord = new DDRRecord(communicationCostDDRType.getTypeId(), config, accountId, 1);
                switch (status) {
                    case SENT:
                        String fromAddress = senderName != null && !senderName.isEmpty() ? senderName
                            : config.getFormattedMyAddress();
                        ddrRecord.setFromAddress(fromAddress);
                        ddrRecord.setToAddress(addresses);
                        ddrRecord.setDirection("outbound");
                        break;
                    case RECEIVED:
                        ddrRecord.setFromAddress(addresses.keySet().iterator().next());
                        Map<String, String> toAddresses = new HashMap<String, String>();
                        String toAddress = config.getFormattedMyAddress();
                        if(sessionKeyMap != null && !sessionKeyMap.values().isEmpty()) {
                            String localName = sessionKeyMap.values().iterator().next().getLocalName();
                            if(localName != null && !localName.isEmpty()) {
                                toAddress = localName;
                            }
                        }
                        toAddresses.put(toAddress, "");
                        ddrRecord.setToAddress(toAddresses);
                        ddrRecord.setDirection("inbound");
                        break;
                    default:
                        throw new Exception("Unknown CommunicationStatus seen: " + status.name());
                }
                ddrRecord.setQuantity(quantity);
                //add individual statuses
                for (String address : addresses.keySet()) {
                    if (config.isCallAdapter() || config.isSMSAdapter()) {
                        address = PhoneNumberUtils.formatNumber(address, null);
                    }
                    ddrRecord.addStatusForAddress(address, status);
                }
                ddrRecord.addAdditionalInfo(DDR_MESSAGE_KEY, message);
                ddrRecord.setSessionKeysFromMap(sessionKeyMap);
                ddrRecord.addAdditionalInfo(DDRType.DDR_CATEGORY_KEY, category);
                //set the ddrRecord time with server current time creationTime.
                ddrRecord.setStart(TimeUtils.getServerCurrentTimeInMillis());
                if(config.isPrivate()) {
                    ddrRecord.addAdditionalInfo(Adapter.IS_PRIVATE, true);
                }
                ddrRecord = ddrRecord.createOrUpdateWithLog(sessionKeyMap);
                return ddrRecord;
            }
        }
        log.warning(String.format("Not charging this communication from: %s adapterid: %s anything!!",
                                  config.getMyAddress(), config.getConfigId()));
        return null;
    }

    public static Double getCeilingAtPrecision(double value, int precision) {

        return Math.round(value * Math.pow(10, precision)) / Math.pow(10, precision);
    }
    
    /**
     * simple method to return the newest DDRRecord from the list
     * @param ddrRecords
     * @return
     */
    private static DDRRecord fetchNewestDdrRecord(Collection<DDRRecord> ddrRecords){
        DDRRecord selecteDdrRecord = null;
        for (DDRRecord ddrRecord : ddrRecords) {
            if (selecteDdrRecord == null ||
                (ddrRecord.getStart() != null && selecteDdrRecord.getStart() < ddrRecord.getStart())) {
                selecteDdrRecord = ddrRecord;
            }
        }
        return selecteDdrRecord;
    }
    
    /**
     * calculates the total cost for this ddrRecord and ddr price
     * 
     * @param ddrRecord
     * @param ddrPrice
     * @return the total cost chargeable
     * @throws Exception
     */
    private static Double calculateCommunicationDDRCost(DDRRecord ddrRecord, DDRPrice ddrPrice) throws Exception {

        DDRType ddrType = null;
        AdapterConfig adapterConfig = null;
        Double totalCost = null;

        if (ddrRecord != null) {
            ddrType = DDRType.getDDRType(ddrRecord.getDdrTypeId());
            adapterConfig = ddrRecord.getAdapter();
            if (adapterConfig != null && adapterConfig.isPrivate()) {
                return totalCost;
            }
        }
        if (ddrType != null && ddrPrice != null) {
            switch (ddrType.getCategory()) {
                case INCOMING_COMMUNICATION_COST:
                case OUTGOING_COMMUNICATION_COST: {
                    Double totalTime = null;
                    double duration_double = ddrRecord.getDuration() != null ? ddrRecord.getDuration().doubleValue()
                        : 0.0;
                    switch (ddrPrice.getUnitType()) {
                        case SECOND:
                            totalTime = duration_double / 1000; //in secs
                            break;
                        case MINUTE:
                            totalTime = duration_double / (60 * 1000); //in mins
                            break;
                        case HOUR:
                            totalTime = duration_double / (60 * 60 * 1000); //in hrs
                            break;
                        case DAY:
                            totalTime = duration_double / (24 * 60 * 60 * 1000); //in days
                            break;
                        case MONTH:
                            int monthOfYear = TimeUtils.getServerCurrentTime().getMonthOfYear();
                            int totalDays = Calendar.getInstance(TimeUtils.getServerTimeZone())
                                                    .getActualMaximum(monthOfYear);
                            totalTime = duration_double / (totalDays * 24 * 60 * 60 * 1000); //in months
                            break;
                        case PART:
                            totalTime = 1.0;
                            break;
                        default:
                            throw new Exception("DDR not implemented for this UnitType: " + ddrPrice.getUnitType());
                    }
                    double noOfComsumedUnits = Math.ceil(totalTime) / ((double) ddrPrice.getUnits());
                    totalCost = ddrRecord.getQuantity() * Math.ceil(noOfComsumedUnits) * ddrPrice.getPrice();
                    break;
                }
                default:
                    throw new Exception("DDR not implemented for this category: " + ddrType.getCategory());
            }
            //add the selected ddrPrice into the additionalInfo for tracking purposes
            //DO NOT add a save here. As this method might have been called from the getter itself
            ddrRecord.addAdditionalInfo("ddrPriceId", ddrPrice.getId());
        }
        return totalCost;
    }
    
    /**
     * calculates the cost for the given ddrRecord based on the linked DDRPrice.
     * DDRPrice with the most recent {@link DDRPrice#getEndTime() endTime} is
     * chosen if the {@link DDRRecord#getStart() startTime} doesnt fall between
     * {@link DDRPrice#getStartTime() startTime} and
     * {@link DDRPrice#getEndTime() endTime}. <br>
     * Following costs can be added: <br>
     * 1. {@link DDRTypeCategory#INCOMING_COMMUNICATION_COST} If its an incoming ddr <br>
     *  or {@link DDRTypeCategory#OUTGOING_COMMUNICATION_COST} If its an outgoing ddr <br>
     * 2. {@link DDRTypeCategory#START_UP_COST} if its a ddr record linked to calling <br>
     * 3. {@link DDRTypeCategory#SERVICE_COST} per dialog cost if includeServiceCosts is true <br>
     * @param ddrRecord
     * @param includeServiceCosts includes the service cost for this ddr too, if true. The service cost is 
     * ignored if it is lesser than the communiciation cost
     * @return
     * @throws Exception
     */
    private static Double calculateCommunicationDDRCost(DDRRecord ddrRecord, Boolean includeServiceCosts)
        throws Exception {

        double result = 0.0;
        if (ddrRecord != null) {
            
            AdapterConfig config = ddrRecord.getAdapter();
            if (config != null && !config.isPrivate()) {
                
                DDRType ddrType = ddrRecord.getDdrType();
                List<DDRPrice> communicationDDRPrices = null;
                if (config.isCallAdapter()) {
                    String toAddress = null;
                    //for a calling ddr record, it must always have one address in the toList
                    if (ddrRecord.getToAddress().keySet().size() == 1) {
                        toAddress = ddrRecord.getToAddress().keySet().iterator().next();
                        PhoneNumberType numberType = PhoneNumberUtils.getPhoneNumberType(toAddress);
                        String keyword = null;
                        if (!numberType.equals(PhoneNumberType.UNKNOWN)) {
                            PhoneNumber phoneNumber = PhoneNumberUtils.getPhoneNumberProto(toAddress, null);
                            keyword = phoneNumber.getCountryCode() + "|" + numberType.name().toUpperCase();
                        }
                        communicationDDRPrices = DDRPrice.getDDRPrices(ddrType.getTypeId(),
                                                                       AdapterType.getByValue(config.getAdapterType()),
                                                                       config.getConfigId(), null, keyword);
                    }
                    else {
                        log.severe("Multiple addresses found in the toAddress field for CALL: " +
                            ddrRecord.getToAddressString());
                    }
                }
                else {
                    communicationDDRPrices = DDRPrice.getDDRPrices(ddrType.getTypeId(),
                                                                   AdapterType.getByValue(config.getAdapterType()),
                                                                   config.getConfigId(), null, null);
                }
                if (communicationDDRPrices != null && !communicationDDRPrices.isEmpty() &&
                    ddrRecord.getAccountId() != null) {
                    //use the ddrPrice that has the most recent start date and matches the keyword based on the 
                    //to address, if it is mobile or landline
                    DDRPrice selectedDDRPrice = null;
                    boolean isDDRPriceInTimerange = false;
                    for (DDRPrice ddrPrice : communicationDDRPrices) {
                        //pick a price whose start and endTimestamp falls in that of the ddrRecords
                        if (ddrRecord.getStart() != null && ddrPrice.isValidForTimestamp(ddrRecord.getStart())) {
                            isDDRPriceInTimerange = true;
                        }
                        //TODO: should check for other mechanisms that fetch the closet offer to the ddrRecord timestamp
                        //1. Fetch the lastest ddr price by creation time 
                        if (isDDRPriceInTimerange &&
                            (selectedDDRPrice == null || (ddrPrice.getCreationTime() > selectedDDRPrice.getCreationTime()))) {

                            selectedDDRPrice = ddrPrice;
                        }
                    }
                    //else pick the last ddrPrice in the list
                    if (selectedDDRPrice == null) {
                        selectedDDRPrice = communicationDDRPrices.get(communicationDDRPrices.size() - 1);
                    }
                    if (!isDDRPriceInTimerange) {
                        log.warning(String.format("No DDRPrice date range match for DDRRecord: %s. In turn fetched: %s of type: %s with price: %s",
                                                  ddrRecord.getId(), selectedDDRPrice.getId(),
                                                  selectedDDRPrice.getUnitType(), selectedDDRPrice.getPrice()));
                    }
                    result = calculateCommunicationDDRCost(ddrRecord, selectedDDRPrice);
                }
                result = applyStartUpCost(result, config, ddrRecord.getDirection());
            }
            result = applyServiceCharges(ddrRecord, includeServiceCosts, result, config);
        }
        return getCeilingAtPrecision(result, 3);
    }
    
    /**
     * Returns true if the address given is valid phonenumber. If it is not a
     * valid number, it also tries to fetch the linked ddrRecord from the
     * session and adds an addtional info that it is an invalid number.
     * 
     * @param address
     * @param sessionKey
     * @return
     */
    public static boolean validateAddressAndUpdateDDRIfInvalid(String address, String sessionKey) {

        return validateAddressAndUpdateDDRIfInvalid(address, Session.getSession(sessionKey));
    }
    
    /**
     * Returns true if the address given is valid phonenumber. If it is not a
     * valid number, it also tries to fetch the linked ddrRecord from the
     * session and adds an addtional info that it is an invalid number.
     * 
     * @param address
     * @param session
     * @return 
     */
    public static boolean validateAddressAndUpdateDDRIfInvalid(String address, Session session) {

        address = address != null ? address.replaceFirst("tel:", "").trim() : null;
        if (!PhoneNumberUtils.isValidPhoneNumber(address)) {
            if (session != null && session.getDDRRecord() != null) {
                try {
                    DDRRecord ddrRecord = session.getDDRRecord();
                    if (ddrRecord != null) {
                        ddrRecord.addAdditionalInfo(address, "Invalid address");
                        ddrRecord.addStatusForAddress(address, CommunicationStatus.ERROR);
                        ddrRecord.createOrUpdate();
                        session.drop();
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return false;
        }
        return true;
    }
}