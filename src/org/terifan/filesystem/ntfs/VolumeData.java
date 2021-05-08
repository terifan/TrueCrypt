package org.terifan.filesystem.ntfs;


class VolumeData
{
	public long VolumeSerialNumber; // UInt64
	public long NumberSectors; // UInt64
	public long TotalClusters; // UInt64
	public long FreeClusters; // UInt64
	public long TotalReserved; // UInt64
	public int BytesPerSector; // UInt32
	public int BytesPerCluster; // UInt32
	public int BytesPerFileRecordSegment; // UInt32
	public int ClustersPerFileRecordSegment; // UInt32
	public long MftValidDataLength; // UInt64
	public long MftStartLcn; // UInt64
	public long Mft2StartLcn; // UInt64
	public long MftZoneStart; // UInt64
	public long MftZoneEnd; // UInt64
}
