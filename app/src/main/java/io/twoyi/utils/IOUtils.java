/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.twoyi.utils;

import android.text.TextUtils;

import androidx.annotation.Keep;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * @author weishu
 * @date 2018/8/28.
 */
@Keep
public class IOUtils {

    public static void ensureCreated(File file) {
        if (!file.exists()) {
            boolean ret = file.mkdirs();
            if (!ret) {
                throw new RuntimeException("create dir: " + file + " failed");
            }
        }
    }

    public static boolean deleteDir(File dir) {
        if (dir == null) {
            return false;
        }
        boolean success = true;
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (String file : children) {
                boolean ret = deleteDir(new File(dir, file));
                if (!ret) {
                    success = false;
                }
            }
            if (success) {
                // if all subdirectory are deleted, delete the dir itself.
                return dir.delete();
            }
        }
        return dir.delete();
    }

    public static void deleteAll(List<File> files) {
        if (files.isEmpty()) {
            return;
        }

        for (File file : files) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    public static void copyFile(File source, File target) throws IOException {
        FileInputStream inputStream = null;
        FileOutputStream outputStream = null;
        try {
            inputStream = new FileInputStream(source);
            outputStream = new FileOutputStream(target);
            FileChannel iChannel = inputStream.getChannel();
            FileChannel oChannel = outputStream.getChannel();

            ByteBuffer buffer = ByteBuffer.allocate(1024);
            while (true) {
                buffer.clear();
                int r = iChannel.read(buffer);
                if (r == -1)
                    break;
                buffer.limit(buffer.position());
                buffer.position(0);
                oChannel.write(buffer);
            }
        } finally {
            closeSilently(inputStream);
            closeSilently(outputStream);
        }
    }

    public static void closeSilently(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            // e.printStackTrace();
        }
    }

    public static void setPermissions(String path, int mode, int uid, int gid) {
        try {
            Class<?> fileUtilsClass = Class.forName("android.os.FileUtils");
            Method setPermissions = fileUtilsClass.getDeclaredMethod("setPermissions", String.class, int.class, int.class, int.class);
            setPermissions.setAccessible(true);
            setPermissions.invoke(null, path, mode, uid, gid);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void writeContent(File file, String content) {
        if (file == null || TextUtils.isEmpty(content)) {
            return;
        }
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(file);
            fileWriter.write(content);
            fileWriter.flush();
        } catch (Throwable ignored) {
        } finally {
            IOUtils.closeSilently(fileWriter);
        }
    }

    public static String readContent(File file) {
        if (file == null) {
            return null;
        }
        BufferedReader fileReader = null;
        try {
            fileReader = new BufferedReader(new FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = fileReader.readLine()) != null) {
                sb.append(line);
                sb.append('\n');
            }
            return sb.toString().trim();
        } catch (Throwable ignored) {
            return null;
        } finally {
            IOUtils.closeSilently(fileReader);
        }
    }

    public static boolean deleteDirectory(File directory) {
        try {
            Files.walk(directory.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Copy file using Files.copy
     */
    public static void copy(InputStream inputStream, File file) throws IOException {
        Files.copy(inputStream, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Calculate MD5 hash of a file
     */
    public static byte[] md5(File file) throws IOException {
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            try {
                byte[] buffer = new byte[1024];
                java.security.MessageDigest messageDigest = java.security.MessageDigest.getInstance("MD5");
                int bytesRead;
                do {
                    bytesRead = fileInputStream.read(buffer);
                    if (bytesRead > 0) {
                        messageDigest.update(buffer, 0, bytesRead);
                    }
                } while (bytesRead != -1);
                byte[] digest = messageDigest.digest();
                fileInputStream.close();
                return digest;
            } catch (Throwable th) {
                try {
                    fileInputStream.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
                throw th;
            }
        } catch (IOException | java.security.NoSuchAlgorithmException unused) {
            return null;
        }
    }

    /**
     * Get MD5 sum as string
     */
    public static String md5sum(File file) throws IOException {
        byte[] digest = md5(file);
        if (digest == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(Integer.toString((b & 255) + 256, 16).substring(1));
        }
        return sb.toString();
    }

    /**
     * Unzip a file to destination
     */
    public static void unzip(File zipFile, File destDir) throws IOException {
        java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        byte[] buffer = new byte[16384];
        java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            java.util.zip.ZipEntry entry = entries.nextElement();
            File entryFile = new File(destDir, entry.getName());
            entryFile.getParentFile().mkdirs();
            if (!entry.isDirectory()) {
                java.io.BufferedInputStream inputStream = new java.io.BufferedInputStream(zip.getInputStream(entry));
                java.io.BufferedOutputStream outputStream = new java.io.BufferedOutputStream(new FileOutputStream(entryFile));
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
                outputStream.close();
                inputStream.close();
            }
        }
        zip.close();
    }

    /**
     * Interface for download progress callback
     */
    public interface DownloadProgressCallback {
        void onProgress(int progress);
    }

    /**
     * Download file with progress callback
     */
    public static boolean downloadFile(String url, File destFile, DownloadProgressCallback progressCallback) {
        // Create a simple implementation that downloads using HttpURLConnection
        // Note: This is a simplified version replacing the complex networking implementation
        try {
            java.net.URL downloadUrl = new java.net.URL(url);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) downloadUrl.openConnection();

            // Set request properties
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "Twoyi/0.7.5");

            if (connection.getResponseCode() != java.net.HttpURLConnection.HTTP_OK) {
                return false;
            }

            long contentLength = connection.getContentLength();
            java.io.InputStream inputStream = connection.getInputStream();
            java.io.FileOutputStream fileOutputStream = new java.io.FileOutputStream(destFile);

            byte[] buffer = new byte[8192];
            long totalBytesRead = 0;
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;

                if (contentLength > 0 && progressCallback != null) {
                    int progress = (int) ((totalBytesRead * 100) / contentLength);
                    progressCallback.onProgress(progress);
                }
            }

            fileOutputStream.flush();
            fileOutputStream.close();
            inputStream.close();
            connection.disconnect();

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Write buffer to file descriptor completely
     */
    public static void writeFully(java.io.FileDescriptor fileDescriptor, java.nio.ByteBuffer byteBuffer) throws IOException {
        try {
            java.lang.reflect.Method writeMethod = android.system.Os.class.getDeclaredMethod("write",
                java.io.FileDescriptor.class, java.nio.ByteBuffer.class);
            writeMethod.setAccessible(true);

            int remaining = byteBuffer.remaining();
            while (remaining > 0) {
                Integer result = (Integer) writeMethod.invoke(null, fileDescriptor, byteBuffer);
                remaining -= result;
            }
        } catch (Exception e) {
            // Fallback using traditional method
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(fileDescriptor);
            fos.write(bytes);
            fos.close();
        }
    }

    /**
     * Write bytes to file descriptor completely
     */
    public static void writeFully(java.io.FileDescriptor fileDescriptor, byte[] bytes, int offset, int length) throws IOException {
        writeFully(fileDescriptor, java.nio.ByteBuffer.wrap(bytes, offset, length));
    }
}
