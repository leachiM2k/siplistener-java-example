package com.sipgate.siplistener;

import gov.nist.javax.sip.clientauthutils.UserCredentials;

public class SipgateUserCredentials implements UserCredentials
{
	private String extensionSipId = "1234567e20";
	private String password = "PASSWORD";
	private String domain = "sipgate.de";
	private String displayName = "Michael";
	private String proxy = "proxy.live.sipgate.de:5060";
		
	public String getUserName()
	{
		return extensionSipId;
	}

	public String getPassword()
	{
		return password;
	}

	public String getSipDomain()
	{
		return domain;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public String getProxy()
	{
		return proxy;
	}

}
