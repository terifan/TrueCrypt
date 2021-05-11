package org.terifan.filesystem.ntfs;


class DiskInfoWrapper implements IDiskInfo
{
	public short mBytesPerSector;
	public byte mSectorsPerCluster;
	public long mTotalSectors;
	public long mMftStartLcn;
	public long mMft2StartLcn;
	public int mClustersPerMftRecord;
	public int mClustersPerIndexRecord;
	public long mBytesPerMftRecord;
	public long mBytesPerCluster;
	public long mTotalClusters;


	public int getBytesPerSector()
	{
		return mBytesPerSector;
	}


	public byte getSectorsPerCluster()
	{
		return mSectorsPerCluster;
	}


	public long getTotalSectors()
	{
		return mTotalSectors;
	}


	public long getMftStartLcn()
	{
		return mMftStartLcn;
	}


	public long getMft2StartLcn()
	{
		return mMft2StartLcn;
	}


	public int getClustersPerMftRecord()
	{
		return mClustersPerMftRecord;
	}


	public int getClustersPerIndexRecord()
	{
		return mClustersPerIndexRecord;
	}


	public long getBytesPerMftRecord()
	{
		return mBytesPerMftRecord;
	}


	public long getBytesPerCluster()
	{
		return mBytesPerCluster;
	}


	public long getTotalClusters()
	{
		return mTotalClusters;
	}
}
