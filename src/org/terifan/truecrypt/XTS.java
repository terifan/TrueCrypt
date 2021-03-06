package org.terifan.truecrypt;

import org.terifan.util.Convert;



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


	public static boolean selftest()
	{
		try
		{
			// http://grouper.ieee.org/groups/1619/email/msg01574.html

			test("917cf69ebd68b2ec9b9fe9a3eadda692cd43d2f59598ed858c02c2652fbf922e",
				 "0000000000000000000000000000000000000000000000000000000000000000",
				 0,
				 "00000000000000000000000000000000",
				 "00000000000000000000000000000000");

			test("c454185e6a16936e39334038acef838bfb186fff7480adc4289382ecd6d394f0",
				 "4444444444444444444444444444444444444444444444444444444444444444",
				 0x3333333333L,
				 "11111111111111111111111111111111",
				 "22222222222222222222222222222222");

			test("af85336b597afc1a900b2eb21ec949d292df4c047e0b21532186a5971a227a89",
				 "4444444444444444444444444444444444444444444444444444444444444444",
				 0x3333333333L,
				 "fffefdfcfbfaf9f8f7f6f5f4f3f2f1f0",
				 "22222222222222222222222222222222");

			test("27a7479befa1d476489f308cd4cfa6e2a96e4bbe3208ff25287dd3819616e89c" +
				 "c78cf7f5e543445f8333d8fa7f56000005279fa5d8b5e4ad40e736ddb4d35412" +
				 "328063fd2aab53e5ea1e0a9f332500a5df9487d07a5c92cc512c8866c7e860ce" +
				 "93fdf166a24912b422976146ae20ce846bb7dc9ba94a767aaef20c0d61ad0265" +
				 "5ea92dc4c4e41a8952c651d33174be51a10c421110e6d81588ede82103a252d8" +
				 "a750e8768defffed9122810aaeb99f9172af82b604dc4b8e51bcb08235a6f434" +
				 "1332e4ca60482a4ba1a03b3e65008fc5da76b70bf1690db4eae29c5f1badd03c" +
				 "5ccf2a55d705ddcd86d449511ceb7ec30bf12b1fa35b913f9f747a8afd1b130e" +
				 "94bff94effd01a91735ca1726acd0b197c4e5b03393697e126826fb6bbde8ecc" +
				 "1e08298516e2c9ed03ff3c1b7860f6de76d4cecd94c8119855ef5297ca67e9f3" +
				 "e7ff72b1e99785ca0a7e7720c5b36dc6d72cac9574c8cbbc2f801e23e56fd344" +
				 "b07f22154beba0f08ce8891e643ed995c94d9a69c9f1b5f499027a78572aeebd" +
				 "74d20cc39881c213ee770b1010e4bea718846977ae119f7a023ab58cca0ad752" +
				 "afe656bb3c17256a9f6e9bf19fdd5a38fc82bbe872c5539edb609ef4f79c203e" +
				 "bb140f2e583cb2ad15b4aa5b655016a8449277dbd477ef2c8d6c017db738b18d" +
				 "eb4a427d1923ce3ff262735779a418f20a282df920147beabe421ee5319d0568",
				 "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" +
				 "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f" +
				 "404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f" +
				 "606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f" +
				 "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f" +
				 "a0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf" +
				 "c0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedf" +
				 "e0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff" +
				 "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" +
				 "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f" +
				 "404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f" +
				 "606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f" +
				 "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f" +
				 "a0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf" +
				 "c0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedf" +
				 "e0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff",
				 0,
				 "27182818284590452353602874713526",
				 "31415926535897932384626433832795");

			test("264d3ca8512194fec312c8c9891f279fefdd608d0c027b60483a3fa811d65ee5" +
				 "9d52d9e40ec5672d81532b38b6b089ce951f0f9c35590b8b978d175213f329bb" +
				 "1c2fd30f2f7f30492a61a532a79f51d36f5e31a7c9a12c286082ff7d2394d18f" +
				 "783e1a8e72c722caaaa52d8f065657d2631fd25bfd8e5baad6e527d763517501" +
				 "c68c5edc3cdd55435c532d7125c8614deed9adaa3acade5888b87bef641c4c99" +
				 "4c8091b5bcd387f3963fb5bc37aa922fbfe3df4e5b915e6eb514717bdd2a7407" +
				 "9a5073f5c4bfd46adf7d282e7a393a52579d11a028da4d9cd9c77124f9648ee3" +
				 "83b1ac763930e7162a8d37f350b2f74b8472cf09902063c6b32e8c2d9290cefb" +
				 "d7346d1c779a0df50edcde4531da07b099c638e83a755944df2aef1aa31752fd" +
				 "323dcb710fb4bfbb9d22b925bc3577e1b8949e729a90bbafeacf7f7879e7b114" +
				 "7e28ba0bae940db795a61b15ecf4df8db07b824bb062802cc98a9545bb2aaeed" +
				 "77cb3fc6db15dcd7d80d7d5bc406c4970a3478ada8899b329198eb61c193fb62" +
				 "75aa8ca340344a75a862aebe92eee1ce032fd950b47d7704a3876923b4ad6284" +
				 "4bf4a09c4dbe8b4397184b7471360c9564880aedddb9baa4af2e75394b08cd32" +
				 "ff479c57a07d3eab5d54de5f9738b8d27f27a9f0ab11799d7b7ffefb2704c95c" +
				 "6ad12c39f1e867a4b7b1d7818a4b753dfd2a89ccb45e001a03a867b187f225dd",
				 "27a7479befa1d476489f308cd4cfa6e2a96e4bbe3208ff25287dd3819616e89c" +
				 "c78cf7f5e543445f8333d8fa7f56000005279fa5d8b5e4ad40e736ddb4d35412" +
				 "328063fd2aab53e5ea1e0a9f332500a5df9487d07a5c92cc512c8866c7e860ce" +
				 "93fdf166a24912b422976146ae20ce846bb7dc9ba94a767aaef20c0d61ad0265" +
				 "5ea92dc4c4e41a8952c651d33174be51a10c421110e6d81588ede82103a252d8" +
				 "a750e8768defffed9122810aaeb99f9172af82b604dc4b8e51bcb08235a6f434" +
				 "1332e4ca60482a4ba1a03b3e65008fc5da76b70bf1690db4eae29c5f1badd03c" +
				 "5ccf2a55d705ddcd86d449511ceb7ec30bf12b1fa35b913f9f747a8afd1b130e" +
				 "94bff94effd01a91735ca1726acd0b197c4e5b03393697e126826fb6bbde8ecc" +
				 "1e08298516e2c9ed03ff3c1b7860f6de76d4cecd94c8119855ef5297ca67e9f3" +
				 "e7ff72b1e99785ca0a7e7720c5b36dc6d72cac9574c8cbbc2f801e23e56fd344" +
				 "b07f22154beba0f08ce8891e643ed995c94d9a69c9f1b5f499027a78572aeebd" +
				 "74d20cc39881c213ee770b1010e4bea718846977ae119f7a023ab58cca0ad752" +
				 "afe656bb3c17256a9f6e9bf19fdd5a38fc82bbe872c5539edb609ef4f79c203e" +
				 "bb140f2e583cb2ad15b4aa5b655016a8449277dbd477ef2c8d6c017db738b18d" +
				 "eb4a427d1923ce3ff262735779a418f20a282df920147beabe421ee5319d0568",
				 1,
				 "27182818284590452353602874713526",
				 "31415926535897932384626433832795");

			test("fa762a3680b76007928ed4a4f49a9456031b704782e65e16cecb54ed7d017b5e" +
				 "18abd67b338e81078f21edb7868d901ebe9c731a7c18b5e6dec1d6a72e078ac9" +
				 "a4262f860beefa14f4e821018272e411a951502b6e79066e84252c3346f3aa62" +
				 "344351a291d4bedc7a07618bdea2af63145cc7a4b8d4070691ae890cd65733e7" +
				 "946e9021a1dffc4c59f159425ee6d50ca9b135fa6162cea18a939838dc000fb3" +
				 "86fad086acce5ac07cb2ece7fd580b00cfa5e98589631dc25e8e2a3daf2ffdec" +
				 "26531659912c9d8f7a15e5865ea8fb5816d6207052bd7128cd743c12c8118791" +
				 "a4736811935eb982a532349e31dd401e0b660a568cb1a4711f552f55ded59f1f" +
				 "15bf7196b3ca12a91e488ef59d64f3a02bf45239499ac6176ae321c4a211ec54" +
				 "5365971c5d3f4f09d4eb139bfdf2073d33180b21002b65cc9865e76cb24cd92c" +
				 "874c24c18350399a936ab3637079295d76c417776b94efce3a0ef7206b151105" +
				 "19655c956cbd8b2489405ee2b09a6b6eebe0c53790a12a8998378b33a5b71159" +
				 "625f4ba49d2a2fdba59fbf0897bc7aabd8d707dc140a80f0f309f835d3da54ab" +
				 "584e501dfa0ee977fec543f74186a802b9a37adb3e8291eca04d66520d229e60" +
				 "401e7282bef486ae059aa70696e0e305d777140a7a883ecdcb69b9ff938e8a42" +
				 "31864c69ca2c2043bed007ff3e605e014bcf518138dc3a25c5e236171a2d01d6",
				 "264d3ca8512194fec312c8c9891f279fefdd608d0c027b60483a3fa811d65ee5" +
				 "9d52d9e40ec5672d81532b38b6b089ce951f0f9c35590b8b978d175213f329bb" +
				 "1c2fd30f2f7f30492a61a532a79f51d36f5e31a7c9a12c286082ff7d2394d18f" +
				 "783e1a8e72c722caaaa52d8f065657d2631fd25bfd8e5baad6e527d763517501" +
				 "c68c5edc3cdd55435c532d7125c8614deed9adaa3acade5888b87bef641c4c99" +
				 "4c8091b5bcd387f3963fb5bc37aa922fbfe3df4e5b915e6eb514717bdd2a7407" +
				 "9a5073f5c4bfd46adf7d282e7a393a52579d11a028da4d9cd9c77124f9648ee3" +
				 "83b1ac763930e7162a8d37f350b2f74b8472cf09902063c6b32e8c2d9290cefb" +
				 "d7346d1c779a0df50edcde4531da07b099c638e83a755944df2aef1aa31752fd" +
				 "323dcb710fb4bfbb9d22b925bc3577e1b8949e729a90bbafeacf7f7879e7b114" +
				 "7e28ba0bae940db795a61b15ecf4df8db07b824bb062802cc98a9545bb2aaeed" +
				 "77cb3fc6db15dcd7d80d7d5bc406c4970a3478ada8899b329198eb61c193fb62" +
				 "75aa8ca340344a75a862aebe92eee1ce032fd950b47d7704a3876923b4ad6284" +
				 "4bf4a09c4dbe8b4397184b7471360c9564880aedddb9baa4af2e75394b08cd32" +
				 "ff479c57a07d3eab5d54de5f9738b8d27f27a9f0ab11799d7b7ffefb2704c95c" +
				 "6ad12c39f1e867a4b7b1d7818a4b753dfd2a89ccb45e001a03a867b187f225dd",
				 2,
				 "27182818284590452353602874713526",
				 "31415926535897932384626433832795");

			test("d55f684f81f4426e9fde92a5ff02df2ac896af63962888a97910c1379e20b0a3" +
				 "b1db613fb7fe2e07004329ea5c22bfd33e3dbe4cf58cc608c2c26c19a2e2fe22" +
				 "f98732c2b5cb844cc6c0702d91e1d50fc4382a7eba5635cd602432a2306ac4ce" +
				 "82f8d70c8d9bc15f918fe71e74c622d5cf71178bf6e0b9cc9f2b41dd8dbe441c" +
				 "41cd0c73a6dc47a348f6702f9d0e9b1b1431e948e299b9ec2272ab2c5f0c7be8" +
				 "6affa5dec87a0bee81d3d50007edaa2bcfccb35605155ff36ed8edd4a40dcd4b" +
				 "243acd11b2b987bdbfaf91a7cac27e9c5aea525ee53de7b2d3332c8644402b82" +
				 "3e94a7db26276d2d23aa07180f76b4fd29b9c0823099c9d62c519880aee7e969" +
				 "7617c1497d47bf3e571950311421b6b734d38b0db91eb85331b91ea9f61530f5" +
				 "4512a5a52a4bad589eb69781d537f23297bb459bdad2948a29e1550bf4787e0b" +
				 "e95bb173cf5fab17dab7a13a052a63453d97ccec1a321954886b7a1299faaeec" +
				 "ae35c6eaaca753b041b5e5f093bf83397fd21dd6b3012066fcc058cc32c3b09d" +
				 "7562dee29509b5839392c9ff05f51f3166aaac4ac5f238038a3045e6f72e48ef" +
				 "0fe8bc675e82c318a268e43970271bf119b81bf6a982746554f84e72b9f00280" +
				 "a320a08142923c23c883423ff949827f29bbacdc1ccdb04938ce6098c95ba6b3" +
				 "2528f4ef78eed778b2e122ddfd1cbdd11d1c0a6783e011fc536d63d053260637",
				 "8e41b78c390b5af9d758bb214a67e9f6bf7727b09ac6124084c37611398fa45d" +
				 "aad94868600ed391fb1acd4857a95b466e62ef9f4b377244d1c152e7b30d731a" +
				 "ad30c716d214b707aed99eb5b5e580b3e887cf7497465651d4b60e6042051da3" +
				 "693c3b78c14489543be8b6ad0ba629565bba202313ba7b0d0c94a3252b676f46" +
				 "cc02ce0f8a7d34c0ed229129673c1f61aed579d08a9203a25aac3a77e9db6026" +
				 "7996db38df637356d9dcd1632e369939f2a29d89345c66e05066f1a3677aef18" +
				 "dea4113faeb629e46721a66d0a7e785d3e29af2594eb67dfa982affe0aac058f" +
				 "6e15864269b135418261fc3afb089472cf68c45dd7f231c6249ba0255e1e0338" +
				 "33fc4d00a3fe02132d7bc3873614b8aee34273581ea0325c81f0270affa13641" +
				 "d052d36f0757d484014354d02d6883ca15c24d8c3956b1bd027bcf41f151fd80" +
				 "23c5340e5606f37e90fdb87c86fb4fa634b3718a30bace06a66eaf8f63c4aa3b" +
				 "637826a87fe8cfa44282e92cb1615af3a28e53bc74c7cba1a0977be9065d0c1a" +
				 "5dec6c54ae38d37f37aa35283e048e5530a85c4e7a29d7b92ec0c3169cdf2a80" +
				 "5c7604bce60049b9fb7b8eaac10f51ae23794ceba68bb58112e293b9b692ca72" +
				 "1b37c662f8574ed4dba6f88e170881c82cddc1034a0ca7e284bf0962b6b26292" +
				 "d836fa9f73c1ac770eef0f2d3a1eaf61d3e03555fd424eedd67e18a18094f888",
				 0xfd,
				 "27182818284590452353602874713526",
				 "31415926535897932384626433832795");

			test("64497e5a831e4a932c09be3e5393376daa599548b816031d224bbf50a818ed23" +
				 "50eae7e96087c8a0db51ad290bd00c1ac1620857635bf246c176ab463be30b80" +
				 "8da548081ac847b158e1264be25bb0910bbc92647108089415d45fab1b3d2604" +
				 "e8a8eff1ae4020cfa39936b66827b23f371b92200be90251e6d73c5f86de5fd4" +
				 "a950781933d79a28272b782a2ec313efdfcc0628f43d744c2dc2ff3dcb66999b" +
				 "50c7ca895b0c64791eeaa5f29499fb1c026f84ce5b5c72ba1083cddb5ce45434" +
				 "631665c333b60b11593fb253c5179a2c8db813782a004856a1653011e93fb6d8" +
				 "76c18366dd8683f53412c0c180f9c848592d593f8609ca736317d356e13e2bff" +
				 "3a9f59cd9aeb19cd482593d8c46128bb32423b37a9adfb482b99453fbe25a41b" +
				 "f6feb4aa0bef5ed24bf73c762978025482c13115e4015aac992e5613a3b5c2f6" +
				 "85b84795cb6e9b2656d8c88157e52c42f978d8634c43d06fea928f2822e465aa" +
				 "6576e9bf419384506cc3ce3c54ac1a6f67dc66f3b30191e698380bc999b05abc" +
				 "e19dc0c6dcc2dd001ec535ba18deb2df1a101023108318c75dc98611a09dc48a" +
				 "0acdec676fabdf222f07e026f059b672b56e5cbc8e1d21bbd867dd9272120546" +
				 "81d70ea737134cdfce93b6f82ae22423274e58a0821cc5502e2d0ab4585e94de" +
				 "6975be5e0b4efce51cd3e70c25a1fbbbd609d273ad5b0d59631c531f6a0a57b9",
				 "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" +
				 "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f" +
				 "404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f" +
				 "606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f" +
				 "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f" +
				 "a0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf" +
				 "c0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedf" +
				 "e0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff" +
				 "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" +
				 "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f" +
				 "404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f" +
				 "606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f" +
				 "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f" +
				 "a0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf" +
				 "c0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedf" +
				 "e0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff",
				 0xffffffffffL,
				 "2718281828459045235360287471352662497757247093699959574966967627",
				 "3141592653589793238462643383279502884197169399375105820974944592");

			test("bf53d2dade78e822a4d949a9bc6766b01b06a8ef70d26748c6a7fc36d80ae4c5" +
				 "520f7c4ab0ac8544424fa405162fef5a6b7f229498063618d39f0003cb5fb8d1" +
				 "c86b643497da1ff945c8d3bedeca4f479702a7a735f043ddb1d6aaade3c4a0ac" +
				 "7ca7f3fa5279bef56f82cd7a2f38672e824814e10700300a055e1630b8f1cb0e" +
				 "919f5e942010a416e2bf48cb46993d3cb6a51c19bacf864785a00bc2ecff15d3" +
				 "50875b246ed53e68be6f55bd7e05cfc2b2ed6432198a6444b6d8c247fab941f5" +
				 "69768b5c429366f1d3f00f0345b96123d56204c01c63b22ce78baf116e525ed9" +
				 "0fdea39fa469494d3866c31e05f295ff21fea8d4e6e13d67e47ce722e9698a1c" +
				 "1048d68ebcde76b86fcf976eab8aa9790268b7068e017a8b9b749409514f1053" +
				 "027fd16c3786ea1bac5f15cb79711ee2abe82f5cf8b13ae73030ef5b9e4457e7" +
				 "5d1304f988d62dd6fc4b94ed38ba831da4b7634971b6cd8ec325d9c61c00f1df" +
				 "73627ed3745a5e8489f3a95c69639c32cd6e1d537a85f75cc844726e8a72fc00" +
				 "77ad22000f1d5078f6b866318c668f1ad03d5a5fced5219f2eabbd0aa5c0f460" +
				 "d183f04404a0d6f469558e81fab24a167905ab4c7878502ad3e38fdbe62a4155" +
				 "6cec37325759533ce8f25f367c87bb5578d667ae93f9e2fd99bcbc5f2fbba88c" +
				 "f6516139420fcff3b7361d86322c4bd84c82f335abb152c4a93411373aaa8220",
				 "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" +
				 "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f" +
				 "404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f" +
				 "606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f" +
				 "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f" +
				 "a0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf" +
				 "c0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedf" +
				 "e0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff" +
				 "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" +
				 "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f" +
				 "404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f" +
				 "606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f" +
				 "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f" +
				 "a0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf" +
				 "c0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedf" +
				 "e0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff",
				 0xffffffffL,
				 "2718281828459045235360287471352662497757247093699959574966967627",
				 "3141592653589793238462643383279502884197169399375105820974944592");

			return true;
		}
		catch (IllegalStateException e)
		{
			return false;
		}
	}


	private static void test(String aCipherText, String aPlainText, long aStartDataUnit, String aCipherKey, String aTweakCipherKey)
	{
		byte [] plainText = Convert.hexToBytes(aPlainText);
		byte [] cipherText = Convert.hexToBytes(aCipherText);
		byte [] originalText = plainText.clone();

		Cipher cipher = new AES(new SecretKey(Convert.hexToBytes(aCipherKey)));
		Cipher tweakCipher = new AES(new SecretKey(Convert.hexToBytes(aTweakCipherKey)));

		new XTS(512).encrypt(plainText, 0, plainText.length, aStartDataUnit, cipher, tweakCipher);

		if (!java.util.Arrays.equals(cipherText, plainText))
		{
			throw new IllegalStateException();
		}

		new XTS(512).decrypt(plainText, 0, plainText.length, aStartDataUnit, cipher, tweakCipher);

		if (!java.util.Arrays.equals(originalText, plainText))
		{
			throw new IllegalStateException();
		}
	}


