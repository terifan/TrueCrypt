package org.terifan.filesystem.ntfs;


/// Simple structure of available disk informations.
class DiskInfoWrapper implements IDiskInfo
{
	public short BytesPerSector; // UInt16
	public byte SectorsPerCluster;
	public long TotalSectors; // UInt64
	public long MftStartLcn; // UInt64
	public long Mft2StartLcn; // UInt64
	public int ClustersPerMftRecord; // UInt32
	public int ClustersPerIndexRecord; // UInt32
	public long BytesPerMftRecord; // UInt64
	public long BytesPerCluster; // UInt64
	public long TotalClusters; // UInt64

	public int getBytesPerSector()
	{
		return BytesPerSector;
	}

	public byte getSectorsPerCluster()
	{
		return SectorsPerCluster;
	}

	public long getTotalSectors()
	{
		return TotalSectors;
	}

	public long getMftStartLcn()
	{
		return MftStartLcn;
	}

	public long getMft2StartLcn()
	{
		return Mft2StartLcn;
	}

	public int getClustersPerMftRecord()
	{
		return ClustersPerMftRecord;
	}

	public int getClustersPerIndexRecord()
	{
		return ClustersPerIndexRecord;
	}

	public long getBytesPerMftRecord()
	{
		return BytesPerMftRecord;
	}

	public long getBytesPerCluster()
	{
		return BytesPerCluster;
	}

	public long getTotalClusters()
	{
		return TotalClusters;
	}
}
