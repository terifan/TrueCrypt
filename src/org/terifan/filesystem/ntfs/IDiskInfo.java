package org.terifan.filesystem.ntfs;

// Disk information

public interface IDiskInfo
{
	int getBytesPerSector();


	byte getSectorsPerCluster();


	long getTotalSectors();


	long getMftStartLcn();


	long getMft2StartLcn();


	long getClustersPerMftRecord();


	long getClustersPerIndexRecord();


	long getBytesPerMftRecord();


	long getBytesPerCluster();


	long getTotalClusters();
}
