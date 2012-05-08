package com.sipgate.siplistener;

import gov.nist.javax.sip.SipStackExt;
import gov.nist.javax.sip.clientauthutils.AccountManager;
import gov.nist.javax.sip.clientauthutils.AuthenticationHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogState;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.PeerUnavailableException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.TransactionUnavailableException;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.UserAgentHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.log4j.Logger;

import com.sipgate.siplistener.processor.RequestProcessor;

public class SipgateSipListener implements SipListener
{
	private SipStack sipStack;
	private SipProvider sipProvider;
	private ClientTransaction registerTid;
	private HeaderFactory headerFactory;
	private AddressFactory addressFactory;
	private MessageFactory messageFactory;
	private Dialog dialog;
	
	private ListeningPoint lp;
	private AccountManager accountManager = new SipgateAccountManager();
	private SipgateUserCredentials credentials;
	private ServerTransaction inviteTid;
	
	private String localIPAddress;
	private RequestProcessor requestProcessor;
	
	private final static Logger log = Logger.getLogger( SipgateSipListener.class ); 

	class MyTimerTask extends TimerTask
	{
		SipgateSipListener listener;

		public MyTimerTask(SipgateSipListener listener)
		{
			this.listener = listener;

		}

		public void run()
		{
			log.debug("Reinvite please");
			try
			{
				listener.register();
			}
			catch (Exception e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}
	
	public SipgateSipListener() throws PeerUnavailableException
	{
		requestProcessor = new RequestProcessor();
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

		// Create the client transaction.
		registerTid = sipProvider.getNewClientTransaction(request);

		// send the request out.
		registerTid.sendRequest();

		dialog = registerTid.getDialog();
	}

	public String getLocalIPAddress()
	{
		if(localIPAddress != null)
		{
			return localIPAddress;
		}
		
		InetAddress internetAddress = null;
		try
		{
			internetAddress = InetAddress.getLocalHost();
		}
		catch (UnknownHostException e)
		{
			// Default to loopback
			e.printStackTrace();
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

		localIPAddress = address.toString();
		
		if(internetAddress.isSiteLocalAddress() || internetAddress.isLoopbackAddress())
		{
			try
			{
				localIPAddress = getIPFromWhatismyip();
			}
			catch (Exception e)
			{
				e.printStackTrace();
				localIPAddress = "127.0.0.1";
			}			
		}
		
		log.debug("getLocalIp return: " + localIPAddress);
		return localIPAddress;
	}

	protected String getIPFromWhatismyip() throws MalformedURLException, IOException
	{
		URL whatismyip = new URL("http://automation.whatismyip.com/n09230945.asp");
		URLConnection connection = whatismyip.openConnection();
		connection.addRequestProperty("Protocol", "Http/1.1");
		connection.addRequestProperty("Connection", "keep-alive");
		connection.addRequestProperty("Keep-Alive", "1000");
		connection.addRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:12.0) Gecko/20100101 Firefox/12.0");

		BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

		String ip = in.readLine(); // you get the IP as a String
		return ip;
	}

	public void processRequest(RequestEvent requestReceivedEvent)
	{
		try {
			requestProcessor.process(requestReceivedEvent, dialog);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	public void processResponse(ResponseEvent responseReceivedEvent)
	{	
		log.debug("Got a response. Code: " + responseReceivedEvent.getResponse().getStatusCode() + " / CSeq: "
				+ responseReceivedEvent.getResponse().getHeader(CSeqHeader.NAME));
		
		Response response = (Response) responseReceivedEvent.getResponse();
		ClientTransaction tid = responseReceivedEvent.getClientTransaction();
		CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);

		log.debug("Response received : Status Code = " + response.getStatusCode() + " " + cseq);

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
					Long seconds = (long) contactHeader.getExpires();
					Long delay = (seconds * 1000) - 100;
					new Timer().schedule(new MyTimerTask(this), delay);
					log.debug("Okay. We are registered for next " + seconds + " seconds.");
				}
				else if (cseq.getMethod().equals(Request.INVITE))
				{
					Dialog dialog = inviteTid.getDialog();
					Request ackRequest = dialog.createAck(cseq.getSeqNumber());
					log.debug("Sending ACK");
					dialog.sendAck(ackRequest);
				} else if (cseq.getMethod().equals(Request.CANCEL)) {
                    if (dialog.getState() == DialogState.CONFIRMED) {
                        // oops cancel went in too late. Need to hang up the
                        // dialog.
                        log.debug("Sending BYE -- cancel went in too late !!");
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
        log.debug("Process event recieved.");
	}

	public void processTransactionTerminated(TransactionTerminatedEvent arg0)
	{
        log.debug("Transaction terminated event recieved.");
	}

	public void processDialogTerminated(DialogTerminatedEvent arg0)
	{
        log.debug("Dialog terminated event recieved.");
	}

	public void processIOException(IOExceptionEvent arg0)
	{
        log.debug("IO Exception event recieved.");
	}	
}
