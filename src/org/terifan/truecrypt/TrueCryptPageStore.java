package org.terifan.truecrypt;

import org.terifan.pagestore.PageStore;
import org.terifan.util.ByteArray;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.zip.CRC32;


public class TrueCryptPageStore implements PageStore, AutoCloseable
{
	private final static int VERSION_NUM = 0x063a;

	private final static int ENCRYPTION_DATA_UNIT_SIZE = 512;
	private final static int PKCS5_SALT_SIZE = 64;
	private final static int MASTER_KEYDATA_SIZE = 256;
	private final static int VOLUME_HEADER_VERSION = 0x0004;

	private final static int TC_VOLUME_MIN_REQUIRED_PROGRAM_VERSION = 0x0600;
	private final static int TC_VOLUME_HEADER_EFFECTIVE_SIZE = 512;

	private final static int TC_HEADER_OFFSET_MAGIC = 64;
	private final static int TC_HEADER_OFFSET_VERSION = 68;
	private final static int TC_HEADER_OFFSET_REQUIRED_VERSION = 70;
	private final static int TC_HEADER_OFFSET_KEY_AREA_CRC = 72;
	private final static int TC_HEADER_OFFSET_VOLUME_CREATION_TIME = 76;
	private final static int TC_HEADER_OFFSET_MODIFICATION_TIME = 84;
	private final static int TC_HEADER_OFFSET_HIDDEN_VOLUME_SIZE = 92;
	private final static int TC_HEADER_OFFSET_VOLUME_SIZE = 100;
	private final static int TC_HEADER_OFFSET_ENCRYPTED_AREA_START = 108;
	private final static int TC_HEADER_OFFSET_ENCRYPTED_AREA_LENGTH = 116;
	private final static int TC_HEADER_OFFSET_FLAGS = 124;
	private final static int TC_HEADER_OFFSET_HEADER_CRC = 252;

	private final static int HEADER_SALT_OFFSET = 0;
	private final static int HEADER_ENCRYPTED_DATA_OFFSET = PKCS5_SALT_SIZE;
	private final static int HEADER_MASTER_KEYDATA_OFFSET = 256;
	private final static int HEADER_ENCRYPTED_DATA_SIZE = (TC_VOLUME_HEADER_EFFECTIVE_SIZE - HEADER_ENCRYPTED_DATA_OFFSET);

	public static enum CipherOption
	{
		AES("aes"),
		SERPENT("serpent"),
		TWOFISH("twofish"),
		TWOFISH_AES("twofish", "aes"),
		SERPENT_TWOFISH_AES("serpent", "twofish", "aes"),
		AES_SERPENT("aes", "serpent"),
		AES_TWOFISH_SERPENT("aes", "twofish", "serpent"),
		SERPENT_TWOFISH("serpent", "twofish");

		private final String[] mAlgorithms;

		CipherOption(String... aAlgorithms)
		{
			mAlgorithms = aAlgorithms;
		}
	}

	public static enum DigestOption
	{
		SHA512(1000),
		RIPEMD160(2000),
		WHIRLPOOL(1000);

		private final int mIterations;

		private DigestOption(int aIterations)
		{
			mIterations = aIterations;
		}

		MessageDigest getDigestInstance()
		{
			switch (this)
			{
				case SHA512:
					return new SHA512();
				case RIPEMD160:
					return new RIPEMD160();
				case WHIRLPOOL:
					return new Whirlpool();
			}

			throw new RuntimeException();
		}
	}

	private PageStore mPageStore;
	private long mVolumeDataAreaOffset;
	private long mVolumeDataAreaLength;
	private Cipher[] mCiphers;
	private Cipher[] mTweakCiphers;


	private TrueCryptPageStore(PageStore aPageStore) throws IOException
	{
		if (aPageStore.getPageSize() != ENCRYPTION_DATA_UNIT_SIZE)
		{
			throw new IllegalArgumentException("Provided page store must have a " + ENCRYPTION_DATA_UNIT_SIZE + "-byte page size.");
		}

		mPageStore = aPageStore;
	}


	public static TrueCryptPageStore open(PageStore aPageStore, String aPassword) throws IOException
	{
		TrueCryptPageStore tc = new TrueCryptPageStore(aPageStore);
		tc.readVolumeHeader(aPassword);

		return tc;
	}


	public static TrueCryptPageStore create(PageStore aPageStore, long aPageCount, String aPassword, CipherOption aCipherOption, DigestOption aDigestOption, Consumer<Long> aProgressCallback) throws IOException
	{
		aPageStore.resize(aPageCount);

		TrueCryptPageStore tc = new TrueCryptPageStore(aPageStore);
		tc.format(aProgressCallback);
		tc.writeVolumeHeader(aPassword);

		return tc;
	}


