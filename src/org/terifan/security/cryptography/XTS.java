package org.terifan.security.cryptography;


/**
 * This is an implementation of the XTS cipher mode. Source code is ported from
 * TrueCrypt 7.0 (http://www.truecrypt.org).
 *
 * XTS mode was approved as the IEEE 1619 standard for cryptographic protection
 * of data on block-oriented storage devices in December 2007.
 */
class XTS
{
	private final static int BYTES_PER_XTS_BLOCK = 16;

	private final transient int mBlocksPerUnit;


	public XTS(int aUnitSize)
	{
		if (aUnitSize != 512 && aUnitSize != 4096)
		{
			throw new IllegalArgumentException("Unit size must be 512 or 4096.");
		}

		mBlocksPerUnit = aUnitSize / BYTES_PER_XTS_BLOCK;
	}


	/**
	 * Encrypts a buffer using the XTS cipher mode and the provided Ciphers.
	 *
	 * @param aBuffer
	 *   the buffer to encrypt
	 * @param aOffset
	 *   the start offset in the buffer
	 * @param aLength
	 *   number of bytes to encrypt; must be divisible by 16
	 * @param aStartDataUnitNo
	 *   the sequential number of the data unit with which the buffer starts.
	 *   Each data unit is 512 bytes in length.
	 * @param aCipher
	 *   the primary key schedule
	 * @param aTweakCipher
	 *   the secondary key schedule
	 */
	public void encrypt(byte [] aBuffer, int aOffset, int aLength, long aStartDataUnitNo, Cipher aCipher, Cipher aTweakCipher)
	{
		if ((aLength & 15) != 0)
		{
			throw new IllegalArgumentException("Provided argument 'aLength' is not a multiple of 16.");
		}

		int finalCarry;
		byte [] whiteningValue = new byte[BYTES_PER_XTS_BLOCK];

		/* The encrypted data unit number (i.e. the resultant ciphertext block) is to be multiplied in the
		finite field GF(2^128) by j-th power of n, where j is the sequential plaintext/ciphertext block
		number and n is 2, a primitive element of GF(2^128). This can be (and is) simplified and implemented
		as a left shift of the preceding whitening value by one bit (with carry propagating). In addition, if
		the shift of the highest byte results in a carry, 135 is XORed into the lowest byte. The value 135 is
		derived from the modulus of the Galois Field (x^128+x^7+x^2+x+1). */

		// Convert the 64-bit data unit number into a little-endian 16-byte array.
		// Note that as we are converting a 64-bit number into a 16-byte array we can always zero the last 8 bytes.
		long dataUnitNo = aStartDataUnitNo;

		int blocksRemaining = aLength / BYTES_PER_XTS_BLOCK;

		// Process all blocks in the buffer
		while (blocksRemaining > 0)
		{
			int endBlock;
			if (blocksRemaining < mBlocksPerUnit)
			{
				endBlock = blocksRemaining;
			}
			else
			{
				endBlock = mBlocksPerUnit;
			}

			// Encrypt the data unit number using the secondary key (in order to generate the first
			// whitening value for this data unit)
			putLong(whiteningValue, 0, dataUnitNo);
			putLong(whiteningValue, 8, 0);

			encipherBlock(whiteningValue, 0, aTweakCipher);

			// Generate (and apply) subsequent whitening values for blocks in this data unit and
			// encrypt all relevant blocks in this data unit
			for (int block = 0; block < endBlock; block++)
			{
				// Pre-whitening
				xor(aBuffer, aOffset, whiteningValue, 0);

				// Actual encryption
				encipherBlock(aBuffer, aOffset, aCipher);

				// Post-whitening
				xor(aBuffer, aOffset, whiteningValue, 0);
				aOffset += BYTES_PER_XTS_BLOCK;

				// Derive the next whitening value

				finalCarry = ((whiteningValue[8+7] & 0x80) != 0) ? 135 : 0;

				putLong(whiteningValue, 8, getLong(whiteningValue, 8) << 1);

				if ((whiteningValue[7] & 0x80) != 0)
				{
					whiteningValue[8] |= 0x01;
				}

				putLong(whiteningValue, 0, getLong(whiteningValue, 0) << 1);

				whiteningValue[0] ^= finalCarry;
			}

			blocksRemaining -= endBlock;
			dataUnitNo++;
		}
	}


