package org.terifan.truecrypt;


public class InvalidKeyException extends RuntimeException
{
	private static final long serialVersionUID = 1L;


	public InvalidKeyException()
	{
	}


	public InvalidKeyException(String aMessage)
	{
		super(aMessage);
	}
}
