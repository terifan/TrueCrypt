package org.terifan.filesystem.ntfs;


enum AttributeType
{
	AttributeInvalid(0x00),         /* Not defined by Windows */
	AttributeStandardInformation(0x10),
	AttributeAttributeList(0x20),
	AttributeFileName(0x30),
	AttributeObjectId(0x40),
	AttributeSecurityDescriptor(0x50),
	AttributeVolumeName(0x60),
	AttributeVolumeInformation(0x70),
	AttributeData(0x80),
	AttributeIndexRoot(0x90),
	AttributeIndexAllocation(0xA0),
	AttributeBitmap(0xB0),
	AttributeReparsePoint(0xC0),         /* Reparse Point(Symbolic link */
	AttributeEAInformation(0xD0),
	AttributeEA(0xE0),
	AttributePropertySet(0xF0),
	AttributeLoggedUtilityStream(0x100);

	final int CODE;


	private AttributeType(int aCode)
	{
		CODE = aCode;
	}


	static AttributeType decode(int aAttributeType)
	{
		for (AttributeType at : values())
		{
			if (at.CODE == aAttributeType)
			{
				return at;
			}
		}

		throw new IllegalArgumentException();
	}
}