package org.terifan.filesystem.fat32;

import org.terifan.util.Cache;
import java.io.IOException;
import java.util.ArrayList;
import java.util.TreeMap;
import static org.terifan.util.ByteArray.LE;


class FatAllocationTable
{
	private FatFileSystem mFileSystem;
	private Cache<Integer, byte[]> mReadCache;
	private TreeMap<Integer, byte[]> mWriteCache;
	private long mFatSize;
	private long mFatOffset;
	private FreeSpaceMap mFreeSpace;


	FatAllocationTable(FatFileSystem aFileSystem) throws IOException
	{
		mFileSystem = aFileSystem;
		mReadCache = new Cache<>(100);
		mWriteCache = new TreeMap<>();
		mFreeSpace = new FreeSpaceMap();

		mFatOffset = mFileSystem.getBootSector().getReservedSectorCount();
		mFatSize = mFileSystem.getBootSector().getSectorsPerFat();

		findFreeSpace(0);
	}


	public synchronized byte[] readSector(Integer aSectorIndex) throws IOException
	{
//		if (mReadCache.containsKey(aSectorIndex))
//		{
//			System.out.println("Reading FAT sector " + aSectorIndex + " from read-cache");
//		}
//		else if (mWriteCache.containsKey(aSectorIndex))
//		{
//			System.out.println("Reading FAT sector " + aSectorIndex + " from write-cache");
//		}
//		else
//		{
//			System.out.println("Reading FAT sector " + aSectorIndex + " from disk");
//		}

		byte[] buffer = mReadCache.get(aSectorIndex);

		if (buffer == null)
		{
			buffer = mWriteCache.get(aSectorIndex);
		}
		if (buffer == null)
		{
			buffer = new byte[512];

			mFileSystem.getPageStore().read(mFatOffset + aSectorIndex, buffer);

			mReadCache.put(aSectorIndex, buffer, 1);
		}

		return buffer;
	}


	public synchronized void writeSector(Integer aSectorIndex, byte[] aBuffer)
	{
		System.out.println("FAT::writeSector(" + aSectorIndex + ") : " + (mReadCache.containsKey(aSectorIndex) ? "READ" : mWriteCache.containsKey(aSectorIndex) ? "WRITE" : "NEW"));

		mReadCache.remove(aSectorIndex);

		mWriteCache.put(aSectorIndex, aBuffer);
	}


	public synchronized void commitSectorWrites() throws IOException
	{
		if (mWriteCache.isEmpty())
		{
			return;
		}

		for (int fatIndex = 0; fatIndex < mFileSystem.getBootSector().getFatCount(); fatIndex++)
		{
			long fatOffset = mFatOffset + fatIndex * mFatSize;

			for (Integer sectorIndex : mWriteCache.keySet())
			{
				System.out.println("commitSector(" + sectorIndex + ")");

				byte[] buffer = mWriteCache.get(sectorIndex);

				mFileSystem.getPageStore().write(fatOffset + sectorIndex, buffer);
			}
		}

		/*
		// this code is probably polluting the read cache !!
		for (Integer sectorIndex : mWriteCache.keySet())
		{
			mReadCache.put(sectorIndex, mWriteCache.remove(sectorIndex), 1);
		}
		 */
		mWriteCache.clear();

		// TODO: mFileSystem.getInfoSector().commitChanges();
	}


	/**
	 * Traces a cluster chain returning a list of clusters.
	 *
	 * Notice: the starting cluster is not included in the result list.
	 *
	 * @param aStartCluster
	 * the starting cluster; the start cluster is not necessarily the start of
	 * a file.
	 * @param aReadMaxClusters
	 * how many clusters to read
	 * @param aResultList
	 * a list containing the cluster indices
	 * @return
	 * true if the end of chain was reached
	 */
	public synchronized boolean getChain(long aStartCluster, int aReadMaxClusters, ArrayList<Long> aResultList) throws IOException
	{
		byte[] buffer = null;

		for (long cluster = aStartCluster, prevSectorIndex = -1; aReadMaxClusters-- >= 0;)
		{
			int sectorIndex = (int)(cluster / (512 / 4));

			if (sectorIndex != prevSectorIndex)
			{
				buffer = readSector(sectorIndex);
				prevSectorIndex = sectorIndex;
			}

			cluster = LE.getUnsignedInt(buffer, 4 * (int)(cluster % (512 / 4)));

			if (cluster >= 0xFFFFFF8L)
			{
				return true;
			}

			aResultList.add(cluster);
		}

		return false;
	}


	public synchronized int deleteChain(long aStartCluster) throws IOException
	{
		return freeClusterChain(aStartCluster, true);
	}


	public synchronized int truncateChain(long aEndCluster) throws IOException
	{
		return freeClusterChain(aEndCluster, false);
	}


	/*
	public long createChain(int aClusterCount)
	{
	}
	 */

	public void extendChain(long aStartCluster, int aClusterCount) throws IOException
	{
		ArrayList<Long> resultList = new ArrayList<>();
		getChain(aStartCluster, Integer.MAX_VALUE, resultList);
	}


	private int freeClusterChain(long aStartCluster, boolean aFreeStartCluster) throws IOException
	{
		byte[] buffer = null;
		int freeClusterCount = 0;
		int prevSectorIndex = -1;

		for (long cluster = aStartCluster;;)
		{
			int sectorIndex = (int)(cluster / (512 / 4));

			if (sectorIndex != prevSectorIndex)
			{
				if (freeClusterCount > 0)
				{
					writeSector(prevSectorIndex, buffer);
				}

				buffer = readSector(sectorIndex);
				prevSectorIndex = sectorIndex;
			}

			int offset = 4 * (int)(cluster % (512 / 4));

			long nextCluster = LE.getUnsignedInt(buffer, offset);

			if (aFreeStartCluster || freeClusterCount > 0)
			{
				LE.putInt(buffer, offset, 0);
			}
			else
			{
				LE.putInt(buffer, offset, 0xFFFFFFFF);
			}

			cluster = nextCluster;
			freeClusterCount++;

			if (cluster >= 0xFFFFFF8L)
			{
				break;
			}
		}

		writeSector(prevSectorIndex, buffer);

		return freeClusterCount;
	}


	private void findFreeSpace(int aMinimumClusterCount) throws IOException
	{
//		long t = System.nanoTime();
		int pointersPerSector = mFileSystem.getBootSector().getBytesPerSector() / 4;
		for (int sectorIndex = 0; sectorIndex < mFatSize; sectorIndex++)
		{
			byte[] buffer = readSector(sectorIndex);

			int start = -1;
			int length = 0;
			for (int offset = 0; offset < pointersPerSector; offset++)
			{
				if (LE.getUnsignedInt(buffer, offset) == 0)
				{
					if (start == -1)
					{
						start = offset;
					}
					length++;
				}
				else if (start != -1)
				{
					mFreeSpace.free(sectorIndex * pointersPerSector + start, length);
					start = -1;
					length = 0;
				}
			}

			if (start != -1)
			{
				mFreeSpace.free(sectorIndex * pointersPerSector + start, length);
			}
		}
//		System.out.println((System.nanoTime() - t) / 1000000);
//		mFreeSpace.debug();
//		throw new RuntimeException();
	}
}
