package org.terifan.filesystem.fat32;

import org.terifan.util.ByteArray;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import static org.terifan.util.ByteArray.LE;


public class FatFile
{
	protected final static int READONLY = 0x01;
	protected final static int HIDDEN = 0x02;
	protected final static int SYSTEM = 0x04;
	protected final static int LABEL = 0x08;
	protected final static int DIRECTORY = 0x10;
	protected final static int ARCHIVE = 0x20;

	protected FatFileSystem mFileSystem;
	protected long mStartCluster;
	protected String mName;
	protected long mCreated;
	protected long mAccessed;
	protected long mLastModified;
	protected long mLength;
	protected String mPath;
	protected FatFile mParent;
	protected String mShortName;
	protected int mFlags;
	protected boolean mDirectory;


	FatFile(FatFileSystem aFileSystem, FatFile aParent, long aStartCluster, boolean aDirectory)
	{
		mFileSystem = aFileSystem;
		mStartCluster = aStartCluster;
		mParent = aParent;
		mDirectory = aDirectory;
	}


	public FatFileSystem getFileSystem()
	{
		return mFileSystem;
	}


	long getStartCluster()
	{
		return mStartCluster;
	}


	public boolean isReadOnly()
	{
		return (mFlags & READONLY) != 0;
	}


	public boolean isHidden()
	{
		return (mFlags & HIDDEN) != 0;
	}


	public boolean isSystemFile()
	{
		return (mFlags & SYSTEM) != 0;
	}


	public boolean isArchiveFlagSet()
	{
		return (mFlags & ARCHIVE) != 0;
	}


	public long getCreatedTime()
	{
		return mCreated;
	}


	public long getAccessed()
	{
		return mAccessed;
	}


	public long getLastModifiedTime()
	{
		return mLastModified;
	}


	public long getLastChangedTime()
	{
		return mLastModified;
	}


	public long getLength()
	{
		return mLength;
	}


	public String getName()
	{
		return mName;
	}


	public String getShortName()
	{
		return mShortName;
	}


	public FatFile getParent()
	{
		return mParent;
	}


	public String getPath()
	{
		return mPath;
	}


	public boolean isDirectory()
	{
		return mDirectory;
	}


	public boolean isFile()
	{
		return !mDirectory;
	}


	protected void copyTo(FatFile aObject)
	{
		aObject.mName = this.mName;
		aObject.mPath = this.mPath;
		aObject.mLastModified = this.mLastModified;
		aObject.mLength = this.mLength;
		aObject.mParent = this.mParent;
		aObject.mStartCluster = this.mStartCluster;
	}


	@Override
	public String toString()
	{
		if (mParent == null && (this instanceof FatFile))
		{
			return "/";
		}
		return mPath;
	}


	protected void debug()
	{
		System.out.println(isFile() ? "FILE" : "DIRECTORY");
		System.out.println("  name=" + mName);
		System.out.println("  short-name=" + mShortName);
		System.out.println("  flags=" + mFlags);
		System.out.println("  created=" + new Date(mCreated));
		System.out.println("  accessed=" + new Date(mAccessed));
		System.out.println("  modified=" + new Date(mLastModified));
		System.out.println("  cluster=" + mStartCluster);
		System.out.println("  size=" + mLength);
		System.out.println("  cluster=" + mStartCluster);
	}


	public FatRandomAccessStream getRandomAccessStream() throws IOException
	{
		return new FatRandomAccessStream(this);
	}


	public byte[] readAll() throws IOException
	{
		if (mDirectory)
		{
			throw new IllegalArgumentException("Directories cannot be read with this method.");
		}
		
		try (FatRandomAccessStream file = getRandomAccessStream())
		{
			byte[] buffer = new byte[(int)file.length()];
			file.read(buffer);
			return buffer;
		}
	}


	public FatFile getFile(String aName) throws IOException
	{
		ArrayList<FatFile> list = new ArrayList<>();
		loadDirectory(aName, true, true, list);
		return list.isEmpty() ? null : list.get(0);
	}


	public FatFile[] listFiles() throws IOException
	{
		ArrayList<FatFile> list = new ArrayList<>();
		loadDirectory(null, true, true, list);
		return list.toArray(new FatFile[list.size()]);
	}


