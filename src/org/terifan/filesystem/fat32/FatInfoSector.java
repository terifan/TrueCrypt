package org.terifan.filesystem.fat32;

import java.io.IOException;
import static org.terifan.util.ByteArray.LE;


class FatInfoSector
{
	private FatFileSystem mFileSystem;
	private byte [] mBuffer;


	FatInfoSector(FatFileSystem aFileSystem) throws IOException
	{
		mFileSystem = aFileSystem;
		mBuffer = new byte[512];

		long index = mFileSystem.getBootSector().getInfoSectorNumber();
		if (index != 0)
		{
			mFileSystem.getPageStore().read(index, mBuffer);
		}
	}


	public void commitChanges() throws IOException
	{
		long index = mFileSystem.getBootSector().getInfoSectorNumber();
		if (index != 0)
		{
			mFileSystem.getPageStore().write(index, mBuffer);
		}
	}


	public void setFreeClusterCount(long aValue)
	{
		LE.putInt(mBuffer, 0x1e8, (int)aValue);
	}


	public long getFreeClusterCount()
	{
		return LE.getUnsignedInt(mBuffer, 0x1e8);
	}


	public void setAllocatedClusterIndex(long aIndex)
	{
		LE.putInt(mBuffer, 0x1ec, (int)aIndex);
	}


	public long getAllocatedClusterIndex()
	{
		return LE.getUnsignedInt(mBuffer, 0x1ec);
	}
}
