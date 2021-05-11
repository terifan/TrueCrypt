package org.terifan.filesystem.ntfs;


/**
 * SerializedStruct - do not modify!
 */
class FileRecordHeader
{
	public RecordHeader mRecordHeader;
	public short mSequenceNumber;        // Sequence number
	public short mLinkCount;             // Hard link count
	public short mAttributeOffset;       // Offset to the first Attribute
	public short mFlags;                 // Flags. bit 1 = in use, bit 2 = directory, bit 4 & 8 = unknown.
	public int mBytesInUse;             // Real size of the FILE record
	public int mBytesAllocated;         // Allocated size of the FILE record
	public NodeReference mBaseFileRecord;     // File reference to the base FILE record
	public short mNextAttributeNumber;   // Next Attribute Id
	public short mPadding;               // Align to 4 UCHAR boundary (XP)
	public int mMFTRecordNumber;        // Number of this MFT Record (XP)
	public short mUpdateSeqNum;
};
