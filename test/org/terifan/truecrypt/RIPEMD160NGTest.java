package org.terifan.truecrypt;

import java.security.MessageDigest;
import static org.terifan.truecrypt.__Convert.toHexString;
import static org.testng.Assert.*;
import org.testng.annotations.Test;


public class RIPEMD160NGTest
{
	@Test
	public void test1()
	{
		assertEquals("9C1185A5C5E9FC54612808977EE8F548B2258D31", toHexString(new RIPEMD160().digest("".getBytes())));
	}


	@Test
	public void test2()
	{
		assertEquals("0BDC9D2D256B3EE9DAAE347BE6F4DC835A467FFE", toHexString(new RIPEMD160().digest("a".getBytes())));
	}


	@Test
	public void test3()
	{
		assertEquals("8EB208F7E05D987A9B044A8E98C6B087F15A0BFC", toHexString(new RIPEMD160().digest("abc".getBytes())));
	}


	@Test
	public void test4()
	{
		assertEquals("5D0689EF49D2FAE572B881B123A85FFA21595F36", toHexString(new RIPEMD160().digest("message digest".getBytes())));
	}


	@Test
	public void test5()
	{
		assertEquals("F71C27109C692C1B56BBDCEB5B9D2865B3708DBC", toHexString(new RIPEMD160().digest("abcdefghijklmnopqrstuvwxyz".getBytes())));
	}


	@Test
	public void test6()
	{
		assertEquals("12A053384A9C0C88E405A06C27DCF49ADA62EB2B", toHexString(new RIPEMD160().digest("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".getBytes())));
	}


	@Test
	public void test7()
	{
		assertEquals("B0E20B6E3116640286ED3A87A5713079B21F5189", toHexString(new RIPEMD160().digest("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".getBytes())));
	}


	@Test
	public void test8()
	{
		assertEquals("9B752E45573D4B39F4DBD3323CAB82BF63326BFB", toHexString(new RIPEMD160().digest("12345678901234567890123456789012345678901234567890123456789012345678901234567890".getBytes())));
	}


	@Test
	public void test9()
	{
		MessageDigest md = new RIPEMD160();
		for (int i = 0; i < 1000000; i++)
		{
			md.update((byte)'a');
		}

		assertEquals("52783243C1697BDBE16D37F97F68F08325DC1528", toHexString(md.digest()));
	}
}
