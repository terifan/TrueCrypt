package org.terifan.filesystem.ntfs;


/**
 * SerializedStruct - do not modify!
 */
class VolumeData
{
	public long mVolumeSerialNumber;
	public long mNumberSectors;
	public long mTotalClusters;
	public long mFreeClusters;
	public long mTotalReserved;
	public int mBytesPerSector;
	public int mBytesPerCluster;
	public int mBytesPerFileRecordSegment;
	public int mClustersPerFileRecordSegment;
	public long mMftValidDataLength;
	public long mMftStartLcn;
	public long mMft2StartLcn;
	public long mMftZoneStart;
	public long mMftZoneEnd;
}