	private void loadDirectory(String aTargetName, boolean aDirectories, boolean aFiles, ArrayList aResultList) throws IOException
	{
		FatRandomAccessStream stream = new FatRandomAccessStream(this);

		try
		{
			byte[] entry = new byte[32];
			StringBuilder longName = new StringBuilder();
			int nameChecksum = 0;
			int sequence = 0;

			for (int entryIndex = 0;; entryIndex++)
			{
				if (stream.read(entry) == 0)
				{
					break;
				}

				int firstByte = LE.getUnsignedByte(entry, 0x00);
				if (firstByte == 0) // end of directory reached
				{
					break;
				}
				if (firstByte == 0x2e || firstByte == 0xe5) // ignore special entries
				{
					continue;
				}
				if (firstByte == 0x05)
				{
					LE.put(entry, 0, 0xe5);
				}

				if (LE.getUnsignedByte(entry, 0x0b) == 0x0f) // long file name entry
				{
					if (ByteArray.getBit(entry, 7)) // deleted name entry
					{
						// TODO: restart name? update sequence?
					}
					else
					{
						int seq = LE.getUnsignedByte(entry, 0x00) & 0x3f;
						int chk = LE.getUnsignedByte(entry, 0x0d);
						boolean restart = ByteArray.getBit(entry, 6);

						if (restart || seq != sequence - 1 || chk != nameChecksum)
						{
							longName.setLength(0);
						}
						sequence = seq;
						nameChecksum = chk;

						try
						{
							longName.insert(0, new String(entry, 0x1c, 4, "UTF-16LE"));
							longName.insert(0, new String(entry, 0x0e, 12, "UTF-16LE"));
							longName.insert(0, new String(entry, 0x01, 10, "UTF-16LE"));
						}
						catch (UnsupportedEncodingException e)
						{
							throw new IOException(e);
						}
					}
				}
				else
				{
					int flags = LE.getUnsignedByte(entry, 0x0b);

					boolean isDir = (flags & FatFile.DIRECTORY) != 0;

					if (isDir && aDirectories || !isDir && aFiles)
					{
						String baseName = new String(entry, 0, 8).trim();
						String extension = new String(entry, 8, 3).trim();
						int nameFlags = LE.getUnsignedByte(entry, 0x0c);
						long created = decodeDateTime(LE.getUnsignedShort(entry, 0x10), LE.getUnsignedShort(entry, 0x0e), LE.getUnsignedByte(entry, 0x0d));
						long accessed = decodeDateTime(LE.getUnsignedShort(entry, 0x12), 0, 0);
						long modified = decodeDateTime(LE.getUnsignedShort(entry, 0x18), LE.getUnsignedShort(entry, 0x16), 0);
						long startCluster = ((LE.getUnsignedShort(entry, 0x14) << 16) + LE.getUnsignedShort(entry, 0x1a));
						long size = LE.getUnsignedInt(entry, 0x1c);
						int checksum = getFilenameChecksum(new String(entry, 0, 11));

						if (extension.length() > 0)
						{
							extension = "." + extension;
						}

						String shortName = baseName + extension;

						if ((nameFlags & 4) != 0)
						{
							extension = extension.toLowerCase();
						}
						if ((nameFlags & 8) != 0)
						{
							baseName = baseName.toLowerCase();
						}

						if (checksum != nameChecksum || sequence != 1)
						{
							longName.setLength(0);
							longName.append(baseName);
							longName.append(extension);
						}
						if (longName.indexOf("\0") != -1)
						{
							longName.delete(longName.indexOf("\0"), longName.length());
						}

						if (aTargetName == null || longName.toString().equalsIgnoreCase(aTargetName))
						{
							FatFile element = new FatFile(mFileSystem, this, startCluster, isDir);
							aResultList.add(element);

							element.mName = longName.toString();
							element.mShortName = shortName;
							element.mCreated = created;
							element.mAccessed = accessed;
							element.mLastModified = modified;
							element.mLength = size;
							element.mPath = (mPath == null ? "" : mPath) + "/" + element.mName;

							//element.debug();
						}
					}

					longName.setLength(0);
				}
			}
		}
		finally
		{
			stream.close();
		}
	}


	private long decodeDateTime(int aDate, int aTime, int aTimeHiRes)
	{
		return new Date(1980 + (aDate >> 9),
			(aDate >> 5) & 15,
			aDate & 31,
			aTime >> 11,
			(aTime >> 5) & 63,
			2 * (aTime & 31) + (aTimeHiRes / 100)).getTime();
	}


	private int getFilenameChecksum(String aFilename)
	{
		int sum = 0;
		for (int i = 0; i < 11; i++)
		{
			sum = (((sum & 1) << 7) + (sum >> 1) + aFilename.charAt(i)) & 255;
		}
		return sum;
	}


	public boolean isRoot()
	{
		return mDirectory && mParent == null;
	}


	@Override
	protected FatFile clone()
	{
		FatFile clone = new FatFile(mFileSystem, mParent, mStartCluster, mDirectory);
		copyTo(clone);
		return clone;
	}
}
