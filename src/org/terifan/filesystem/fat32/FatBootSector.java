package org.terifan.filesystem.fat32;

import java.io.IOException;
import static org.terifan.util.ByteArray.LE;


class FatBootSector
{
	private FatFileSystem mFileSystem;
	private byte[] mBuffer;


	FatBootSector(FatFileSystem aFileSystem) throws IOException
	{
		mFileSystem = aFileSystem;
		mBuffer = new byte[512];
		mFileSystem.getPageStore().read(0, mBuffer);

		if (LE.getUnsignedShort(mBuffer, 0x16) != 0)
		{
			throw new IOException("File system appears to be a FAT12 or FAT16 file system which is not supported.");
		}
	}


	public long getSectorsPerFat()
	{
		return LE.getUnsignedInt(mBuffer, 0x24);
	}


	public int getInfoSectorNumber()
	{
		return LE.getUnsignedShort(mBuffer, 0x30);
	}


	public int getBootSectorCopySector()
	{
		return LE.getUnsignedShort(mBuffer, 0x32);
	}


	public long getSectorCount()
	{
		return LE.getUnsignedInt(mBuffer, 0x20);
	}


	public int getFileSystemId()
	{
		return LE.getInt(mBuffer, 0x43);
	}


	public int getBytesPerSector()
	{
		return LE.getUnsignedShort(mBuffer, 0x0b);
	}


	public int getSectorsPerCluster()
	{
		return LE.getUnsignedByte(mBuffer, 0x0d);
	}


	public int getReservedSectorCount()
	{
		return LE.getUnsignedShort(mBuffer, 0xe);
	}


	public int getFatCount()
	{
		return LE.getUnsignedByte(mBuffer, 0x10);
	}


	public long getRootFirstCluster()
	{
		return LE.getUnsignedInt(mBuffer, 0x2c);
	}


	public int getLogicalSectorCount()
	{
		return LE.getUnsignedShort(mBuffer, 0x13);
	}


	public long getTotalSectorCount()
	{
		return LE.getUnsignedInt(mBuffer, 0x20);
	}


	public int getMediumDescriptor()
	{
		return LE.getUnsignedByte(mBuffer, 0x15);
	}


	public int getSectorsPerTrack()
	{
		return LE.getUnsignedShort(mBuffer, 0x18);
	}


	public int getHeadCount()
	{
		return LE.getUnsignedShort(mBuffer, 0x1a);
	}


	public long getHiddenSectorCount()
	{
		return LE.getUnsignedInt(mBuffer, 0x1c);
	}


	public void writeCopy() throws IOException
	{
		int offset = getBootSectorCopySector();
		if (offset > 0)
		{
			mFileSystem.getPageStore().write(offset, mBuffer);
		}
	}


	protected void debug()
	{
		System.out.println("BootSector:");
		System.out.println("  BootSectorCopySector="+getBootSectorCopySector());
		System.out.println("  BytesPerSector="+getBytesPerSector());
		System.out.println("  FatCount="+getFatCount());
		System.out.println("  FileSystemId="+getFileSystemId());
		System.out.println("  HeadCount="+getHeadCount());
		System.out.println("  HiddenSectorCount="+getHiddenSectorCount());
		System.out.println("  InfoSectorNumber="+getInfoSectorNumber());
		System.out.println("  LogicalSectorCount="+getLogicalSectorCount());
		System.out.println("  MediumDescriptor="+getMediumDescriptor());
		System.out.println("  ReservedSectorCount="+getReservedSectorCount());
		System.out.println("  RootFirstCluster="+getRootFirstCluster());
		System.out.println("  SectorCount="+getSectorCount());
		System.out.println("  SectorsPerCluster="+getSectorsPerCluster());
		System.out.println("  SectorsPerFat="+getSectorsPerFat());
		System.out.println("  SectorsPerTrack="+getSectorsPerTrack());
		System.out.println("  TotalSectorCount="+getTotalSectorCount());
	}
}