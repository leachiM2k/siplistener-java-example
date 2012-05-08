package com.sipgate.siplistener.processor;

import javax.sip.Dialog;
import javax.sip.DialogState;
import javax.sip.InvalidArgumentException;
import javax.sip.PeerUnavailableException;
import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.SipProvider;
import javax.sip.TransactionState;
import javax.sip.header.ContactHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.log4j.Logger;

public class RequestProcessor
{
	private MessageFactory messageFactory;

	private ServerTransaction inviteTid;

	private Request inviteRequest;
	
	private Dialog dialog;
	
	private final static Logger log = Logger.getLogger( RequestProcessor.class ); 
	
	public RequestProcessor() throws PeerUnavailableException
	{
		messageFactory = SipFactory.getInstance().createMessageFactory();		
	}
	
	public void process(RequestEvent requestReceivedEvent, Dialog dialog)
	{
		this.dialog = dialog;
		
        Request request = requestReceivedEvent.getRequest();
        ServerTransaction serverTransactionId = requestReceivedEvent.getServerTransaction();

        log.debug("Request " + request.getMethod() + " received"
                + " with server transaction id " + serverTransactionId);

        if (request.getMethod().equals(Request.BYE))
            processBye(request, serverTransactionId);
        else if (request.getMethod().equals(Request.INVITE))
            processInvite(requestReceivedEvent, serverTransactionId);
        else if (request.getMethod().equals(Request.ACK))
            processAck(request, serverTransactionId);
        else if ( request.getMethod().equals(Request.CANCEL) )
        	processCancel(request, serverTransactionId);		
	}
	
    public void processBye(Request request,
            ServerTransaction serverTransactionId) {
        try {
            log.debug("got a bye");
            if (serverTransactionId == null) {
                log.debug("null TID.");
                return;
            }
            Dialog dialog = serverTransactionId.getDialog();
            log.debug("Dialog State = " + dialog.getState());
            Response response = messageFactory.createResponse(200, request);
            serverTransactionId.sendResponse(response);
            log.debug("Sending OK.");
            log.debug("Dialog State = " + dialog.getState());

        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(0);

        }
    }
    

    /**
     * Process the invite request.
     */
    public void processInvite(RequestEvent requestEvent,ServerTransaction serverTransaction) {
        SipProvider sipProvider = (SipProvider) requestEvent.getSource();
        Request request = requestEvent.getRequest();
        try {
            log.debug("Got an Invite sending Trying");

            FromHeader from = (FromHeader) request.getHeader("From");
        	log.debug("Call from: " + from.getAddress().getDisplayName() +"/"+ from.getAddress().getURI());

        	ContactHeader contact = (ContactHeader) request.getHeader("Contact");
        	log.debug("Contact: " + contact.getAddress().getDisplayName() +"/"+ contact.getAddress().getURI());
            
            // log.debug("shootme: " + request);
            Response response = messageFactory.createResponse(Response.TRYING,
                    request);
            ServerTransaction st = requestEvent.getServerTransaction();

            if (st == null) {
                st = sipProvider.getNewServerTransaction(request);
            }
            dialog = st.getDialog();

            st.sendResponse(response);

            Response okResponse = messageFactory.createResponse(Response.NOT_FOUND,
                    request);
            
            ToHeader toHeader = (ToHeader) okResponse.getHeader(ToHeader.NAME);
            toHeader.setTag("4321"); // Application is supposed to set.
            
            inviteTid = st;
            // Defer sending the OK to simulate the phone ringing.
            inviteRequest = request;
            
            try {
                if (inviteTid.getState() != TransactionState.COMPLETED) {
                    inviteTid.sendResponse(okResponse);
                }
            } catch (SipException ex) {
                ex.printStackTrace();
            } catch (InvalidArgumentException ex) {
                ex.printStackTrace();
            }            
           
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(0);
        }
    }
    
    public void processCancel(Request request,
            ServerTransaction serverTransactionId) {
        try {
            log.debug("shootme:  got a cancel.");
            if (serverTransactionId == null) {
                log.debug("shootme:  null tid.");
                return;
            }
            Response response = messageFactory.createResponse(200, request);
            serverTransactionId.sendResponse(response);
            if (dialog.getState() != DialogState.CONFIRMED) {
                response = messageFactory.createResponse(
                        Response.REQUEST_TERMINATED, inviteRequest);
                inviteTid.sendResponse(response);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(0);

        }
    }
    
    /**
     * Process the ACK request. Send the bye and complete the call flow.
     */
    public void processAck(Request request,
            ServerTransaction serverTransaction) {
        log.debug("shootme: got an ACK! ");
        log.debug("Dialog State = " + dialog.getState());
    } 
}
