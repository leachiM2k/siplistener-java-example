package com.sipgate.siplistener;

import com.sipgate.siplistener.SipgateSipListener;

public class App
{

	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		try
		{
			SipgateSipListener listener = new SipgateSipListener();
			listener.init();
			listener.register();
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	    
	}

}
