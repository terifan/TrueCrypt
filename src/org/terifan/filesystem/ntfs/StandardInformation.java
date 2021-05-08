package org.terifan.filesystem.ntfs;


// Contains extra information not required for basic purposes.
class StandardInformation
{
	public DateTime CreationTime; // UInt64
	public DateTime LastAccessTime; // UInt64
	public DateTime LastChangeTime; // UInt64

	public StandardInformation(DateTime creationTime, DateTime lastAccessTime, DateTime lastChangeTime)
	{
		CreationTime = creationTime;
		LastAccessTime = lastAccessTime;
		LastChangeTime = lastChangeTime;
	}
}
