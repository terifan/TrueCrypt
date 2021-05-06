package org.terifan.filesystem.ntfs;


public enum Attributes
{
	// The file is read-only.
	ReadOnly,
	// The file is hidden, and thus is not included in an ordinary directory listing.
	Hidden,
	// The file is a system file. The file is part of the operating system or is
	// used exclusively by the operating system.
	System,
	// The file is a directory.
	Directory,
	// The file's archive status. Applications use this attribute to mark files
	// for backup or removal.
	Archive,
	// Reserved for future use.
	Device,
	// The file is normal and has no other attributes set. This attribute is valid
	// only if used alone.
	Normal,
	// The file is temporary. File systems attempt to keep all of the data in memory
	// for quicker access rather than flushing the data back to mass storage. A
	// temporary file should be deleted by the application as soon as it is no longer
	// needed.
	Temporary,
	// The file is a sparse file. Sparse files are typically large files whose data
	// are mostly zeros.
	SparseFile,
	// The file contains a reparse point, which is a block of user-defined data
	// associated with a file or a directory.
	ReparsePoint,
	// The file is compressed.
	Compressed,
	// The file is offline. The data of the file is not immediately available.
	Offline,
	// The file will not be indexed by the operating system's content indexing service.
	NotContentIndexed,
	// The file or directory is encrypted. For a file, this means that all data
	// in the file is encrypted. For a directory, this means that encryption is
	// the default for newly created files and directories.
	Encrypted;

	public boolean isSet(int aAttributes)
	{
		return (aAttributes & (1 << ordinal())) != 0;
	}
}
