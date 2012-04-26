package com.sipgate.siplistener;

import gov.nist.javax.sip.SipStackExt;
import gov.nist.javax.sip.clientauthutils.AccountManager;
import gov.nist.javax.sip.clientauthutils.AuthenticationHelper;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogState;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionState;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.TransactionUnavailableException;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.Header;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.UserAgentHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

public class SipgateSipListener implements SipListener
{
	private SipStack sipStack;
	private SipProvider sipProvider;
	private boolean sipOnOffFlag;
	private ClientTransaction registerTid;
	private HeaderFactory headerFactory;
	private AddressFactory addressFactory;
	private MessageFactory messageFactory;
	private Dialog dialog;
	
	private ListeningPoint lp;
	private AccountManager accountManager = new SipgateAccountManager();
	private SipgateUserCredentials credentials;
	private ServerTransaction inviteTid;
	private Request inviteRequest;
	
	public SipgateSipListener()
	{
		credentials = (SipgateUserCredentials) accountManager.getCredentials(null, null);
	}
	
	public void init() throws Exception
	{
		SipFactory sipFactory = SipFactory.getInstance();

		sipStack = null;
		Properties properties = new Properties();
		properties.setProperty("javax.sip.IP_ADDRESS", getLocalIPAddress());
		properties.setProperty("javax.sip.OUTBOUND_PROXY", credentials.getProxy() +  "/" + ListeningPoint.UDP);
		properties.setProperty("javax.sip.STACK_NAME", "Sip Test");
//		properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
//		properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "shootmedebug.txt");
//		properties.setProperty("gov.nist.javax.sip.SERVER_LOG", "shootmelog.txt");

		// Create SipStack object
		sipStack = sipFactory.createSipStack(properties);
		headerFactory = sipFactory.createHeaderFactory();
		addressFactory = sipFactory.createAddressFactory();
		messageFactory = sipFactory.createMessageFactory();
		lp = sipStack.createListeningPoint(getLocalIPAddress(), 8002, ListeningPoint.UDP);

		SipgateSipListener listener = this;

		sipProvider = sipStack.createSipProvider(lp);
		sipOnOffFlag = true;
		sipProvider.addSipListener(listener);
	}

	public void register() throws ParseException, InvalidArgumentException, TransactionUnavailableException,
			SipException
	{
		// create >From Header
		SipURI fromAddress = addressFactory.createSipURI(credentials.getUserName(), getLocalIPAddress());

		Address fromNameAddress = addressFactory.createAddress(fromAddress);
		fromNameAddress.setDisplayName(credentials.getDisplayName());
		FromHeader fromHeader = headerFactory.createFromHeader(fromNameAddress, null);

		// create To Header
		SipURI toAddress = addressFactory.createSipURI(credentials.getUserName(), credentials.getSipDomain());
		Address toNameAddress = addressFactory.createAddress(toAddress);
		toNameAddress.setDisplayName(credentials.getDisplayName());
		ToHeader toHeader = headerFactory.createToHeader(toNameAddress, null);

		// create Request URI
		SipURI requestURI = addressFactory.createSipURI(credentials.getUserName(), credentials.getSipDomain());

		// Create ViaHeaders

		List<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
		String ipAddress = lp.getIPAddress();
		ViaHeader viaHeader = headerFactory.createViaHeader(ipAddress, lp.getPort(), lp.getTransport(), null);

		// add via headers
		viaHeaders.add(viaHeader);

		
		// Create a new CallId header
		CallIdHeader callIdHeader = sipProvider.getNewCallId();

		// Create a new Cseq header
		CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.REGISTER);

		// Create a new MaxForwardsHeader
		MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

		// Create the request.
		Request request =
				messageFactory.createRequest(requestURI, Request.REGISTER, callIdHeader, cSeqHeader, fromHeader,
						toHeader, viaHeaders, maxForwards);
		
		// Create contact headers
		SipURI contactUrl = addressFactory.createSipURI(credentials.getUserName(), getLocalIPAddress());
		contactUrl.setPort(8002);
		contactUrl.setLrParam();

		// Create the contact name address.
		SipURI contactURI = addressFactory.createSipURI(credentials.getUserName(), getLocalIPAddress());
		contactURI.setPort(sipProvider.getListeningPoint(lp.getTransport()).getPort());

		Address contactAddress = addressFactory.createAddress(contactURI);

		// Add the contact address.
		contactAddress.setDisplayName(credentials.getDisplayName());

		ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
		contactHeader.setParameter("expires", "3600");
		request.addHeader(contactHeader);

		// Create UserAgentHeader
		ArrayList<String> productList = new ArrayList<String>(1);
		productList.add("SipListenerSample");
		UserAgentHeader userAgentHeader = headerFactory.createUserAgentHeader(productList);		
		request.addHeader(userAgentHeader);

		System.out.println("***********************");
		System.out.println(request.toString());
		// Create the client transaction.
		registerTid = sipProvider.getNewClientTransaction(request);

		// send the request out.
		registerTid.sendRequest();

