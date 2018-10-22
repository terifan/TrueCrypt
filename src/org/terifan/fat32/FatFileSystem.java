package org.terifan.fat32;

import org.terifan.pagestore.PageStore;
import java.io.IOException;


public class FatFileSystem implements AutoCloseable
{
	private PageStore mPageStore;
	private FatBootSector mBootSector;
	private FatInfoSector mInfoSector;
	private FatFile mRootDirectory;
	private FatAllocationTable mAllocationTable;


	public FatFileSystem(PageStore aPageStore) throws IOException
	{
		mPageStore = aPageStore;
		mBootSector = new FatBootSector(this);
		mInfoSector = new FatInfoSector(this);
		mAllocationTable = new FatAllocationTable(this);

		if (mBootSector.getBytesPerSector() != 512)
		{
			throw new IOException("Unsupported sector size; sectors must be 512 bytes: size: " + mBootSector.getBytesPerSector());
		}
/*
		{
		ArrayList<Long> list = new ArrayList<Long>();
		mAllocationTable.getChain(0xef, false, list);
		System.out.println(list);
		}

		for (long index = 0xef;;)
		{
		ArrayList<Long> list = new ArrayList<Long>();
		boolean b = mAllocationTable.getChain(index, true, list);
		System.out.println(list);
		if (b) break;
		index = list.get(list.size()-1);
		}
*/
		mRootDirectory = new FatFile(this, null, mBootSector.getRootFirstCluster(), true);
	}


	PageStore getPageStore()
	{
		return mPageStore;
	}


	FatBootSector getBootSector()
	{
		return mBootSector;
	}


	FatInfoSector getInfoSector()
	{
		return mInfoSector;
	}


	FatAllocationTable getAllocationTable()
	{
		return mAllocationTable;
	}


	int getClusterSize()
	{
		return mBootSector.getSectorsPerCluster() * mBootSector.getBytesPerSector();
	}


	long getSectorOffset(long aClusterIndex)
	{
		return mBootSector.getReservedSectorCount()+mBootSector.getFatCount()*mBootSector.getSectorsPerFat()+(aClusterIndex-2)*mBootSector.getSectorsPerCluster();
	}


	public FatFile getFile(String aPath) throws IOException
	{
		aPath = aPath.replace('\\', '/');
		while (aPath.startsWith("/"))
		{
			aPath = aPath.substring(1);
		}

		FatFile dir = mRootDirectory;

		int i;
		while ((i = aPath.indexOf("/")) != -1)
		{
			dir = dir.getFile(aPath.substring(0,i));
			aPath = aPath.substring(i+1);
		}
		
		if (aPath.isEmpty())
		{
			return dir.clone();
		}

		return dir.getFile(aPath);
	}


	@Override
	public void close() throws IOException
	{
		if (mPageStore != null)
		{
			mPageStore.close();
			mPageStore = null;
		}
	}


	public boolean equals(FatFileSystem obj)
	{
		if (obj instanceof FatFileSystem)
		{
			return this.mPageStore == ((FatFileSystem)obj).mPageStore;
		}
		return true;
	}
}