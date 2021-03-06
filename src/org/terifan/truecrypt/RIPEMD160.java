package org.terifan.truecrypt;

import java.security.MessageDigest;
import static org.terifan.util.Convert.toHexString;


/**
 * An implementation of the RIPEMD160 Algorithm
 *
 * Implementation from bouncycastle.org
 *
 * Copyright (c) 2000-2006 The Legion Of The Bouncy Castle (http://www.bouncycastle.org)
 */
final class RIPEMD160 extends MessageDigest implements Cloneable
{
    private static final int DIGEST_LENGTH = 20;

	private int H0, H1, H2, H3, H4;
	private int[] X;
	private int xOff;
	private byte[] xBuf;
	private int xBufOff;
	private long byteCount;


    public RIPEMD160()
    {
    	super("RIPEMD-160");

		X = new int[16];
        xBuf = new byte[4];
        xBufOff = 0;

        reset();
    }


//	protected RIPEMD160(RIPEMD160 aBase)
//	{
//		super("RIPEMD160");
//
//		H0 = aBase.H0;
//		H1 = aBase.H1;
//		H2 = aBase.H2;
//		H3 = aBase.H3;
//		H4 = aBase.H4;
//		X = aBase.X.clone();
//		xOff = aBase.xOff;
//		xBuf = aBase.xBuf.clone();
//		xBufOff = aBase.xBufOff;
//		byteCount = aBase.byteCount;
//	}


	@Override
    protected int engineGetDigestLength()
    {
        return DIGEST_LENGTH;
    }


	private void processWord(byte[] in, int inOff)
	{
		X[xOff++] = (in[inOff] & 0xff) | ((in[inOff + 1] & 0xff) << 8) | ((in[inOff + 2] & 0xff) << 16) | ((in[inOff + 3] & 0xff) << 24);

		if (xOff == 16)
		{
			processBlock();
		}
	}

	
	private void processLength(long bitLength)
	{
		if (xOff > 14)
		{
			processBlock();
		}

		X[14] = (int)(bitLength & 0xffffffff);
		X[15] = (int)(bitLength >>> 32);
	}

	
	private void unpackWord(int word, byte[] out, int outOff)
	{
		out[outOff] = (byte)word;
		out[outOff + 1] = (byte)(word >>> 8);
		out[outOff + 2] = (byte)(word >>> 16);
		out[outOff + 3] = (byte)(word >>> 24);
	}


	@Override
	protected int engineDigest(byte[] out, int outOff, int len)
    {
        finish();

        unpackWord(H0, out, outOff);
        unpackWord(H1, out, outOff + 4);
        unpackWord(H2, out, outOff + 8);
        unpackWord(H3, out, outOff + 12);
        unpackWord(H4, out, outOff + 16);

        reset();

        return DIGEST_LENGTH;
    }


	@Override
    protected byte [] engineDigest()
    {
		byte [] buf = new byte[DIGEST_LENGTH];

		engineDigest(buf, 0, buf.length);

		return buf;
    }

	
	@Override
	protected void engineUpdate(byte in)
    {
        xBuf[xBufOff++] = in;

        if (xBufOff == xBuf.length)
        {
            processWord(xBuf, 0);
            xBufOff = 0;
        }

        byteCount++;
    }

	@Override
	protected void engineUpdate(byte[] in, int inOff, int len)
    {
        while ((xBufOff != 0) && (len > 0))
        {
            update(in[inOff]);

            inOff++;
            len--;
        }

        while (len > xBuf.length)
        {
            processWord(in, inOff);

            inOff += xBuf.length;
            len -= xBuf.length;
            byteCount += xBuf.length;
        }

        while (len > 0)
        {
            update(in[inOff]);

            inOff++;
            len--;
        }
    }

	
    private void finish()
    {
		long bitLength = byteCount << 3;

        update((byte)128);

        while (xBufOff != 0)
        {
            update((byte)0);
        }

        processLength(bitLength);

        processBlock();
    }


