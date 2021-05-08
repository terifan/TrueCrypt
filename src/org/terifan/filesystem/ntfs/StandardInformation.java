package org.terifan.filesystem.ntfs;


// Contains extra information not required for basic purposes.
class StandardInformation
{
	public long CreationTime; // UInt64
	public long LastAccessTime; // UInt64
	public long LastChangeTime; // UInt64

	public StandardInformation(long creationTime, long lastAccessTime, long lastChangeTime)
	{
		CreationTime = creationTime;
		LastAccessTime = lastAccessTime;
		LastChangeTime = lastChangeTime;
	}
}
