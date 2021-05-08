package org.terifan.filesystem.ntfs;


class FileRecordHeader
{
	public RecordHeader RecordHeader;
	public short SequenceNumber; // UInt16        /* Sequence number */
	public short LinkCount; // UInt16             /* Hard link count */
	public short AttributeOffset; // UInt16       /* Offset to the first Attribute */
	public short Flags; // UInt16                 /* Flags. bit 1 = in use, bit 2 = directory, bit 4 & 8 = unknown. */
	public int BytesInUse; // UInt32             /* Real size of the FILE record */
	public int BytesAllocated; // UInt32         /* Allocated size of the FILE record */
	public NodeReference BaseFileRecord;     /* File reference to the base FILE record */
	public short NextAttributeNumber; // UInt16   /* Next Attribute Id */
	public short Padding; // UInt16               /* Align to 4 UCHAR boundary (XP) */
	public int MFTRecordNumber; // UInt32        /* Number of this MFT Record (XP) */
	public short UpdateSeqNum; // UInt16          /*  */
};