	@Override
    protected void engineReset()
    {
        byteCount = 0;

        xBufOff = 0;
        for (int i = 0; i < xBuf.length; i++)
        {
            xBuf[i] = 0;
        }

        H0 = 0x67452301;
        H1 = 0xefcdab89;
        H2 = 0x98badcfe;
        H3 = 0x10325476;
        H4 = 0xc3d2e1f0;

        xOff = 0;

        for (int i = 0; i != X.length; i++)
        {
            X[i] = 0;
        }
    }

	
	private int RL(int x, int n)
    {
        return (x << n) | (x >>> (32 - n));
    }

	
	private int f1(int x, int y, int z)
    {
        return x ^ y ^ z;
    }

	
	private int f2(int x, int y, int z)
    {
        return (x & y) | (~x & z);
    }

	
	private int f3(int x, int y, int z)
    {
        return (x | ~y) ^ z;
    }


	private int f4(int x, int y, int z)
    {
        return (x & z) | (y & ~z);
    }


	private int f5(int x, int y, int z)
	{
        return x ^ (y | ~z);
    }

	
    protected void processBlock()
    {
        int a, aa;
        int b, bb;
        int c, cc;
        int d, dd;
        int e, ee;

        a = aa = H0;
        b = bb = H1;
        c = cc = H2;
        d = dd = H3;
        e = ee = H4;

        //
        // Rounds 1 - 16
        //
        // left
        a = RL(a + f1(b,c,d) + X[ 0], 11) + e; c = RL(c, 10);
        e = RL(e + f1(a,b,c) + X[ 1], 14) + d; b = RL(b, 10);
        d = RL(d + f1(e,a,b) + X[ 2], 15) + c; a = RL(a, 10);
        c = RL(c + f1(d,e,a) + X[ 3], 12) + b; e = RL(e, 10);
        b = RL(b + f1(c,d,e) + X[ 4],  5) + a; d = RL(d, 10);
        a = RL(a + f1(b,c,d) + X[ 5],  8) + e; c = RL(c, 10);
        e = RL(e + f1(a,b,c) + X[ 6],  7) + d; b = RL(b, 10);
        d = RL(d + f1(e,a,b) + X[ 7],  9) + c; a = RL(a, 10);
        c = RL(c + f1(d,e,a) + X[ 8], 11) + b; e = RL(e, 10);
        b = RL(b + f1(c,d,e) + X[ 9], 13) + a; d = RL(d, 10);
        a = RL(a + f1(b,c,d) + X[10], 14) + e; c = RL(c, 10);
        e = RL(e + f1(a,b,c) + X[11], 15) + d; b = RL(b, 10);
        d = RL(d + f1(e,a,b) + X[12],  6) + c; a = RL(a, 10);
        c = RL(c + f1(d,e,a) + X[13],  7) + b; e = RL(e, 10);
        b = RL(b + f1(c,d,e) + X[14],  9) + a; d = RL(d, 10);
        a = RL(a + f1(b,c,d) + X[15],  8) + e; c = RL(c, 10);

        // right
        aa = RL(aa + f5(bb,cc,dd) + X[ 5] + 0x50a28be6,  8) + ee; cc = RL(cc, 10);
        ee = RL(ee + f5(aa,bb,cc) + X[14] + 0x50a28be6,  9) + dd; bb = RL(bb, 10);
        dd = RL(dd + f5(ee,aa,bb) + X[ 7] + 0x50a28be6,  9) + cc; aa = RL(aa, 10);
        cc = RL(cc + f5(dd,ee,aa) + X[ 0] + 0x50a28be6, 11) + bb; ee = RL(ee, 10);
        bb = RL(bb + f5(cc,dd,ee) + X[ 9] + 0x50a28be6, 13) + aa; dd = RL(dd, 10);
        aa = RL(aa + f5(bb,cc,dd) + X[ 2] + 0x50a28be6, 15) + ee; cc = RL(cc, 10);
        ee = RL(ee + f5(aa,bb,cc) + X[11] + 0x50a28be6, 15) + dd; bb = RL(bb, 10);
        dd = RL(dd + f5(ee,aa,bb) + X[ 4] + 0x50a28be6,  5) + cc; aa = RL(aa, 10);
        cc = RL(cc + f5(dd,ee,aa) + X[13] + 0x50a28be6,  7) + bb; ee = RL(ee, 10);
        bb = RL(bb + f5(cc,dd,ee) + X[ 6] + 0x50a28be6,  7) + aa; dd = RL(dd, 10);
        aa = RL(aa + f5(bb,cc,dd) + X[15] + 0x50a28be6,  8) + ee; cc = RL(cc, 10);
        ee = RL(ee + f5(aa,bb,cc) + X[ 8] + 0x50a28be6, 11) + dd; bb = RL(bb, 10);
        dd = RL(dd + f5(ee,aa,bb) + X[ 1] + 0x50a28be6, 14) + cc; aa = RL(aa, 10);
        cc = RL(cc + f5(dd,ee,aa) + X[10] + 0x50a28be6, 14) + bb; ee = RL(ee, 10);
        bb = RL(bb + f5(cc,dd,ee) + X[ 3] + 0x50a28be6, 12) + aa; dd = RL(dd, 10);
        aa = RL(aa + f5(bb,cc,dd) + X[12] + 0x50a28be6,  6) + ee; cc = RL(cc, 10);

        //
        // Rounds 16-31
        //
        // left
        e = RL(e + f2(a,b,c) + X[ 7] + 0x5a827999,  7) + d; b = RL(b, 10);
        d = RL(d + f2(e,a,b) + X[ 4] + 0x5a827999,  6) + c; a = RL(a, 10);
        c = RL(c + f2(d,e,a) + X[13] + 0x5a827999,  8) + b; e = RL(e, 10);
        b = RL(b + f2(c,d,e) + X[ 1] + 0x5a827999, 13) + a; d = RL(d, 10);
        a = RL(a + f2(b,c,d) + X[10] + 0x5a827999, 11) + e; c = RL(c, 10);
        e = RL(e + f2(a,b,c) + X[ 6] + 0x5a827999,  9) + d; b = RL(b, 10);
        d = RL(d + f2(e,a,b) + X[15] + 0x5a827999,  7) + c; a = RL(a, 10);
        c = RL(c + f2(d,e,a) + X[ 3] + 0x5a827999, 15) + b; e = RL(e, 10);
        b = RL(b + f2(c,d,e) + X[12] + 0x5a827999,  7) + a; d = RL(d, 10);
        a = RL(a + f2(b,c,d) + X[ 0] + 0x5a827999, 12) + e; c = RL(c, 10);
        e = RL(e + f2(a,b,c) + X[ 9] + 0x5a827999, 15) + d; b = RL(b, 10);
        d = RL(d + f2(e,a,b) + X[ 5] + 0x5a827999,  9) + c; a = RL(a, 10);
        c = RL(c + f2(d,e,a) + X[ 2] + 0x5a827999, 11) + b; e = RL(e, 10);
        b = RL(b + f2(c,d,e) + X[14] + 0x5a827999,  7) + a; d = RL(d, 10);
        a = RL(a + f2(b,c,d) + X[11] + 0x5a827999, 13) + e; c = RL(c, 10);
        e = RL(e + f2(a,b,c) + X[ 8] + 0x5a827999, 12) + d; b = RL(b, 10);

        // right
        ee = RL(ee + f4(aa,bb,cc) + X[ 6] + 0x5c4dd124,  9) + dd; bb = RL(bb, 10);
        dd = RL(dd + f4(ee,aa,bb) + X[11] + 0x5c4dd124, 13) + cc; aa = RL(aa, 10);
        cc = RL(cc + f4(dd,ee,aa) + X[ 3] + 0x5c4dd124, 15) + bb; ee = RL(ee, 10);
        bb = RL(bb + f4(cc,dd,ee) + X[ 7] + 0x5c4dd124,  7) + aa; dd = RL(dd, 10);
        aa = RL(aa + f4(bb,cc,dd) + X[ 0] + 0x5c4dd124, 12) + ee; cc = RL(cc, 10);
        ee = RL(ee + f4(aa,bb,cc) + X[13] + 0x5c4dd124,  8) + dd; bb = RL(bb, 10);
        dd = RL(dd + f4(ee,aa,bb) + X[ 5] + 0x5c4dd124,  9) + cc; aa = RL(aa, 10);
        cc = RL(cc + f4(dd,ee,aa) + X[10] + 0x5c4dd124, 11) + bb; ee = RL(ee, 10);
        bb = RL(bb + f4(cc,dd,ee) + X[14] + 0x5c4dd124,  7) + aa; dd = RL(dd, 10);
        aa = RL(aa + f4(bb,cc,dd) + X[15] + 0x5c4dd124,  7) + ee; cc = RL(cc, 10);
        ee = RL(ee + f4(aa,bb,cc) + X[ 8] + 0x5c4dd124, 12) + dd; bb = RL(bb, 10);
        dd = RL(dd + f4(ee,aa,bb) + X[12] + 0x5c4dd124,  7) + cc; aa = RL(aa, 10);
        cc = RL(cc + f4(dd,ee,aa) + X[ 4] + 0x5c4dd124,  6) + bb; ee = RL(ee, 10);
        bb = RL(bb + f4(cc,dd,ee) + X[ 9] + 0x5c4dd124, 15) + aa; dd = RL(dd, 10);
        aa = RL(aa + f4(bb,cc,dd) + X[ 1] + 0x5c4dd124, 13) + ee; cc = RL(cc, 10);
        ee = RL(ee + f4(aa,bb,cc) + X[ 2] + 0x5c4dd124, 11) + dd; bb = RL(bb, 10);

        //
        // Rounds 32-47
        //
        // left
        d = RL(d + f3(e,a,b) + X[ 3] + 0x6ed9eba1, 11) + c; a = RL(a, 10);
        c = RL(c + f3(d,e,a) + X[10] + 0x6ed9eba1, 13) + b; e = RL(e, 10);
        b = RL(b + f3(c,d,e) + X[14] + 0x6ed9eba1,  6) + a; d = RL(d, 10);
        a = RL(a + f3(b,c,d) + X[ 4] + 0x6ed9eba1,  7) + e; c = RL(c, 10);
        e = RL(e + f3(a,b,c) + X[ 9] + 0x6ed9eba1, 14) + d; b = RL(b, 10);
        d = RL(d + f3(e,a,b) + X[15] + 0x6ed9eba1,  9) + c; a = RL(a, 10);
        c = RL(c + f3(d,e,a) + X[ 8] + 0x6ed9eba1, 13) + b; e = RL(e, 10);
        b = RL(b + f3(c,d,e) + X[ 1] + 0x6ed9eba1, 15) + a; d = RL(d, 10);
        a = RL(a + f3(b,c,d) + X[ 2] + 0x6ed9eba1, 14) + e; c = RL(c, 10);
        e = RL(e + f3(a,b,c) + X[ 7] + 0x6ed9eba1,  8) + d; b = RL(b, 10);
        d = RL(d + f3(e,a,b) + X[ 0] + 0x6ed9eba1, 13) + c; a = RL(a, 10);
        c = RL(c + f3(d,e,a) + X[ 6] + 0x6ed9eba1,  6) + b; e = RL(e, 10);
        b = RL(b + f3(c,d,e) + X[13] + 0x6ed9eba1,  5) + a; d = RL(d, 10);
        a = RL(a + f3(b,c,d) + X[11] + 0x6ed9eba1, 12) + e; c = RL(c, 10);
        e = RL(e + f3(a,b,c) + X[ 5] + 0x6ed9eba1,  7) + d; b = RL(b, 10);
        d = RL(d + f3(e,a,b) + X[12] + 0x6ed9eba1,  5) + c; a = RL(a, 10);

        // right
        dd = RL(dd + f3(ee,aa,bb) + X[15] + 0x6d703ef3,  9) + cc; aa = RL(aa, 10);
        cc = RL(cc + f3(dd,ee,aa) + X[ 5] + 0x6d703ef3,  7) + bb; ee = RL(ee, 10);
        bb = RL(bb + f3(cc,dd,ee) + X[ 1] + 0x6d703ef3, 15) + aa; dd = RL(dd, 10);
        aa = RL(aa + f3(bb,cc,dd) + X[ 3] + 0x6d703ef3, 11) + ee; cc = RL(cc, 10);
        ee = RL(ee + f3(aa,bb,cc) + X[ 7] + 0x6d703ef3,  8) + dd; bb = RL(bb, 10);
        dd = RL(dd + f3(ee,aa,bb) + X[14] + 0x6d703ef3,  6) + cc; aa = RL(aa, 10);
        cc = RL(cc + f3(dd,ee,aa) + X[ 6] + 0x6d703ef3,  6) + bb; ee = RL(ee, 10);
        bb = RL(bb + f3(cc,dd,ee) + X[ 9] + 0x6d703ef3, 14) + aa; dd = RL(dd, 10);
        aa = RL(aa + f3(bb,cc,dd) + X[11] + 0x6d703ef3, 12) + ee; cc = RL(cc, 10);
        ee = RL(ee + f3(aa,bb,cc) + X[ 8] + 0x6d703ef3, 13) + dd; bb = RL(bb, 10);
        dd = RL(dd + f3(ee,aa,bb) + X[12] + 0x6d703ef3,  5) + cc; aa = RL(aa, 10);
        cc = RL(cc + f3(dd,ee,aa) + X[ 2] + 0x6d703ef3, 14) + bb; ee = RL(ee, 10);
        bb = RL(bb + f3(cc,dd,ee) + X[10] + 0x6d703ef3, 13) + aa; dd = RL(dd, 10);
        aa = RL(aa + f3(bb,cc,dd) + X[ 0] + 0x6d703ef3, 13) + ee; cc = RL(cc, 10);
        ee = RL(ee + f3(aa,bb,cc) + X[ 4] + 0x6d703ef3,  7) + dd; bb = RL(bb, 10);
        dd = RL(dd + f3(ee,aa,bb) + X[13] + 0x6d703ef3,  5) + cc; aa = RL(aa, 10);

        //
        // Rounds 48-63
        //
        // left
        c = RL(c + f4(d,e,a) + X[ 1] + 0x8f1bbcdc, 11) + b; e = RL(e, 10);
        b = RL(b + f4(c,d,e) + X[ 9] + 0x8f1bbcdc, 12) + a; d = RL(d, 10);
        a = RL(a + f4(b,c,d) + X[11] + 0x8f1bbcdc, 14) + e; c = RL(c, 10);
        e = RL(e + f4(a,b,c) + X[10] + 0x8f1bbcdc, 15) + d; b = RL(b, 10);
        d = RL(d + f4(e,a,b) + X[ 0] + 0x8f1bbcdc, 14) + c; a = RL(a, 10);
        c = RL(c + f4(d,e,a) + X[ 8] + 0x8f1bbcdc, 15) + b; e = RL(e, 10);
        b = RL(b + f4(c,d,e) + X[12] + 0x8f1bbcdc,  9) + a; d = RL(d, 10);
        a = RL(a + f4(b,c,d) + X[ 4] + 0x8f1bbcdc,  8) + e; c = RL(c, 10);
        e = RL(e + f4(a,b,c) + X[13] + 0x8f1bbcdc,  9) + d; b = RL(b, 10);
        d = RL(d + f4(e,a,b) + X[ 3] + 0x8f1bbcdc, 14) + c; a = RL(a, 10);
        c = RL(c + f4(d,e,a) + X[ 7] + 0x8f1bbcdc,  5) + b; e = RL(e, 10);
        b = RL(b + f4(c,d,e) + X[15] + 0x8f1bbcdc,  6) + a; d = RL(d, 10);
        a = RL(a + f4(b,c,d) + X[14] + 0x8f1bbcdc,  8) + e; c = RL(c, 10);
        e = RL(e + f4(a,b,c) + X[ 5] + 0x8f1bbcdc,  6) + d; b = RL(b, 10);
        d = RL(d + f4(e,a,b) + X[ 6] + 0x8f1bbcdc,  5) + c; a = RL(a, 10);
        c = RL(c + f4(d,e,a) + X[ 2] + 0x8f1bbcdc, 12) + b; e = RL(e, 10);

        // right
        cc = RL(cc + f2(dd,ee,aa) + X[ 8] + 0x7a6d76e9, 15) + bb; ee = RL(ee, 10);
        bb = RL(bb + f2(cc,dd,ee) + X[ 6] + 0x7a6d76e9,  5) + aa; dd = RL(dd, 10);
        aa = RL(aa + f2(bb,cc,dd) + X[ 4] + 0x7a6d76e9,  8) + ee; cc = RL(cc, 10);
        ee = RL(ee + f2(aa,bb,cc) + X[ 1] + 0x7a6d76e9, 11) + dd; bb = RL(bb, 10);
        dd = RL(dd + f2(ee,aa,bb) + X[ 3] + 0x7a6d76e9, 14) + cc; aa = RL(aa, 10);
        cc = RL(cc + f2(dd,ee,aa) + X[11] + 0x7a6d76e9, 14) + bb; ee = RL(ee, 10);
        bb = RL(bb + f2(cc,dd,ee) + X[15] + 0x7a6d76e9,  6) + aa; dd = RL(dd, 10);
        aa = RL(aa + f2(bb,cc,dd) + X[ 0] + 0x7a6d76e9, 14) + ee; cc = RL(cc, 10);
        ee = RL(ee + f2(aa,bb,cc) + X[ 5] + 0x7a6d76e9,  6) + dd; bb = RL(bb, 10);
        dd = RL(dd + f2(ee,aa,bb) + X[12] + 0x7a6d76e9,  9) + cc; aa = RL(aa, 10);
        cc = RL(cc + f2(dd,ee,aa) + X[ 2] + 0x7a6d76e9, 12) + bb; ee = RL(ee, 10);
        bb = RL(bb + f2(cc,dd,ee) + X[13] + 0x7a6d76e9,  9) + aa; dd = RL(dd, 10);
        aa = RL(aa + f2(bb,cc,dd) + X[ 9] + 0x7a6d76e9, 12) + ee; cc = RL(cc, 10);
        ee = RL(ee + f2(aa,bb,cc) + X[ 7] + 0x7a6d76e9,  5) + dd; bb = RL(bb, 10);
        dd = RL(dd + f2(ee,aa,bb) + X[10] + 0x7a6d76e9, 15) + cc; aa = RL(aa, 10);
        cc = RL(cc + f2(dd,ee,aa) + X[14] + 0x7a6d76e9,  8) + bb; ee = RL(ee, 10);

        //
        // Rounds 64-79
        //
        // left
        b = RL(b + f5(c,d,e) + X[ 4] + 0xa953fd4e,  9) + a; d = RL(d, 10);
        a = RL(a + f5(b,c,d) + X[ 0] + 0xa953fd4e, 15) + e; c = RL(c, 10);
        e = RL(e + f5(a,b,c) + X[ 5] + 0xa953fd4e,  5) + d; b = RL(b, 10);
        d = RL(d + f5(e,a,b) + X[ 9] + 0xa953fd4e, 11) + c; a = RL(a, 10);
        c = RL(c + f5(d,e,a) + X[ 7] + 0xa953fd4e,  6) + b; e = RL(e, 10);
        b = RL(b + f5(c,d,e) + X[12] + 0xa953fd4e,  8) + a; d = RL(d, 10);
        a = RL(a + f5(b,c,d) + X[ 2] + 0xa953fd4e, 13) + e; c = RL(c, 10);
        e = RL(e + f5(a,b,c) + X[10] + 0xa953fd4e, 12) + d; b = RL(b, 10);
        d = RL(d + f5(e,a,b) + X[14] + 0xa953fd4e,  5) + c; a = RL(a, 10);
        c = RL(c + f5(d,e,a) + X[ 1] + 0xa953fd4e, 12) + b; e = RL(e, 10);
        b = RL(b + f5(c,d,e) + X[ 3] + 0xa953fd4e, 13) + a; d = RL(d, 10);
        a = RL(a + f5(b,c,d) + X[ 8] + 0xa953fd4e, 14) + e; c = RL(c, 10);
        e = RL(e + f5(a,b,c) + X[11] + 0xa953fd4e, 11) + d; b = RL(b, 10);
        d = RL(d + f5(e,a,b) + X[ 6] + 0xa953fd4e,  8) + c; a = RL(a, 10);
        c = RL(c + f5(d,e,a) + X[15] + 0xa953fd4e,  5) + b; e = RL(e, 10);
        b = RL(b + f5(c,d,e) + X[13] + 0xa953fd4e,  6) + a; d = RL(d, 10);

        // right
        bb = RL(bb + f1(cc,dd,ee) + X[12],  8) + aa; dd = RL(dd, 10);
        aa = RL(aa + f1(bb,cc,dd) + X[15],  5) + ee; cc = RL(cc, 10);
        ee = RL(ee + f1(aa,bb,cc) + X[10], 12) + dd; bb = RL(bb, 10);
        dd = RL(dd + f1(ee,aa,bb) + X[ 4],  9) + cc; aa = RL(aa, 10);
        cc = RL(cc + f1(dd,ee,aa) + X[ 1], 12) + bb; ee = RL(ee, 10);
        bb = RL(bb + f1(cc,dd,ee) + X[ 5],  5) + aa; dd = RL(dd, 10);
        aa = RL(aa + f1(bb,cc,dd) + X[ 8], 14) + ee; cc = RL(cc, 10);
        ee = RL(ee + f1(aa,bb,cc) + X[ 7],  6) + dd; bb = RL(bb, 10);
        dd = RL(dd + f1(ee,aa,bb) + X[ 6],  8) + cc; aa = RL(aa, 10);
        cc = RL(cc + f1(dd,ee,aa) + X[ 2], 13) + bb; ee = RL(ee, 10);
        bb = RL(bb + f1(cc,dd,ee) + X[13],  6) + aa; dd = RL(dd, 10);
        aa = RL(aa + f1(bb,cc,dd) + X[14],  5) + ee; cc = RL(cc, 10);
        ee = RL(ee + f1(aa,bb,cc) + X[ 0], 15) + dd; bb = RL(bb, 10);
        dd = RL(dd + f1(ee,aa,bb) + X[ 3], 13) + cc; aa = RL(aa, 10);
        cc = RL(cc + f1(dd,ee,aa) + X[ 9], 11) + bb; ee = RL(ee, 10);
        bb = RL(bb + f1(cc,dd,ee) + X[11], 11) + aa; dd = RL(dd, 10);

        dd += c + H1;
        H1 = H2 + d + ee;
        H2 = H3 + e + aa;
        H3 = H4 + a + bb;
        H4 = H0 + b + cc;
        H0 = dd;

        //
        // reset the offset and clean out the word buffer.
        //
        xOff = 0;
        for (int i = 0; i != X.length; i++)
        {
            X[i] = 0;
        }
    }


