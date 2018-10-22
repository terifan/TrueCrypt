package org.terifan.truecrypt;

import org.terifan.pagestore.PageStore;
import org.terifan.util.ByteArray;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.CRC32;


public class TrueCryptPageStore implements PageStore, AutoCloseable
{
	private final static int VERSION_NUM                            = 0x063a;

	private final static int ENCRYPTION_DATA_UNIT_SIZE              = 512;
	private final static int PKCS5_SALT_SIZE                        = 64;
	private final static int MASTER_KEYDATA_SIZE                    = 256;
	private final static int VOLUME_HEADER_VERSION                  = 0x0004;

	private final static int TC_VOLUME_MIN_REQUIRED_PROGRAM_VERSION	= 0x0600;
	private final static int TC_VOLUME_HEADER_EFFECTIVE_SIZE        = 512;

	private final static int TC_HEADER_OFFSET_MAGIC                 = 64;
	private final static int TC_HEADER_OFFSET_VERSION               = 68;
	private final static int TC_HEADER_OFFSET_REQUIRED_VERSION      = 70;
	private final static int TC_HEADER_OFFSET_KEY_AREA_CRC          = 72;
	private final static int TC_HEADER_OFFSET_VOLUME_CREATION_TIME  = 76;
	private final static int TC_HEADER_OFFSET_MODIFICATION_TIME     = 84;
	private final static int TC_HEADER_OFFSET_HIDDEN_VOLUME_SIZE    = 92;
	private final static int TC_HEADER_OFFSET_VOLUME_SIZE           = 100;
	private final static int TC_HEADER_OFFSET_ENCRYPTED_AREA_START  = 108;
	private final static int TC_HEADER_OFFSET_ENCRYPTED_AREA_LENGTH = 116;
	private final static int TC_HEADER_OFFSET_FLAGS                 = 124;
	private final static int TC_HEADER_OFFSET_HEADER_CRC            = 252;

	private final static int HEADER_SALT_OFFSET                     = 0;
	private final static int HEADER_ENCRYPTED_DATA_OFFSET           = PKCS5_SALT_SIZE;
	private final static int HEADER_MASTER_KEYDATA_OFFSET           = 256;
	private final static int HEADER_ENCRYPTED_DATA_SIZE             = (TC_VOLUME_HEADER_EFFECTIVE_SIZE - HEADER_ENCRYPTED_DATA_OFFSET);

	private PageStore mPageStore;
	private long mVolumeDataAreaOffset;
	private long mVolumeDataAreaLength;
	private Cipher [] mCiphers;
	private Cipher [] mTweakCiphers;


	public TrueCryptPageStore(PageStore aPageStore, String aPassword) throws IOException
	{
		if (aPageStore.getPageSize() != ENCRYPTION_DATA_UNIT_SIZE)
		{
			throw new IllegalArgumentException("Provided page store must have a "+ENCRYPTION_DATA_UNIT_SIZE+"-byte page size.");
		}

		mPageStore = aPageStore;

		readVolumeHeader(aPassword);
	}


	private void readVolumeHeader(String aPassword) throws IOException
	{
		byte [] headerBuffer = new byte[ENCRYPTION_DATA_UNIT_SIZE];
		mPageStore.read(0, headerBuffer);

		String [][] ciphers = {{"aes"},
							   {"serpent"},
							   {"twofish"},
							   {"twofish", "aes"},
							   {"serpent", "twofish", "aes"},
							   {"aes", "serpent"},
							   {"aes", "twofish", "serpent"},
							   {"serpent", "twofish"}};

		int [] iterations = {1000, 2000, 1000};

		String [] digests = {"sha512", "ripemd160", "whirlpool"};

		ArrayList<Callable<Boolean>> tasks = new ArrayList<>();
		for (String[] cipher : ciphers)
		{
			for (int j = 0; j < digests.length; j++)
			{
				Worker task = new Worker(headerBuffer.clone(), cipher, digests[j], iterations[j], aPassword.getBytes());
				tasks.add(task);
			}
		}

		int cpu = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
		ExecutorService pool = Executors.newFixedThreadPool(cpu);
		try
		{
			pool.invokeAll(tasks);
		}
		catch (InterruptedException e)
		{
		}
		pool.shutdown();

		if (mCiphers == null)
		{
			throw new InvalidKeyException("Incorrect password or an unsupported file version.");
		}
	}


