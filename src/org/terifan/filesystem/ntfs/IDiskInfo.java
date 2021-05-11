package org.terifan.filesystem.ntfs;


public interface IDiskInfo
{
	int getBytesPerSector();


	byte getSectorsPerCluster();


	long getTotalSectors();


	long getMftStartLcn();


	long getMft2StartLcn();


	int getClustersPerMftRecord();


	int getClustersPerIndexRecord();


	long getBytesPerMftRecord();


	long getBytesPerCluster();


	long getTotalClusters();
}