	@Override
	public Object clone() throws CloneNotSupportedException
	{
		RIPEMD160 instance = new RIPEMD160();
		
		instance.H0 = this.H0;
		instance.H1 = this.H1;
		instance.H2 = this.H2;
		instance.H3 = this.H3;
		instance.H4 = this.H4;
		instance.X = this.X.clone();
		instance.byteCount = this.byteCount;
		instance.xBuf = this.xBuf.clone();
		instance.xBufOff = this.xBufOff;
		instance.xOff = this.xOff;

		return instance;
	}


	public static boolean selftest()
	{
		MessageDigest md = new RIPEMD160();
		boolean ok = true;

		ok &= "9C1185A5C5E9FC54612808977EE8F548B2258D31".equals(toHexString(md.digest("".getBytes())));
		ok &= "0BDC9D2D256B3EE9DAAE347BE6F4DC835A467FFE".equals(toHexString(md.digest("a".getBytes())));
		ok &= "8EB208F7E05D987A9B044A8E98C6B087F15A0BFC".equals(toHexString(md.digest("abc".getBytes())));
		ok &= "5D0689EF49D2FAE572B881B123A85FFA21595F36".equals(toHexString(md.digest("message digest".getBytes())));
		ok &= "F71C27109C692C1B56BBDCEB5B9D2865B3708DBC".equals(toHexString(md.digest("abcdefghijklmnopqrstuvwxyz".getBytes())));
		ok &= "12A053384A9C0C88E405A06C27DCF49ADA62EB2B".equals(toHexString(md.digest("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".getBytes())));
		ok &= "B0E20B6E3116640286ED3A87A5713079B21F5189".equals(toHexString(md.digest("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".getBytes())));
		ok &= "9B752E45573D4B39F4DBD3323CAB82BF63326BFB".equals(toHexString(md.digest("12345678901234567890123456789012345678901234567890123456789012345678901234567890".getBytes())));

		for (int i = 0; i < 1000000; i++)
		{
			md.update((byte)'a');
		}

		ok &= "52783243C1697BDBE16D37F97F68F08325DC1528".equals(toHexString(md.digest()));

		return ok;
	}
}