package log.charter.util;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import log.charter.io.Logger;

/**
 * Opens the native Windows Explorer file/folder picker via the IFileOpenDialog
 * COM interface.
 *
 * Every call assigns a freshly-generated client GUID, which prevents Windows
 * from applying its persisted MRU folder for the dialog. This guarantees that
 * SetFolder() is always honoured – i.e. the dialog always opens in the
 * requested project folder regardless of where the user last browsed.
 */
final class WinFileDialog {

	// -----------------------------------------------------------------------
	// Structures
	// -----------------------------------------------------------------------

	@Structure.FieldOrder({ "Data1", "Data2", "Data3", "Data4" })
	public static class GUID extends Structure {
		public int Data1;
		public short Data2;
		public short Data3;
		public byte[] Data4 = new byte[8];

		public GUID() {
		}

		/** Parse a registry-style GUID: {xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx} */
		public GUID(final String s) {
			final String h = s.replace("{", "").replace("}", "").replace("-", "");
			Data1 = (int) Long.parseLong(h.substring(0, 8), 16);
			Data2 = (short) Integer.parseInt(h.substring(8, 12), 16);
			Data3 = (short) Integer.parseInt(h.substring(12, 16), 16);
			for (int i = 0; i < 8; i++) {
				Data4[i] = (byte) Integer.parseInt(h.substring(16 + 2 * i, 18 + 2 * i), 16);
			}
			write();
		}
	}

	@Structure.FieldOrder({ "pszName", "pszSpec" })
	public static class COMDLG_FILTERSPEC extends Structure {
		public Pointer pszName;
		public Pointer pszSpec;
	}

	// -----------------------------------------------------------------------
	// Native libraries
	// -----------------------------------------------------------------------

	interface Ole32 extends StdCallLibrary {
		Ole32 INSTANCE = Native.load("ole32", Ole32.class, W32APIOptions.DEFAULT_OPTIONS);

		int CoCreateInstance(GUID rclsid, Pointer pUnkOuter, int dwClsContext, GUID riid, PointerByReference ppv);

		void CoTaskMemFree(Pointer pv);
	}

	interface Shell32 extends StdCallLibrary {
		Shell32 INSTANCE = Native.load("shell32", Shell32.class, W32APIOptions.DEFAULT_OPTIONS);

		int SHCreateItemFromParsingName(WString pszPath, Pointer pbc, GUID riid, PointerByReference ppv);
	}

	// -----------------------------------------------------------------------
	// Constants
	// -----------------------------------------------------------------------

	private static final GUID CLSID_FileOpenDialog = new GUID("{DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7}");
	private static final GUID IID_IFileOpenDialog = new GUID("{D57C7288-D4AD-4768-BE02-9D969532D960}");
	private static final GUID IID_IShellItem = new GUID("{43826D1E-E718-42EE-BC55-A1E261C37BFE}");

	private static final int CLSCTX_INPROC_SERVER = 1;
	private static final int S_OK = 0;
	private static final int FOS_PICKFOLDERS = 0x00000020;
	private static final int SIGDN_FILESYSPATH = 0x80058000;

	// IFileDialog vtable indices
	private static final int VT_Release = 2;
	private static final int VT_Show = 3;
	private static final int VT_SetFileTypes = 4;
	private static final int VT_SetOptions = 9;
	private static final int VT_SetFolder = 12;
	private static final int VT_GetResult = 20;
	private static final int VT_SetClientGuid = 24;

	// IShellItem vtable index
	private static final int VT_SI_GetDisplayName = 5;

	// -----------------------------------------------------------------------
	// Helpers
	// -----------------------------------------------------------------------

	private static Function vtMethod(final Pointer pObj, final int index) {
		final Pointer vtbl = pObj.getPointer(0);
		return Function.getFunction(vtbl.getPointer((long) index * Native.POINTER_SIZE), Function.ALT_CONVENTION);
	}

	private static void release(final Pointer pObj) {
		if (pObj != null) {
			vtMethod(pObj, VT_Release).invoke(Integer.class, new Object[] { pObj });
		}
	}

	/** Allocates a null-terminated UTF-16LE string in native memory. */
	private static Memory wstr(final String s) {
		final byte[] bytes = s.getBytes(StandardCharsets.UTF_16LE);
		final Memory m = new Memory(bytes.length + 2);
		m.write(0, bytes, 0, bytes.length);
		m.setShort(bytes.length, (short) 0);
		return m;
	}

	private static GUID randomGuid() {
		return new GUID("{" + UUID.randomUUID() + "}");
	}

