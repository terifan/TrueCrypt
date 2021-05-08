package org.terifan.filesystem.ntfs;


class BootSector
{
	byte[] AlignmentOrReserved1 = new byte[3];
	public long Signature; // UInt64
	public short BytesPerSector; // UInt16
	public byte SectorsPerCluster;
	byte[] AlignmentOrReserved2 = new byte[26];
	public long TotalSectors; // UInt64
	public long MftStartLcn; // UInt64
	public long Mft2StartLcn; // UInt64
	public int ClustersPerMftRecord; // UInt32
	public int ClustersPerIndexRecord; // UInt32
}