//	public static void main(String... args)
//	{
//		try
//		{
//			System.out.println("selftest=" + selftest());
//
//			AES cipher = new AES(new SecretKey(Convert.hexToBytes("0123456789abcdef0123456789abcdef")));
//			AES tweak = new AES(new SecretKey(Convert.hexToBytes("fedcba9876543210fedcba9876543210")));
//			XTS xts = new XTS(512);
//			Random rnd = new Random(1);
//
//			byte [] plainText = new byte[131072];
//			byte [] cipherText = new byte[131072];
//			byte [] outputText = new byte[131072];
//
//			for (int testIndex = 0; testIndex < 100; testIndex++)
//			{
//				rnd.nextBytes(plainText);
//
//				for (int size = 512; size <= 131072; size+=512)
//				{
//					long dataUnitIndex = rnd.nextLong();
//
//					System.arraycopy(plainText, 0, cipherText, 0, size);
//
//					xts.encrypt(cipherText, 0, size, dataUnitIndex, cipher, tweak);
//
//					System.arraycopy(cipherText, 0, outputText, 0, size);
//
//					xts.decrypt(outputText, 0, size, dataUnitIndex, cipher, tweak);
//
//					for (int i = 0; i < size; i++)
//					{
//						if (plainText[i] != outputText[i])
//						{
//							Debug.hexDump(plainText);
//							System.out.println("------------------------------------------------------------------------------------------------------------------------------------------------------");
//							Debug.hexDump(cipherText);
//							System.out.println("------------------------------------------------------------------------------------------------------------------------------------------------------");
//							Debug.hexDump(outputText);
//
//							throw new IllegalStateException();
//						}
//					}
//				}
//			}
//			
//			System.out.println("OK");
//		}
//		catch (Exception e)
//		{
//			e.printStackTrace(System.out);
//		}
//	}
}