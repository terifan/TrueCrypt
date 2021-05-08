package org.terifan.filesystem.ntfs;


class RecordHeader
{
	public int Type;              /* File type, for example 'FILE' */
	public short UsaOffset; // UInt16             /* Offset to the Update Sequence Array */
	public short UsaCount; // UInt16              /* Size in words of Update Sequence Array */
	public long Lsn; // UInt64                   /* $LogFile Sequence Number (LSN) */
}
