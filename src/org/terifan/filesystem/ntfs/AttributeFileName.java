package org.terifan.filesystem.ntfs;


class AttributeFileName
{
	public NodeReference ParentDirectory;
	public long CreationTime; // UInt64
	public long ChangeTime; // UInt64
	public long LastWriteTime; // UInt64
	public long LastAccessTime; // UInt64
	public long AllocatedSize; // UInt64
	public long DataSize; // UInt64
	public int FileAttributes; // UInt32
	public int AlignmentOrReserved; // UInt32
	public byte NameLength;
	public byte NameType;                 /* NTFS=0x01, DOS=0x02 */
	@Unmarshaller.Hint(length = "NameLength") public char[] Name;
}