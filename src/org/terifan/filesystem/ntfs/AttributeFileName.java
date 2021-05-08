package org.terifan.filesystem.ntfs;


class AttributeFileName
{
	public NodeReference ParentDirectory;
	public DateTime CreationTime; // UInt64
	public DateTime ChangeTime; // UInt64
	public DateTime LastWriteTime; // UInt64
	public DateTime LastAccessTime; // UInt64
	public long AllocatedSize; // UInt64
	public long DataSize; // UInt64
	public int FileAttributes; // UInt32
	public int AlignmentOrReserved; // UInt32
	public byte NameLength;
	public byte NameType;                 /* NTFS=0x01, DOS=0x02 */
	@Unmarshaller.Hint(length = "NameLength", format=Unmarshaller.Format.UNICODE) public String Name;
}