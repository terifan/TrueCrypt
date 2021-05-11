package org.terifan.filesystem.ntfs;


/**
 * SerializedStruct - do not modify!
 */
class BootSector
{
	byte[] mAlignmentOrReserved1 = new byte[3];
	public long mSignature;
	public short mBytesPerSector;
	public byte mSectorsPerCluster;
	byte[] mAlignmentOrReserved2 = new byte[26];
	public long mTotalSectors;
	public long mMftStartLcn;
	public long mMft2StartLcn;
	public int mClustersPerMftRecord;
	public int mClustersPerIndexRecord;
}