	// -----------------------------------------------------------------------
	// Public API
	// -----------------------------------------------------------------------

	/**
	 * Shows the native Windows file/folder picker.
	 *
	 * @param startDir   directory to open in (always honoured – bypasses MRU)
	 * @param filters    { {"Display name", "*.ext1;*.ext2"}, ... } or null
	 * @param pickFolder true for folder picker, false for file picker
	 * @return selected File, or null if cancelled / COM unavailable
	 */
	static File open(final File startDir, final String[][] filters, final boolean pickFolder) {
		try {
			return openInternal(startDir, filters, pickFolder);
		} catch (final Exception e) {
			Logger.error("WinFileDialog failed, caller should fall back to JFileChooser", e);
			return null;
		}
	}

	private static File openInternal(final File startDir, final String[][] filters, final boolean pickFolder)
			throws Exception {
		final PointerByReference ppDialog = new PointerByReference();
		if (Ole32.INSTANCE.CoCreateInstance(CLSID_FileOpenDialog, null, CLSCTX_INPROC_SERVER, IID_IFileOpenDialog,
				ppDialog) != S_OK) {
			return null;
		}
		final Pointer pDialog = ppDialog.getValue();
		try {
			// Fresh GUID → Windows has no MRU → SetFolder is always honoured
			vtMethod(pDialog, VT_SetClientGuid).invoke(Integer.class,
					new Object[] { pDialog, randomGuid().getPointer() });

			// Set initial folder
			if (startDir != null && startDir.isDirectory()) {
				final PointerByReference ppFolder = new PointerByReference();
				if (Shell32.INSTANCE.SHCreateItemFromParsingName(new WString(startDir.getAbsolutePath()), null,
						IID_IShellItem, ppFolder) == S_OK) {
					final Pointer pFolder = ppFolder.getValue();
					vtMethod(pDialog, VT_SetFolder).invoke(Integer.class, new Object[] { pDialog, pFolder });
					release(pFolder);
				}
			}

			// Folder-picker mode
			if (pickFolder) {
				vtMethod(pDialog, VT_SetOptions).invoke(Integer.class,
						new Object[] { pDialog, FOS_PICKFOLDERS });
			}

			// File type filters – keep Memory refs alive until after Show()
			Memory[] nameMems = null;
			Memory[] specMems = null;
			if (!pickFolder && filters != null && filters.length > 0) {
				final COMDLG_FILTERSPEC[] specs = (COMDLG_FILTERSPEC[]) new COMDLG_FILTERSPEC()
						.toArray(filters.length);
				nameMems = new Memory[filters.length];
				specMems = new Memory[filters.length];
				for (int i = 0; i < filters.length; i++) {
					nameMems[i] = wstr(filters[i][0]);
					specMems[i] = wstr(filters[i][1]);
					specs[i].pszName = nameMems[i];
					specs[i].pszSpec = specMems[i];
					specs[i].write();
				}
				vtMethod(pDialog, VT_SetFileTypes).invoke(Integer.class,
						new Object[] { pDialog, filters.length, specs[0].getPointer() });
			}

			// Show dialog (blocks until user picks or cancels)
			final int showHr = (Integer) vtMethod(pDialog, VT_Show).invoke(Integer.class,
					new Object[] { pDialog, null });

			// Safe to release filter string memory now
			if (nameMems != null) {
				for (final Memory m : nameMems) {
					m.close();
				}
				for (final Memory m : specMems) {
					m.close();
				}
			}

			if (showHr != S_OK) {
				return null; // user cancelled
			}

			// Get result IShellItem
			final PointerByReference ppItem = new PointerByReference();
			if ((Integer) vtMethod(pDialog, VT_GetResult).invoke(Integer.class,
					new Object[] { pDialog, ppItem }) != S_OK) {
				return null;
			}
			final Pointer pItem = ppItem.getValue();
			try {
				final PointerByReference ppName = new PointerByReference();
				if ((Integer) vtMethod(pItem, VT_SI_GetDisplayName).invoke(Integer.class,
						new Object[] { pItem, SIGDN_FILESYSPATH, ppName }) != S_OK) {
					return null;
				}
				final Pointer pName = ppName.getValue();
				final String path = pName.getWideString(0);
				Ole32.INSTANCE.CoTaskMemFree(pName);
				return new File(path);
			} finally {
				release(pItem);
			}
		} finally {
			release(pDialog);
		}
	}

	private WinFileDialog() {
	}
}