		dialog = registerTid.getDialog();
	}

	public String getLocalIPAddress()
	{
		return "217.10.64.189";
		/*
		InetAddress internetAddress = null;
		try
		{
			internetAddress = InetAddress.getLocalHost();
		}
		catch (UnknownHostException e)
		{
			// Default to loopback
			return "127.0.0.1";
		}
		StringBuffer address = new StringBuffer();
		byte[] bytes = internetAddress.getAddress();
		for (int j = 0; j < bytes.length; j++)
		{
			int i = bytes[j] < 0 ? bytes[j] + 256 : bytes[j];
			address.append(i);
			if (j < 3)
				address.append('.');
		}

		System.out.println("getLocalIp return: " + address.toString());
		return address.toString();
		 */
	}

	public void processDialogTerminated(DialogTerminatedEvent arg0)
	{
		System.out.println(arg0);
	}

	public void processIOException(IOExceptionEvent arg0)
	{
		System.out.println(arg0);
	}

	public void processRequest(RequestEvent requestReceivedEvent)
	{
        Request request = requestReceivedEvent.getRequest();
        ServerTransaction serverTransactionId = requestReceivedEvent.getServerTransaction();

        System.out.println("Request " + request.getMethod() + " received at "
                + sipStack.getStackName()
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

	public void processResponse(ResponseEvent responseReceivedEvent)
	{	
		System.out.println("Got a response");
		
		Response response = (Response) responseReceivedEvent.getResponse();
		ClientTransaction tid = responseReceivedEvent.getClientTransaction();
		CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);

		System.out.println("Response received : Status Code = " + response.getStatusCode() + " " + cseq);

		if (response.getStatusCode() == Response.UNAUTHORIZED
				|| response.getStatusCode() == Response.PROXY_AUTHENTICATION_REQUIRED)
		{
			AuthenticationHelper authHelper =
					((SipStackExt) sipStack).getAuthenticationHelper(accountManager, headerFactory);
			try
			{
				registerTid = authHelper.handleChallenge(response, tid, sipProvider, -1);
				registerTid.sendRequest();
			}
			catch (SipException e1)
			{
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}

		try
		{
			if (response.getStatusCode() == Response.OK)
			{
				if (cseq.getMethod().equals(Request.REGISTER))
				{
					ContactHeader contactHeader = (ContactHeader) response.getHeader("Contact");
					Integer seconds = contactHeader.getExpires();
					System.out.println("Okay. We are registered for next " + seconds + " seconds.");
				}
				else if (cseq.getMethod().equals(Request.INVITE))
				{
					Dialog dialog = inviteTid.getDialog();
					Request ackRequest = dialog.createAck(cseq.getSeqNumber());
					System.out.println("Sending ACK");
					dialog.sendAck(ackRequest);
				} else if (cseq.getMethod().equals(Request.CANCEL)) {
                    if (dialog.getState() == DialogState.CONFIRMED) {
                        // oops cancel went in too late. Need to hang up the
                        // dialog.
                        System.out.println("Sending BYE -- cancel went in too late !!");
                        Request byeRequest = dialog.createRequest(Request.BYE);
                        ClientTransaction ct = sipProvider.getNewClientTransaction(byeRequest);
                        dialog.sendRequest(ct);
                    }
                }
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public void processTimeout(TimeoutEvent arg0)
	{
		// TODO Auto-generated method stub
		System.out.println(arg0);
	}

	public void processTransactionTerminated(TransactionTerminatedEvent arg0)
	{
        System.out.println("Transaction terminated event recieved");
	}
	
    public void processBye(Request request,
            ServerTransaction serverTransactionId) {
        try {
            System.out.println("got a bye");
            if (serverTransactionId == null) {
                System.out.println("null TID.");
                return;
            }
            Dialog dialog = serverTransactionId.getDialog();
            System.out.println("Dialog State = " + dialog.getState());
            Response response = messageFactory.createResponse(200, request);
            serverTransactionId.sendResponse(response);
            System.out.println("Sending OK.");
            System.out.println("Dialog State = " + dialog.getState());

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
            System.out.println("Got an Invite sending Trying");

            FromHeader from = (FromHeader) request.getHeader("From");
        	System.out.println("Call from: " + from.getAddress().getDisplayName());

        	ContactHeader contact = (ContactHeader) request.getHeader("Contact");
        	System.out.println("Contact: " + contact.getAddress().getDisplayName() );
            
            // System.out.println("shootme: " + request);
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
                    System.out.println("shootme: Dialog state before 404: "
                            + inviteTid.getDialog().getState());
                    inviteTid.sendResponse(okResponse);
                    System.out.println("shootme: Dialog state after 404: "
                            + inviteTid.getDialog().getState());
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
            System.out.println("shootme:  got a cancel.");
            if (serverTransactionId == null) {
                System.out.println("shootme:  null tid.");
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
        System.out.println("shootme: got an ACK! ");
        System.out.println("Dialog State = " + dialog.getState());
    }    
}