	/**
	 * Decrypts a buffer using the XTS cipher mode and the provided Ciphers.
	 *
	 * @param aBuffer
	 *   the buffer to decrypt
	 * @param aOffset
	 *   the start offset in the buffer
	 * @param aLength
	 *   number of bytes to decrypt; must be divisible by 16
	 * @param aStartDataUnitNo
	 *   the sequential number of the data unit with which the buffer starts.
	 *   Each data unit is 512 bytes in length.
	 * @param aCipher
	 *   the primary key schedule
	 * @param aTweakCipher
	 *   the secondary key schedule
	 */
	public void decrypt(byte [] aBuffer, int aOffset, int aLength, long aStartDataUnitNo, Cipher aCipher, Cipher aTweakCipher)
	{
		if ((aLength & 15) != 0)
		{
			throw new IllegalArgumentException("Provided argument 'aLength' is not a multiple of 16.");
		}

		byte [] whiteningValue = new byte[BYTES_PER_XTS_BLOCK];

		long dataUnitNo = aStartDataUnitNo;

		int blocksRemaining = aLength / BYTES_PER_XTS_BLOCK;

		// Process all blocks in the buffer
		while (blocksRemaining > 0)
		{
			int endBlock;
			if (blocksRemaining < mBlocksPerUnit)
			{
				endBlock = blocksRemaining;
			}
			else
			{
				endBlock = mBlocksPerUnit;
			}

			// Encrypt the data unit number using the secondary key (in order to generate the first
			// whitening value for this data unit)
			putLong(whiteningValue, 0, dataUnitNo);
			putLong(whiteningValue, 8, 0);

			encipherBlock(whiteningValue, 0, aTweakCipher);

			// Generate (and apply) subsequent whitening values for blocks in this data unit and
			// decrypt all relevant blocks in this data unit
			for (int block = 0; block < endBlock; block++)
			{
				// Post-whitening
				xor(aBuffer, aOffset, whiteningValue, 0);

				// Actual decryption
				decipherBlock(aBuffer, aOffset, aCipher);

				// Pre-whitening
				xor(aBuffer, aOffset, whiteningValue, 0);
				aOffset += BYTES_PER_XTS_BLOCK;

				// Derive the next whitening value

				int finalCarry = (whiteningValue[8+7] & 0x80) != 0 ? 135 : 0;

				putLong(whiteningValue, 8, getLong(whiteningValue, 8) << 1);

				if ((whiteningValue[7] & 0x80) != 0)
				{
					whiteningValue[8] |= 0x01;
				}

				putLong(whiteningValue, 0, getLong(whiteningValue, 0) << 1);

				whiteningValue[0] ^= finalCarry;
			}

			blocksRemaining -= endBlock;
			dataUnitNo++;
		}
	}


	private static void xor(byte [] aBuffer, int aOffset, byte [] aMask, int aMaskOffset)
	{
		for (int i = 0; i < 16; i++)
		{
			aBuffer[aOffset+i] ^= aMask[aMaskOffset+i];
		}
	}


	private static void encipherBlock(byte [] aBuffer, int aOffset, Cipher aCipher)
	{
		aCipher.engineEncryptBlock(aBuffer, aOffset, aBuffer, aOffset);
	}


	private static void decipherBlock(byte [] aBuffer, int aOffset, Cipher aCipher)
	{
		aCipher.engineDecryptBlock(aBuffer, aOffset, aBuffer, aOffset);
	}


	private static void putLong(byte [] aBuffer, int aOffset, long aValue)
	{
		aBuffer[aOffset++] = (byte)(aValue       );
		aBuffer[aOffset++] = (byte)(aValue >>   8);
		aBuffer[aOffset++] = (byte)(aValue >>  16);
		aBuffer[aOffset++] = (byte)(aValue >>  24);
		aBuffer[aOffset++] = (byte)(aValue >>  32);
		aBuffer[aOffset++] = (byte)(aValue >>  40);
		aBuffer[aOffset++] = (byte)(aValue >>  48);
		aBuffer[aOffset  ] = (byte)(aValue >>> 56);
	}


	private static long getLong(byte [] aBuffer, int aOffset)
	{
		return  (      (255 & aBuffer[aOffset++])      )
			  + (      (255 & aBuffer[aOffset++]) <<  8)
			  + (      (255 & aBuffer[aOffset++]) << 16)
			  + ((long)(255 & aBuffer[aOffset++]) << 24)
			  + ((long)(255 & aBuffer[aOffset++]) << 32)
			  + ((long)(255 & aBuffer[aOffset++]) << 40)
			  + ((long)(255 & aBuffer[aOffset++]) << 48)
			  + ((long)(255 & aBuffer[aOffset  ]) << 56);
	}
}