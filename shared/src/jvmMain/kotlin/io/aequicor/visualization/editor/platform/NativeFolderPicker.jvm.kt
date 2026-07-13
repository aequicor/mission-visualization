package io.aequicor.visualization.editor.platform

import java.awt.GraphicsEnvironment
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import javax.swing.JFileChooser

/**
 * Opens the operating system's folder picker where one is available.
 *
 * Swing does not delegate directory selection to the Windows shell, so Windows uses the modern
 * Common Item Dialog (`IFileOpenDialog` + `FOS_PICKFOLDERS`) in a short-lived STA PowerShell
 * process. Other desktop systems retain the portable Swing chooser as a fallback.
 */
fun chooseNativeFolder(title: String, initialDirectory: Path? = null): Path? {
    if (GraphicsEnvironment.isHeadless()) return null

    val initial = initialDirectory
        ?.takeIf(Files::isDirectory)
        ?.toAbsolutePath()
        ?.normalize()

    if (System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) {
        val result = chooseWindowsFolder(title, initial)
        if (result.dialogOpened) return result.selectedPath
    }

    return chooseSwingFolder(title, initial)
}

private data class NativeFolderResult(
    val dialogOpened: Boolean,
    val selectedPath: Path? = null,
)

private fun chooseWindowsFolder(title: String, initialDirectory: Path?): NativeFolderResult {
    val script = buildString {
        appendLine("try {")
        appendLine("  Add-Type -TypeDefinition @'")
        appendLine(windowsFolderPickerTypeDefinition)
        appendLine("'@")
        appendLine("  [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding(${'$'}false)")
        val initialPath = initialDirectory?.toString().orEmpty().asPowerShellLiteral()
        appendLine(
            "  ${'$'}selected = [MissionVisualizationFolderPicker]::Pick(" +
                "'${title.asPowerShellLiteral()}', '$initialPath')",
        )
        appendLine("  if (${'$'}null -ne ${'$'}selected) {")
        appendLine("    [Console]::Out.Write(${'$'}selected)")
        appendLine("  }")
        appendLine("  exit 0")
        appendLine("} catch {")
        appendLine("  exit 1")
        appendLine("}")
    }
    val encodedScript = Base64.getEncoder().encodeToString(script.toByteArray(StandardCharsets.UTF_16LE))

    return runCatching {
        val process = ProcessBuilder(
            "powershell.exe",
            "-NoLogo",
            "-NoProfile",
            "-NonInteractive",
            "-STA",
            "-WindowStyle",
            "Hidden",
            "-EncodedCommand",
            encodedScript,
        )
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        if (process.waitFor() != 0) {
            NativeFolderResult(dialogOpened = false)
        } else {
            NativeFolderResult(
                dialogOpened = true,
                selectedPath = output.takeIf(String::isNotEmpty)?.let(Path::of),
            )
        }
    }.getOrDefault(NativeFolderResult(dialogOpened = false))
}

private fun chooseSwingFolder(title: String, initialDirectory: Path?): Path? {
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
        initialDirectory?.toFile()?.let { currentDirectory = it }
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.toPath()
    } else {
        null
    }
}

private fun String.asPowerShellLiteral(): String = replace("'", "''")