	private void readVolumeHeader(String aPassword) throws IOException
	{
		byte[] headerBuffer = new byte[ENCRYPTION_DATA_UNIT_SIZE];
		mPageStore.read(0, headerBuffer);

		ArrayList<Callable<Boolean>> tasks = new ArrayList<>();
		for (CipherOption cipher : CipherOption.values())
		{
			for (DigestOption digest : DigestOption.values())
			{
				VolumeHeaderDecoder task = new VolumeHeaderDecoder(headerBuffer.clone(), cipher, digest, aPassword.getBytes());
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


	private void writeVolumeHeader(String aPassword)
	{
	}


	private void setup(byte[] aHeader, String[] aCipherAlgorithms)
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
		if (aAlgorithm.equals("aes"))
		{
			return new AES();
		}
		if (aAlgorithm.equals("serpent"))
		{
			return new Serpent();
		}
		if (aAlgorithm.equals("twofish"))
		{
			return new Twofish();
		}
		throw new RuntimeException();
	}


	private class VolumeHeaderDecoder implements Callable<Boolean>
	{
		private byte[] mHeader;
		private CipherOption mCipherOption;
		private DigestOption mDigestOption;
		private byte[] mPassword;


		VolumeHeaderDecoder(byte[] aHeader, CipherOption aCipherOption, DigestOption aDigestOption, byte[] aPassword)
		{
			mHeader = aHeader;
			mCipherOption = aCipherOption;
			mDigestOption = aDigestOption;
			mPassword = aPassword;
		}


		@Override
		public Boolean call()
		{
			try
			{
				HMAC hmac = new HMAC(mDigestOption.getDigestInstance(), mPassword);
				int totalKeyLength = 32 * mCipherOption.mAlgorithms.length * 2;
				byte[] salt = ByteArray.copy(mHeader, HEADER_SALT_OFFSET, PKCS5_SALT_SIZE);
				byte[] keyBytes = PBKDF2.generateKeyBytes(hmac, salt, mDigestOption.mIterations, totalKeyLength);
				XTS xts = new XTS(512);

				for (int i = mCipherOption.mAlgorithms.length; --i >= 0;)
				{
					Cipher cipher = TrueCryptPageStore.getCipherInstance(mCipherOption.mAlgorithms[i]);
					Cipher tweakCipher = TrueCryptPageStore.getCipherInstance(mCipherOption.mAlgorithms[i]);

					cipher.engineInit(new SecretKey(keyBytes, 32 * i, 32));
					tweakCipher.engineInit(new SecretKey(keyBytes, 32 * (i + mCipherOption.mAlgorithms.length), 32));

					xts.decrypt(mHeader, PKCS5_SALT_SIZE, HEADER_ENCRYPTED_DATA_SIZE, 0, cipher, tweakCipher);

					cipher.engineReset();
					tweakCipher.engineReset();
				}

				if (ByteArray.BE.getInt(mHeader, TC_HEADER_OFFSET_MAGIC) == 0x54525545)
				{
					setup(mHeader, mCipherOption.mAlgorithms);
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
		long sectorIndex = mVolumeDataAreaOffset / ENCRYPTION_DATA_UNIT_SIZE + aPageIndex;

		mPageStore.read(sectorIndex, aBuffer, aOffset, aLength);

		XTS xts = new XTS(512);

		for (int j = 0; j < aLength / 512; j++)
		{
			for (int i = mCiphers.length; --i >= 0;)
			{
				xts.decrypt(aBuffer, aOffset + 512 * j, 512 + 0 * aLength, sectorIndex + j, mCiphers[i], mTweakCiphers[i]);
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
		long sectorIndex = mVolumeDataAreaOffset / ENCRYPTION_DATA_UNIT_SIZE + aPageIndex;

		byte[] temp = new byte[aLength];
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
		return mPageStore.getPageCount() - mVolumeDataAreaOffset / ENCRYPTION_DATA_UNIT_SIZE;
	}


	@Override
	public void resize(long aPageCount) throws IOException
	{
		throw new UnsupportedOperationException("Not supported.");
	}


	private void format(Consumer<Long> aProgressCallback) throws IOException
	{
		int pageSize = getPageSize();
		byte[] buffer = new byte[pageSize];

		SecureRandom rnd = new SecureRandom();

		XTS xts = new XTS(512);

		for (long sectorIndex = 0, sz = getPageCount(); sectorIndex < sz; sectorIndex++)
		{
			rnd.nextBytes(buffer);

			for (int i = 0; i < mCiphers.length; i++)
			{
				xts.encrypt(buffer, 0, pageSize, sectorIndex, mCiphers[i], mTweakCiphers[i]);
			}

			mPageStore.write(sectorIndex, buffer, 0, pageSize);

			if (aProgressCallback != null)
			{
				aProgressCallback.accept(sectorIndex);
			}
		}
	}
}