	private void setup(byte [] aHeader, String [] aCipherAlgorithms)
	{
		//Debug.hexDump(aHeader);

		// Header version
		int headerVersion = ByteArray.BE.getShort(aHeader, TC_HEADER_OFFSET_VERSION);

		if (headerVersion > VOLUME_HEADER_VERSION)
		{
			//throw new RuntimeException("ERR_NEW_VERSION_REQUIRED " + headerVersion);
		}

		// Check CRC of the header fields
		if (headerVersion >= 4)
		{
			CRC32 crc = new CRC32();
			crc.update(aHeader, TC_HEADER_OFFSET_MAGIC, TC_HEADER_OFFSET_HEADER_CRC - TC_HEADER_OFFSET_MAGIC);

			if (ByteArray.BE.getInt(aHeader, TC_HEADER_OFFSET_HEADER_CRC) != (int)crc.getValue())
			{
				throw new RuntimeException("Bad header CRC");
			}
		}
		else
		{
			throw new RuntimeException("Bad version: " + headerVersion);
		}

		// Required program version
		int requiredProgramVersion = ByteArray.BE.getShort(aHeader, TC_HEADER_OFFSET_REQUIRED_VERSION);
		boolean legacyVolume = requiredProgramVersion < TC_VOLUME_MIN_REQUIRED_PROGRAM_VERSION;

		CRC32 crc = new CRC32();
		crc.update(aHeader, HEADER_MASTER_KEYDATA_OFFSET, MASTER_KEYDATA_SIZE);

		// Check CRC of the key set
		if (ByteArray.BE.getInt(aHeader, TC_HEADER_OFFSET_KEY_AREA_CRC) != (int)crc.getValue())
		{
			throw new RuntimeException("Bad master key CRC");
		}

		// Now we have the correct password, cipher, hash algorithm, and volume type

		// Check the version required to handle this volume
		if (requiredProgramVersion > VERSION_NUM)
		{
//			throw new RuntimeException("ERR_NEW_VERSION_REQUIRED");
		}

		// Hidden volume size (if any)
		long hiddenVolumeSize = ByteArray.BE.getLong(aHeader, TC_HEADER_OFFSET_HIDDEN_VOLUME_SIZE);

		// Volume size
		long volumeSize = ByteArray.BE.getLong(aHeader, TC_HEADER_OFFSET_VOLUME_SIZE);

		// Encrypted area size and length
		mVolumeDataAreaOffset = ByteArray.BE.getLong(aHeader, TC_HEADER_OFFSET_ENCRYPTED_AREA_START);
		mVolumeDataAreaLength = ByteArray.BE.getLong(aHeader, TC_HEADER_OFFSET_ENCRYPTED_AREA_LENGTH);

		// Flags
		int headerFlags = ByteArray.BE.getInt(aHeader, TC_HEADER_OFFSET_FLAGS);

//		System.out.println("headerVersion="+headerVersion);
//		System.out.println("requiredProgramVersion="+requiredProgramVersion);
//		System.out.println("hiddenVolumeSize="+hiddenVolumeSize);
//		System.out.println("VolumeSize="+volumeSize);
//		System.out.println("EncryptedAreaStart="+mVolumeDataAreaOffset);
//		System.out.println("EncryptedAreaLength="+mVolumeDataAreaLength);
//		System.out.println("HeaderFlags="+headerFlags);

		mCiphers = new Cipher[aCipherAlgorithms.length];
		mTweakCiphers = new Cipher[aCipherAlgorithms.length];

		for (int i = 0; i < aCipherAlgorithms.length; i++)
		{
			mCiphers[i] = getCipherInstance(aCipherAlgorithms[i]);
			mTweakCiphers[i] = getCipherInstance(aCipherAlgorithms[i]);

			mCiphers[i].engineInit(new SecretKey(aHeader, HEADER_MASTER_KEYDATA_OFFSET + 32 * i, 32));
			mTweakCiphers[i].engineInit(new SecretKey(aHeader, HEADER_MASTER_KEYDATA_OFFSET + 32 * (i + aCipherAlgorithms.length), 32));
		}
	}


	private static Cipher getCipherInstance(String aAlgorithm)
	{
		if (aAlgorithm.equals("aes")) return new AES();
		if (aAlgorithm.equals("serpent")) return new Serpent();
		if (aAlgorithm.equals("twofish")) return new Twofish();
		throw new RuntimeException();
	}


	private static MessageDigest getDigestInstance(String aAlgorithm)
	{
		if (aAlgorithm.equals("sha512")) return new SHA512();
		if (aAlgorithm.equals("ripemd160")) return new RIPEMD160();
		if (aAlgorithm.equals("whirlpool")) return new Whirlpool();
		throw new RuntimeException();
	}


	private class Worker implements Callable<Boolean>
	{
		private byte [] mHeader;
		private String [] mCipherAlgorithms;
		private String mDigestAlgorithm;
		private int mIterationCount;
		private byte [] mPassword;

		Worker(byte[] aHeader, String[] aCipherAlgorithms, String aDigestAlgorithm, int aIterationCount, byte [] aPassword)
		{
			mHeader = aHeader;
			mCipherAlgorithms = aCipherAlgorithms;
			mDigestAlgorithm = aDigestAlgorithm;
			mIterationCount = aIterationCount;
			mPassword = aPassword;
		}

