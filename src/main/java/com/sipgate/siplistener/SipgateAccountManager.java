package com.sipgate.siplistener;

import javax.sip.ClientTransaction;

import gov.nist.javax.sip.clientauthutils.AccountManager;
import gov.nist.javax.sip.clientauthutils.UserCredentials;

public class SipgateAccountManager implements AccountManager
{

	public UserCredentials getCredentials(ClientTransaction challengedTransaction, String realm)
	{
		return new SipgateUserCredentials();
	}

}