private val windowsFolderPickerTypeDefinition =
    """
    using System;
    using System.Runtime.InteropServices;

    public static class MissionVisualizationFolderPicker
    {
        private const uint FOS_PICKFOLDERS = 0x00000020;
        private const uint FOS_FORCEFILESYSTEM = 0x00000040;
        private const uint FOS_PATHMUSTEXIST = 0x00000800;
        private const uint SIGDN_FILESYSPATH = 0x80058000;
        private const int HRESULT_CANCELLED = unchecked((int)0x800704C7);

        [ComImport]
        [Guid("DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7")]
        private class FileOpenDialog
        {
        }

        [ComImport]
        [Guid("42F85136-DB7E-439C-85F1-E4075D135FC8")]
        [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IFileDialog
        {
            [PreserveSig] int Show(IntPtr parent);
            [PreserveSig] int SetFileTypes(uint count, IntPtr filterSpecs);
            [PreserveSig] int SetFileTypeIndex(uint fileType);
            [PreserveSig] int GetFileTypeIndex(out uint fileType);
            [PreserveSig] int Advise(IntPtr events, out uint cookie);
            [PreserveSig] int Unadvise(uint cookie);
            [PreserveSig] int SetOptions(uint options);
            [PreserveSig] int GetOptions(out uint options);
            [PreserveSig] int SetDefaultFolder([MarshalAs(UnmanagedType.Interface)] IShellItem shellItem);
            [PreserveSig] int SetFolder([MarshalAs(UnmanagedType.Interface)] IShellItem shellItem);
            [PreserveSig] int GetFolder([MarshalAs(UnmanagedType.Interface)] out IShellItem shellItem);
            [PreserveSig] int GetCurrentSelection([MarshalAs(UnmanagedType.Interface)] out IShellItem shellItem);
            [PreserveSig] int SetFileName([MarshalAs(UnmanagedType.LPWStr)] string name);
            [PreserveSig] int GetFileName(out IntPtr name);
            [PreserveSig] int SetTitle([MarshalAs(UnmanagedType.LPWStr)] string title);
            [PreserveSig] int SetOkButtonLabel([MarshalAs(UnmanagedType.LPWStr)] string text);
            [PreserveSig] int SetFileNameLabel([MarshalAs(UnmanagedType.LPWStr)] string label);
            [PreserveSig] int GetResult([MarshalAs(UnmanagedType.Interface)] out IShellItem shellItem);
            [PreserveSig] int AddPlace([MarshalAs(UnmanagedType.Interface)] IShellItem shellItem, uint alignment);
            [PreserveSig] int SetDefaultExtension([MarshalAs(UnmanagedType.LPWStr)] string extension);
            [PreserveSig] int Close(int errorCode);
            [PreserveSig] int SetClientGuid(ref Guid clientGuid);
            [PreserveSig] int ClearClientData();
            [PreserveSig] int SetFilter(IntPtr filter);
        }

        [ComImport]
        [Guid("43826D1E-E718-42EE-BC55-A1E261C37BFE")]
        [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IShellItem
        {
            [PreserveSig] int BindToHandler(IntPtr bindContext, ref Guid handlerId, ref Guid interfaceId, out IntPtr result);
            [PreserveSig] int GetParent([MarshalAs(UnmanagedType.Interface)] out IShellItem shellItem);
            [PreserveSig] int GetDisplayName(uint displayNameType, out IntPtr name);
            [PreserveSig] int GetAttributes(uint mask, out uint attributes);
            [PreserveSig] int Compare([MarshalAs(UnmanagedType.Interface)] IShellItem shellItem, uint hint, out int order);
        }

        [DllImport("shell32.dll", CharSet = CharSet.Unicode, PreserveSig = true)]
        private static extern int SHCreateItemFromParsingName(
            [MarshalAs(UnmanagedType.LPWStr)] string path,
            IntPtr bindContext,
            ref Guid interfaceId,
            [MarshalAs(UnmanagedType.Interface)] out IShellItem shellItem);

        [DllImport("user32.dll")]
        private static extern IntPtr GetForegroundWindow();

        public static string Pick(string title, string initialDirectory)
        {
            IFileDialog dialog = null;
            IShellItem initialItem = null;
            IShellItem selectedItem = null;
            IntPtr selectedPath = IntPtr.Zero;

            try
            {
                dialog = (IFileDialog)new FileOpenDialog();

                uint options;
                Marshal.ThrowExceptionForHR(dialog.GetOptions(out options));
                options |= FOS_PICKFOLDERS | FOS_FORCEFILESYSTEM | FOS_PATHMUSTEXIST;
                Marshal.ThrowExceptionForHR(dialog.SetOptions(options));
                Marshal.ThrowExceptionForHR(dialog.SetTitle(title));

                if (!String.IsNullOrEmpty(initialDirectory))
                {
                    Guid shellItemId = typeof(IShellItem).GUID;
                    Marshal.ThrowExceptionForHR(
                        SHCreateItemFromParsingName(initialDirectory, IntPtr.Zero, ref shellItemId, out initialItem));
                    Marshal.ThrowExceptionForHR(dialog.SetDefaultFolder(initialItem));
                }

                int showResult = dialog.Show(GetForegroundWindow());
                if (showResult == HRESULT_CANCELLED)
                {
                    return null;
                }
                Marshal.ThrowExceptionForHR(showResult);

                Marshal.ThrowExceptionForHR(dialog.GetResult(out selectedItem));
                Marshal.ThrowExceptionForHR(selectedItem.GetDisplayName(SIGDN_FILESYSPATH, out selectedPath));
                return Marshal.PtrToStringUni(selectedPath);
            }
            finally
            {
                if (selectedPath != IntPtr.Zero)
                {
                    Marshal.FreeCoTaskMem(selectedPath);
                }
                if (selectedItem != null)
                {
                    Marshal.ReleaseComObject(selectedItem);
                }
                if (initialItem != null)
                {
                    Marshal.ReleaseComObject(initialItem);
                }
                if (dialog != null)
                {
                    Marshal.FinalReleaseComObject(dialog);
                }
            }
        }
    }
    """.trimIndent()
