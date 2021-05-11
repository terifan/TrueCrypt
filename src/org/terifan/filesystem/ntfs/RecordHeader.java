package org.terifan.filesystem.ntfs;


/**
 * SerializedStruct - do not modify!
 */
class RecordHeader
{
	public int mType;              // File type, for example 'FILE'
	public short mUsaOffset;       // Offset to the Update Sequence Array
	public short mUsaCount;        // Size in words of Update Sequence Array
	public long mLsn;              // $LogFile Sequence Number (LSN)
}
