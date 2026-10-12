/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.util;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.skcraft.launcher.model.modpack.DownloadableFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileUtils {
	public static DownloadableFile saveStreamToObjectsDir(InputStream stream, File outputDir) throws IOException {
		byte[] input = ByteStreams.toByteArray(stream);
		HashFunction hf = Hashing.sha1();

		String fileHash = hf.hashBytes(input).toString();
		String filePath = fileHash.substring(0, 2) + "/" + fileHash.substring(2, 4) + "/" + fileHash;

		File dest = new File(outputDir, filePath);
		dest.getParentFile().mkdirs();

		Files.write(input, dest);

		DownloadableFile entry = new DownloadableFile();
		entry.setLocation(filePath);
		entry.setHash(fileHash);
		entry.setSize(input.length);

		return entry;
	}

	public static String getShaHash(File file) throws IOException {
		FileInputStream stream = new FileInputStream(file);
		byte[] input = ByteStreams.toByteArray(stream);
		String res = Hashing.sha1().hashBytes(input).toString();

		stream.close();
		return res;
	}

	public static String getMD5Hash(String path) {
		MessageDigest md;
		try { md = MessageDigest.getInstance("MD5"); }
		catch(NoSuchAlgorithmException ex) {
			System.out.println("FileUtils.getMD5Hash(): "+ex);
			return null;// TODO better error handling
		}
		try(FileChannel fch = FileChannel.open(Paths.get(path), StandardOpenOption.READ)) {
			for(long pos = 0, rem = fch.size(), chunk; rem>pos; pos+=chunk) {
				chunk = Math.min(Integer.MAX_VALUE, rem-pos);
				md.update(fch.map(FileChannel.MapMode.READ_ONLY, pos, chunk));
			}
		} catch(IOException e){
			System.out.println("Having problem with file: " + path);
			return null;// TODO better error handling
		}
		return String.format("%032X", new BigInteger(1, md.digest()));
	}

	public static boolean isDirEmpty(File path) {
		File folder = new File(String.valueOf(path));
		File[] listOfFiles = folder.listFiles();
		if (listOfFiles != null && listOfFiles.length > 0) {
			// The array isn't empty.
			return false;
		} else {
			return true;
		}
	}

	public static void delDir(File folder) {
		File[] files = folder.listFiles();
		if(files!=null) { //some JVMs return null for empty dirs
			for(File f: files) {
				if(f.isDirectory()) {
					delDir(f);
				} else {
					f.delete();
				}
			}
		}
		folder.delete();
	}

	public static void fixChmod(String path, int value) {
		Process p;
		try {
			//System.out.println("Executing recursive chmod " + value + " for files in " + path);
			p = Runtime.getRuntime().exec("chmod -R " + value + " " + path);
			p.waitFor();
			if(p.exitValue()!=0) {
				System.out.println("The permission change failed with exit value " + p.exitValue());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}	
	
}