		@Override
		public Boolean call()
		{
			try
			{
				//System.out.println("start digest="+mDigestAlgorithm+", cipher="+Arrays.asList(mCipherAlgorithms));
				HMAC hmac = new HMAC(TrueCryptPageStore.getDigestInstance(mDigestAlgorithm), mPassword);
				int totalKeyLength = 32 * mCipherAlgorithms.length * 2;
				byte [] salt = ByteArray.copy(mHeader, HEADER_SALT_OFFSET, PKCS5_SALT_SIZE);
				byte [] keyBytes = PBKDF2.generateKeyBytes(hmac, salt, mIterationCount, totalKeyLength);
				XTS xts = new XTS(512);

				for (int i = mCipherAlgorithms.length; --i>=0;)
				{
					Cipher cipher = TrueCryptPageStore.getCipherInstance(mCipherAlgorithms[i]);
					Cipher tweakCipher = TrueCryptPageStore.getCipherInstance(mCipherAlgorithms[i]);

					cipher.engineInit(new SecretKey(keyBytes, 32 * i, 32));
					tweakCipher.engineInit(new SecretKey(keyBytes, 32 * (i + mCipherAlgorithms.length), 32));

					xts.decrypt(mHeader, PKCS5_SALT_SIZE, HEADER_ENCRYPTED_DATA_SIZE, 0, cipher, tweakCipher);

					cipher.engineReset();
					tweakCipher.engineReset();
				}

				if (ByteArray.BE.getInt(mHeader, TC_HEADER_OFFSET_MAGIC) == 0x54525545)
				{
					setup(mHeader, mCipherAlgorithms);
				}

				hmac.reset();
				Arrays.fill(keyBytes, (byte)0);
				Arrays.fill(mHeader, (byte)0);
				Arrays.fill(mPassword, (byte)0);
			}
			catch (Exception e)
			{
				e.printStackTrace(System.out);
			}

			return Boolean.TRUE;
		}
	}


	@Override
	public void read(long aPageIndex, byte[] aBuffer) throws IOException
	{
		read(aPageIndex, aBuffer, 0, aBuffer.length);
	}


	@Override
	public void read(long aPageIndex, byte[] aBuffer, int aOffset, int aLength) throws IOException
	{
		long sectorIndex = mVolumeDataAreaOffset/ENCRYPTION_DATA_UNIT_SIZE + aPageIndex;

		mPageStore.read(sectorIndex, aBuffer, aOffset, aLength);

		XTS xts = new XTS(512);

		for (int j = 0; j < aLength/512; j++)
		{
			for (int i = mCiphers.length; --i >= 0;)
			{
				xts.decrypt(aBuffer, aOffset+512*j, 512+0*aLength, sectorIndex+j, mCiphers[i], mTweakCiphers[i]);
			}
		}
	}


	@Override
	public void write(long aPageIndex, byte[] aBuffer) throws IOException
	{
		write(aPageIndex, aBuffer, 0, aBuffer.length);
	}


	@Override
	public void write(long aPageIndex, byte[] aBuffer, int aOffset, int aLength) throws IOException
	{
		long sectorIndex = mVolumeDataAreaOffset/ENCRYPTION_DATA_UNIT_SIZE + aPageIndex;

		byte [] temp = new byte[aLength];
		System.arraycopy(aBuffer, aOffset, temp, 0, aLength);

		XTS xts = new XTS(512);

		for (int i = 0; i < mCiphers.length; i++)
		{
			xts.encrypt(temp, 0, aLength, sectorIndex, mCiphers[i], mTweakCiphers[i]);
		}

		mPageStore.write(sectorIndex, temp, 0, aLength);
	}


	@Override
	public int getPageSize() throws IOException
	{
		return ENCRYPTION_DATA_UNIT_SIZE;
	}


	@Override
	public void close() throws IOException
	{
		if (mCiphers != null)
		{
			for (int i = 0; i < mCiphers.length; i++)
			{
				mCiphers[i].engineReset();
				mTweakCiphers[i].engineReset();
			}
		}

		mCiphers = null;
		mTweakCiphers = null;
		mVolumeDataAreaLength = 0;
		mVolumeDataAreaOffset = 0;
		if (mPageStore != null)
		{
			mPageStore.close();
		}
		mPageStore = null;
	}


	@Override
	public void flush() throws IOException
	{
		mPageStore.flush();
	}


	@Override
	public long getPageCount() throws IOException
	{
		return mPageStore.getPageCount() - mVolumeDataAreaOffset/ENCRYPTION_DATA_UNIT_SIZE;
	}
}
